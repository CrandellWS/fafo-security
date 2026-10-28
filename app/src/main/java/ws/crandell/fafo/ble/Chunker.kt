package ws.crandell.fafo.ble

import java.io.ByteArrayOutputStream

/**
 * Splits messages into GATT-sized chunks and joins them again. Each chunk is one header byte
 * (1 = last chunk of the message, 0 = more follow) plus payload. Pure Kotlin.
 */
object Chunker {
    const val MAX_MESSAGE = 64 * 1024

    fun split(msg: ByteArray, chunkPayload: Int): List<ByteArray> {
        require(chunkPayload > 0)
        require(msg.size <= MAX_MESSAGE) { "message too large" }
        if (msg.isEmpty()) return listOf(byteArrayOf(1))
        val out = ArrayList<ByteArray>()
        var i = 0
        while (i < msg.size) {
            val n = minOf(chunkPayload, msg.size - i)
            val last = i + n == msg.size
            out += byteArrayOf(if (last) 1 else 0) + msg.copyOfRange(i, i + n)
            i += n
        }
        return out
    }

    /** Reassembles chunks; [add] returns a whole message when its last chunk arrives. */
    class Joiner {
        private val buf = ByteArrayOutputStream()

        fun add(chunk: ByteArray): ByteArray? {
            if (chunk.isEmpty()) return null
            if (buf.size() + chunk.size - 1 > MAX_MESSAGE) {
                buf.reset()
                throw IllegalStateException("message too large")
            }
            buf.write(chunk, 1, chunk.size - 1)
            if (chunk[0].toInt() != 1) return null
            val msg = buf.toByteArray()
            buf.reset()
            return msg
        }
    }
}
