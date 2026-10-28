package ws.crandell.fafo.auth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import ws.crandell.fafo.arm.Notifications
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.pairing.BiometricCredentialGate
import ws.crandell.fafo.ui.MainActivity
import androidx.biometric.BiometricPrompt

/** AC1.7: the notification Disarm action opens the app to confirm; it never disarms from a service intent. */
@RunWith(AndroidJUnit4::class)
class DisarmNotificationTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun disarmActionOpensTheAppThroughTheUnexportedAlias() {
        Notifications.ensureChannels(app)
        listOf(
            Notifications.forState(app, AlarmState.Armed(), photosPaused = false, armedAfterBootNoCamera = false),
            Notifications.alarm(app, photosPaused = false),
        ).forEach { n ->
            val action = n.actions.single { it.title.toString() == "Disarm" }
            val pi = shadowOf(action.actionIntent)
            assertThat(pi.isActivityIntent).isTrue()
            assertThat(pi.savedIntent.action).isEqualTo(MainActivity.ACTION_REQUEST_DISARM)
            assertThat(pi.savedIntent.component?.className).isEqualTo(MainActivity.DISARM_ALIAS)
        }
    }

    @Test fun aliasIsNotExported() {
        @Suppress("DEPRECATION")
        val info = app.packageManager.getActivityInfo(android.content.ComponentName(app, MainActivity.DISARM_ALIAS), 0)
        assertThat(info.exported).isFalse()
        assertThat(info.targetActivity).isEqualTo(MainActivity::class.java.name)
    }

    @Test fun promptErrorsMapToCancelOrLockout() {
        assertThat(BiometricCredentialGate.failure(BiometricPrompt.ERROR_USER_CANCELED, "x").cancelled).isTrue()
        assertThat(BiometricCredentialGate.failure(BiometricPrompt.ERROR_NEGATIVE_BUTTON, "x").cancelled).isTrue()
        val lockout = BiometricCredentialGate.failure(BiometricPrompt.ERROR_LOCKOUT, "x")
        assertThat(lockout.cancelled).isFalse()
        assertThat(lockout.reason).isEqualTo("Too many attempts. Try again later.")
    }
}
