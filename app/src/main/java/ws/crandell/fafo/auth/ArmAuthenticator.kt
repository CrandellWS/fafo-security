package ws.crandell.fafo.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.crandell.fafo.pairing.CredentialGate
import ws.crandell.fafo.pairing.GateResult

/** What the owner is confirming (AC1.7). [title] / [subtitle] are the BiometricPrompt text. */
enum class AuthAction(val title: String, val subtitle: String) {
    ARM("Arm FAFO Security", "Use your fingerprint or screen lock"),
    DISARM("Disarm FAFO Security", "Use your fingerprint or screen lock"),
    CHANGE_GRACE("Change the unlock grace window", "Use your fingerprint or screen lock"),
}

sealed interface AuthResult {
    /** [prompted] is false when a recent unlock was enough (inside the grace window). */
    data class Allowed(val prompted: Boolean) : AuthResult
    data object Cancelled : AuthResult
    data object NoSecureLock : AuthResult
    data class Failed(val reason: String) : AuthResult
}

/**
 * AC1.7: Arm and Disarm need the owner's screen lock or strong biometric, unless the phone was unlocked
 * within the grace window. Tries the Keystore key first (no prompt inside the window); on "not
 * authenticated" shows the prompt, then proves Keystore accepted it by using the key again.
 */
class ArmAuthenticator(
    private val key: ArmAuthKey,
    private val gate: CredentialGate,
    private val graceS: () -> Int,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    /** Per-use key for Disarm when the unlock trail does not allow a skip (always grace 0). */
    private val disarmKey: ArmAuthKey? = null,
) {
    suspend fun authorize(action: AuthAction): AuthResult {
        if (!gate.hasSecureLock()) return AuthResult.NoSecureLock
        val first = try { checkKey(key, graceS()) } catch (e: Exception) {
            return AuthResult.Failed("Could not check your screen lock: ${e.javaClass.simpleName}")
        }
        return when (first) {
            KeyCheck.Ok -> AuthResult.Allowed(prompted = false)
            is KeyCheck.NeedsAuth -> prompt(action, key, first)
            KeyCheck.Invalidated, KeyCheck.NoKey -> AuthResult.Failed("Could not set up the confirmation key")
        }
    }

    /**
     * Disarm (Bill 2026-10-28). No prompt only when [trailAllowsSkip] ([DisarmPolicy]: locked and unlocked
     * after arming, inside the grace window) AND the grace-window key accepts (the unlock used the real
     * credential or a strong biometric). Otherwise always a prompt on the separate per-use disarm key.
     */
    suspend fun authorizeDisarm(trailAllowsSkip: Boolean): AuthResult {
        if (!gate.hasSecureLock()) return AuthResult.NoSecureLock
        try {
            if (trailAllowsSkip && checkKey(key, graceS()) == KeyCheck.Ok) return AuthResult.Allowed(prompted = false)
            // Without a separate key (tests, old callers) the prompt is still forced, on the grace key.
            val dk = disarmKey ?: return prompt(AuthAction.DISARM, key, KeyCheck.NeedsAuth(null))
            return when (val c = checkKey(dk, 0)) {
                is KeyCheck.NeedsAuth -> prompt(AuthAction.DISARM, dk, c)
                // A per-use key never passes without a prompt; anything else is a broken key.
                else -> AuthResult.Failed("Could not set up the confirmation key")
            }
        } catch (e: Exception) {
            return AuthResult.Failed("Could not check your screen lock: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun checkKey(k: ArmAuthKey, grace: Int): KeyCheck = withContext(io) {
        if (k.graceOfKey() != grace) k.create(grace)
        when (val c = k.check()) {
            KeyCheck.Invalidated, KeyCheck.NoKey -> { k.create(grace); k.check() }
            else -> c
        }
    }

    private suspend fun prompt(action: AuthAction, key: ArmAuthKey, needs: KeyCheck.NeedsAuth): AuthResult {
        return when (val r = gate.confirmUser(action.title, action.subtitle, needs.mac)) {
            is GateResult.Success -> {
                val accepted = withContext(io) {
                    runCatching {
                        if (needs.mac != null) key.finish(r.mac ?: needs.mac) else key.check() == KeyCheck.Ok
                    }.getOrDefault(false)
                }
                if (accepted) AuthResult.Allowed(prompted = true) else AuthResult.Failed("Confirmation not accepted")
            }
            GateResult.NoSecureLock -> AuthResult.NoSecureLock
            is GateResult.Failed -> if (r.cancelled) AuthResult.Cancelled else AuthResult.Failed(r.reason)
        }
    }

    /**
     * Re-creates the key for a new grace window. Call only after [authorize] with [AuthAction.CHANGE_GRACE]
     * returned [AuthResult.Allowed] (changing the window needs the same confirmation as Arm / Disarm).
     */
    suspend fun rekey(newGraceS: Int) = withContext(io) { key.create(newGraceS) }
}
