package ws.crandell.fafo.reach

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import ws.crandell.fafo.arm.ServiceTestSupport
import ws.crandell.fafo.storage.CeStore

@RunWith(AndroidJUnit4::class)
class ReachableTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before fun setUp() {
        CeStore.override = CeStore.create(app, scope)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun tearDown() {
        CeStore.override = null
        scope.cancel()
    }

    @Test fun startsAsConnectedDeviceOnlyWithNotification() {
        val c = Robolectric.buildService(ReachableService::class.java, Intent(app, ReachableService::class.java))
        val svc = c.create().startCommand(0, 1).get()
        assertThat(svc.currentFgsType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        val n = shadowOf(svc).lastForegroundNotification
        assertThat(n.extras.getString("android.title")).startsWith("Reachable by:")
        assertThat(n.actions.map { it.title.toString() }).contains("Stop")
        c.destroy()
    }

    @Test fun manifestTypeAndNotDirectBootAware() {
        val si = app.packageManager.getServiceInfo(ComponentName(app, ReachableService::class.java), PackageManager.GET_META_DATA)
        assertThat(si.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        assertThat(si.directBootAware).isFalse()
    }

    @Test fun resumesFromBootCompletedWhenOn() {
        runBlocking { CeStore.get(app).update { it.copy(stayReachable = true) } }
        ReachableBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        ServiceTestSupport.idleUntil { shadowOf(app).peekNextStartedService()?.component?.className == ReachableService::class.java.name }
    }

    @Test fun notFromLockedBootCompleted() {
        runBlocking { CeStore.get(app).update { it.copy(stayReachable = true) } }
        ReachableBootReceiver().onReceive(app, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        Thread.sleep(300)
        ServiceTestSupport.advance(10)
        assertThat(shadowOf(app).peekNextStartedService()).isNull()
    }

    @Test fun offStaysOff() {
        ReachableBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        Thread.sleep(300)
        ServiceTestSupport.advance(10)
        assertThat(shadowOf(app).peekNextStartedService()).isNull()
    }
}
