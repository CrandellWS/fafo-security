package ws.crandell.fafo.net.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ws.crandell.fafo.net.KeyWrapper
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AC9.6: the beacon key at rest is wrapped by a non-exportable Keystore AES-256-GCM key. CE only. */
class KeystoreKeyWrapper(private val alias: String = "fafo_beacon_wrap_v1") : KeyWrapper {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = c.iv
        return byteArrayOf(iv.size.toByte()) + iv + c.doFinal(plain)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val n = wrapped[0].toInt()
        val iv = wrapped.copyOfRange(1, 1 + n)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return c.doFinal(wrapped, 1 + n, wrapped.size - 1 - n)
    }
}
