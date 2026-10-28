package ws.crandell.fafo.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ws.crandell.fafo.pairing.GateResult
import ws.crandell.fafo.testutil.FakeGate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Keystore stand-in: [authenticated] is "the owner unlocked or confirmed within the grace window". */
class FakeArmAuthKey : ArmAuthKey {
    var grace: Int? = null
    var created = 0
    var authenticated = false
    var invalidated = false
    var finished = 0

    override fun graceOfKey(): Int? = grace
    override fun create(graceS: Int) { grace = graceS; created++; invalidated = false; authenticated = false }
    override fun check(): KeyCheck = when {
        grace == null -> KeyCheck.NoKey
        invalidated -> KeyCheck.Invalidated
        grace == 0 -> KeyCheck.NeedsAuth(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32), "HmacSHA256")) })
        authenticated -> KeyCheck.Ok
        else -> KeyCheck.NeedsAuth(null)
    }
    override fun finish(mac: Mac): Boolean { mac.doFinal(byteArrayOf(1)); finished++; return true }
}

class ArmAuthenticatorTest {
    private val key = FakeArmAuthKey()
    private val gate = FakeGate().apply { onConfirmed = { key.authenticated = true } }
    private var grace = 30
    private val auth = ArmAuthenticator(key, gate, { grace }, Dispatchers.Unconfined)

    @Test fun insideGraceWindowNoPrompt() = runTest {
        key.create(30); key.authenticated = true // the owner just unlocked the phone
        assertThat(auth.authorize(AuthAction.ARM)).isEqualTo(AuthResult.Allowed(prompted = false))
        assertThat(gate.calls).isEmpty()
    }

    @Test fun outsideGraceWindowPrompts() = runTest {
        key.create(30)
        assertThat(auth.authorize(AuthAction.DISARM)).isEqualTo(AuthResult.Allowed(prompted = true))
        assertThat(gate.calls).containsExactly("Disarm FAFO Security")
        assertThat(gate.confirmMacs).containsExactly(null)
    }

    @Test fun promptCancelledIsCancelled() = runTest {
        key.create(30)
        gate.result = GateResult.Failed("Cancelled", cancelled = true)
        assertThat(auth.authorize(AuthAction.ARM)).isEqualTo(AuthResult.Cancelled)
    }

    @Test fun lockoutIsFailedWithReason() = runTest {
        key.create(30)
        gate.result = GateResult.Failed("Too many attempts. Try again later.")
        assertThat(auth.authorize(AuthAction.ARM)).isEqualTo(AuthResult.Failed("Too many attempts. Try again later."))
    }

    @Test fun promptSucceededButKeyStillLockedFails() = runTest {
        key.create(30)
        gate.onConfirmed = {} // Keystore did not get an auth token (e.g. Class 2 face)
        assertThat(auth.authorize(AuthAction.ARM)).isEqualTo(AuthResult.Failed("Confirmation not accepted"))
    }

    @Test fun invalidatedKeyIsRecreatedThenPrompts() = runTest {
        key.create(30); key.invalidated = true
        assertThat(auth.authorize(AuthAction.ARM)).isEqualTo(AuthResult.Allowed(prompted = true))
        assertThat(key.created).isEqualTo(2)
        assertThat(gate.calls).hasSize(1)
    }

    @Test fun missingKeyIsCreatedWithTheSetting() = runTest {
        grace = 60
        auth.authorize(AuthAction.ARM)
        assertThat(key.grace).isEqualTo(60)
    }

    @Test fun keyWithOtherGraceIsRecreated() = runTest {
        key.create(15); key.authenticated = true
        grace = 120
        assertThat(auth.authorize(AuthAction.ARM)).isEqualTo(AuthResult.Allowed(prompted = true))
        assertThat(key.grace).isEqualTo(120)
    }

    @Test fun graceZeroAlwaysPromptsWithCryptoObject() = runTest {
        grace = 0
        key.create(0); key.authenticated = true // even right after an unlock
        assertThat(auth.authorize(AuthAction.ARM)).isEqualTo(AuthResult.Allowed(prompted = true))
        assertThat(gate.confirmMacs.single()).isNotNull()
        assertThat(key.finished).isEqualTo(1)
    }

    @Test fun noSecureLockBlocks() = runTest {
        gate.result = GateResult.NoSecureLock
        assertThat(auth.authorize(AuthAction.ARM)).isEqualTo(AuthResult.NoSecureLock)
        assertThat(key.created).isEqualTo(0)
    }

    @Test fun keystoreErrorFailsClosed() = runTest {
        val broken = object : ArmAuthKey by key { override fun check(): KeyCheck = throw java.security.ProviderException("boom") }
        val r = ArmAuthenticator(broken, gate, { 30 }, Dispatchers.Unconfined).authorize(AuthAction.ARM)
        assertThat(r).isInstanceOf(AuthResult.Failed::class.java)
    }
}
