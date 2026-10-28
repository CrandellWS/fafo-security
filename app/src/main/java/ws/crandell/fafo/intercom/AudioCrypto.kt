package ws.crandell.fafo.intercom

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sliding replay window (64 frames) on the 64-bit frame counter, plus the late-frame rule: with 20 ms frames
 * a 200 ms jitter window is 10 frames, so anything more than [lateFrames] behind the newest is dropped.
 */
class ReplayWindow(private val lateFrames: Int = 10) {
    private var highest = -1L
    private var bitmap = 0L

    @Synchronized
    fun accept(counter: Long): Boolean {
        if (counter < 0) return false
        if (highest < 0) {
            highest = counter
            bitmap = 1
            return true
        }
        if (counter > highest) {
            val shift = counter - highest
            bitmap = if (shift >= 64) 1 else (bitmap shl shift.toInt()) or 1
            highest = counter
            return true
        }
        val behind = highest - counter
        if (behind > lateFrames || behind >= 64) return false
        val bit = 1L shl behind.toInt()
        if (bitmap and bit != 0L) return false
        bitmap = bitmap or bit
        return true
    }
}

/**
 * AC10.7 audio frames: AES-256-GCM under the per-direction audio key (never the control key). Nonce =
 * 32-bit stream id || 64-bit frame counter; AAD = session id || stream id. Wire packet = session id (8, clear,
 * only to find the key) || stream id (4) || counter (8) || ciphertext. The send counter runs for the whole
 * session (never reset per stream), so a nonce is never reused under one key.
 */
class AudioFrameCrypto(private val sendKey: ByteArray, private val receiveKey: ByteArray, val sessionId: ByteArray) {
    private var sendCounter = 0L
    private val windows = HashMap<Int, ReplayWindow>()

    @Synchronized
    fun seal(streamId: Int, frame: ByteArray): ByteArray {
        val c = sendCounter++
        val ct = cipher(Cipher.ENCRYPT_MODE, sendKey, streamId, c).doFinal(frame)
        return ByteBuffer.allocate(HEADER + ct.size).put(sessionId).putInt(streamId).putLong(c).put(ct).array()
    }

    /** Returns (streamId, counter, frame) or null: wrong session, failed decryption, replay or too late. */
    @Synchronized
    fun open(packet: ByteArray): Triple<Int, Long, ByteArray>? {
        if (packet.size < HEADER + 16) return null
        val bb = ByteBuffer.wrap(packet)
        val sid = ByteArray(8).also { bb.get(it) }
        if (!sid.contentEquals(sessionId)) return null
        val stream = bb.int
        val counter = bb.long
        val plain = try {
            cipher(Cipher.DECRYPT_MODE, receiveKey, stream, counter).doFinal(packet, HEADER, packet.size - HEADER)
        } catch (e: Exception) {
            return null
        }
        if (!windows.getOrPut(stream) { ReplayWindow() }.accept(counter)) return null
        return Triple(stream, counter, plain)
    }

    private fun cipher(mode: Int, key: ByteArray, stream: Int, counter: Long): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, ByteBuffer.allocate(12).putInt(stream).putLong(counter).array()))
            updateAAD(ByteBuffer.allocate(12).put(sessionId).putInt(stream).array())
        }

    companion object {
        const val HEADER = 8 + 4 + 8

        fun sessionIdOf(packet: ByteArray): ByteArray? = if (packet.size >= 8) packet.copyOf(8) else null
    }
}

/**
 * Small playout buffer: frames come out in counter order after [targetFrames] are queued; anything older
 * than what was already played is dropped. Pure Kotlin.
 */
class JitterBuffer(private val targetFrames: Int = 3, private val maxFrames: Int = 10) {
    private val frames = java.util.TreeMap<Long, ByteArray>()
    private var next = -1L
    private var primed = false

    @Synchronized
    fun push(counter: Long, frame: ByteArray) {
        if (next >= 0 && counter < next) return
        frames[counter] = frame
        while (frames.size > maxFrames) frames.pollFirstEntry()
        if (!primed && frames.size >= targetFrames) primed = true
    }

    /** Next frame to play, or null (caller plays silence / concealment). */
    @Synchronized
    fun pop(): ByteArray? {
        if (!primed || frames.isEmpty()) return null
        val e = frames.pollFirstEntry()
        next = e.key + 1
        if (frames.isEmpty()) primed = false
        return e.value
    }

    @Synchronized
    fun clear() {
        frames.clear()
        next = -1
        primed = false
    }
}
