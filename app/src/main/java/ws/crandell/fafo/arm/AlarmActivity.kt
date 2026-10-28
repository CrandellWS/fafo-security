package ws.crandell.fafo.arm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

/**
 * Full-screen alarm over the lock screen. directBootAware, showWhenLocked, turnScreenOn (manifest).
 * Opening it is a visible-app start, so it tells the service it may re-add the camera type (spec section 2).
 * It never stops the alarm itself: only the owner's unlock or Disarm after unlock does. It closes itself
 * once the alarm has ended.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        tapService()
        setContent { AlarmContent() }
        // The full-screen intent opens this screen by itself on every alarm, so close it once the alarm
        // ends (owner unlock, Disarm, remote Reset) instead of leaving a stale red screen on top. Only after
        // the alarm was seen here: a tap in a fresh process starts before the service publishes its state.
        lifecycleScope.launch {
            var sawAlarm = false
            AlarmStatusBus.state.collect { s ->
                if (isAlarming(s)) sawAlarm = true else if (sawAlarm) finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tapService()
    }

    private fun tapService() {
        val i = Intent(this, ArmService::class.java).setAction(ArmService.ACTION_NOTIFICATION_TAP)
        try {
            ContextCompat.startForegroundService(this, i)
        } catch (_: IllegalStateException) {
            // Not allowed right now; the alarm keeps running regardless.
        }
    }

    companion object {
        fun isAlarming(s: AlarmState): Boolean =
            s is AlarmState.PreAlarm || s is AlarmState.Triggered || s is AlarmState.IncidentPersisted

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 10,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Thin wrapper: live state and a clock for the countdown and pulse; the drawing is [AlarmScreen]. */
@Composable
private fun AlarmContent() {
    val state by AlarmStatusBus.state.collectAsStateWithLifecycle()
    val camera by AlarmStatusBus.cameraActive.collectAsStateWithLifecycle()
    val paused by AlarmStatusBus.photosPaused.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), state) {
        while (true) {
            value = System.currentTimeMillis()
            // The countdown needs 4 updates/s; the alarm pulse 10/s for a smooth 1 Hz fade.
            delay(if (AlarmActivity.isAlarming(state) && state !is AlarmState.PreAlarm) 100 else 250)
        }
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val theme by remember { DpStore.get(ctx).settings }.collectAsStateWithLifecycle(null)
    val mode = ThemeMode.parse(theme?.theme)
    val dark = mode.isDark()
    LaunchedEffect(dark) { (ctx as? ComponentActivity)?.let { applySystemBars(it, dark) } }
    FafoTheme(mode) { AlarmScreen(state, now, photosOn = camera && !paused) }
}
