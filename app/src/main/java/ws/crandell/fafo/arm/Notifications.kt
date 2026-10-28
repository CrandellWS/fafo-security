package ws.crandell.fafo.arm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ws.crandell.fafo.R
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.ui.MainActivity

/** Notification channels and builders. Safe in direct boot (no storage access). */
object Notifications {
    const val CH_ARMED = "armed"
    const val CH_ALARM = "alarm"
    const val CH_ALERTS = "alerts"
    const val CH_REACHABLE = "reachable"
    const val CH_INTERCOM = "intercom"

    const val ID_ARMED = 1001
    const val ID_STILL_ARMED = 1002
    const val ID_ALERT_BASE = 2000
    const val ID_REACHABLE = 3001
    const val ID_INTERCOM = 4001

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_ARMED, "Armed", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Shown the whole time FAFO is armed"
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(CH_ALARM, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Pre-alarm countdown and alarm"
                    setSound(null, null) // the service plays the siren itself on USAGE_ALARM
                    enableVibration(false)
                    setBypassDnd(true)
                },
                NotificationChannel(CH_ALERTS, "Alerts from your devices", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_REACHABLE, "Stay reachable", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_INTERCOM, "Intercom", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                },
            ),
        )
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context, requestCode, Intent(context, ArmService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Disarm action: needs the device unlocked (API 31+ authentication; on API 30 it opens the app). */
    private fun disarmAction(context: Context): NotificationCompat.Action {
        val pi = if (Build.VERSION.SDK_INT >= 31) serviceIntent(context, ArmService.ACTION_DISARM, 1) else openAppIntent(context)
        return NotificationCompat.Action.Builder(0, "Disarm", pi)
            .setAuthenticationRequired(true)
            .build()
    }

    /** Persistent notification for ARMED / PRE_ALARM / TRIGGERED / INCIDENT_PERSISTED (AC1.2). */
    fun forState(context: Context, state: AlarmState, photosPaused: Boolean, armedAfterBootNoCamera: Boolean): Notification {
        val alarming = state is AlarmState.PreAlarm || state is AlarmState.Triggered || state is AlarmState.IncidentPersisted
        val b = NotificationCompat.Builder(context, if (alarming) CH_ALARM else CH_ARMED)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setDeleteIntent(serviceIntent(context, ArmService.ACTION_REPOST, 2))
            .addAction(disarmAction(context))
        when (state) {
            is AlarmState.Armed -> {
                b.setContentTitle("FAFO Security is armed")
                    .setContentText(
                        when {
                            state.awaitingPower -> "Reset remotely. Plug in to protect again."
                            armedAfterBootNoCamera -> "Armed after reboot, open the app to enable photos"
                            else -> "Unplugging while locked sets off the alarm."
                        },
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(notificationTapIntent(context))
            }
            is AlarmState.PreAlarm -> {
                b.setContentTitle("Plug me back in")
                    .setContentText(if (photosPaused) "Photos paused: storage low" else "Alarm in a few seconds")
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(state.endsAt)
                    .setShowWhen(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(notificationTapIntent(context))
            }
            is AlarmState.Triggered, is AlarmState.IncidentPersisted -> {
                b.setContentTitle("ALARM")
                    .setContentText(if (photosPaused) "Photos paused: storage low" else "Unlock this phone to stop the alarm")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(notificationTapIntent(context))
                    .setFullScreenIntent(AlarmActivity.pendingIntent(context), true)
            }
            AlarmState.Disarmed -> b.setContentTitle("FAFO Security")
        }
        return b.build()
    }

    /** Tap on the armed/alarm notification: a visible-app start that may re-add the camera type. */
    fun notificationTapIntent(context: Context): PendingIntent = AlarmActivity.pendingIntent(context)

    fun stillArmed(context: Context): Notification = NotificationCompat.Builder(context, CH_ARMED)
        .setSmallIcon(R.drawable.ic_stat_shield)
        .setContentTitle("Still armed")
        .setContentText("Plug back in or disarm")
        .setAutoCancel(true)
        .setContentIntent(openAppIntent(context))
        .build()
}
