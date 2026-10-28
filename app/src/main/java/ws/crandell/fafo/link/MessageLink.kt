package ws.crandell.fafo.link

/**
 * A reliable, ordered, message-oriented link between two devices (BLE GATT, a TCP socket). Messages are
 * whole byte arrays; framing and chunking are the transport's job. Nothing here is encrypted by itself.
 */
interface MessageLink {
    suspend fun send(msg: ByteArray)

    /** Next message; throws [LinkClosed] once the link is gone. */
    suspend fun receive(): ByteArray

    fun close()
}

class LinkClosed(message: String = "link closed") : Exception(message)
