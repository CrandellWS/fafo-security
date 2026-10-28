package ws.crandell.fafo.net.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.link.MessageLink
import ws.crandell.fafo.net.BeaconTag
import ws.crandell.fafo.net.LocalAddressPolicy
import ws.crandell.fafo.net.StreamLink
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * F9 over Wi-Fi: `_fafo._tcp` via NsdManager (random instance name, TXT `v` + rotating `t` tag), a TCP
 * listener bound to the Wi-Fi address, outgoing sockets bound to the Wi-Fi [Network] (never mobile data).
 * Every address goes through [LocalAddressPolicy]: accepted peers, dialed peers and NSD answers.
 */
class WifiTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ownBeaconKey: () -> ByteArray,
    /** Trusted peers' beacon keys by fingerprint. */
    private val peerBeaconKeys: () -> Map<String, ByteArray>,
    private val onIncoming: suspend (MessageLink) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val nsd = context.getSystemService(NsdManager::class.java)
    @Volatile private var network: Network? = null
    @Volatile private var localV4: InetAddress? = null
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null
    private var registerJob: Job? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val resolveLock = Mutex()
    private val endpoints = ConcurrentHashMap<String, Pair<InetSocketAddress, Long>>()
    /** Services whose tag matched no known key: tried for peers whose beacon key we do not have yet. */
    private val unknownEndpoints = ConcurrentHashMap<String, Pair<InetSocketAddress, Long>>()
    @Volatile var onNetworkChanged: () -> Unit = {}

    val onWifi: Boolean get() = network != null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) {
            if (network == null) {
                network = n
                scope.launch { restart() }
            }
        }

        override fun onLinkPropertiesChanged(n: Network, lp: LinkProperties) {
            if (n == network && lp.linkAddresses.map { it.address }.none { it == localV4 }) scope.launch { restart() }
        }

        override fun onLost(n: Network) {
            if (n == network) {
                network = null
                scope.launch { stopServing() }
            }
        }
    }

    fun start() {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm?.registerNetworkCallback(req, callback) }.onFailure { Log.w(TAG, "network callback", it) }
    }

    fun stop() {
        runCatching { cm?.unregisterNetworkCallback(callback) }
        network = null
        scope.launch { stopServing() }
    }

    private suspend fun restart() {
        stopServing()
        val n = network ?: return
        val lp = cm?.getLinkProperties(n) ?: return
        val addr = lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && LocalAddressPolicy.isAllowed(it) }
            ?: return
        localV4 = addr
        onNetworkChanged()
        val ss = withContext(Dispatchers.IO) {
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(addr, 0)) } }.getOrNull()
        } ?: return
        server = ss
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(ss) }
        registerJob = scope.launch { registerLoop(ss.localPort) }
        startDiscovery()
    }

    private suspend fun stopServing() {
        acceptJob?.cancel()
        registerJob?.cancel()
        runCatching { server?.close() }
        server = null
        unregister()
        stopDiscovery()
        endpoints.clear()
        unknownEndpoints.clear()
        localV4 = null
        onNetworkChanged()
    }

    private suspend fun acceptLoop(ss: ServerSocket) {
        while (scope.isActive && !ss.isClosed) {
            val s = runCatching { ss.accept() }.getOrNull() ?: break
            if (!LocalAddressPolicy.isAllowed(s.remoteSocketAddress)) {
                runCatching { s.close() }
                continue
            }
            s.tcpNoDelay = true
            val link = StreamLink(s.getInputStream(), s.getOutputStream()) { runCatching { s.close() } }
            scope.launch { onIncoming(link) }
        }
    }

    /** Re-register every slot so the tag rotates (AC9.1). */
    private suspend fun registerLoop(port: Int) {
        while (scope.isActive) {
            unregister()
            register(port)
            val now = clock()
            val next = (BeaconTag.slot(now, BeaconTag.NSD_SLOT_MS) + 1) * BeaconTag.NSD_SLOT_MS
            delay((next - now).coerceAtLeast(1_000))
        }
    }

    /** The NSD record: `_fafo._tcp`, random instance name (never the label), TXT `v` and `t` (AC9.1). */
    internal fun serviceInfo(port: Int): NsdServiceInfo = NsdServiceInfo().apply {
        serviceName = "fafo-" + Crypto.hex(Crypto.randomBytes(6)) // random per registration, never the label
        serviceType = SERVICE_TYPE
        setPort(port)
        setAttribute("v", "1")
        setAttribute("t", Crypto.hex(BeaconTag.current(ownBeaconKey(), clock(), BeaconTag.NSD_SLOT_MS)))
    }

    private fun register(port: Int) {
        val info = serviceInfo(port)
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { Log.w(TAG, "register failed $errorCode") }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registration = l
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { Log.w(TAG, "register", it) }
    }

    private fun unregister() {
        registration?.let { l -> runCatching { nsd?.unregisterService(l) } }
        registration = null
    }

    private fun startDiscovery() {
        if (discovery != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "discovery failed $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                scope.launch { resolve(serviceInfo) }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }
        discovery = l
        runCatching { nsd?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { Log.w(TAG, "discover", it) }
    }

    private fun stopDiscovery() {
        discovery?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        discovery = null
    }

    private suspend fun resolve(found: NsdServiceInfo) = resolveLock.withLock {
        val info = withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                @Suppress("DEPRECATION")
                nsd?.resolveService(
                    found,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { if (cont.isActive) cont.resume(null) }
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) { if (cont.isActive) cont.resume(serviceInfo) }
                    },
                ) ?: cont.resume(null)
            }
        } ?: return@withLock
        onResolved(info)
    }

    internal fun onResolved(info: NsdServiceInfo) {
        val addrs: List<InetAddress> = if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(info.host)
        }
        // AC9.2: a spoofed mDNS answer pointing at a public host is refused here.
        val addr = addrs.firstOrNull { LocalAddressPolicy.isAllowed(it) } ?: return
        val tagHex = info.attributes["t"]?.let { String(it, Charsets.UTF_8) } ?: return
        val tag = runCatching { Crypto.unhex(tagHex) }.getOrNull() ?: return
        if (BeaconTag.matches(ownBeaconKey(), tag, clock(), BeaconTag.NSD_SLOT_MS)) return // ourselves
        val ep = InetSocketAddress(addr, info.port) to clock()
        val owner = BeaconTag.findOwner(peerBeaconKeys(), tag, clock(), BeaconTag.NSD_SLOT_MS)
        if (owner != null) endpoints[owner] = ep else unknownEndpoints[info.serviceName] = ep
    }

    /** Peers seen on this Wi-Fi recently (reachable dot, transport choice). */
    fun reachablePeers(): Set<String> {
        val cutoff = clock() - ENDPOINT_TTL_MS
        return endpoints.filterValues { it.second >= cutoff }.keys
    }

    internal fun knownEndpoint(fp: String): InetSocketAddress? = endpoints[fp]?.first

    fun hasUnknownEndpoints(): Boolean = unknownEndpoints.isNotEmpty()

    /** Connects to [fp]'s advertised endpoint (or, without its beacon key yet, to unknown FAFO services). */
    suspend fun dial(fp: String, tryUnknown: Boolean): List<MessageLink> {
        val n = network ?: return emptyList()
        val targets = buildList {
            endpoints[fp]?.let { add(it.first) }
            if (tryUnknown && isEmpty()) addAll(unknownEndpoints.values.map { it.first })
        }
        return targets.mapNotNull { connect(n, it) }
    }

    private suspend fun connect(n: Network, addr: InetSocketAddress): MessageLink? = withContext(Dispatchers.IO) {
        if (!LocalAddressPolicy.isAllowed(addr)) return@withContext null
        val s: Socket = runCatching { n.socketFactory.createSocket() }.getOrNull() ?: return@withContext null
        try {
            s.connect(addr, CONNECT_TIMEOUT_MS)
            s.tcpNoDelay = true
            if (!LocalAddressPolicy.isAllowed(s.remoteSocketAddress)) throw SecurityException("non-local")
            StreamLink(s.getInputStream(), s.getOutputStream()) { runCatching { s.close() } }
        } catch (e: Exception) {
            runCatching { s.close() }
            null
        }
    }

    /** The Wi-Fi network, for UDP audio sockets (F10). */
    fun currentNetwork(): Network? = network
    fun localAddress(): InetAddress? = localV4

    companion object {
        private const val TAG = "WifiTransport"
        const val SERVICE_TYPE = "_fafo._tcp"
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val ENDPOINT_TTL_MS = 15 * 60 * 1000L
    }
}
