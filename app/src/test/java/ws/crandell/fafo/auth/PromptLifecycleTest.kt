package ws.crandell.fafo.auth

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager
import ws.crandell.fafo.pairing.BiometricCredentialGate
import ws.crandell.fafo.pairing.GateResult

/**
 * AC1.7: if the activity showing the prompt is destroyed (rotation, theme change) the prompt can never answer.
 * The gate must report a cancel then, or Arm / Disarm would stay "Confirming" for good.
 */
@RunWith(AndroidJUnit4::class)
class PromptLifecycleTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun destroyedActivityEndsThePromptAsCancelled() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        Shadow.extract<ShadowBiometricManager>(activity.getSystemService(BiometricManager::class.java)).apply {
            setCanAuthenticate(true)
            setAuthenticatorType(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        }
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val result = scope.async(start = CoroutineStart.UNDISPATCHED) {
            BiometricCredentialGate(activity).confirmUser("Disarm", "test", null)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(result.isActive).isTrue()

        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result.isCompleted).isTrue()
        val r = result.getCompleted()
        assertThat(r).isInstanceOf(GateResult.Failed::class.java)
        assertThat((r as GateResult.Failed).cancelled).isTrue()
        assertThat(GuardedAction.message(AuthAction.DISARM, AuthResult.Cancelled)).isEqualTo("Still armed. Confirmation cancelled.")
        scope.cancel()
    }
}
