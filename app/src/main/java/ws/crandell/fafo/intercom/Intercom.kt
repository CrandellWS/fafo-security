package ws.crandell.fafo.intercom

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.core.isAlarming
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.net.IntercomEndpoint
import ws.crandell.fafo.net.Message
import ws.crandell.fafo.net.PeerHub
import ws.crandell.fafo.net.Transport
import ws.crandell.fafo.net.android.PeerNetwork
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.trust.TrustGraph
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** What the Intercom screen and the room-mode notification show. */
data class IntercomUi(
    val roomCircles: Set<String>? = null,
    val listeners: List<String> = emptyList(),
    val talker: String? = null,
    val listeningTo: String? = null,
    val talking: Boolean = false,
    val note: String? = null,
)

/**
 * F10 runtime: intercom room mode (listen) and push-to-talk between devices of a circle. Rules come from
 * [IntercomPolicy] (room mode only from the local UI, never during an alarm, listeners need Intercom in a
 * chosen circle, mic open only while needed); audio is live only, encrypted with the session's audio keys
 * ([AudioFrameCrypto]) and sent over UDP on Wi-Fi or an L2CAP channel on Bluetooth. Nothing is recorded.
 */
object Intercom : IntercomEndpoint {
    private const val TAG = "Intercom"
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val policy by lazy { IntercomPolicy { TrustGraph.circles(app).all() } }
    private val floor = FloorControl()
    private var sockets: AudioSockets? = null
    private val _ui = MutableStateFlow(IntercomUi())
    val ui: StateFlow<IntercomUi> = _ui

    private class Outgoing(val peer: String, val route: AudioRoute, val streamId: Int, val codec: Codec, val crypto: AudioFrameCrypto)
    private class Incoming(val peer: String, val streamId: Int, val decoder: FrameCodec, val jitter: JitterBuffer = JitterBuffer())

    /** Audio crypto per session, found by the clear session id at the front of each packet. */
    private val sessions = ConcurrentHashMap<String, Pair<String, AudioFrameCrypto>>()
    private val listeners = ConcurrentHashMap<String, Outgoing>()
    private val pttTargets = CopyOnWriteArrayList<Outgoing>()
    @Volatile private var incoming: Incoming? = null
    @Volatile private var listenStream: Int? = null
    private val encoders = ConcurrentHashMap<Codec, FrameCodec>()
    private var mic: MicCapture? = null
    private var speaker: Speaker? = null
    @Volatile private var attached = false

    /** Called by PeerNetwork when the hub starts. Safe to call repeatedly. */
    fun attach(context: Context) {
        if (attached) return
        attached = true
        app = context.applicationContext
        AlarmStatusBus.endIntercom = { onIncident(true) }
        scope.launch { AlarmStatusBus.state.collect { s -> if (!s.isAlarming) policy.onIncident(false) else onIncident(true) } }
    }

    private fun hub(): PeerHub? = PeerNetwork.hub

    private fun label(fp: String) = hub()?.localLabel(fp) ?: "Device"

    private fun publish(note: String? = _ui.value.note) {
        _ui.value = IntercomUi(
            roomCircles = policy.roomCircles,
            listeners = policy.listeners.map(::label),
            talker = incoming?.takeIf { listenStream == null }?.peer?.let(::label),
            listeningTo = incoming?.takeIf { listenStream != null }?.peer?.let(::label),
            talking = policy.localTalking,
            note = note,
        )
    }

    private fun sockets(): AudioSockets = sockets ?: AudioSockets(app, ::onPacket).also { sockets = it }

    private fun transportOf(conn: PeerHub.Conn): Transport = if (conn.channel.remote is InetAddress) Transport.WIFI else Transport.BLUETOOTH

    /** This phone's audio port for a session: UDP port on Wi-Fi, L2CAP PSM on Bluetooth. */
    private fun localPort(t: Transport): Int = if (t == Transport.WIFI) {
        val w = PeerNetwork.wifiTransport()
        sockets().udpPort(w?.currentNetwork(), w?.localAddress())
    } else {
        sockets().l2capPsm()
    }

    private fun route(conn: PeerHub.Conn, port: Int): AudioRoute? = when (val r = conn.channel.remote) {
        is InetAddress -> AudioRoute.Udp(InetSocketAddress(r, port))
        is BluetoothDevice -> sockets().connectL2cap(r, port)
        else -> null
    }

    private fun cryptoFor(conn: PeerHub.Conn): AudioFrameCrypto {
        val sid = Crypto.hex(conn.keys.sessionId)
        return sessions.getOrPut(sid) { conn.peer to AudioFrameCrypto(conn.keys.audioSend, conn.keys.audioReceive, conn.keys.sessionId) }.second
    }

    private fun decodable(): Set<Codec> = buildSet {
        add(Codec.PCM)
        if (OpusSupport.hasDecoder()) add(Codec.OPUS)
    }

    private fun encoder(c: Codec): FrameCodec? = encoders[c] ?: runCatching {
        (if (c == Codec.OPUS) OpusEncoder() else PcmCodec()).also { encoders[c] = it }
    }.onFailure { Log.w(TAG, "encoder", it) }.getOrNull()

    private fun tone(kind: Int, ms: Int = 150) {
        runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).startTone(kind, ms) }
    }

    // --- room mode (listen side of this phone) ---

    /** From IntercomService only, which the visible app started (AC10.1, AC10.2). */
    fun startRoomMode(context: Context, circles: Set<String>): RoomDecision {
        attach(context)
        if (AlarmStatusBus.state.value.isAlarming) policy.onIncident(true)
        val d = policy.startRoomMode(circles, RoomModeSource.LOCAL_UI, DeviceStatus.notificationsEnabled(context) && DeviceStatus.hasNotificationPermission(context))
        if (d == RoomDecision.STARTED) PeerNetwork.acquire(context, OWNER)
        publish()
        return d
    }

    fun stopRoomMode() {
        val dropped = policy.stopRoomMode()
        dropped.forEach { fp -> listeners.remove(fp)?.let { o -> scope.launch { runCatching { hub()?.conn(fp)?.send(Message.IntercomStop(o.streamId)) } } } }
        listeners.clear()
        updateMic()
        PeerNetwork.release(OWNER)
        publish()
    }

    /** AC10.8: alarm takes priority. Room mode off, listeners dropped, mic closed, playback stopped. */
    fun onIncident(open: Boolean) {
        if (!attached) return
        if (!open) {
            policy.onIncident(false)
            return
        }
        val hadRoom = policy.roomMode
        val dropped = policy.onIncident(true)
        dropped.forEach { fp -> listeners.remove(fp)?.let { o -> scope.launch { runCatching { hub()?.conn(fp)?.send(Message.IntercomStop(o.streamId)) } } } }
        listeners.clear()
        stopTalkLocal()
        stopPlayback()
        updateMic()
        if (hadRoom) PeerNetwork.release(OWNER)
        publish("Alarm: intercom off")
    }

    private fun updateMic() {
        if (policy.micOpen) {
            if (!DeviceStatus.hasPermission(app, Manifest.permission.RECORD_AUDIO)) return
            val m = mic ?: MicCapture(::onMicFrame).also { mic = it }
            m.open()
        } else {
            mic?.close()
            mic = null
        }
    }

    private fun onMicFrame(pcm: ShortArray) {
        val targets = listeners.values + pttTargets
        if (targets.isEmpty()) return
        val encoded = targets.map { it.codec }.toSet().associateWith { c -> encoder(c)?.encode(pcm).orEmpty() }
        for (o in targets) for (frame in encoded[o.codec].orEmpty()) sockets?.send(o.route, o.crypto.seal(o.streamId, frame))
    }

    // --- incoming audio ---

    private fun onPacket(packet: ByteArray, from: AudioRoute) {
        val sid = AudioFrameCrypto.sessionIdOf(packet)?.let(Crypto::hex) ?: return
        val (peer, crypto) = sessions[sid] ?: return
        val (stream, counter, frame) = crypto.open(packet) ?: return
        val inc = incoming ?: return
        if (inc.peer == peer && inc.streamId == stream) inc.jitter.push(counter, frame)
    }

    private fun startPlayback(inc: Incoming) {
        incoming?.decoder?.release()
        incoming = inc
        val sp = speaker ?: Speaker { incoming?.let { i -> i.jitter.pop()?.let { runCatching { i.decoder.decode(it) }.getOrNull() } } }.also { speaker = it }
        sp.start((inc.decoder as? OpusDecoder)?.outputRate ?: AudioFormat.SAMPLE_RATE)
    }

    private fun stopPlayback() {
        speaker?.stop()
        speaker = null
        incoming?.decoder?.release()
        incoming = null
        listenStream = null
        floor.clear()
    }

    // --- messages from verified sessions (PeerHub) ---

    override suspend fun onMessage(peer: String, m: Message, conn: PeerHub.Conn) {
        when (m) {
            is Message.IntercomListen -> onListenRequest(peer, m, conn)
            is Message.IntercomStop -> {
                if (listeners.remove(peer)?.streamId == m.streamId) policy.removeListener(peer)
                if (incoming?.peer == peer && incoming?.streamId == m.streamId) stopPlayback()
                updateMic()
                publish()
            }
            is Message.PttStart -> onPttStart(peer, m, conn)
            is Message.PttStop -> if (incoming?.peer == peer && incoming?.streamId == m.streamId && listenStream == null) {
                floor.onStop(peer, m.streamId)
                stopPlayback()
                tone(ToneGenerator.TONE_PROP_BEEP2)
                publish()
            }
            is Message.PttBusy -> {
                pttTargets.removeAll { it.peer == peer && it.streamId == m.streamId }
                tone(ToneGenerator.TONE_SUP_BUSY, 600)
                publish("${label(peer)} is busy")
            }
            else -> Unit
        }
    }

    override fun onDisconnected(peer: String) {
        if (!attached) return
        listeners.remove(peer)
        policy.removeListener(peer)
        floor.onDisconnected(peer)
        pttTargets.removeAll { it.peer == peer }
        if (incoming?.peer == peer) stopPlayback()
        updateMic()
        publish()
    }

    private suspend fun onListenRequest(peer: String, m: Message.IntercomListen, conn: PeerHub.Conn) {
        val t = transportOf(conn)
        suspend fun refuse(why: String) = conn.send(Message.IntercomListenReply(m.streamId, false, 0, ByteArray(0), 0, why))
        val d = policy.mayListen(peer, m.circleId)
        if (d != ListenDecision.OK) return refuse(d.name)
        val codec = CodecSelector.select(OpusSupport.hasEncoder(), Codec.fromBits(m.codecs), t)
            ?: return refuse(CodecSelector.unavailableText(t) ?: "NO_CODEC")
        val enc = encoder(codec) ?: return refuse("NO_CODEC")
        val route = route(conn, m.udpPort) ?: return refuse("NO_ROUTE")
        if (!policy.addListener(peer, m.circleId)) return refuse("NOT_PERMITTED")
        listeners[peer] = Outgoing(peer, route, m.streamId, codec, cryptoFor(conn))
        conn.send(Message.IntercomListenReply(m.streamId, true, codec.id, enc.csd, localPort(t), ""))
        if (runCatching { CeStore.get(app).read().chimeOnListen }.getOrDefault(true)) tone(ToneGenerator.TONE_PROP_ACK, 300)
        updateMic()
        publish()
    }

    private suspend fun onPttStart(peer: String, m: Message.PttStart, conn: PeerHub.Conn) {
        val busy = Message.PttBusy(m.streamId)
        if (!policy.mayReceivePtt(peer, m.circleId) || listenStream != null) return run { conn.send(busy) }
        if (!floor.onStart(peer, m.streamId, policy.localTalking)) {
            tone(ToneGenerator.TONE_SUP_BUSY, 600)
            return run { conn.send(busy) }
        }
        val decoder: FrameCodec = when (Codec.of(m.codec)) {
            Codec.PCM -> PcmCodec()
            Codec.OPUS -> runCatching { OpusDecoder(m.csd) }.getOrNull()
            null -> null
        } ?: run {
            floor.onStop(peer, m.streamId)
            return conn.send(busy)
        }
        cryptoFor(conn)
        startPlayback(Incoming(peer, m.streamId, decoder))
        tone(ToneGenerator.TONE_PROP_BEEP)
        conn.send(Message.IntercomListenReply(m.streamId, true, m.codec, ByteArray(0), localPort(transportOf(conn)), ""))
        publish()
    }

    // --- this phone listens to a room phone ---

    suspend fun listen(context: Context, fp: String, circleId: String): String? {
        attach(context)
        if (AlarmStatusBus.state.value.isAlarming) return "Not during an alarm"
        val conn = hub()?.connect(fp) ?: return "${label(fp)} is not reachable"
        val t = transportOf(conn)
        val port = localPort(t).takeIf { it > 0 } ?: return CodecSelector.unavailableText(t) ?: "No audio path"
        val stream = Crypto.random.nextInt()
        cryptoFor(conn)
        val reply = conn.request(Message.IntercomListen(circleId, stream, port, Codec.bits(decodable()))) {
            it is Message.IntercomListenReply && it.streamId == stream
        } as? Message.IntercomListenReply ?: return "${label(fp)} did not answer"
        if (!reply.ok) return if (reply.reason == ListenDecision.ROOM_OFF.name) "${label(fp)} does not have intercom room mode on" else "${label(fp)} refused: ${reply.reason}"
        val decoder: FrameCodec = when (Codec.of(reply.codec)) {
            Codec.OPUS -> runCatching { OpusDecoder(reply.csd) }.getOrNull()
            Codec.PCM -> PcmCodec()
            null -> null
        } ?: return "No decoder"
        listenStream = stream
        startPlayback(Incoming(fp, stream, decoder))
        listenStream = stream
        publish(null)
        return null
    }

    fun stopListening() {
        val inc = incoming ?: return
        val s = listenStream ?: return
        scope.launch { runCatching { hub()?.conn(inc.peer)?.send(Message.IntercomStop(s)) } }
        stopPlayback()
        publish()
    }

    // --- push-to-talk sender (Talk held in the visible app, AC10.4) ---

    suspend fun startTalk(context: Context, targets: List<String>, circleId: String): String? {
        attach(context)
        if (AlarmStatusBus.state.value.isAlarming) return "Not during an alarm"
        if (!DeviceStatus.hasPermission(context, Manifest.permission.RECORD_AUDIO)) return "Allow the microphone to talk"
        policy.setLocalTalking(true)
        publish(null)
        val h = hub() ?: return "Network not available"
        var reached = 0
        for (fp in targets) {
            val conn = h.connect(fp) ?: continue
            val t = transportOf(conn)
            val codec = CodecSelector.select(OpusSupport.hasEncoder(), setOf(Codec.OPUS, Codec.PCM), t) ?: continue
            val enc = encoder(codec) ?: continue
            val stream = Crypto.random.nextInt()
            val crypto = cryptoFor(conn)
            val reply = conn.request(Message.PttStart(circleId, stream, codec.id, enc.csd, 0), 2_000) {
                (it is Message.IntercomListenReply && it.streamId == stream) || (it is Message.PttBusy && it.streamId == stream)
            }
            if (!policy.localTalking) break
            when (reply) {
                is Message.IntercomListenReply -> if (reply.ok) {
                    route(conn, reply.udpPort)?.let { pttTargets += Outgoing(fp, it, stream, codec, crypto); reached++ }
                }
                is Message.PttBusy -> {
                    tone(ToneGenerator.TONE_SUP_BUSY, 600)
                    publish("${label(fp)} is busy")
                }
                else -> Unit
            }
        }
        updateMic()
        return if (reached == 0 && policy.localTalking) "Nobody reachable" else null
    }

    fun stopTalk() {
        stopTalkLocal()
        updateMic()
        publish()
    }

    private fun stopTalkLocal() {
        val sent = pttTargets.toList()
        pttTargets.clear()
        policy.setLocalTalking(false)
        sent.forEach { o -> scope.launch { runCatching { hub()?.conn(o.peer)?.send(Message.PttStop(o.streamId)) } } }
    }

    const val OWNER = "intercom"
}
