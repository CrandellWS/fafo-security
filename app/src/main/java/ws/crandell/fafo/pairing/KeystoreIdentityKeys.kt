package ws.crandell.fafo.pairing

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Never touched on the boot path (Keystore is off limits in direct boot, AC2.6a). */
class KeystoreIdentityKeys : IdentityKeys {
    private val ks: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun ensure(alias: String, authBound: Boolean) {
        if (ks.containsAlias(alias)) return
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply {
                if (authBound) {
                    setUserAuthenticationRequired(true)
                    setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG,
                    )
                    setInvalidatedByBiometricEnrollment(false)
                }
            }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    private fun publicOf(alias: String, authBound: Boolean): ByteArray {
        ensure(alias, authBound)
        return ks.getCertificate(alias).publicKey.encoded
    }

    private fun privateOf(alias: String, authBound: Boolean): PrivateKey {
        ensure(alias, authBound)
        return ks.getKey(alias, null) as PrivateKey
    }

    override fun identityPublic() = publicOf(IDENTITY, false)
    override fun commandPublic() = publicOf(COMMAND, true)

    override fun signIdentity(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(privateOf(IDENTITY, false))
        update(data)
        sign()
    }

    override fun commandSigner(): Signature = Signature.getInstance("SHA256withECDSA").apply {
        initSign(privateOf(COMMAND, true))
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val IDENTITY = "fafo_identity_v1"
        const val COMMAND = "fafo_command_v1"
    }
}
