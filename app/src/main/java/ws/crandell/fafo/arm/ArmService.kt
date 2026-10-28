package ws.crandell.fafo.arm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ws.crandell.fafo.core.AlarmEffect
import ws.crandell.fafo.core.AlarmEvent
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.AlarmStateMachine
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.storage.DpSettings
import ws.crandell.fafo.storage.DpStore
import ws.crandell.fafo.system.DeviceStatus

/**
 * The armed foreground service (spec F1, F2). `specialUse|camera`, directBootAware, START_STICKY.
 * All alarm logic lives in [AlarmStateMachine]; this class only feeds it events and runs its effects
 * in order. Nothing here touches CE storage or Keystore on the boot path (AC2.6a).
 */
class ArmService : LifecycleService() {

    private lateinit var dp: DpStore
    private lateinit var sound: AlarmSound
    private lateinit var machine: AlarmStateMachine
    private var dpSettings: DpSettings? = null
    private val events = Channel<AlarmEvent>(Channel.UNLIMITED)

    private var windowJob: Job? = null
    private var preAlarmJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeKeepAlive: Job? = null
    private var receiverRegistered = false
    private var inForeground = false
    private var cameraType = false
    /** Started from boot / sticky restart: no camera until a visible-app start (notification tap). */
    private var startedWithoutCamera = false

    /** Foreground service type last passed to startForeground, for tests. */
    var currentFgsType: Int = 0
        private set

    val state: AlarmState get() = machine.state

    /** True once the power / user-present receivers are registered (tests wait on it). */
    val isListening: Boolean get() = receiverRegistered

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = ArmDeps.clock.now()
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED -> events.trySend(AlarmEvent.PowerDisconnected(now))
                Intent.ACTION_POWER_CONNECTED -> events.trySend(AlarmEvent.PowerConnected(now))
                Intent.ACTION_USER_PRESENT -> events.trySend(AlarmEvent.UserPresent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.ensureChannels(this)
        dp = ArmDeps.dpStore(this)
        sound = ArmDeps.sound(this, lifecycleScope) { dpSettings ?: DpSettings() }
        machine = AlarmStateMachine(ArmDeps.clock, { (dpSettings ?: DpSettings()).alarmConfig() })
        lifecycleScope.launch { dp.settings.collect { dpSettings = it } }
        lifecycleScope.launch { for (e in events) process(e) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_ARM -> {
                // Visible-app start (the Arm tap): camera type allowed if the permission is granted.
                goForeground(withCamera = DeviceStatus.hasCamera(this))
                events.trySend(
                    AlarmEvent.ArmRequested(
                        plugged = DeviceStatus.isPlugged(this),
                        secureLock = DeviceStatus.isDeviceSecure(this),
                    ),
                )
            }
            ACTION_DISARM -> lifecycleScope.launch {
                if (machine.state == AlarmState.Disarmed) {
                    // Fresh process with stale flags: clear them so nothing resumes later.
                    dp.setArmed(false, false)
                    dp.setIncident(false, null, null)
                }
                events.send(AlarmEvent.DisarmTapped)
            }
            ACTION_REPOST -> postNotification()
            ACTION_NOTIFICATION_TAP -> {
                // Visible-app start (alarm activity / notification tap). The camera type may be added only
                // when CE storage is unlocked, i.e. after a process death, never in direct boot.
                val ce = DeviceStatus.isCeUnlocked(this)
                val camOk = ce && DeviceStatus.hasCamera(this)
                val fresh = !inForeground
                if (fresh || (camOk && !cameraType)) goForeground(withCamera = camOk)
                lifecycleScope.launch {
                    if (fresh) events.send(bootEvent())
                    events.send(AlarmEvent.NotificationTapped(ceUnlocked = ce))
                }
            }
            else -> {
                // ACTION_BOOT or a START_STICKY restart (null intent): SPECIAL_USE only, never camera.
                startedWithoutCamera = true
                goForeground(withCamera = false)
                lifecycleScope.launch { events.send(bootEvent()) }
            }
        }
        return START_STICKY
    }

    private suspend fun bootEvent(): AlarmEvent.BootCompleted {
        val st = dp.readState()
        return AlarmEvent.BootCompleted(
            armed = st.armed,
            incidentActive = st.incidentActive,
            incidentId = st.incidentId,
            incidentStartedAt = st.incidentStartedAt,
            awaitingPower = st.awaitingPower,
            plugged = DeviceStatus.isPlugged(this),
            locked = DeviceStatus.isDeviceLocked(this),
        )
    }

    private fun goForeground(withCamera: Boolean) {
        var type = 0
        if (Build.VERSION.SDK_INT >= 34) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (withCamera) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        val notification = Notifications.forState(this, currentStateForNotification(), AlarmStatusBus.photosPaused.value, false)
        try {
            ServiceCompat.startForeground(this, Notifications.ID_ARMED, notification, type)
            currentFgsType = type
            cameraType = withCamera
        } catch (e: SecurityException) {
            // Camera type refused (background start): fall back to SPECIAL_USE only.
            Log.w(TAG, "startForeground with camera refused, using specialUse only", e)
            val fallback = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, Notifications.ID_ARMED, notification, fallback)
            currentFgsType = fallback
            cameraType = false
        }
        inForeground = true
        AlarmStatusBus.setCameraActive(cameraType)
    }

    private fun currentStateForNotification(): AlarmState =
        if (::machine.isInitialized && machine.state != AlarmState.Disarmed) machine.state else AlarmState.Armed()

    private suspend fun process(e: AlarmEvent) {
        if (dpSettings == null) dpSettings = dp.readSettings()
        val effects = machine.handle(e)
        var stop = false
        for (fx in effects) {
            if (fx == AlarmEffect.StopService) stop = true else run(fx)
        }
        val s = machine.state
        if (e == AlarmEvent.PreAlarmElapsed && s is AlarmState.PreAlarm) {
            // The timer fired a little before endsAt by the clock: wait out the rest.
            val rest = (s.endsAt - ArmDeps.clock.now()).coerceAtLeast(1)
            preAlarmJob = lifecycleScope.launch {
                delay(rest)
                events.send(AlarmEvent.PreAlarmElapsed)
            }
        }
        AlarmStatusBus.publish(s)
        if (s != AlarmState.Disarmed) registerReceivers()
        updateWakeLock()
        if (stop || s == AlarmState.Disarmed) {
            shutdown()
        } else {
            postNotification()
        }
    }

    private suspend fun run(fx: AlarmEffect) {
        when (fx) {
            is AlarmEffect.PersistArmed -> dp.setArmed(fx.armed, fx.awaitingPower)
            is AlarmEffect.PersistIncident -> dp.setIncident(fx.active, fx.id, fx.startedAt)
            is AlarmEffect.StartWindowTimer -> {
                windowJob?.cancel()
                windowJob = lifecycleScope.launch {
                    delay(fx.delayMs)
                    // AC2.2: re-read plugged state and keyguard when the window runs out.
                    events.send(
                        AlarmEvent.WindowElapsed(
                            plugged = DeviceStatus.isPlugged(this@ArmService),
                            locked = DeviceStatus.isDeviceLocked(this@ArmService),
                        ),
                    )
                }
            }
            AlarmEffect.CancelWindowTimer -> windowJob?.cancel()
            is AlarmEffect.StartPreAlarm -> {
                sound.apply(fx)
                preAlarmJob?.cancel()
                preAlarmJob = lifecycleScope.launch {
                    delay(fx.durationMs)
                    events.send(AlarmEvent.PreAlarmElapsed)
                }
            }
            AlarmEffect.StartSiren, AlarmEffect.StopSound -> {
                preAlarmJob?.cancel()
                sound.apply(fx)
            }
            AlarmEffect.StillArmedWarning -> {
                sound.apply(fx)
                notify(Notifications.ID_STILL_ARMED) { Notifications.stillArmed(this) }
            }
            AlarmEffect.RemoteChirp -> sound.apply(fx)
            AlarmEffect.AddCameraType -> Unit // handled in onStartCommand (needs the visible start)
            AlarmEffect.StartPhotos, AlarmEffect.StopPhotos -> hooks.forEach { it.onEffect(fx) }
            is AlarmEffect.CloseIncident -> hooks.forEach { it.onEffect(fx) }
            is AlarmEffect.StartAlerts, AlarmEffect.StopAlerts, AlarmEffect.EndIntercom -> hooks.forEach { it.onEffect(fx) }
            is AlarmEffect.Refused -> Log.i(TAG, "refused: ${fx.reason}")
            AlarmEffect.StopService -> Unit
        }
    }

    /** Later milestones (photos, alerts, intercom) attach here. */
    fun interface EffectHook { fun onEffect(fx: AlarmEffect) }
    private val hooks = mutableListOf<EffectHook>()
    fun addHook(h: EffectHook) { hooks += h }

    private fun registerReceivers() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // AC2.1: not a manifest-allowed implicit broadcast; register at runtime.
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    /** Partial wake lock only during the sensitivity window, PreAlarm and Triggered. */
    private fun updateWakeLock() {
        val s = machine.state
        val need = (s is AlarmState.Armed && s.unplugPendingSince != null) ||
            s is AlarmState.PreAlarm || s is AlarmState.Triggered || s is AlarmState.IncidentPersisted
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (need) {
            val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fafo:alarm").also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
            wl.acquire(WAKE_LOCK_TIMEOUT_MS)
            if (wakeKeepAlive?.isActive != true) {
                wakeKeepAlive = lifecycleScope.launch {
                    while (true) {
                        delay(WAKE_LOCK_TIMEOUT_MS / 2)
                        wl.acquire(WAKE_LOCK_TIMEOUT_MS)
                    }
                }
            }
        } else {
            wakeKeepAlive?.cancel()
            wakeKeepAlive = null
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    private fun postNotification() {
        if (!inForeground || !::machine.isInitialized) return
        val s = machine.state
        if (s == AlarmState.Disarmed) return
        notify(Notifications.ID_ARMED) {
            Notifications.forState(this, s, AlarmStatusBus.photosPaused.value, startedWithoutCamera && !cameraType)
        }
    }

    private inline fun notify(id: Int, build: () -> android.app.Notification) {
        if (!DeviceStatus.hasNotificationPermission(this)) return
        try {
            NotificationManagerCompat.from(this).notify(id, build())
        } catch (e: SecurityException) {
            Log.w(TAG, "notify failed", e)
        }
    }

    private fun shutdown() {
        windowJob?.cancel()
        preAlarmJob?.cancel()
        sound.stop()
        AlarmStatusBus.setCameraActive(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(systemReceiver)
        receiverRegistered = false
        wakeLock?.let { if (it.isHeld) it.release() }
        sound.release()
        events.close()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ArmService"
        const val ACTION_ARM = "ws.crandell.fafo.action.ARM"
        const val ACTION_DISARM = "ws.crandell.fafo.action.DISARM"
        const val ACTION_REPOST = "ws.crandell.fafo.action.REPOST"
        const val ACTION_NOTIFICATION_TAP = "ws.crandell.fafo.action.NOTIFICATION_TAP"
        const val ACTION_BOOT = "ws.crandell.fafo.action.BOOT"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        @Volatile var instance: ArmService? = null
            private set

        /** Arm tap from the visible app. */
        fun arm(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ArmService::class.java).setAction(ACTION_ARM))
        }

        fun disarm(context: Context) {
            context.startService(Intent(context, ArmService::class.java).setAction(ACTION_DISARM))
        }
    }
}
