package ws.crandell.fafo.net

import ws.crandell.fafo.crypto.Crypto
import java.io.File

/** Wraps secrets with a non-exportable key (Android Keystore AES-GCM in the app; a fake in tests). */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * AC9.6: one random 256-bit beacon key per install, stored in CE storage wrapped by a Keystore key. Used
 * only for discovery tags and BLE alert beacons. [rotate] is called when a trusted device is removed.
 */
class BeaconKeyStore(private val file: File, private val wrapper: KeyWrapper) {
    @Volatile private var cached: ByteArray? = null

    @Synchronized
    fun key(): ByteArray {
        cached?.let { return it }
        val existing = if (file.exists()) runCatching { wrapper.unwrap(file.readBytes()) }.getOrNull() else null
        val k = existing?.takeIf { it.size == 32 } ?: write(Crypto.randomBytes(32))
        cached = k
        return k
    }

    @Synchronized
    fun rotate(): ByteArray = write(Crypto.randomBytes(32)).also { cached = it }

    private fun write(k: ByteArray): ByteArray {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(wrapper.wrap(k))
        tmp.renameTo(file)
        return k
    }
}
