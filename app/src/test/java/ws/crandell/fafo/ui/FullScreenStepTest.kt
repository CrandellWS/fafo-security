package ws.crandell.fafo.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import ws.crandell.fafo.arm.Notifications
import ws.crandell.fafo.system.DeviceStatus

/** The full-screen alarm step stays on the checklist until the permission AND the alarm channel are OK. */
@RunWith(AndroidJUnit4::class)
class FullScreenStepTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val nm get() = app.getSystemService(NotificationManager::class.java)

    @Test fun stepNeedsPermissionAndChannel() {
        assertThat(OnboardingStatus(fullScreen = true, alarmChannel = true).isDone(OnboardingStep.FULL_SCREEN)).isTrue()
        assertThat(OnboardingStatus(fullScreen = false, alarmChannel = true).isDone(OnboardingStep.FULL_SCREEN)).isFalse()
        assertThat(OnboardingStatus(fullScreen = true, alarmChannel = false).isDone(OnboardingStep.FULL_SCREEN)).isFalse()
    }

    @Test fun deepLinksPermissionFirstThenChannel() {
        val perm = OnboardingIntents.fullScreenStep(app, OnboardingStatus(fullScreen = false))
        assertThat(perm.action).isEqualTo(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
        val ch = OnboardingIntents.fullScreenStep(app, OnboardingStatus(fullScreen = true, alarmChannel = false))
        assertThat(ch.action).isEqualTo(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        assertThat(ch.getStringExtra(Settings.EXTRA_CHANNEL_ID)).isEqualTo(Notifications.CH_ALARM_SCREEN)
    }

    @Test fun alarmChannelOkTracksImportance() {
        assertThat(DeviceStatus.alarmChannelOk(app)).isTrue() // not created yet
        Notifications.ensureChannels(app)
        assertThat(DeviceStatus.alarmChannelOk(app)).isTrue()
        // Simulate the user lowering it in system settings.
        nm.createNotificationChannel(NotificationChannel(Notifications.CH_ALARM_SCREEN, "Alarm screen", NotificationManager.IMPORTANCE_LOW))
        assertThat(DeviceStatus.alarmChannelOk(app)).isFalse()
    }
}
