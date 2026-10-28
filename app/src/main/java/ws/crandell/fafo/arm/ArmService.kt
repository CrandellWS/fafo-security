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
import ws.crandell.fafo.camera.DeviceSnapshot
import ws.crandell.fafo.camera.PhotoController
import ws.crandell.fafo.camera.PhotoSettings
import ws.crandell.fafo.camera.PhotoStore
import ws.crandell.fafo.core.incidentId
import ws.crandell.fafo.motion.MotionMonitor
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.motion.MotionSensitivity
import ws.crandell.fafo.storage.DpSettings
import ws.crandell.fafo.storage.DpStore
import ws.crandell.fafo.system.BootClock
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.net.AlertInfo
import ws.crandell.fafo.net.Command
import ws.crandell.fafo.net.CommandTarget
import ws.crandell.fafo.net.android.AlarmNetwork
import ws.crandell.fafo.core.wireName
import ws.crandell.fafo.core.LadderEffect
import ws.crandell.fafo.core.Motion
import ws.crandell.fafo.core.ThreatLadder
import ws.crandell.fafo.core.ThreatLevel
import ws.crandell.fafo.motion.ChirpLimiter
import ws.crandell.fafo.siren.TorchStrobe
import ws.crandell.fafo.mail.MailDispatcher
import ws.crandell.fafo.ui.status.statusLook

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
    private lateinit var motion: MotionMonitor
    private val events = Channel<AlarmEvent>(Channel.UNLIMITED)
    private var network: AlarmNetwork? = null
    private var networkAttached = false

    private var windowJob: Job? = null
    private var armingJob: Job? = null
    private var preAlarmJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeKeepAlive: Job? = null
    private var receiverRegistered = false
    private var inForeground = false
    private var cameraType = false
    /** Started from boot / sticky restart: no camera until a visible-app start (notification tap). */
    private var startedWithoutCamera = false
    /** True while the separate full-screen alarm notification ([Notifications.ID_ALARM]) is posted. */
    private var alarmPosted = false
    private var alarmPhotosPaused = false

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
                Intent.ACTION_USER_PRESENT -> {
                    recordTrail(screenOff = false)
                    events.trySend(AlarmEvent.UserPresent)
                }
                Intent.ACTION_SCREEN_OFF -> recordTrail(screenOff = true)
            }
        }
    }

    /** Disarm unlock trail (DisarmPolicy): screen-off and unlock times while ARMED, in DP storage. */
    private fun recordTrail(screenOff: Boolean) {
        if (!::machine.isInitialized || machine.state !is AlarmState.Armed) return
        val at = BootClock.now()
        val boot = BootClock.bootId(this)
        lifecycleScope.launch { if (screenOff) dp.recordScreenOff(at, boot) else dp.recordUserPresent(at, boot) }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.ensureChannels(this)
        dp = ArmDeps.dpStore(this)
        sound = ArmDeps.sound(this, lifecycleScope) { dpSettings ?: DpSettings() }
        motion = MotionMonitor(this) { m -> lifecycleScope.launch { onMotion(m) } }
        machine = AlarmStateMachine(ArmDeps.clock, { (dpSettings ?: DpSettings()).alarmConfig() })
        lifecycleScope.launch {
            dp.settings.collect {
                dpSettings = it
                if (::machine.isInitialized) updateMotion()
            }
        }
        lifecycleScope.launch { for (e in events) process(e) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_ARM -> {
                // Visible-app start (the Arm tap): camera type allowed if the permission is granted.
                goForeground(withCamera = DeviceStatus.hasCamera(this), arming = true)
                events.trySend(
                    AlarmEvent.ArmRequested(
                        plugged = DeviceStatus.isPlugged(this),
                        secureLock = DeviceStatus.isDeviceSecure(this),
                    ),
                )
            }
            ACTION_CANCEL_ARMING -> lifecycleScope.launch { events.send(AlarmEvent.ArmCancelled) }
            ACTION_DISARM -> lifecycleScope.launch {
                if (machine.state == AlarmState.Disarmed) {
                    // Fresh process with stale flags: clear them so nothing resumes later.
                    dp.setArmed(false, false)
                    dp.setIncident(false, null, null)
                }
                events.send(AlarmEvent.DisarmTapped)
            }
            ACTION_REPOST -> postNotification()
            ACTION_REPOST_ALARM -> {
                // The alarm notification was swiped away: post it again as new, so the alarm screen returns.
                alarmPosted = false
                postNotification()
            }
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

    private fun goForeground(withCamera: Boolean, arming: Boolean = false) {
        var type = 0
        if (Build.VERSION.SDK_INT >= 34) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (withCamera) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        val notification = Notifications.forState(this, currentStateForNotification(arming), AlarmStatusBus.photosPaused.value, false)
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

    private fun currentStateForNotification(arming: Boolean = false): AlarmState = when {
        ::machine.isInitialized && machine.state != AlarmState.Disarmed -> machine.state
        arming -> ArmDeps.clock.now().let { AlarmState.Arming(it, it + (dpSettings ?: DpSettings()).armingS * 1000L) }
        else -> AlarmState.Armed()
    }

    private suspend fun process(e: AlarmEvent) {
        if (dpSettings == null) dpSettings = dp.readSettings()
        val before = machine.state
        val effects = machine.handle(e)
        // The ladder starts GREEN on every change into or out of ARMED.
        if ((before is AlarmState.Armed) != (machine.state is AlarmState.Armed)) {
            ladder.reset()
            coolJob?.cancel()
            publishThreat()
        }
        var stop = false
        for (fx in effects) {
            if (fx == AlarmEffect.StopService) stop = true else run(fx)
        }
        val s = machine.state
        if (e is AlarmEvent.ArmingElapsed && s is AlarmState.Arming) {
            // The timer fired a little before endsAt by the clock: wait out the rest.
            scheduleArming((s.endsAt - ArmDeps.clock.now()).coerceAtLeast(1))
        }
        if (e == AlarmEvent.PreAlarmElapsed && s is AlarmState.PreAlarm) {
            // The timer fired a little before endsAt by the clock: wait out the rest.
            val rest = (s.endsAt - ArmDeps.clock.now()).coerceAtLeast(1)
            preAlarmJob = lifecycleScope.launch {
                delay(rest)
                events.send(AlarmEvent.PreAlarmElapsed)
            }
        }
        AlarmStatusBus.publish(s)
        updateTorch()
        if (s != AlarmState.Disarmed) {
            registerReceivers()
            ensureNetwork()
        }
        updateWakeLock()
        updateMotion()
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
            is AlarmEffect.StartArmingTimer -> scheduleArming(fx.delayMs)
            AlarmEffect.CancelArmingTimer -> armingJob?.cancel()
            AlarmEffect.RecordArmedAt -> dp.recordArmedAt(BootClock.now(), BootClock.bootId(this))
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
            AlarmEffect.StartPhotos -> startPhotos()
            AlarmEffect.StopPhotos -> photos?.stop()
            is AlarmEffect.CloseIncident -> {
                // CE storage only; after a reboot the incident ends with the first unlock, so CE is open.
                if (DeviceStatus.isCeUnlocked(this)) {
                    runCatching { photoStore().close(fx.id, fx.reason.name, fx.by, ArmDeps.clock.now()) }
                }
                hooks.forEach { it.onEffect(fx) }
            }
            is AlarmEffect.StartAlerts -> {
                ensureNetwork()
                network?.startAlerts(::alertInfo)
                hooks.forEach { it.onEffect(fx) }
            }
            AlarmEffect.StopAlerts -> {
                network?.stopAlerts()
                hooks.forEach { it.onEffect(fx) }
            }
            AlarmEffect.EndIntercom -> {
                AlarmStatusBus.endIntercom?.invoke()
                hooks.forEach { it.onEffect(fx) }
            }
            is AlarmEffect.Refused -> Log.i(TAG, "refused: ${fx.reason}")
            AlarmEffect.StopService -> Unit
        }
    }

    private fun scheduleArming(delayMs: Long) {
        armingJob?.cancel()
        armingJob = lifecycleScope.launch {
            delay(delayMs)
            events.send(
                AlarmEvent.ArmingElapsed(
                    plugged = DeviceStatus.isPlugged(this@ArmService),
                    secureLock = DeviceStatus.isDeviceSecure(this@ArmService),
                ),
            )
        }
    }

    /** Bump escalation while ARMED (Bill 2026-10-28): colors, chirps, cool-down and the motion pre-alarm. */
    private val ladder = ThreatLadder()
    private val chirpLimiter = ChirpLimiter()
    private var coolJob: Job? = null

    private suspend fun onMotion(m: Motion) {
        val s = machine.state
        if (s !is AlarmState.Armed || s.awaitingPower) return
        val settings = dpSettings ?: DpSettings()
        val locked = DeviceStatus.isDeviceLocked(this)
        val now = ArmDeps.clock.now()
        for (fx in ladder.onMotion(m, now, mayAlarm = locked && settings.motionCanAlarm)) {
            when (fx) {
                is LadderEffect.Chirp -> if (chirpLimiter.allow(now)) sound.chirp(if (fx.loud) ChirpKind.PICKUP else ChirpKind.BUMP)
                LadderEffect.Escalate -> events.send(AlarmEvent.MotionEscalated(locked = locked))
            }
        }
        publishThreat()
        coolJob?.cancel()
        coolJob = lifecycleScope.launch {
            delay(ThreatLadder.COOL_MS)
            if (ladder.tick(ArmDeps.clock.now())) publishThreat()
        }
    }

    /** Alarm light (round 3): follows the status screen's level. */
    private val torch by lazy {
        TorchStrobe(lifecycleScope, ArmDeps.torch(this)) { (dpSettings ?: DpSettings()).flashAlarm }
    }

    private fun updateTorch() {
        torch.setLevel(statusLook(machine.state, ladder.level, ArmDeps.clock.now()).light)
    }

    /** True while the torch pattern runs (tests). */
    val torchRunning: Boolean get() = torch.running

    private fun publishThreat() {
        updateTorch()
        AlarmStatusBus.setThreat(ladder.level)
        hooks.forEach { it.onThreat(ladder.level) }
    }

    /** Current ladder level (tests). */
    val threat: ThreatLevel get() = ladder.level

    private var photos: PhotoController? = null
    private var store: PhotoStore? = null
    private fun photoStore() = store ?: PhotoStore(PhotoStore.root(filesDir)).also {
        it.pinned = { MailDispatcher.pinned(this) }
        store = it
    }

    /** F5. Only with the camera FGS type active and CE storage unlocked (never on the boot path). */
    private fun startPhotos() {
        val s = machine.state
        val id = s.incidentId ?: return
        val startedAt = when (s) {
            is AlarmState.PreAlarm -> s.startedAt
            is AlarmState.Triggered -> s.startedAt
            is AlarmState.IncidentPersisted -> s.startedAt
            else -> return
        }
        if (!cameraType || !DeviceStatus.isCeUnlocked(this)) return
        val pc = photos ?: PhotoController(
            scope = lifecycleScope,
            capturer = ArmDeps.capturer(this, this),
            store = ::photoStore,
            settings = {
                val ce = CeStore.get(this).read()
                PhotoSettings(ce.photoOnTrigger, ce.photoIntervalS, ce.camera, ce.photoCapMb)
            },
            freeBytes = { ArmDeps.freeBytes(this) },
            device = { DeviceSnapshot(DeviceStatus.batteryPercent(this), DeviceStatus.isPlugged(this)) },
            clock = ArmDeps.clock,
            onPaused = {
                AlarmStatusBus.setPhotosPaused(it)
                postNotification()
            },
            beforeCapture = { torch.pauseForCapture() },
            onSaved = { incident, name -> MailDispatcher.photoSaved(this, incident, name, ArmDeps.clock.now()) },
            afterCapture = { torch.resumeAfterCapture() },
        ).also { photos = it }
        pc.start(id, startedAt)
    }

    /**
     * F9: listen for verified Reset / Disarm and send alerts, after the first unlock only (the trust list and
     * keys are CE / Keystore). An armed phone can always be reset or disarmed by a circle that grants it.
     */
    private fun ensureNetwork() {
        if (!DeviceStatus.isCeUnlocked(this)) return
        // Photo email (round 3) drains while the armed service runs.
        if (!mailHost) { MailDispatcher.acquire(this, MailDispatcher.HOST_ARMED); mailHost = true }
        if (networkAttached) return
        val n = network ?: ArmDeps.network(this).also { network = it }
        networkAttached = n.attach(commandTarget)
    }

    private fun alertInfo(): AlertInfo? {
        val s = machine.state
        val id = s.incidentId ?: return null
        val started = when (s) {
            is AlarmState.PreAlarm -> s.startedAt
            is AlarmState.Triggered -> s.startedAt
            is AlarmState.IncidentPersisted -> s.startedAt
            else -> return null
        }
        return AlertInfo(id, s.wireName, started)
    }

    private val commandTarget = object : CommandTarget {
        override fun currentIncident(): String? = machine.state.incidentId

        override suspend fun apply(cmd: Command, peerLabel: String, circleId: String): Boolean {
            val ce = DeviceStatus.isCeUnlocked(this@ArmService)
            val e = when (cmd) {
                Command.RESET -> AlarmEvent.RemoteReset(peerLabel, circleId, DeviceStatus.isPlugged(this@ArmService), ce)
                Command.DISARM -> AlarmEvent.RemoteDisarm(peerLabel, circleId, ce)
            }
            return events.trySend(e).isSuccess
        }
    }

    /** Photos taken so far in this service instance (tests). */
    val photosTaken: Int get() = photos?.shots ?: 0

    /** Later milestones (photos, alerts, intercom) attach here. */
    interface EffectHook {
        fun onEffect(fx: AlarmEffect)
        fun onThreat(level: ThreatLevel) {}
    }
    private val hooks = mutableListOf<EffectHook>()
    fun addHook(h: EffectHook) { hooks += h }

    private fun registerReceivers() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
            // Disarm unlock trail: SCREEN_OFF can only be registered at runtime.
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // AC2.1: not a manifest-allowed implicit broadcast; register at runtime.
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    /** AC4.1: accelerometer only while ARMED (never in PRE_ALARM / TRIGGERED), unless motion is Off. */
    private fun updateMotion() {
        val armed = machine.state is AlarmState.Armed
        motion.update(if (armed) MotionSensitivity.fromSetting((dpSettings ?: DpSettings()).motion) else null)
    }

    /** True while the accelerometer listener is registered (tests). */
    val motionRunning: Boolean get() = motion.running

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
        syncAlarmNotification(s)
    }

    /**
     * Full-screen alarm: SystemUI launches a full-screen intent only when a notification is first posted
     * (an update, like the ongoing [Notifications.ID_ARMED] one, never launches it). So on entering
     * TRIGGERED (or after a reboot during an incident) post [Notifications.ID_ALARM] fresh, once; cancel it
     * when the alarm ends.
     */
    private fun syncAlarmNotification(s: AlarmState) {
        val alarming = s is AlarmState.Triggered || s is AlarmState.IncidentPersisted
        val paused = AlarmStatusBus.photosPaused.value
        when {
            alarming && !alarmPosted -> {
                cancelNotification(Notifications.ID_ALARM)
                notify(Notifications.ID_ALARM) { Notifications.alarm(this, paused) }
                alarmPosted = true
                alarmPhotosPaused = paused
            }
            alarming && paused != alarmPhotosPaused -> {
                notify(Notifications.ID_ALARM) { Notifications.alarm(this, paused, update = true) }
                alarmPhotosPaused = paused
            }
            !alarming && alarmPosted -> cancelAlarmNotification()
        }
    }

    private fun cancelAlarmNotification() {
        cancelNotification(Notifications.ID_ALARM)
        alarmPosted = false
    }

    private fun cancelNotification(id: Int) {
        try {
            NotificationManagerCompat.from(this).cancel(id)
        } catch (e: SecurityException) {
            Log.w(TAG, "cancel failed", e)
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
        detachNetwork()
        photos?.stop()
        motion.stop()
        windowJob?.cancel()
        armingJob?.cancel()
        coolJob?.cancel()
        torch.stop()
        AlarmStatusBus.setThreat(ThreatLevel.GREEN)
        preAlarmJob?.cancel()
        sound.stop()
        AlarmStatusBus.setCameraActive(false)
        cancelAlarmNotification()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    private var mailHost = false

    private fun detachNetwork() {
        if (mailHost) { MailDispatcher.release(MailDispatcher.HOST_ARMED); mailHost = false }
        if (networkAttached) network?.detach()
        networkAttached = false
    }

    override fun onDestroy() {
        torch.stop()
        detachNetwork()
        if (::motion.isInitialized) motion.stop()
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
        /** Cancel the ARMING countdown (Home button, notification action). No auth: the result is "not armed". */
        const val ACTION_CANCEL_ARMING = "ws.crandell.fafo.action.CANCEL_ARMING"
        const val ACTION_REPOST = "ws.crandell.fafo.action.REPOST"
        const val ACTION_REPOST_ALARM = "ws.crandell.fafo.action.REPOST_ALARM"
        const val ACTION_NOTIFICATION_TAP = "ws.crandell.fafo.action.NOTIFICATION_TAP"
        const val ACTION_BOOT = "ws.crandell.fafo.action.BOOT"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        @Volatile var instance: ArmService? = null
            private set

        /** Arm tap from the visible app. */
        fun arm(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ArmService::class.java).setAction(ACTION_ARM))
        }

        fun cancelArming(context: Context) {
            context.startService(Intent(context, ArmService::class.java).setAction(ACTION_CANCEL_ARMING))
        }

        fun disarm(context: Context) {
            context.startService(Intent(context, ArmService::class.java).setAction(ACTION_DISARM))
        }
    }
}
