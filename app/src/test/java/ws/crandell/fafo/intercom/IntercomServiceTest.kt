package ws.crandell.fafo.intercom

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import ws.crandell.fafo.trust.TrustGraph

@RunWith(AndroidJUnit4::class)
class IntercomServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var homeId: String

    @Before fun setUp() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS, android.Manifest.permission.RECORD_AUDIO)
        homeId = TrustGraph.circles(app).all().first().id
    }

    @After fun tearDown() {
        Intercom.stopRoomMode()
        TrustGraph.reset()
    }

    private fun start(intent: Intent?) = Robolectric.buildService(IntercomService::class.java, intent).create().startCommand(0, 1).get()

    private fun startIntent() = Intent(app, IntercomService::class.java)
        .setAction(IntercomService.ACTION_START)
        .putExtra(IntercomService.EXTRA_CIRCLES, arrayOf(homeId))

    @Test fun startsWithMicrophoneAndConnectedDeviceAndNotificationFirst() {
        val svc = start(startIntent())
        assertThat(svc.currentFgsType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        val n = shadowOf(svc).lastForegroundNotification
        assertThat(n.extras.getString("android.title")).isEqualTo("Intercom on: Home can listen")
        assertThat(n.actions.map { it.title.toString() }).contains("Stop")
        assertThat(Intercom.ui.value.roomCircles).containsExactly(homeId)
    }

    @Test fun refusesWhenNotificationsBlocked() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val svc = start(startIntent())
        assertThat(svc.currentFgsType).isEqualTo(0)
        assertThat(shadowOf(svc).isStoppedBySelf).isTrue()
        assertThat(Intercom.ui.value.roomCircles).isNull()
    }

    @Test fun neverFromStickyRestartOrWithoutTheStartAction() {
        val sticky = start(null)
        assertThat(sticky.currentFgsType).isEqualTo(0)
        assertThat(shadowOf(sticky).isStoppedBySelf).isTrue()
        val other = start(Intent(app, IntercomService::class.java).putExtra(IntercomService.EXTRA_CIRCLES, arrayOf(homeId)))
        assertThat(other.currentFgsType).isEqualTo(0)
        assertThat(Intercom.ui.value.roomCircles).isNull()
    }

    @Test fun alarmEndsRoomMode() {
        start(startIntent())
        assertThat(Intercom.ui.value.roomCircles).isNotNull()
        ws.crandell.fafo.core.AlarmStatusBus.endIntercom!!.invoke()
        assertThat(Intercom.ui.value.roomCircles).isNull()
        Intercom.onIncident(false)
    }

    @Test fun manifestTypeNotDirectBootAware() {
        val si = app.packageManager.getServiceInfo(ComponentName(app, IntercomService::class.java), PackageManager.GET_META_DATA)
        assertThat(si.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        assertThat(si.directBootAware).isFalse()
        assertThat(si.exported).isFalse()
    }
}
