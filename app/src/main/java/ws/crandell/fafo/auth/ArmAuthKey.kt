package ws.crandell.fafo.auth

import javax.crypto.Mac

/** Result of trying the arm-auth key without a prompt (AC1.7). */
sealed interface KeyCheck {
    /** Keystore accepted a recent unlock or prompt: the owner is inside the grace window. */
    data object Ok : KeyCheck

    /**
     * The owner must confirm. [mac] is non-null for a per-use key (grace window Off): the prompt must carry
     * it as a CryptoObject and the operation runs with the unlocked Mac afterwards.
     */
    data class NeedsAuth(val mac: Mac?) : KeyCheck

    /** The screen lock was removed or reset since the key was made: it must be re-created. */
    data object Invalidated : KeyCheck

    data object NoKey : KeyCheck
}

/**
 * The user-authentication-bound key behind Arm / Disarm confirmation (AC1.7). Pure interface so
 * [ArmAuthenticator] is tested on the JVM; the Android Keystore version is [KeystoreArmAuthKey].
 */
interface ArmAuthKey {
    /** Grace window (s) the current key was made with, or null when there is no key. */
    fun graceOfKey(): Int?

    /** Deletes any existing key and makes one bound to [graceS] seconds after an unlock (0 = every use). */
    fun create(graceS: Int)

    fun check(): KeyCheck

    /** Runs the key operation on a Mac unlocked by the prompt (per-use key). True when Keystore accepted it. */
    fun finish(mac: Mac): Boolean
}
