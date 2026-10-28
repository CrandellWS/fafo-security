package ws.crandell.fafo.mail

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ws.crandell.fafo.camera.PhotoStore
import ws.crandell.fafo.mail.flavor.FlavorMail
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.system.DeviceStatus

/**
 * Photo email queue runner (round 3). No new service and no WorkManager: it drains only while a host is
 * alive (the armed service, the visible app, or Stay reachable) and only on a network with INTERNET and
 * VALIDATED (captive portals are skipped). Mail queued when the owner disarms waits for the next host.
 */
object MailDispatcher {
    const val HOST_ARMED = "armed"
    const val HOST_APP = "app"
    const val HOST_REACHABLE = "reachable"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hosts = mutableSetOf<String>()
    private val lock = Mutex()
    private var app: Context? = null
    private var job: Job? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var validated: Network? = null

    private val _status = MutableStateFlow(MailStatus())
    val status: StateFlow<MailStatus> = _status

    /** Fires when queued mail has no validated network: the open Wi-Fi fallback (spec F12). */
    @Volatile var onNoNetwork: ((Context) -> Unit)? = { OpenWifiFallback.onNoNetwork(it) }
    /** A specific network to send on (validated open Wi-Fi from the fallback); null = default network. */
    @Volatile var preferredNetwork: Network? = null

    fun outbox(context: Context) = Outbox(Outbox.root(context.applicationContext.filesDir))

    @Synchronized fun acquire(context: Context, host: String) {
        if (!DeviceStatus.isCeUnlocked(context)) return
        app = context.applicationContext
        if (hosts.add(host) && hosts.size == 1) start()
        kick()
    }

    @Synchronized fun release(host: String) {
        if (hosts.remove(host) && hosts.isEmpty()) stop()
    }

    /** A trigger photo was saved: queue it if photo email is on, then try to send. */
    fun photoSaved(context: Context, incidentId: String, name: String, now: Long) {
        scope.launch {
            val cfg = runCatching { CeStore.get(context).read().mailConfig() }.getOrNull() ?: return@launch
            if (!cfg.enabled || !cfg.complete) return@launch
            outbox(context).enqueuePhoto(incidentId, name, now)
            refreshStatus(context)
            kick()
        }
    }

    /** Photos still waiting for email; the photo cap must not prune them. */
    fun pinned(context: Context): Set<Pair<String, String>> = runCatching { outbox(context).pinned() }.getOrDefault(emptySet())

    fun discardAll(context: Context) {
        outbox(context).discardAll()
        refreshStatus(context)
    }

    /** Send on [network] (validated open Wi-Fi from the fallback), or back to the default network (null). */
    fun useNetwork(network: Network?) {
        preferredNetwork = network
        if (network != null) kick()
    }

    fun sendNow() {
        app?.let { outbox(it).resetBackoff(System.currentTimeMillis()) }
        kick()
    }

    private fun start() {
        val ctx = app ?: return
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val ok = usable(caps)
                val was = validated
                validated = if (ok) network else if (validated == network) null else validated
                if (ok && was == null) { outbox(ctx).resetBackoff(System.currentTimeMillis()); kick() }
            }
            override fun onLost(network: Network) { if (validated == network) validated = null }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { callback = cb }
        refreshStatus(ctx)
    }

    private fun stop() {
        job?.cancel()
        job = null
        val ctx = app ?: return
        callback?.let { cb -> runCatching { ctx.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
        callback = null
        validated = null
    }

    /** INTERNET and VALIDATED; a captive portal is never treated as a connection. */
    fun usable(caps: NetworkCapabilities): Boolean =
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)

    @Synchronized private fun kick() {
        val ctx = app ?: return
        if (hosts.isEmpty() || job?.isActive == true) return
        job = scope.launch {
            while (hosts.isNotEmpty()) {
                val next = lock.withLock {
                    val net = preferredNetwork ?: validated
                    if (net == null) {
                        if (outbox(ctx).pending().isNotEmpty()) onNoNetwork?.invoke(ctx)
                        null
                    } else runCatching {
                        val d = drainer(ctx, net)
                        d.drainOnce().also { record(d) }
                    }.onFailure { Log.w(TAG, "drain failed: ${it.javaClass.simpleName}") }.getOrNull()
                }
                refreshStatus(ctx)
                if (next == null) break
                delay((next - System.currentTimeMillis()).coerceIn(1_000L, 15 * 60_000L))
            }
        }
    }

    // Totals across passes (each pass gets a fresh MailDrain bound to the current network).
    @Volatile private var sentTotal = 0
    @Volatile private var lastError: String? = null

    private fun record(d: MailDrain) {
        sentTotal += d.sentPhotos
        if (d.lastError != null) lastError = d.lastError else if (d.sentPhotos > 0) lastError = null
    }

    private fun drainer(ctx: Context, net: Network): MailDrain {
        val store = PhotoStore(PhotoStore.root(ctx.filesDir))
        return MailDrain(
            outbox(ctx),
            config = { CeStore.get(ctx).read().mailConfig() },
            senderFor = { cfg -> senderFor(ctx, cfg, net) },
            photoFile = store::photoFile,
            deviceLabel = { CeStore.get(ctx).read().deviceLabel },
            clock = System::currentTimeMillis,
        )
    }

    /** SMTP (every build) or Gmail (play flavor), bound to [net]. */
    fun senderFor(ctx: Context, cfg: MailConfig, net: Network?): MailSender? = when (cfg.provider) {
        MailProviderKind.SMTP -> SmtpClient(
            SocketSmtpConnector(net?.socketFactory ?: javax.net.SocketFactory.getDefault()),
            cfg.host, cfg.port, cfg.security, cfg.user,
            password = { kotlinx.coroutines.runBlocking { SecretBox.open(KeystoreSecretCipher(), CeStore.get(ctx).read().mailSecret) } },
        )
        MailProviderKind.GMAIL -> FlavorMail.gmailSender(ctx, cfg.gmailAccount, net)
    }

    private fun refreshStatus(ctx: Context) {
        val o = outbox(ctx)
        if (o.pending().isEmpty()) runCatching { OpenWifiFallback.maybeCleanup(ctx) }
        _status.value = MailStatus(
            pending = o.pending().sumOf { it.photos.size },
            sentPhotos = sentTotal,
            lastError = lastError,
            failedPermanently = o.all().count { it.state == OutboxState.FAILED_PERMANENT },
        )
    }

    private const val TAG = "MailDispatcher"
}
