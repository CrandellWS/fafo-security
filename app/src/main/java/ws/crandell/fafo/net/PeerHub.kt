package ws.crandell.fafo.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import ws.crandell.fafo.circles.CircleManager
import ws.crandell.fafo.circles.CirclePolicy
import ws.crandell.fafo.circles.CircleStore
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.link.MessageLink
import ws.crandell.fafo.pairing.IdentityKeys
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.pairing.TrustedDeviceStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** The armed service's side of remote commands (AC9.8). */
interface CommandTarget {
    /** Open incident id, or null when there is nothing to reset or disarm. */
    fun currentIncident(): String?

    /** Apply a verified command; returns once the state machine took it (target: within 500 ms). */
    suspend fun apply(cmd: Command, peerLabel: String, circleId: String): Boolean
}

/** Alert payload while an incident is open (AC9.7). */
data class AlertInfo(val incidentId: String, val state: String, val startedAt: Long)

/** F10 hooks: the intercom gets the intercom / push-to-talk messages of verified sessions. */
interface IntercomEndpoint {
    suspend fun onMessage(peer: String, m: Message, conn: PeerHub.Conn)
    fun onDisconnected(peer: String) {}
}

private class Waiter(val match: (Message) -> Boolean) {
    val result = CompletableDeferred<Message>()
}

sealed interface CommandOutcome {
    data object Applied : CommandOutcome
    data object Unreachable : CommandOutcome
    data object Cancelled : CommandOutcome
    data class Refused(val reason: String) : CommandOutcome
}

/**
 * F8/F9 session layer, transport-agnostic and pure Kotlin: authenticated sessions (SessionHandshake +
 * SecureChannel), message dispatch, permission checks on everything received, alerts, invites and remote
 * commands. Transports hand it links ([accept]) and a [dialer] for outgoing connections.
 */
class PeerHub(
    private val scope: CoroutineScope,
    private val identity: IdentityKeys,
    private val trusted: TrustedDeviceStore,
    private val circles: CircleStore,
    private val circleManager: CircleManager,
    private val myLabel: suspend () -> String,
    private val beaconKey: () -> ByteArray,
    private val events: Events,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    interface Events {
        fun onAlert(peer: TrustedDevice, label: String, alert: Message.Alert)
        fun onInvite(peer: TrustedDevice, invite: Message.CircleInvite)
        fun onInviteAccepted(peer: TrustedDevice, circleName: String, accepted: Boolean) {}
    }

    /** Candidate outgoing links to a trusted peer by fingerprint (Wi-Fi first, then Bluetooth). */
    @Volatile var dialer: (suspend (String) -> List<MessageLink>)? = null
    @Volatile var commandTarget: CommandTarget? = null
    @Volatile var intercom: IntercomEndpoint? = null

    private val trust = TrustLookup { fp -> trusted.get(fp)?.identityPublic }
    private val book = ChallengeBook(clock)
    private val verifier = CommandVerifier(identity.fingerprint(), book, { circles.all() }) { fp -> trusted.get(fp)?.commandPublic }
    private val conns = ConcurrentHashMap<String, Conn>()
    private val dialLocks = ConcurrentHashMap<String, Mutex>()
    private val pendingInvites = ConcurrentHashMap<Pair<String, String>, Long>()
    private val suggested = ConcurrentHashMap<String, String>()
    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    /** Peers with a live session right now. */
    val connected: StateFlow<Set<String>> = _connected
    private var alertJob: Job? = null

    inner class Conn(val channel: SecureChannel) {
        val peer: String get() = channel.peer
        val keys: SessionKeys get() = channel.keys
        private val waiters = CopyOnWriteArrayList<Waiter>()

        suspend fun send(m: Message) = channel.send(m)

        /** Sends [req] and waits for the first reply that [match]es. */
        suspend fun request(req: Message, timeoutMs: Long = 10_000, match: (Message) -> Boolean): Message? {
            val w = Waiter(match)
            waiters += w
            try {
                send(req)
                return withTimeoutOrNull(timeoutMs) { w.result.await() }
            } finally {
                waiters -= w
            }
        }

        internal fun offer(m: Message): Boolean {
            val w = waiters.firstOrNull { it.match(m) } ?: return false
            waiters -= w
            return w.result.complete(m)
        }

        fun close() = channel.close()
    }

    /** Responder side of an incoming link. Unknown keys and bad signatures close it without a reply. */
    suspend fun accept(link: MessageLink): Conn? = try {
        val ch = withTimeout(HANDSHAKE_TIMEOUT_MS) { SecureChannel.respond(link, SessionHandshake.Responder(identity, trust), clock) }
        adopt(ch)
    } catch (e: Exception) {
        link.close()
        null
    }

    /** A live session to [fp], dialing if needed. */
    suspend fun connect(fp: String): Conn? {
        conns[fp]?.takeIf { !it.channel.expired }?.let { return it }
        if (trusted.get(fp) == null) return null
        val lock = dialLocks.getOrPut(fp) { Mutex() }
        return lock.withLock {
            conns[fp]?.takeIf { !it.channel.expired }?.let { return@withLock it }
            val links = runCatching { dialer?.invoke(fp) }.getOrNull().orEmpty()
            var result: Conn? = null
            for (link in links) {
                if (result != null) {
                    link.close()
                    continue
                }
                result = try {
                    val ch = withTimeout(HANDSHAKE_TIMEOUT_MS) {
                        SecureChannel.initiate(link, SessionHandshake.Initiator(identity, trust, expectedPeer = fp), clock)
                    }
                    adopt(ch)
                } catch (e: Exception) {
                    link.close()
                    null
                }
            }
            result
        }
    }

    /** Runs a finished session: Hello first, then the receive loop. */
    fun adopt(ch: SecureChannel): Conn {
        val conn = Conn(ch)
        conns.put(ch.peer, conn)?.takeIf { it !== conn }?.close()
        publish()
        scope.launch {
            try {
                conn.send(Message.Hello(myLabel(), beaconKey()))
                while (isActive) {
                    val m = ch.receive()
                    if (!conn.offer(m)) handle(conn, m)
                }
            } catch (e: Exception) {
                // link gone, expired or bad frame
            } finally {
                ch.close()
                conns.remove(ch.peer, conn)
                publish()
                intercom?.onDisconnected(ch.peer)
            }
        }
        return conn
    }

    private fun publish() {
        _connected.value = conns.keys.toSet()
    }

    fun disconnect(fp: String) {
        conns.remove(fp)?.close()
        publish()
    }

    fun disconnectAll() {
        conns.values.forEach { it.close() }
        conns.clear()
        publish()
    }

    /** The label this device shows for [fp]: its local circle label, else the paired name. */
    fun localLabel(fp: String): String =
        circles.all().firstNotNullOfOrNull { it.member(fp)?.label } ?: trusted.get(fp)?.name ?: suggested[fp] ?: "Device"

    private suspend fun handle(conn: Conn, m: Message) {
        val fp = conn.peer
        val peer = trusted.get(fp) ?: return conn.close()
        when (m) {
            is Message.Hello -> {
                suggested[fp] = m.label
                m.beaconKey?.takeIf { it.size == 32 }?.let { trusted.setBeaconKey(fp, it) }
            }
            is Message.CircleInvite -> if (m.expiresAt > clock()) events.onInvite(peer, m)
            is Message.CircleAccept -> {
                val expires = pendingInvites.remove(fp to m.circleId) ?: return
                val circle = circles.get(m.circleId) ?: return
                if (expires > clock() && m.accepted) {
                    // The owner passed the credential when sending the invite (AC8.5).
                    circleManager.addMember(m.circleId, fp, m.label, alreadyAuthorized = true)
                }
                events.onInviteAccepted(peer, circle.name, m.accepted && expires > clock())
            }
            is Message.Alert -> events.onAlert(peer, localLabel(fp), m)
            is Message.CommandChallenge -> {
                val cmd = Command.of(m.command) ?: return
                if (m.challenge.isNotEmpty()) return
                val v = verifier.mayChallenge(fp, cmd, m.circleId, m.incidentId, commandTarget?.currentIncident())
                if (v == Verdict.OK) {
                    conn.send(Message.CommandChallenge(m.circleId, cmd.code, m.incidentId, book.issue(fp, m.circleId, cmd, m.incidentId)))
                } else {
                    conn.send(Message.CommandResult(cmd.code, false, v.name))
                }
            }
            is Message.Reset -> command(conn, Command.RESET, m.circleId, m.incidentId, m.challenge, m.signature)
            is Message.Disarm -> command(conn, Command.DISARM, m.circleId, m.incidentId, m.challenge, m.signature)
            is Message.IntercomListen, is Message.IntercomStop, is Message.PttStart, is Message.PttStop,
            is Message.IntercomListenReply, is Message.PttBusy,
            -> intercom?.onMessage(fp, m, conn)
            else -> Unit // Ping, stray replies, unknown types: ignored
        }
    }

    private suspend fun command(conn: Conn, cmd: Command, circleId: String, incidentId: String, challenge: ByteArray, sig: ByteArray) {
        val target = commandTarget
        val v = verifier.verify(conn.peer, cmd, circleId, incidentId, challenge, sig, target?.currentIncident())
        val ok = v == Verdict.OK && target != null && target.apply(cmd, localLabel(conn.peer), circleId)
        conn.send(Message.CommandResult(cmd.code, ok, if (ok) "" else v.name))
    }

    // --- outgoing ---

    /** AC9.8 sender: challenge, owner signs with the auth-bound command key ([sign]), command, result. */
    suspend fun sendCommand(
        fp: String,
        cmd: Command,
        circleId: String,
        incidentId: String,
        sign: suspend (payload: ByteArray) -> ByteArray?,
    ): CommandOutcome {
        val c = connect(fp) ?: return CommandOutcome.Unreachable
        val r = c.request(Message.CommandChallenge(circleId, cmd.code, incidentId, ByteArray(0))) {
            (it is Message.CommandChallenge && it.challenge.isNotEmpty() && it.incidentId == incidentId) ||
                (it is Message.CommandResult && it.command == cmd.code)
        } ?: return CommandOutcome.Unreachable
        if (r is Message.CommandResult) return CommandOutcome.Refused(r.reason)
        val challenge = (r as Message.CommandChallenge).challenge
        val sig = sign(CommandPayload.build(cmd, circleId, fp, incidentId, challenge)) ?: return CommandOutcome.Cancelled
        val msg = if (cmd == Command.RESET) Message.Reset(circleId, incidentId, challenge, sig) else Message.Disarm(circleId, incidentId, challenge, sig)
        val result = (connect(fp) ?: return CommandOutcome.Unreachable)
            .request(msg) { it is Message.CommandResult && it.command == cmd.code } as? Message.CommandResult
            ?: return CommandOutcome.Unreachable
        return if (result.ok) CommandOutcome.Applied else CommandOutcome.Refused(result.reason)
    }

    /** AC8.6: invite a trusted device into a circle. The caller already passed the credential gate. */
    suspend fun invite(fp: String, circleId: String): Boolean {
        val circle = circles.get(circleId) ?: return false
        val c = connect(fp) ?: return false
        val expires = clock() + INVITE_TTL_MS
        pendingInvites[fp to circleId] = expires
        c.send(Message.CircleInvite(circleId, circle.name, Permission.toBits(circle.permissions), myLabel(), expires))
        return true
    }

    suspend fun answerInvite(fp: String, circleId: String, accepted: Boolean): Boolean {
        val c = connect(fp) ?: return false
        c.send(Message.CircleAccept(circleId, accepted, myLabel()))
        return true
    }

    /** AC9.7: alert every member of every circle that grants Alerts, every 30 s while [info] is non-null. */
    fun startAlerts(info: () -> AlertInfo?) {
        alertJob?.cancel()
        alertJob = scope.launch {
            while (isActive) {
                val i = info() ?: break
                sendAlertsOnce(i)
                delay(ALERT_RETRY_MS)
            }
        }
    }

    fun stopAlerts() {
        alertJob?.cancel()
        alertJob = null
    }

    suspend fun sendAlertsOnce(i: AlertInfo) {
        val label = myLabel()
        for (alert in alertsFor(i, label)) {
            scope.launch { runCatching { connect(alert.first)?.send(alert.second) } }
        }
    }

    /** Which peers get an alert and under which circle (the one granting the most powers). Pure. */
    fun alertsFor(i: AlertInfo, label: String): List<Pair<String, Message.Alert>> {
        val all = circles.all()
        return trusted.all().mapNotNull { d ->
            val grant = CirclePolicy.circlesGranting(all, d.fingerprint, Permission.ALERTS)
                .maxByOrNull { CirclePolicy.powers(all, it.id, d.fingerprint).size } ?: return@mapNotNull null
            val powers = CirclePolicy.powers(all, grant.id, d.fingerprint).filter { it in Permission.GUARDED }.toSet()
            d.fingerprint to Message.Alert(grant.id, i.state, i.incidentId, i.startedAt, label, Permission.toBits(powers))
        }
    }

    /** Re-send Hello (new beacon key after a rotation, AC9.6) to every live session. */
    suspend fun resendHello() {
        val label = myLabel()
        conns.values.forEach { runCatching { it.send(Message.Hello(label, beaconKey())) } }
    }

    fun conn(fp: String): Conn? = conns[fp]

    companion object {
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        const val INVITE_TTL_MS = 10 * 60 * 1000L
        const val ALERT_RETRY_MS = 30_000L
    }
}
