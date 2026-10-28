package ws.crandell.fafo.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the Arm / Disarm button shows while a confirmation runs. */
sealed interface ArmAuthUi {
    data object Idle : ArmAuthUi
    data class Confirming(val action: AuthAction) : ArmAuthUi
}

/**
 * Runs Arm / Disarm / grace changes behind [authorize] (AC1.7) and turns the result into UI: a busy button
 * while confirming and a short message afterwards. The [block] (start / stop the armed service, save the
 * setting) runs only when the owner was confirmed.
 */
class GuardedAction(private val authorize: suspend (AuthAction) -> AuthResult) {
    private val _ui = MutableStateFlow<ArmAuthUi>(ArmAuthUi.Idle)
    val ui: StateFlow<ArmAuthUi> = _ui

    suspend fun run(action: AuthAction, onMessage: (String) -> Unit, block: suspend () -> Unit): AuthResult =
        run(action, onMessage, { authorize(it) }, block)

    /** Same, with a custom authorization for this one action (Disarm uses the unlock trail). */
    suspend fun run(
        action: AuthAction,
        onMessage: (String) -> Unit,
        auth: suspend (AuthAction) -> AuthResult,
        block: suspend () -> Unit,
    ): AuthResult {
        if (_ui.value is ArmAuthUi.Confirming) return AuthResult.Failed("Already confirming")
        _ui.value = ArmAuthUi.Confirming(action)
        val r = try { auth(action) } finally { _ui.value = ArmAuthUi.Idle }
        if (r is AuthResult.Allowed) block() else message(action, r)?.let(onMessage)
        return r
    }

    companion object {
        fun message(action: AuthAction, r: AuthResult): String? = when (r) {
            is AuthResult.Allowed -> null
            AuthResult.Cancelled -> when (action) {
                AuthAction.ARM -> "Not armed. Confirmation cancelled."
                AuthAction.DISARM -> "Still armed. Confirmation cancelled."
                AuthAction.CHANGE_GRACE -> "Not changed. Confirmation cancelled."
            }
            AuthResult.NoSecureLock -> "Set a screen lock first"
            is AuthResult.Failed -> r.reason
        }
    }
}
