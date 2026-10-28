package ws.crandell.fafo.mail

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets (the SMTP app password). Interface so the store logic is tested on the JVM. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * The app password at rest: AES-256-GCM with a non-exportable Android Keystore key (`fafo_mail_secret`),
 * stored as base64(iv + ciphertext) in credential-protected settings. The key has no user-auth or
 * unlocked-device requirement on purpose: alarm photos are emailed while the phone is locked. It is only
 * usable by this app after the first unlock (CE storage). Never logged or shown.
 */
object SecretBox {
    fun seal(cipher: SecretCipher, secret: String): String = Base64.getEncoder().encodeToString(cipher.encrypt(secret.toByteArray()))

    fun open(cipher: SecretCipher, sealed: String?): String? {
        if (sealed.isNullOrEmpty()) return null
        return runCatching { String(cipher.decrypt(Base64.getDecoder().decode(sealed))) }.getOrNull()
    }
}

class KeystoreSecretCipher(private val alias: String = "fafo_mail_secret") : SecretCipher {
    private val ks by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    private fun key(): SecretKey = (ks.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12)) }
        return c.doFinal(blob, 12, blob.size - 12)
    }
}
