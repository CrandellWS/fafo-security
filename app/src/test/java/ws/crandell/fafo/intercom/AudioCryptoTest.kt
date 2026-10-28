package ws.crandell.fafo.intercom

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.net.Transport
import java.io.File

class AudioCryptoTest {
    private val k1 = Crypto.randomBytes(32)
    private val k2 = Crypto.randomBytes(32)
    private val sid = Crypto.randomBytes(8)
    private val tx = AudioFrameCrypto(k1, k2, sid)
    private val rx = AudioFrameCrypto(k2, k1, sid)

    @Test fun roundTrip() {
        val (stream, _, frame) = rx.open(tx.seal(7, byteArrayOf(1, 2, 3)))!!
        assertThat(stream).isEqualTo(7)
        assertThat(frame).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test fun replayAndLateFramesDropped() {
        val packets = (0 until 20).map { tx.seal(1, byteArrayOf(it.toByte())) }
        assertThat(rx.open(packets[0])).isNotNull()
        assertThat(rx.open(packets[0])).isNull() // replay
        assertThat(rx.open(packets[19])).isNotNull()
        assertThat(rx.open(packets[5])).isNull() // 14 frames (280 ms) behind: older than the 200 ms window
        assertThat(rx.open(packets[12])).isNotNull() // 7 frames behind: reordered but in time
        assertThat(rx.open(packets[12])).isNull()
    }

    @Test fun tamperedWrongSessionOrControlKeyDropped() {
        val p = tx.seal(1, byteArrayOf(9))
        assertThat(rx.open(p.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() })).isNull()
        assertThat(AudioFrameCrypto(k2, k1, Crypto.randomBytes(8)).open(p)).isNull()
        assertThat(AudioFrameCrypto(k2, Crypto.randomBytes(32), sid).open(p)).isNull()
        // The stream id is authenticated (AAD and nonce).
        val swapped = p.copyOf().also { it[11] = (it[11] + 1).toByte() }
        assertThat(rx.open(swapped)).isNull()
    }

    @Test fun countersNeverResetAcrossStreams() {
        val a = tx.seal(1, byteArrayOf(1))
        val b = tx.seal(2, byteArrayOf(1))
        val ca = java.nio.ByteBuffer.wrap(a, 12, 8).long
        val cb = java.nio.ByteBuffer.wrap(b, 12, 8).long
        assertThat(cb).isGreaterThan(ca)
    }

    @Test fun jitterBufferOrdersAndDropsLate() {
        val j = JitterBuffer(targetFrames = 3)
        j.push(2, byteArrayOf(2)); j.push(1, byteArrayOf(1))
        assertThat(j.pop()).isNull() // not primed yet
        j.push(3, byteArrayOf(3))
        assertThat(j.pop()!![0]).isEqualTo(1)
        assertThat(j.pop()!![0]).isEqualTo(2)
        j.push(1, byteArrayOf(1)) // late: already played past it
        assertThat(j.pop()!![0]).isEqualTo(3)
        assertThat(j.pop()).isNull()
    }

    @Test fun codecSelection() {
        val both = setOf(Codec.OPUS, Codec.PCM)
        assertThat(CodecSelector.select(true, both, Transport.WIFI)).isEqualTo(Codec.OPUS)
        assertThat(CodecSelector.select(true, both, Transport.BLUETOOTH)).isEqualTo(Codec.OPUS)
        assertThat(CodecSelector.select(false, both, Transport.WIFI)).isEqualTo(Codec.PCM)
        assertThat(CodecSelector.select(false, both, Transport.BLUETOOTH)).isNull()
        assertThat(CodecSelector.unavailableText(Transport.BLUETOOTH)).isEqualTo("Intercom needs Wi-Fi on this phone")
        assertThat(CodecSelector.select(true, setOf(Codec.PCM), Transport.WIFI)).isEqualTo(Codec.PCM)
    }

    @Test fun pcmPathEndToEnd() {
        val codec = PcmCodec()
        val pcm = ShortArray(AudioFormat.FRAME_SAMPLES) { (it * 97 - 15000).toShort() }
        val frames = codec.encode(pcm)
        assertThat(frames.single()).hasLength(640)
        val opened = rx.open(tx.seal(3, frames.single()))!!
        assertThat(codec.decode(opened.third)).isEqualTo(pcm)
    }

    /** AC10.3 / M12 done-check: nothing under intercom writes audio to disk. */
    @Test fun noAudioWrittenToDisk() {
        val root = listOf(File("src/main/java/ws/crandell/fafo/intercom"), File("app/src/main/java/ws/crandell/fafo/intercom")).first { it.isDirectory }
        // MediaRecorder.AudioSource constants are fine; a MediaRecorder instance or an output file is not.
        val banned = listOf("FileOutputStream", "MediaRecorder()", "setOutputFile", "openFileOutput", "RandomAccessFile", "Files.write", "writeBytes(", "MediaMuxer", "FileWriter")
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            banned.filter { f.readText().contains(it) }.map { "${f.name}: $it" }
        }.toList()
        assertThat(offenders).isEmpty()
        assertThat(root.listFiles()!!.size).isAtLeast(3)
    }
}
