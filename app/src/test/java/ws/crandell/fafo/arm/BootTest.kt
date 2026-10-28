package ws.crandell.fafo.arm

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.storage.CeSettings
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.storage.DpState
import ws.crandell.fafo.storage.DpStore

/** A CeStore that fails the test if anything on the boot path opens it. */
class ThrowingCeStore : CeStore(null) {
    var touched = false
    private fun boom(): Nothing { touched = true; throw AssertionError("CE storage opened on the boot path") }
    override val settings: Flow<CeSettings> get() = boom()
    override suspend fun read(): CeSettings = boom()
    override suspend fun update(transform: (CeSettings) -> CeSettings) = boom()
    override val onboardingFlags: Flow<Set<String>> get() = boom()
    override suspend fun markOnboarding(flag: String) = boom()
}

@RunWith(AndroidJUnit4::class)
class BootTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dp: DpStore
    private val sound = RecordingSound()
    private val ce = ThrowingCeStore()
    private val network = FakeAlarmNetwork()
    private var controller: ServiceController<ArmService>? = null

    @Before fun setUp() {
        dp = DpStore.create(app, scope)
        ArmDeps.dpStore = { dp }
        ArmDeps.network = { network }
        ArmDeps.sound = { _, _, _ -> sound }
        ArmDeps.clock = ws.crandell.fafo.core.Clock { android.os.SystemClock.uptimeMillis() }
        CeStore.override = ce
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS, android.Manifest.permission.CAMERA)
        ServiceTestSupport.setKeyguard(app, secure = true, locked = true)
        ServiceTestSupport.setPlugged(app, false)
    }

    @After fun tearDown() {
        controller?.destroy()
        ArmDeps.reset()
        CeStore.override = null
        scope.cancel()
    }

    private fun sendBoot(action: String) {
        // Direct call: Robolectric does not dispatch protected boot broadcasts to manifest receivers.
        BootReceiver().onReceive(app, Intent(action))
    }

    private fun startBootService(): ArmService {
        val c = Robolectric.buildService(ArmService::class.java, Intent(app, ArmService::class.java).setAction(ArmService.ACTION_BOOT))
        controller = c
        return c.create().startCommand(0, 1).get()
    }

    @Test fun bootDecision() {
        assertThat(BootReceiver.shouldStart(DpState(), false)).isFalse()
        assertThat(BootReceiver.shouldStart(DpState(incidentActive = true), false)).isTrue()
        assertThat(BootReceiver.shouldStart(DpState(armed = true), false)).isTrue()
        assertThat(BootReceiver.shouldStart(DpState(armed = true, incidentActive = true), serviceRunning = true)).isFalse()
    }

    @Test fun lockedBootWithIncidentStartsService() {
        runBlocking { dp.setArmed(true, false); dp.setIncident(true, "inc", 1L) }
        sendBoot(Intent.ACTION_LOCKED_BOOT_COMPLETED)
        ServiceTestSupport.idleUntil { shadowOf(app).peekNextStartedService() != null }
        val started = shadowOf(app).nextStartedService
        assertThat(started.component?.className).isEqualTo(ArmService::class.java.name)
        assertThat(started.action).isEqualTo(ArmService.ACTION_BOOT)
    }

    @Test fun bootWithoutFlagsStartsNothing() {
        sendBoot(Intent.ACTION_BOOT_COMPLETED)
        Thread.sleep(300)
        ServiceTestSupport.advance(10)
        assertThat(shadowOf(app).peekNextStartedService()).isNull()
    }

    @Test fun bootServiceResumesSirenSpecialUseOnlyWithoutTouchingCe() {
        runBlocking { dp.setArmed(true, false); dp.setIncident(true, "inc", 1L) }
        val svc = startBootService()
        ServiceTestSupport.idleUntil { svc.state is AlarmState.IncidentPersisted && "siren" in sound.calls }
        assertThat(svc.state).isEqualTo(AlarmState.IncidentPersisted("inc", 1L))
        assertThat(svc.currentFgsType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        assertThat(sound.calls).doesNotContain("prealarm:5000") // no second countdown
        assertThat(ce.touched).isFalse()
    }

    @Test fun bootArmedUnpluggedLockedRunsNormalWindowThenPreAlarm() {
        runBlocking { dp.setArmed(true, false); dp.update { it.copy(sensitivityMs = 100, preAlarmS = 1) } }
        val svc = startBootService()
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        assertThat(svc.currentFgsType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        ServiceTestSupport.advance(150)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.PreAlarm && "prealarm:1000" in sound.calls }
        assertThat(ce.touched).isFalse()
    }

    @Test fun bootArmedPluggedResumesArmedWithNotice() {
        ServiceTestSupport.setPlugged(app, true)
        runBlocking { dp.setArmed(true, false) }
        val svc = startBootService()
        ServiceTestSupport.idleUntil { svc.state == AlarmState.Armed() && svc.isListening }
        val n = shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).allNotifications
            .first { it.extras.getString("android.title") == "FAFO Security is armed" }
        assertThat(n.extras.getCharSequence("android.text").toString()).contains("open the app to enable photos")
    }

    @Test fun stickyRestartIsTheBootPath() {
        runBlocking { dp.setArmed(true, false); dp.setIncident(true, "inc", 1L) }
        val c = Robolectric.buildService(ArmService::class.java, null)
        controller = c
        val svc = c.create().startCommand(android.app.Service.START_FLAG_RETRY, 1).get()
        ServiceTestSupport.idleUntil { svc.state is AlarmState.IncidentPersisted }
        assertThat(svc.currentFgsType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    @Test fun bootPathComponentsAreDirectBootAware() {
        val pm = app.packageManager
        assertThat(pm.getReceiverInfo(ComponentName(app, BootReceiver::class.java), 0).directBootAware).isTrue()
        assertThat(pm.getServiceInfo(ComponentName(app, ArmService::class.java), 0).directBootAware).isTrue()
        assertThat(pm.getActivityInfo(ComponentName(app, AlarmActivity::class.java), 0).directBootAware).isTrue()
        val r = pm.getReceiverInfo(ComponentName(app, BootReceiver::class.java), PackageManager.GET_META_DATA)
        assertThat(r.exported).isTrue()
    }
}
