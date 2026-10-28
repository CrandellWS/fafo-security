package ws.crandell.fafo.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * HMAC key in Android Keystore bound to the owner's authentication (AC1.7):
 * `setUserAuthenticationParameters(grace, AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL)`. A device unlock
 * with the PIN / pattern / password or a Class 3 fingerprint produces an auth token Keystore accepts for
 * `grace` seconds, so "I just unlocked the phone" passes with no prompt and no app-side timer.
 *
 * The grace value is part of the alias (`fafo_arm_auth_g30`) so a changed setting is detected without
 * reading KeyInfo. Not invalidated by a new fingerprint enrolment (adding a finger must not break arming);
 * removing the screen lock invalidates it and [ArmAuthenticator] re-creates it.
 *
 * Two instances exist: [ARM_PREFIX] (grace window) and [DISARM_PREFIX] (always created with grace 0, so a
 * recent unlock or a prompt for anything else can never satisfy it).
 */
class KeystoreArmAuthKey(private val prefix: String = ARM_PREFIX) : ArmAuthKey {
    private val ks: KeyStore by lazy { KeyStore.getInstance(STORE).apply { load(null) } }

    private fun alias(): String? = ks.aliases().toList().firstOrNull { it.startsWith(prefix) }

    override fun graceOfKey(): Int? = alias()?.removePrefix(prefix)?.toIntOrNull()

    override fun create(graceS: Int) {
        ks.aliases().toList().filter { it.startsWith(prefix) }.forEach { ks.deleteEntry(it) }
        val spec = KeyGenParameterSpec.Builder("$prefix$graceS", KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(graceS, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .setInvalidatedByBiometricEnrollment(false)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, STORE).run { init(spec); generateKey() }
    }

    override fun check(): KeyCheck {
        val name = alias() ?: return KeyCheck.NoKey
        val key = ks.getKey(name, null) as? SecretKey ?: return KeyCheck.NoKey
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        return try {
            mac.init(key)
            if (graceOfKey() == 0) return KeyCheck.NeedsAuth(mac)
            mac.doFinal(NONCE)
            KeyCheck.Ok
        } catch (_: KeyPermanentlyInvalidatedException) {
            KeyCheck.Invalidated
        } catch (_: UserNotAuthenticatedException) {
            KeyCheck.NeedsAuth(null)
        }
    }

    override fun finish(mac: Mac): Boolean = runCatching { mac.doFinal(NONCE) }.isSuccess

    companion object {
        private const val STORE = "AndroidKeyStore"
        /** Arm (and the "recent real unlock" proof for Disarm): bound to the grace window. */
        const val ARM_PREFIX = "fafo_arm_auth_g"
        /** Disarm when the unlock trail does not allow a skip: always per-use (grace 0, CryptoObject). */
        const val DISARM_PREFIX = "fafo_disarm_g"
        private val NONCE = "fafo-arm".toByteArray()
    }
}
