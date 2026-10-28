package ws.crandell.fafo.pairing

import java.security.Signature

sealed interface GateResult {
    /** [signature] is the unlocked command-key Signature when one was passed in. */
    data class Success(val signature: Signature? = null) : GateResult
    data object NoSecureLock : GateResult
    data class Failed(val reason: String) : GateResult
}

/**
 * The owner's real lock-screen credential (AC6.1, AC8.5). Pairing, adding / removing members and granting
 * Reset or Disarm use [authenticate] with DEVICE_CREDENTIAL only (the PIN / pattern / password, not a
 * fingerprint). Sending Reset / Disarm unlocks the command key and may also use a strong biometric (AC9.8).
 */
interface CredentialGate {
    /** `canAuthenticate(DEVICE_CREDENTIAL)`: false means "Set a screen lock first". No prompt. */
    fun hasSecureLock(): Boolean

    suspend fun authenticate(title: String, subtitle: String): GateResult
    suspend fun unlockCommandKey(title: String, subtitle: String, signer: Signature): GateResult
}
