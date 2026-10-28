package ws.crandell.fafo.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.crandell.fafo.link.LinkClosed
import ws.crandell.fafo.link.MessageLink
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * AC9.5 framing for stream transports (TCP on Wi-Fi): each message is a 4-byte big-endian length then
 * the bytes; anything above [MAX_FRAME] closes the link. Pure JVM.
 */
class StreamLink(input: InputStream, output: OutputStream, private val onClose: () -> Unit = {}) : MessageLink {
    private val din = DataInputStream(input.buffered())
    private val dout = DataOutputStream(output.buffered())
    @Volatile private var closed = false

    override suspend fun send(msg: ByteArray) = withContext(Dispatchers.IO) {
        if (closed) throw LinkClosed()
        require(msg.size <= MAX_FRAME) { "frame too large" }
        try {
            synchronized(dout) {
                dout.writeInt(msg.size)
                dout.write(msg)
                dout.flush()
            }
        } catch (e: Exception) {
            close()
            throw LinkClosed(e.message ?: "write failed")
        }
    }

    override suspend fun receive(): ByteArray = withContext(Dispatchers.IO) {
        if (closed) throw LinkClosed()
        try {
            val n = din.readInt()
            if (n < 0 || n > MAX_FRAME) throw LinkClosed("bad frame length")
            ByteArray(n).also { din.readFully(it) }
        } catch (e: LinkClosed) {
            close()
            throw e
        } catch (e: Exception) {
            close()
            throw LinkClosed(e.message ?: "read failed")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { onClose() }
    }

    companion object {
        const val MAX_FRAME = 64 * 1024
    }
}
