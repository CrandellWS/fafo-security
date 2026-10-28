package ws.crandell.fafo.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ws.crandell.fafo.pairing.GateResult
import ws.crandell.fafo.testutil.FakeGate

class GuardedActionTest {
    private val messages = mutableListOf<String>()

    @Test fun cancelledArmNeverStartsTheService() = runTest {
        var armed = false
        val g = GuardedAction { AuthResult.Cancelled }
        g.run(AuthAction.ARM, messages::add) { armed = true }
        assertThat(armed).isFalse()
        assertThat(messages).containsExactly("Not armed. Confirmation cancelled.")
    }

    @Test fun allowedRunsTheBlockSilently() = runTest {
        var armed = false
        GuardedAction { AuthResult.Allowed(prompted = false) }.run(AuthAction.ARM, messages::add) { armed = true }
        assertThat(armed).isTrue()
        assertThat(messages).isEmpty()
    }

    @Test fun showsConfirmingWhileThePromptIsUp() = runTest {
        val answer = CompletableDeferred<AuthResult>()
        val g = GuardedAction { answer.await() }
        val job = async { g.run(AuthAction.DISARM, messages::add) {} }
        testScheduler.runCurrent()
        assertThat(g.ui.value).isEqualTo(ArmAuthUi.Confirming(AuthAction.DISARM))
        answer.complete(AuthResult.Cancelled)
        job.await()
        assertThat(g.ui.value).isEqualTo(ArmAuthUi.Idle)
        assertThat(messages).containsExactly("Still armed. Confirmation cancelled.")
    }

    @Test fun graceChangeSavesAndRekeysOnlyWhenConfirmed() = runTest {
        val key = FakeArmAuthKey().apply { create(30) }
        val gate = FakeGate().apply { onConfirmed = { key.authenticated = true } }
        var saved = 30
        val auth = ArmAuthenticator(key, gate, { saved }, Dispatchers.Unconfined)
        val g = GuardedAction(auth::authorize)

        gate.result = GateResult.Failed("Cancelled", cancelled = true)
        g.run(AuthAction.CHANGE_GRACE, messages::add) { auth.rekey(60); saved = 60 }
        assertThat(saved).isEqualTo(30)
        assertThat(key.grace).isEqualTo(30)
        assertThat(messages).containsExactly("Not changed. Confirmation cancelled.")

        gate.result = GateResult.Success()
        g.run(AuthAction.CHANGE_GRACE, messages::add) { auth.rekey(60); saved = 60 }
        assertThat(saved).isEqualTo(60)
        assertThat(key.grace).isEqualTo(60)
        assertThat(gate.calls.last()).isEqualTo("Change the unlock grace window")
    }
}
