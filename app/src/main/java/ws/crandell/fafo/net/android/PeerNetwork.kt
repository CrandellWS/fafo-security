package ws.crandell.fafo.net.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ws.crandell.fafo.ble.BleAlertScan
import ws.crandell.fafo.ble.BleTransport
import ws.crandell.fafo.circles.CircleManager
import ws.crandell.fafo.net.Transport
import ws.crandell.fafo.net.TransportSelector
import ws.crandell.fafo.ui.NearbyPermissions
import ws.crandell.fafo.circles.CircleStore
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.link.MessageLink
import ws.crandell.fafo.net.AlertInfo
import ws.crandell.fafo.net.BeaconKeyStore
import ws.crandell.fafo.net.CommandTarget
import ws.crandell.fafo.net.InviteInbox
import ws.crandell.fafo.net.Message
import ws.crandell.fafo.net.PeerHub
import ws.crandell.fafo.pairing.CredentialGate
import ws.crandell.fafo.pairing.DelegatingGate
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.trust.TrustGraph
import java.io.File

/**
 * Process-wide local network (F9): one [PeerHub] plus its transports, running while at least one owner
 * holds it (the visible app, the armed service, Stay reachable, intercom room mode). Credential-protected
 * only: [acquire] refuses before the first unlock, so nothing here runs on the boot path (AC2.6a).
 */
object PeerNetwork {
    private const val TAG = "PeerNetwork"
    private val owners = HashSet<String>()
    private var scope: CoroutineScope? = null
    private var wifi: WifiTransport? = null
    private var ble: BleTransport? = null
    @Volatile var hub: PeerHub? = null
        private set
    @Volatile private var beacon: BeaconKeyStore? = null

    /** Invites waiting on the accept screen (AC8.6). */
    val invites = InviteInbox()

    /** Gate for flows started by the network (accepting invites); the visible activity sets the target. */
    val gate = DelegatingGate()

    private val _reachable = MutableStateFlow<Set<String>>(emptySet())
    /** Trusted peers reachable now (session open or seen on this Wi-Fi), for the reachable dots. */
    val reachable: StateFlow<Set<String>> = _reachable

    fun circles(context: Context): CircleStore = TrustGraph.circles(context)
    fun circleManager(context: Context, gate: CredentialGate = this.gate) = CircleManager(circles(context), gate)

    fun beaconKeys(context: Context): BeaconKeyStore = beacon ?: synchronized(this) {
        beacon ?: BeaconKeyStore(File(context.applicationContext.filesDir, "trust/beacon.key"), KeystoreKeyWrapper()).also { beacon = it }
    }

    @Synchronized
    fun acquire(context: Context, owner: String): PeerHub? {
        if (!DeviceStatus.isCeUnlocked(context)) return null
        owners += owner
        hub?.let { return it }
        return try {
            start(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "network start failed", e)
            owners -= owner
            stopLocked()
            null
        }
    }

    @Synchronized
    fun release(owner: String) {
        owners -= owner
        if (owners.isEmpty()) stopLocked()
    }

    fun isRunning(): Boolean = hub != null

    private fun start(app: Context): PeerHub {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        val trusted = TrustGraph.trusted(app)
        val circles = circles(app)
        val keys = beaconKeys(app)
        keys.key()
        val h = PeerHub(
            scope = s,
            identity = TrustGraph.keys(),
            trusted = trusted,
            circles = circles,
            circleManager = circleManager(app),
            myLabel = { CeStore.get(app).read().deviceLabel },
            beaconKey = { keys.key() },
            events = object : PeerHub.Events {
                override fun onAlert(peer: TrustedDevice, label: String, alert: Message.Alert) {
                    AlertNotifier.post(app, peer.fingerprint, label, alert)
                }

                override fun onInvite(peer: TrustedDevice, invite: Message.CircleInvite) {
                    val (_, answer) = invites.post(
                        peer.fingerprint, hub?.localLabel(peer.fingerprint) ?: invite.inviterLabel,
                        invite.circleId, invite.name, Permission.fromBits(invite.permissions), invite.expiresAt,
                    )
                    s.launch {
                        val accepted = kotlinx.coroutines.withTimeoutOrNull((invite.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)) { answer.await() }
                        hub?.answerInvite(peer.fingerprint, invite.circleId, accepted != null)
                    }
                    AlertNotifier.postInvite(app, hub?.localLabel(peer.fingerprint) ?: invite.inviterLabel, invite.name)
                }
            },
        )
        val w = WifiTransport(
            app, s, ownBeaconKey = { keys.key() },
            peerBeaconKeys = { trusted.all().mapNotNull { d -> d.beaconKey?.let { d.fingerprint to it } }.toMap() },
            onIncoming = { link -> h.accept(link) },
        )
        val b = if (NearbyPermissions.missing(app).isEmpty()) {
            BleTransport(
                app, s, ownBeaconKey = { keys.key() },
                peerBeaconKeys = { trusted.all().mapNotNull { d -> d.beaconKey?.let { d.fingerprint to it } }.toMap() },
                onIncoming = { link -> h.accept(link) },
            )
        } else {
            null
        }
        // AC9.3: Wi-Fi when both are on the same network, else Bluetooth.
        h.dialer = { fp ->
            val order = TransportSelector.order(
                onWifi = w.onWifi, seenOnWifi = fp in w.reachablePeers(),
                seenOnBluetooth = b != null && fp in b.reachablePeers(), bluetoothOn = b?.bluetoothOn == true,
            )
            var links = emptyList<MessageLink>()
            for (t in order) {
                links = if (t == Transport.WIFI) w.dial(fp, tryUnknown = false) else b?.dial(fp).orEmpty()
                if (links.isNotEmpty()) break
            }
            // A peer whose beacon key we do not have yet: try the unrecognised FAFO services on this Wi-Fi.
            if (links.isEmpty() && trusted.get(fp)?.beaconKey == null) links = w.dial(fp, tryUnknown = true)
            links
        }
        ble = b
        w.onNetworkChanged = { h.disconnectAll() } // AC9.4: sessions end when the network changes
        wifi = w
        hub = h
        w.start()
        b?.start()
        if (b != null && trusted.all().isNotEmpty()) BleAlertScan.register(app)
        s.launch {
            h.connected.collect { publishReachable() }
        }
        return h
    }

    fun publishReachable() {
        val h = hub ?: return run { _reachable.value = emptySet() }
        _reachable.value = h.connected.value + (wifi?.reachablePeers() ?: emptySet()) + (ble?.reachablePeers() ?: emptySet())
    }

    fun wifiTransport(): WifiTransport? = wifi

    private fun stopLocked() {
        hub?.stopAlerts()
        hub?.disconnectAll()
        wifi?.stop()
        ble?.stop()
        scope?.cancel()
        hub = null
        wifi = null
        ble = null
        scope = null
        _reachable.value = emptySet()
    }

    /** "Remove device" (AC8.8, AC9.6): forget it everywhere, rotate the beacon key, re-send it to the rest. */
    suspend fun forget(context: Context, fingerprint: String) {
        circleManager(context).forgetPeerEverywhere(fingerprint)
        beaconKeys(context).rotate()
        hub?.disconnect(fingerprint)
        hub?.resendHello()
    }

    /** The armed service's view (ArmDeps.network). */
    class ForAlarm(private val context: Context) : AlarmNetwork {
        override fun attach(target: CommandTarget): Boolean {
            val h = acquire(context, OWNER_ARM) ?: return false
            h.commandTarget = target
            return true
        }

        override fun detach() {
            hub?.commandTarget = null
            hub?.stopAlerts()
            ble?.alarming = false
            release(OWNER_ARM)
        }

        override fun startAlerts(info: () -> AlertInfo?) {
            hub?.startAlerts(info)
            ble?.alarming = true // AC9.7: Bluetooth alert beacon while alarming
        }

        override fun stopAlerts() {
            hub?.stopAlerts()
            ble?.alarming = false
        }
    }

    const val OWNER_ARM = "arm"
    const val OWNER_UI = "ui"
}

/** What ArmService needs from the network; tests swap it out. */
interface AlarmNetwork {
    fun attach(target: CommandTarget): Boolean
    fun detach()
    fun startAlerts(info: () -> AlertInfo?)
    fun stopAlerts()

    object None : AlarmNetwork {
        override fun attach(target: CommandTarget) = false
        override fun detach() {}
        override fun startAlerts(info: () -> AlertInfo?) {}
        override fun stopAlerts() {}
    }
}
