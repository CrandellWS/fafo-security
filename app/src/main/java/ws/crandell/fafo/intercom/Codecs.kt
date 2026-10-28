package ws.crandell.fafo.intercom

import ws.crandell.fafo.net.Transport

enum class Codec(val id: Int, val bit: Int) {
    OPUS(1, 1), PCM(2, 2);

    companion object {
        fun of(id: Int) = entries.firstOrNull { it.id == id }
        fun bits(set: Collection<Codec>) = set.fold(0) { a, c -> a or c.bit }
        fun fromBits(bits: Int) = entries.filter { bits and it.bit != 0 }.toSet()
    }
}

/** AC10.6: 16 kHz mono, 20 ms frames. */
object AudioFormat {
    const val SAMPLE_RATE = 16_000
    const val FRAME_MS = 20
    const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
    const val OPUS_BITRATE = 24_000
}

/**
 * AC10.6 codec choice: Opus when this phone has an `audio/opus` encoder (and the receiver can decode it);
 * 16-bit PCM only on Wi-Fi; over Bluetooth without Opus there is no intercom ("Intercom needs Wi-Fi on
 * this phone").
 */
object CodecSelector {
    fun select(opusEncoder: Boolean, peerDecodes: Set<Codec>, transport: Transport): Codec? = when {
        opusEncoder && Codec.OPUS in peerDecodes -> Codec.OPUS
        transport == Transport.WIFI && Codec.PCM in peerDecodes -> Codec.PCM
        else -> null
    }

    fun unavailableText(transport: Transport): String? =
        if (transport == Transport.BLUETOOTH) "Intercom needs Wi-Fi on this phone" else null
}

/** Encodes / decodes 20 ms frames of 16-bit mono PCM. */
interface FrameCodec {
    val codec: Codec
    /** Codec-specific data for the receiver's decoder (Opus csd-0/1/2), sent once per stream. */
    val csd: ByteArray get() = ByteArray(0)
    fun encode(pcm: ShortArray): List<ByteArray>
    fun decode(frame: ByteArray): ShortArray
    fun release() {}
}

/** 16-bit little-endian PCM (256 kbps), Wi-Fi only. Pure Kotlin. */
class PcmCodec : FrameCodec {
    override val codec = Codec.PCM

    override fun encode(pcm: ShortArray): List<ByteArray> {
        val out = ByteArray(pcm.size * 2)
        pcm.forEachIndexed { i, s ->
            out[2 * i] = (s.toInt() and 0xff).toByte()
            out[2 * i + 1] = (s.toInt() shr 8).toByte()
        }
        return listOf(out)
    }

    override fun decode(frame: ByteArray): ShortArray =
        ShortArray(frame.size / 2) { i -> ((frame[2 * i].toInt() and 0xff) or (frame[2 * i + 1].toInt() shl 8)).toShort() }
}
