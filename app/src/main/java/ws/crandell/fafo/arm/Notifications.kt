package ws.crandell.fafo.arm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ws.crandell.fafo.R
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.ui.MainActivity

/** Notification channels and builders. Safe in direct boot (no storage access). */
object Notifications {
    const val CH_ARMED = "armed"
    const val CH_ALARM = "alarm"
    /**
     * Channel for the separate full-screen alarm notification ([ID_ALARM]). A new ID rather than reusing
     * [CH_ALARM]: a channel's importance can never be raised once created, so a fresh HIGH channel is the
     * only way to be sure installs whose "alarm" channel was lowered (by the user or One UI) still alert.
     */
    const val CH_ALARM_SCREEN = "alarm_screen"
    const val CH_ALERTS = "alerts"
    const val CH_REACHABLE = "reachable"
    const val CH_INTERCOM = "intercom"

    const val ID_ARMED = 1001
    const val ID_STILL_ARMED = 1002
    /** Full-screen alarm notification, posted fresh on entering TRIGGERED (never an update of [ID_ARMED]). */
    const val ID_ALARM = 1005
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
                NotificationChannel(CH_ALARM_SCREEN, "Alarm screen", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Opens the red alarm screen over the lock screen when the alarm goes off"
                    setSound(null, null) // the service plays the siren itself on USAGE_ALARM
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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

    /**
     * Disarm action (AC1.7): opens the app through the unexported [MainActivity.DISARM_ALIAS], which asks for
     * the screen lock or fingerprint unless the phone was unlocked within the grace window. It also needs the
     * device unlocked. It never disarms straight from a service intent: "unlocked" alone is not the owner.
     */
    fun disarmIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(MainActivity.ACTION_REQUEST_DISARM)
            .setClassName(context, MainActivity.DISARM_ALIAS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun disarmAction(context: Context): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(0, "Disarm", disarmIntent(context))
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
                // No full-screen intent here: this is an update of the ongoing service notification, and
                // SystemUI only launches a full-screen intent when a notification is first posted.
                // The full-screen alarm is the separate [alarm] notification.
            }
            AlarmState.Disarmed -> b.setContentTitle("FAFO Security")
        }
        return b.build()
    }

    /**
     * The full-screen alarm (spec F1 TRIGGERED, AC1.2). Posted as a NEW notification ([ID_ALARM]) on
     * entering TRIGGERED so SystemUI launches [AlarmActivity] over the lock screen. [update] is true when
     * only the text changes later (photos paused): that update must not alert again.
     */
    fun alarm(context: Context, photosPaused: Boolean, update: Boolean = false): Notification =
        NotificationCompat.Builder(context, CH_ALARM_SCREEN)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("ALARM")
            .setContentText(if (photosPaused) "Photos paused: storage low" else "Unlock this phone to stop the alarm")
            .setOngoing(true)
            .setOnlyAlertOnce(update)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(notificationTapIntent(context))
            .setFullScreenIntent(AlarmActivity.pendingIntent(context), true)
            .setDeleteIntent(serviceIntent(context, ArmService.ACTION_REPOST_ALARM, 3))
            .addAction(disarmAction(context))
            .build()

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
