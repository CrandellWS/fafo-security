package ws.crandell.fafo.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ws.crandell.fafo.link.MessageLink
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ReplayRejected : Exception("replayed or reordered frame")

/**
 * AC9.5 frame crypto. Each frame is `counter(8) || AES-256-GCM(ciphertext)`, nonce = 4 zero bytes ||
 * per-direction 64-bit counter, AAD = session id || counter. The receiver rejects any counter at or below
 * the last one seen. Pure Kotlin.
 */
class FrameCrypto(private val sendKey: ByteArray, private val receiveKey: ByteArray, private val sessionId: ByteArray) {
    private var sendCounter = 0L
    private var lastReceived = 0L

    @Synchronized
    fun seal(plain: ByteArray): ByteArray {
        require(plain.size <= MAX_PLAIN) { "frame too large" }
        val c = ++sendCounter
        val ct = cipher(Cipher.ENCRYPT_MODE, sendKey, c).doFinal(plain)
        return ByteBuffer.allocate(8 + ct.size).putLong(c).put(ct).array()
    }

    @Synchronized
    fun open(frame: ByteArray): ByteArray {
        if (frame.size < 8 + 16 || frame.size > MAX_PLAIN + 8 + 16) throw ReplayRejected()
        val c = ByteBuffer.wrap(frame, 0, 8).long
        if (c <= lastReceived) throw ReplayRejected()
        val plain = cipher(Cipher.DECRYPT_MODE, receiveKey, c).doFinal(frame, 8, frame.size - 8)
        lastReceived = c
        return plain
    }

    private fun cipher(mode: Int, key: ByteArray, counter: Long): Cipher {
        val nonce = ByteBuffer.allocate(12).putInt(0).putLong(counter).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(ByteBuffer.allocate(sessionId.size + 8).put(sessionId).putLong(counter).array())
        }
    }

    companion object {
        const val MAX_PLAIN = 64 * 1024 - 64
    }
}

/** An authenticated, encrypted session over a [MessageLink] (Wi-Fi socket or BLE GATT). */
class SecureChannel(
    private val link: MessageLink,
    val keys: SessionKeys,
    private val clock: () -> Long = System::currentTimeMillis,
    val maxAgeMs: Long = MAX_AGE_MS,
) {
    private val crypto = FrameCrypto(keys.controlSend, keys.controlReceive, keys.sessionId)
    private val sendLock = Mutex()
    val createdAt = clock()
    val peer: String get() = keys.peerFingerprint
    /** Transport address of the peer (for the F10 audio path). */
    val remote: Any? get() = link.remote

    val expired: Boolean get() = clock() - createdAt >= maxAgeMs

    suspend fun send(msg: Message) = sendLock.withLock {
        if (expired) throw ws.crandell.fafo.link.LinkClosed("session expired")
        link.send(crypto.seal(msg.encode()))
    }

    /** Next message; a frame that fails to decrypt or replays an old counter closes the session. */
    suspend fun receive(): Message {
        if (expired) throw ws.crandell.fafo.link.LinkClosed("session expired")
        val frame = link.receive()
        val plain = try {
            crypto.open(frame)
        } catch (e: Exception) {
            link.close()
            throw ws.crandell.fafo.link.LinkClosed("bad frame")
        }
        return Message.decode(plain)
    }

    fun close() = link.close()

    companion object {
        const val MAX_AGE_MS = 60 * 60 * 1000L

        suspend fun initiate(link: MessageLink, h: SessionHandshake.Initiator, clock: () -> Long = System::currentTimeMillis): SecureChannel {
            link.send(h.start())
            link.send(h.onResponderHello(link.receive()))
            return SecureChannel(link, h.onResponderProof(link.receive()), clock)
        }

        suspend fun respond(link: MessageLink, h: SessionHandshake.Responder, clock: () -> Long = System::currentTimeMillis): SecureChannel {
            link.send(h.onInitiatorHello(link.receive()))
            val (reply, keys) = h.onInitiatorProof(link.receive())
            link.send(reply)
            return SecureChannel(link, keys, clock)
        }
    }
}
