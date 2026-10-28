package ws.crandell.fafo.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ws.crandell.fafo.pairing.GateResult
import ws.crandell.fafo.testutil.FakeGate

/** Disarm needs both the unlock trail and the grace key; otherwise a forced prompt on the per-use key. */
class DisarmAuthTest {
    private val armKey = FakeArmAuthKey()
    private val disarmKey = FakeArmAuthKey()
    private val gate = FakeGate()
    private val auth = ArmAuthenticator(armKey, gate, { 30 }, Dispatchers.Unconfined, disarmKey)

    @Test fun trailAndRealUnlockSkipThePrompt() = runTest {
        armKey.create(30); armKey.authenticated = true
        assertThat(auth.authorizeDisarm(trailAllowsSkip = true)).isEqualTo(AuthResult.Allowed(prompted = false))
        assertThat(gate.calls).isEmpty()
    }

    @Test fun recentUnlockBeforeArmingStillPrompts() = runTest {
        // Bill's case: the grace key is happy (unlocked a moment ago) but the unlock was before arming.
        armKey.create(30); armKey.authenticated = true
        assertThat(auth.authorizeDisarm(trailAllowsSkip = false)).isEqualTo(AuthResult.Allowed(prompted = true))
        assertThat(gate.calls).containsExactly("Disarm FAFO Security")
    }

    @Test fun smartLockUnlockPromptsOnTheDisarmKey() = runTest {
        // USER_PRESENT after arming, but no auth token: the grace key says NeedsAuth.
        armKey.create(30)
        assertThat(auth.authorizeDisarm(trailAllowsSkip = true)).isEqualTo(AuthResult.Allowed(prompted = true))
        assertThat(gate.confirmMacs.single()).isNotNull()
        assertThat(disarmKey.grace).isEqualTo(0)
        assertThat(disarmKey.finished).isEqualTo(1)
    }

    @Test fun forcedPromptUsesAPerUseCryptoObject() = runTest {
        auth.authorizeDisarm(trailAllowsSkip = false)
        assertThat(gate.confirmMacs.single()).isNotNull()
        assertThat(disarmKey.grace).isEqualTo(0)
        assertThat(armKey.grace).isNull() // the arm key is not touched by a forced prompt
    }

    @Test fun cancelLeavesItArmed() = runTest {
        gate.result = GateResult.Failed("Cancelled", cancelled = true)
        assertThat(auth.authorizeDisarm(trailAllowsSkip = false)).isEqualTo(AuthResult.Cancelled)
    }

    @Test fun armingStillUsesTheGraceWindow() = runTest {
        armKey.create(30); armKey.authenticated = true
        assertThat(auth.authorize(AuthAction.ARM)).isEqualTo(AuthResult.Allowed(prompted = false))
    }

    @Test fun noSecureLockBlocksDisarm() = runTest {
        gate.result = GateResult.NoSecureLock
        assertThat(auth.authorizeDisarm(trailAllowsSkip = true)).isEqualTo(AuthResult.NoSecureLock)
    }

    @Test fun withoutADisarmKeyThePromptIsStillForced() = runTest {
        armKey.create(30); armKey.authenticated = true
        val plain = ArmAuthenticator(armKey, gate.apply { onConfirmed = { armKey.authenticated = true } }, { 30 }, Dispatchers.Unconfined)
        assertThat(plain.authorizeDisarm(trailAllowsSkip = false)).isEqualTo(AuthResult.Allowed(prompted = true))
        assertThat(gate.calls).hasSize(1)
    }
}
