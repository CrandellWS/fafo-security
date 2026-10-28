package ws.crandell.fafo.intercom

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import ws.crandell.fafo.crypto.WireReader
import ws.crandell.fafo.crypto.WireWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AC10.6 Opus over `MediaCodec` (`audio/opus`, about 24 kbps, 16 kHz mono, 20 ms frames). The encoder's
 * codec-specific data (csd-0 OpusHead, csd-1 pre-skip, csd-2 seek pre-roll) is packed into one blob that
 * the stream start carries inside the encrypted session (AC10.7). Live audio only: nothing is written.
 */
object OpusSupport {
    const val MIME = "audio/opus"

    fun hasEncoder(): Boolean = has(encoder = true)
    fun hasDecoder(): Boolean = has(encoder = false)

    private fun has(encoder: Boolean): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder == encoder && info.supportedTypes.any { it.equals(MIME, ignoreCase = true) }
        }
    }.getOrDefault(false)

    fun packCsd(parts: List<ByteArray>): ByteArray = WireWriter().u8(parts.size).also { w -> parts.forEach { w.bytes(it) } }.build()

    fun unpackCsd(blob: ByteArray): List<ByteArray> = WireReader(blob).run { List(u8().coerceAtMost(3)) { bytes(512) } }
}

class OpusEncoder : FrameCodec {
    override val codec = Codec.OPUS
    private val mc: MediaCodec = MediaCodec.createEncoderByType(OpusSupport.MIME)
    private val info = MediaCodec.BufferInfo()
    private var pts = 0L
    private val csdParts = ArrayList<ByteArray>()
    override var csd: ByteArray = ByteArray(0)
        private set

    init {
        val f = MediaFormat.createAudioFormat(OpusSupport.MIME, AudioFormat.SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AudioFormat.OPUS_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AudioFormat.FRAME_SAMPLES * 2)
        }
        mc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mc.start()
        // Prime with silence until the codec-specific data is known (it must go out with the stream start).
        var tries = 0
        while (csd.isEmpty() && tries++ < 25) encode(ShortArray(AudioFormat.FRAME_SAMPLES))
    }

    override fun encode(pcm: ShortArray): List<ByteArray> {
        val idx = mc.dequeueInputBuffer(10_000)
        if (idx >= 0) {
            val buf = mc.getInputBuffer(idx)!!
            buf.clear()
            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm)
            mc.queueInputBuffer(idx, 0, pcm.size * 2, pts, 0)
            pts += AudioFormat.FRAME_MS * 1000L
        }
        val out = ArrayList<ByteArray>()
        while (true) {
            val o = mc.dequeueOutputBuffer(info, 2_000)
            when {
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> readCsd(mc.outputFormat)
                o >= 0 -> {
                    val b = mc.getOutputBuffer(o)!!
                    val bytes = ByteArray(info.size).also { b.position(info.offset); b.get(it) }
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        csdParts += bytes
                        if (csd.isEmpty() && csdParts.size >= 1) csd = OpusSupport.packCsd(csdParts)
                    } else if (bytes.isNotEmpty()) {
                        out += bytes
                    }
                    mc.releaseOutputBuffer(o, false)
                }
                else -> break
            }
        }
        return out
    }

    private fun readCsd(f: MediaFormat) {
        val parts = listOf("csd-0", "csd-1", "csd-2").mapNotNull { k -> f.getByteBuffer(k)?.let { bb -> ByteArray(bb.remaining()).also { bb.duplicate().get(it) } } }
        if (parts.isNotEmpty()) csd = OpusSupport.packCsd(parts)
    }

    override fun decode(frame: ByteArray): ShortArray = throw UnsupportedOperationException()

    override fun release() {
        runCatching { mc.stop() }
        runCatching { mc.release() }
    }
}

/** Decoder for one incoming stream, configured with the sender's csd. [outputRate] may be 48 kHz. */
class OpusDecoder(csdBlob: ByteArray) : FrameCodec {
    override val codec = Codec.OPUS
    private val mc: MediaCodec = MediaCodec.createDecoderByType(OpusSupport.MIME)
    private val info = MediaCodec.BufferInfo()
    private var pts = 0L
    @Volatile var outputRate: Int = 48_000
        private set

    init {
        val f = MediaFormat.createAudioFormat(OpusSupport.MIME, 48_000, 1)
        OpusSupport.unpackCsd(csdBlob).forEachIndexed { i, b -> f.setByteBuffer("csd-$i", ByteBuffer.wrap(b)) }
        mc.configure(f, null, null, 0)
        mc.start()
    }

    override fun encode(pcm: ShortArray): List<ByteArray> = throw UnsupportedOperationException()

    override fun decode(frame: ByteArray): ShortArray {
        val idx = mc.dequeueInputBuffer(10_000)
        if (idx >= 0) {
            val buf = mc.getInputBuffer(idx)!!
            buf.clear()
            buf.put(frame)
            mc.queueInputBuffer(idx, 0, frame.size, pts, 0)
            pts += AudioFormat.FRAME_MS * 1000L
        }
        var pcm = ShortArray(0)
        while (true) {
            val o = mc.dequeueOutputBuffer(info, 2_000)
            when {
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputRate = mc.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                o >= 0 -> {
                    val b = mc.getOutputBuffer(o)!!
                    b.position(info.offset)
                    b.limit(info.offset + info.size)
                    val s = ShortArray(info.size / 2)
                    b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s)
                    pcm += s
                    mc.releaseOutputBuffer(o, false)
                }
                else -> break
            }
        }
        return pcm
    }

    override fun release() {
        runCatching { mc.stop() }
        runCatching { mc.release() }
    }
}
