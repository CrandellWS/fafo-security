package ws.crandell.fafo.arm

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Looper
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
import org.robolectric.android.controller.ServiceController
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.storage.DpStore
import java.util.concurrent.TimeUnit

class RecordingSound : AlarmSound {
    val calls = mutableListOf<String>()
    override fun startPreAlarm(durationMs: Long) { calls += "prealarm:$durationMs" }
    override fun startSiren() { calls += "siren" }
    override fun stop() { calls += "stop" }
    override fun chirp(kind: ChirpKind) { calls += "chirp:$kind" }
}

/** Stands in for the local network: records attach / alerts and exposes the command target. */
class FakeAlarmNetwork : ws.crandell.fafo.net.android.AlarmNetwork {
    var target: ws.crandell.fafo.net.CommandTarget? = null
    var alertInfo: (() -> ws.crandell.fafo.net.AlertInfo?)? = null
    var attaches = 0
    override fun attach(target: ws.crandell.fafo.net.CommandTarget): Boolean { this.target = target; attaches++; return true }
    override fun detach() { target = null }
    override fun startAlerts(info: () -> ws.crandell.fafo.net.AlertInfo?) { alertInfo = info }
    override fun stopAlerts() { alertInfo = null }
}

/** Shared helpers for service tests under Robolectric's paused main looper. */
object ServiceTestSupport {
    fun setPlugged(app: Application, plugged: Boolean) {
        @Suppress("DEPRECATION")
        app.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED, if (plugged) BatteryManager.BATTERY_PLUGGED_AC else 0),
        )
    }

    fun setKeyguard(app: Application, secure: Boolean, locked: Boolean) {
        val km = app.getSystemService(KeyguardManager::class.java)
        shadowOf(km).setIsDeviceSecure(secure)
        shadowOf(km).setIsDeviceLocked(locked)
    }

    /** Run the main looper (with real time for DataStore IO) until [cond] holds. */
    fun idleUntil(timeoutMs: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        check(cond()) { "condition not reached within $timeoutMs ms" }
    }

    fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)
}

@RunWith(AndroidJUnit4::class)
class ArmServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dp: DpStore
    private val sound = RecordingSound()
    private val network = FakeAlarmNetwork()
    private val fakeCapturer = ws.crandell.fafo.camera.Capturer { ws.crandell.fafo.camera.CapturedPhoto(ByteArray(500), IntArray(1024)) }
    private var controller: ServiceController<ArmService>? = null

    @Before fun setUp() {
        dp = DpStore.create(app, scope)
        ArmDeps.dpStore = { dp }
        ArmDeps.network = { network }
        ArmDeps.sound = { _, _, _ -> sound }
        ArmDeps.capturer = { _, _ -> fakeCapturer }
        ArmDeps.freeBytes = { Long.MAX_VALUE }
        ws.crandell.fafo.storage.CeStore.override = ws.crandell.fafo.storage.CeStore.create(app, scope)
        ArmDeps.clock = ws.crandell.fafo.core.Clock { android.os.SystemClock.uptimeMillis() }
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS, android.Manifest.permission.CAMERA)
        ServiceTestSupport.setPlugged(app, true)
        ServiceTestSupport.setKeyguard(app, secure = true, locked = true)
    }

    @After fun tearDown() {
        controller?.destroy()
        ArmDeps.reset()
        ws.crandell.fafo.storage.CeStore.override = null
        scope.cancel()
    }

    private fun startArm(): ArmService {
        val c = Robolectric.buildService(ArmService::class.java, Intent(app, ArmService::class.java).setAction(ArmService.ACTION_ARM))
        controller = c
        val svc = c.create().startCommand(0, 1).get()
        ServiceTestSupport.idleUntil { svc.state is AlarmState.Armed && svc.isListening }
        return svc
    }

    @Test fun armStartsForegroundWithSpecialUseAndCamera() {
        val svc = startArm()
        assertThat(svc.currentFgsType).isEqualTo(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )
        val n = shadowOf(svc).lastForegroundNotification
        assertThat(n).isNotNull()
        assertThat(n.extras.getString("android.title")).isEqualTo("FAFO Security is armed")
        assertThat(n.actions.map { it.title.toString() }).contains("Disarm")
        assertThat(n.deleteIntent).isNotNull()
        ServiceTestSupport.idleUntil { runBlocking { dp.readState().armed } }
    }

    @Test fun armWithoutCameraPermissionUsesSpecialUseOnly() {
        shadowOf(app).denyPermissions(android.Manifest.permission.CAMERA)
        val svc = startArm()
        assertThat(svc.currentFgsType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    @Test fun armRefusedWhenUnpluggedStopsService() {
        ServiceTestSupport.setPlugged(app, false)
        val c = Robolectric.buildService(ArmService::class.java, Intent(app, ArmService::class.java).setAction(ArmService.ACTION_ARM))
        controller = c
        val svc = c.create().startCommand(0, 1).get()
        ServiceTestSupport.idleUntil { shadowOf(svc).isStoppedBySelf }
        assertThat(svc.state).isEqualTo(AlarmState.Disarmed)
    }

    @Test fun receiversRegisteredWhenArmed() {
        startArm()
        val actions = shadowOf(app).registeredReceivers.flatMap { w -> w.intentFilter.actionsIterator().asSequence().toList() }
        assertThat(actions).containsAtLeast(
            Intent.ACTION_POWER_DISCONNECTED, Intent.ACTION_POWER_CONNECTED, Intent.ACTION_USER_PRESENT,
        )
    }

    @Test fun disconnectThenWindowWhileLockedTriggers() {
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 500) } }
        val svc = startArm()
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(600)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.Triggered && "siren" in sound.calls }
        ServiceTestSupport.idleUntil { runBlocking { dp.readState().incidentActive } }
    }

    /** M10: alerts start with the incident; a verified remote Reset leaves the phone ARMED, waiting for power. */
    @Test fun alertsAndRemoteResetWhileUnplugged() {
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 100) } }
        val svc = startArm()
        assertThat(network.target).isNotNull()
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(150)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.Triggered && network.alertInfo != null }
        val info = network.alertInfo!!.invoke()!!
        assertThat(info.state).isEqualTo("TRIGGERED")
        assertThat(network.target!!.currentIncident()).isEqualTo(info.incidentId)
        runBlocking { network.target!!.apply(ws.crandell.fafo.net.Command.RESET, "Register", "home") }
        ServiceTestSupport.idleUntil { svc.state == AlarmState.Armed(awaitingPower = true) && network.alertInfo == null }
        ServiceTestSupport.idleUntil { "chirp:REMOTE" in sound.calls }
        ServiceTestSupport.idleUntil { runBlocking { dp.readState().let { it.armed && it.awaitingPower && !it.incidentActive } } }
    }

    @Test fun remoteDisarmStopsService() {
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 100) } }
        val svc = startArm()
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(150)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.Triggered }
        runBlocking { network.target!!.apply(ws.crandell.fafo.net.Command.DISARM, "Register", "home") }
        ServiceTestSupport.idleUntil { svc.state == AlarmState.Disarmed && shadowOf(svc).isStoppedBySelf }
        assertThat(network.target).isNull()
    }

    @Test fun preAlarmThenTriggered() {
        runBlocking { dp.update { it.copy(preAlarmS = 1, sensitivityMs = 100) } }
        val svc = startArm()
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(150)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.PreAlarm && "prealarm:1000" in sound.calls }
        ServiceTestSupport.advance(1100)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.Triggered }
    }

    @Test fun reconnectInsideWindowDoesNotTrigger() {
        runBlocking { dp.update { it.copy(sensitivityMs = 2000) } }
        val svc = startArm()
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(500)
        ServiceTestSupport.setPlugged(app, true)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_CONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince == null }
        ServiceTestSupport.advance(3000)
        assertThat(svc.state).isEqualTo(AlarmState.Armed())
        assertThat(sound.calls).doesNotContain("siren")
    }

    @Test fun unlockStopsAlarm() {
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 100) } }
        val svc = startArm()
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(150)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.Triggered }
        app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        ServiceTestSupport.idleUntil { svc.state == AlarmState.Disarmed }
        assertThat(sound.calls.last()).isEqualTo("stop")
        ServiceTestSupport.idleUntil { runBlocking { !dp.readState().incidentActive && !dp.readState().armed } }
    }

    @Test fun motionListensOnlyWhileArmed() {
        val sm = app.getSystemService(android.hardware.SensorManager::class.java)
        shadowOf(sm).addSensor(org.robolectric.shadows.ShadowSensor.newInstance(android.hardware.Sensor.TYPE_ACCELEROMETER))
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 100) } }
        val svc = startArm()
        ServiceTestSupport.idleUntil { svc.motionRunning }
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(150)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.Triggered && !svc.motionRunning }
    }

    @Test fun motionOffSettingDoesNotListen() {
        val sm = app.getSystemService(android.hardware.SensorManager::class.java)
        shadowOf(sm).addSensor(org.robolectric.shadows.ShadowSensor.newInstance(android.hardware.Sensor.TYPE_ACCELEROMETER))
        runBlocking { dp.update { it.copy(motion = "OFF") } }
        val svc = startArm()
        ServiceTestSupport.advance(50)
        assertThat(svc.motionRunning).isFalse()
    }

    @Test fun triggerTakesFirstPhotoIntoIncidentFolder() {
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 100) } }
        val svc = startArm()
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(150)
        ServiceTestSupport.idleUntil { svc.photosTaken >= 1 }
        val id = (svc.state as AlarmState.Triggered).incidentId
        val dir = java.io.File(app.filesDir, "incidents/$id")
        assertThat(dir.listFiles()!!.map { it.name }).contains("incident.json")
        app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        ServiceTestSupport.idleUntil { svc.state == AlarmState.Disarmed }
        ServiceTestSupport.idleUntil { java.io.File(dir, "incident.json").readText().contains("UNLOCKED") }
    }

    private fun nm() = app.getSystemService(android.app.NotificationManager::class.java)
    private fun posted(id: Int): android.app.Notification? = shadowOf(nm()).getNotification(id)

    private fun triggerNow(svc: ArmService) {
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(150)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.Triggered && posted(Notifications.ID_ALARM) != null }
    }

    /** Bug 2026-10-28: the full-screen intent rode on an UPDATE of the armed notification, which never launches. */
    @Test fun triggerPostsSeparateFullScreenAlarmNotification() {
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 100) } }
        val svc = startArm()
        assertThat(posted(Notifications.ID_ALARM)).isNull()
        triggerNow(svc)
        val alarm = posted(Notifications.ID_ALARM)!!
        assertThat(alarm.fullScreenIntent).isNotNull()
        assertThat(alarm.channelId).isEqualTo(Notifications.CH_ALARM_SCREEN)
        assertThat(alarm.category).isEqualTo(android.app.Notification.CATEGORY_ALARM)
        assertThat(alarm.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE).isEqualTo(0)
        assertThat(alarm.flags and android.app.Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(alarm.visibility).isEqualTo(android.app.Notification.VISIBILITY_PUBLIC)
        assertThat(alarm.actions.map { it.title.toString() }).contains("Disarm")
        assertThat(alarm.deleteIntent).isNotNull()
        // The ongoing service notification is only updated and carries no full-screen intent.
        assertThat(posted(Notifications.ID_ARMED)!!.fullScreenIntent).isNull()
        val channel = nm().getNotificationChannel(Notifications.CH_ALARM_SCREEN)
        assertThat(channel.importance).isEqualTo(android.app.NotificationManager.IMPORTANCE_HIGH)
        assertThat(channel.canBypassDnd()).isTrue()
    }

    @Test fun alarmNotificationPostedOnceThenCancelledOnUnlock() {
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 100) } }
        val svc = startArm()
        triggerNow(svc)
        val first = posted(Notifications.ID_ALARM)!!
        ServiceTestSupport.idleUntil { svc.photosTaken >= 1 }
        ServiceTestSupport.advance(100)
        // Later state changes (photos, IncidentPersisted) do not re-post the alarm notification.
        assertThat(posted(Notifications.ID_ALARM)).isSameInstanceAs(first)
        app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        ServiceTestSupport.idleUntil { svc.state == AlarmState.Disarmed && posted(Notifications.ID_ALARM) == null }
    }

    @Test fun remoteResetCancelsAlarmNotification() {
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 100) } }
        val svc = startArm()
        triggerNow(svc)
        runBlocking { network.target!!.apply(ws.crandell.fafo.net.Command.RESET, "Register", "home") }
        ServiceTestSupport.idleUntil { svc.state == AlarmState.Armed(awaitingPower = true) && posted(Notifications.ID_ALARM) == null }
        assertThat(posted(Notifications.ID_ARMED)).isNotNull()
    }

    @Test fun preAlarmHasNoFullScreenNotification() {
        runBlocking { dp.update { it.copy(preAlarmS = 5, sensitivityMs = 100) } }
        val svc = startArm()
        ServiceTestSupport.setPlugged(app, false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        ServiceTestSupport.idleUntil { (svc.state as? AlarmState.Armed)?.unplugPendingSince != null }
        ServiceTestSupport.advance(150)
        ServiceTestSupport.idleUntil { svc.state is AlarmState.PreAlarm }
        assertThat(posted(Notifications.ID_ALARM)).isNull()
    }

    @Test fun swipedAlarmNotificationIsPostedAgain() {
        runBlocking { dp.update { it.copy(preAlarmS = 0, sensitivityMs = 100) } }
        val svc = startArm()
        triggerNow(svc)
        nm().cancel(Notifications.ID_ALARM)
        controller!!.withIntent(Intent(app, ArmService::class.java).setAction(ArmService.ACTION_REPOST_ALARM)).startCommand(0, 2)
        ServiceTestSupport.idleUntil { posted(Notifications.ID_ALARM)?.fullScreenIntent != null }
    }
}
