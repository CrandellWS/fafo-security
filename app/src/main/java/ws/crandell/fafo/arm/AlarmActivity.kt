package ws.crandell.fafo.arm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.ui.theme.FafoTheme
import ws.crandell.fafo.ui.theme.ThemeMode
import ws.crandell.fafo.ui.theme.applySystemBars
import ws.crandell.fafo.ui.theme.isDark
import ws.crandell.fafo.storage.DpStore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.crandell.fafo.core.incidentId
import ws.crandell.fafo.ui.status.PHOTO_BANNER_MS
import ws.crandell.fafo.ui.status.photoTakenLook

/**
 * Full-screen alarm over the lock screen. directBootAware, showWhenLocked, turnScreenOn (manifest).
 * Opening it is a visible-app start, so it tells the service it may re-add the camera type (spec section 2).
 * It never stops the alarm itself: only the owner's unlock or Disarm after unlock does. It closes itself
 * once the alarm has ended.
 */
class AlarmActivity : ComponentActivity() {
    /**
     * Pin app while armed (spec F14): set only by the owner's Arm tap with the setting on ([pinIntent]; this
     * activity is not exported, so nothing else can send it), cleared once handled.
     */
    private var pinRequest = false
    /** Android's lock task state, for the Unpin button. */
    private val pinned = mutableStateOf(false)
    private val screenPin get() = ArmDeps.screenPin

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (savedInstanceState == null) takePinRequest(intent)
        tapService()
        setContent { AlarmContent() }
        // The full-screen intent opens this screen by itself on every alarm, so close it once the alarm
        // ends (owner unlock, Disarm, remote Reset) instead of leaving a stale red screen on top. Only after
        // the alarm was seen here: a tap in a fresh process starts before the service publishes its state.
        lifecycleScope.launch {
            var sawAlarm = false
            var sawProtected = false
            AlarmStatusBus.state.collect { s ->
                // The pin state matters only for a disarm; onResume and focus changes keep it fresh otherwise.
                if (s == AlarmState.Disarmed) refreshPinned()
                if (PinPolicy.shouldUnpin(pinned.value, s) && sawProtected) unpin()
                if (isAlarming(s)) sawAlarm = true else if (sawAlarm && PinPolicy.closeWhenAlarmEnds(pinned.value, s)) finish()
                // Opened as the calm status screen: close once FAFO is disarmed.
                if (s != AlarmState.Disarmed) sawProtected = true else if (sawProtected) finish()
                // Keep the screen on only for the pre-alarm and the alarm (calm green must not burn it).
                if (isAlarming(s)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        // Pinning needs this screen in front, and the Arm tap usually arrives before the service publishes ARMING.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                AlarmStatusBus.state.collect { s -> maybePin(s) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takePinRequest(intent)
        tapService()
    }

    override fun onResume() {
        super.onResume()
        refreshPinned()
    }

    /** Android's "Pin app?" dialog takes the focus; it comes back here once answered. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        refreshPinned()
    }

    private fun takePinRequest(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_PIN, false) == true) pinRequest = true
    }

    private fun maybePin(s: AlarmState) {
        if (!pinRequest) return
        when {
            PinPolicy.shouldPin(pinRequest, s, screenPin.isPinned(this)) -> {
                pinRequest = false
                screenPin.pin(this)
                refreshPinned()
            }
            PinPolicy.dropRequest(s) -> pinRequest = false
        }
    }

    /** Only while this screen exists: a status update must never reach the system for a closed screen. */
    private fun refreshPinned() {
        if (isDestroyed) return
        pinned.value = runCatching { screenPin.isPinned(this) }.getOrDefault(false)
    }

    /** The status screen's Unpin button, and every disarm: ends the pin. */
    fun unpin() {
        screenPin.unpin(this)
        refreshPinned()
    }

    /**
     * Disarm from the status screen (it shows over the lock screen): needs the screen lock first. While pinned,
     * Android would block the app's Disarm confirmation (another task), so the pin ends first; with Android's
     * "Ask for PIN before unpinning" the phone locks, and Disarm then starts from the lock screen.
     */
    fun requestDisarm() {
        if (!pinned.value) return requestDisarmOverLock(this)
        unpin()
        lifecycleScope.launch {
            delay(UNPIN_SETTLE_MS)
            requestDisarmOverLock(this@AlarmActivity)
        }
    }

    /** True while pinned (Compose). */
    internal val isPinnedState: Boolean get() = pinned.value

    private fun tapService() = tapArmService(this)

    companion object {
        /** Extra on [pinIntent]: this status screen was opened by the owner's Arm tap with "Pin app while armed" on. */
        const val EXTRA_PIN = "ws.crandell.fafo.extra.PIN"
        /** Time for Android to end the pin (and lock, if set) before the Disarm request. */
        private const val UNPIN_SETTLE_MS = 600L

        fun isAlarming(s: AlarmState): Boolean =
            s is AlarmState.PreAlarm || s is AlarmState.Triggered || s is AlarmState.IncidentPersisted

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 10,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** After the owner's Arm tap with "Pin app while armed" on: the status screen, which then pins itself. */
        fun pinIntent(context: Context): Intent =
            Intent(context, AlarmActivity::class.java).putExtra(EXTRA_PIN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/**
 * Disarm from a screen shown over the lock screen: first ask Android to dismiss the keyguard (the owner's PIN,
 * pattern, password or fingerprint), then the app runs the Disarm confirmation (an unlock right now, after
 * arming, counts). A stranger holding the phone never gets past the keyguard, so never reaches Disarm.
 */
fun requestDisarmOverLock(activity: ComponentActivity) {
    val km = activity.getSystemService(android.app.KeyguardManager::class.java)
    val open = {
        activity.startActivity(
            Intent(ws.crandell.fafo.ui.MainActivity.ACTION_REQUEST_DISARM)
                .setClassName(activity, ws.crandell.fafo.ui.MainActivity.DISARM_ALIAS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }
    if (km?.isKeyguardLocked == true) {
        km.requestDismissKeyguard(activity, object : android.app.KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = open()
        })
    } else open()
}

/** A visible-app start: tells the service it may re-add the camera type (spec section 2). */
fun tapArmService(context: Context) {
    val i = Intent(context, ArmService::class.java).setAction(ArmService.ACTION_NOTIFICATION_TAP)
    try {
        ContextCompat.startForegroundService(context, i)
    } catch (_: IllegalStateException) {
        // Not allowed right now; the alarm keeps running regardless.
    } catch (_: SecurityException) {
        // Same.
    }
}

/** Android's "Remove animations" (animator duration scale 0): no shutter flash, just the label. */
fun reducedMotion(context: Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

/** The trigger photo, decoded small for the PHOTO TAKEN thumbnail (long edge about [maxPx]). */
fun decodeThumbnail(path: String, maxPx: Int = 320): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/**
 * Thin wrapper shared by the alarm and HANDS OFF screens: live state, the ladder, the latest trigger photo and
 * a clock for the countdown, flash and shutter fade; the drawing is [StatusScreen].
 */
@Composable
internal fun LiveStatusContent(handsOff: Boolean, onDisarm: (() -> Unit)?, pinned: Boolean = false, onUnpin: (() -> Unit)? = null) {
    val state by AlarmStatusBus.state.collectAsStateWithLifecycle()
    val threat by AlarmStatusBus.threat.collectAsStateWithLifecycle()
    val camera by AlarmStatusBus.cameraActive.collectAsStateWithLifecycle()
    val paused by AlarmStatusBus.photosPaused.collectAsStateWithLifecycle()
    val shot by AlarmStatusBus.photoShot.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), state, shot) {
        while (true) {
            value = System.currentTimeMillis()
            val shutter = shot?.let { value - it.at in 0 until PHOTO_BANNER_MS } == true
            // Countdowns need 4 updates/s; the 2 Hz alarm flash and the shutter fade 20/s.
            delay(if (shutter || (AlarmActivity.isAlarming(state) && state !is AlarmState.PreAlarm)) 50 else 250)
        }
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, shot?.path) {
        value = shot?.path?.let { p -> withContext(Dispatchers.IO) { decodeThumbnail(p) } }
    }
    val calm = remember { reducedMotion(ctx) }
    val theme by remember { DpStore.get(ctx).settings }.collectAsStateWithLifecycle(null)
    val mode = ThemeMode.parse(theme?.theme)
    val dark = mode.isDark()
    LaunchedEffect(dark) { (ctx as? ComponentActivity)?.let { applySystemBars(it, dark) } }
    // Only this incident's photos count.
    val current = shot?.takeIf { it.incidentId == state.incidentId }
    FafoTheme(mode) {
        StatusScreen(
            state, threat, now, photosOn = camera && !paused,
            onDisarm = onDisarm,
            onCancelArming = { ArmService.cancelArming(ctx) },
            handsOff = handsOff,
            photo = current?.let { photoTakenLook(it.at, it.count, now, calm) },
            thumbnail = thumb,
            pinned = pinned,
            onUnpin = onUnpin,
        )
    }
}

@Composable
private fun AlarmContent() {
    val activity = androidx.activity.compose.LocalActivity.current as? AlarmActivity
    LiveStatusContent(
        handsOff = false, onDisarm = activity?.let { a -> { a.requestDisarm() } },
        pinned = activity?.isPinnedState == true, onUnpin = activity?.let { a -> { a.unpin() } },
    )
}
