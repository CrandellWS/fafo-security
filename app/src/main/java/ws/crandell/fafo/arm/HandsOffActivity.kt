package ws.crandell.fafo.arm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.core.Defaults
import ws.crandell.fafo.core.ThreatLevel
import ws.crandell.fafo.storage.DpStore

/**
 * HANDS OFF (device feedback 2026-10-28). Started through the full-screen intent of
 * [Notifications.handsOff] when the armed phone is bumped or picked up with the screen off (or locked): it
 * wakes the screen over the lock screen (showWhenLocked, turnScreenOn) with a big HANDS OFF warning in the
 * traffic-light colors and the pre-alarm countdown. Its Disarm button asks for the screen lock first
 * ([requestDisarmOverLock]), so a stranger cannot stop anything from here.
 *
 * It closes itself when the warning is over: the status cooled back to green (the bump reset time), FAFO was
 * disarmed, or the owner unlocked the phone. The keyguard then turns the screen off on its own timeout.
 */
class HandsOffActivity : ComponentActivity() {
    private var timeout: Job? = null

    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The owner unlocked: the warning is not for them.
            if (!AlarmActivity.isAlarming(AlarmStatusBus.state.value)) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ContextCompat.registerReceiver(this, unlocked, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        tapArmService(this)
        setContent { LiveStatusContent(handsOff = true, onDisarm = { requestDisarmOverLock(this) }) }
        lifecycleScope.launch {
            combine(AlarmStatusBus.state, AlarmStatusBus.threat) { s, t -> s to t }.collect { (s, t) ->
                if (shouldClose(s, t)) {
                    finish()
                    return@collect
                }
                // Safety net: nothing changed for the bump reset time (plus a moment) while ARMED, close anyway.
                timeout?.cancel()
                if (s is AlarmState.Armed) {
                    timeout = lifecycleScope.launch {
                        val resetS = runCatching { DpStore.get(this@HandsOffActivity).readSettings().bumpResetS }
                            .getOrDefault(Defaults.BUMP_RESET_S)
                        delay(resetS * 1000L + TIMEOUT_SLACK_MS)
                        finish()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        showing = true
    }

    override fun onStop() {
        showing = false
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlocked) }
        // The notification that opened this screen has done its job.
        runCatching { NotificationManagerCompat.from(this).cancel(Notifications.ID_HANDS_OFF) }
        super.onDestroy()
    }

    companion object {
        private const val TIMEOUT_SLACK_MS = 2_000L

        /** True while the HANDS OFF screen is on top: the service updates it through the bus, no re-post. */
        @Volatile var showing: Boolean = false
            private set

        /** Nothing left to warn about: disarmed or arming, or ARMED with the ladder back at green. */
        fun shouldClose(s: AlarmState, threat: ThreatLevel): Boolean = when (s) {
            AlarmState.Disarmed, is AlarmState.Arming -> true
            is AlarmState.Armed -> threat == ThreatLevel.GREEN || s.awaitingPower
            else -> false
        }

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 11,
            Intent(context, HandsOffActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
