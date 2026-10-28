package ws.crandell.fafo.intercom

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ws.crandell.fafo.R
import ws.crandell.fafo.arm.Notifications
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.trust.TrustGraph

/**
 * AC10.1 / AC10.2 intercom room mode: `microphone|connectedDevice`, started only by [start] from the visible
 * app (an activity), never from a message, boot or a sticky restart (START_NOT_STICKY, not directBootAware).
 * The notification is posted first, names who can listen and who is listening, and has Stop. Refuses to
 * start, and stops, when notifications are blocked.
 */
class IntercomService : LifecycleService() {

    /** Foreground service type last passed to startForeground, for tests. */
    var currentFgsType: Int = 0
        private set

    private fun notificationsOk() = DeviceStatus.hasNotificationPermission(this) && DeviceStatus.notificationsEnabled(this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            Intercom.stopRoomMode()
            stop()
            return START_NOT_STICKY
        }
        val circles = intent?.getStringArrayExtra(EXTRA_CIRCLES)?.toSet()
        if (intent?.action != ACTION_START || circles.isNullOrEmpty() || !notificationsOk() || !DeviceStatus.isCeUnlocked(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        Notifications.ensureChannels(this)
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        try {
            ServiceCompat.startForeground(this, Notifications.ID_INTERCOM, notification(this, names(circles), emptyList()), type)
            currentFgsType = type
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (Intercom.startRoomMode(this, circles) != RoomDecision.STARTED) {
            stop()
            return START_NOT_STICKY
        }
        lifecycleScope.launch {
            Intercom.ui.collect { ui ->
                val room = ui.roomCircles
                if (room == null || !notificationsOk()) {
                    if (room != null) Intercom.stopRoomMode()
                    stop()
                    return@collect
                }
                try {
                    NotificationManagerCompat.from(this@IntercomService).notify(Notifications.ID_INTERCOM, notification(this@IntercomService, names(room), ui.listeners))
                } catch (e: SecurityException) {
                    Intercom.stopRoomMode()
                }
            }
        }
        // AC10.2: stop if notifications get blocked while room mode runs (no event for that, so poll).
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(NOTIFICATION_CHECK_MS)
                if (!notificationsOk()) {
                    Intercom.stopRoomMode()
                    stop()
                    break
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun names(ids: Set<String>): List<String> = TrustGraph.circles(this).all().filter { it.id in ids }.map { it.name }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (Intercom.ui.value.roomCircles != null) Intercom.stopRoomMode()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "IntercomService"
        const val ACTION_START = "ws.crandell.fafo.action.ROOM_MODE"
        const val ACTION_STOP = "ws.crandell.fafo.action.ROOM_MODE_STOP"
        const val EXTRA_CIRCLES = "circles"
        private const val NOTIFICATION_CHECK_MS = 3_000L

        fun notification(context: Context, circles: List<String>, listeners: List<String>): Notification =
            NotificationCompat.Builder(context, Notifications.CH_INTERCOM)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setContentTitle(
                    if (listeners.isNotEmpty()) "${listeners.joinToString(", ")} ${if (listeners.size == 1) "is" else "are"} listening"
                    else "Intercom on: ${circles.joinToString(", ")} can listen",
                )
                .setContentText("Nothing is recorded. Tap Stop to turn intercom off.")
                .setOngoing(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(Notifications.openAppIntent(context))
                .addAction(
                    0, "Stop",
                    PendingIntent.getService(
                        context, 21, Intent(context, IntercomService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build()

        /** From the visible app only (an Activity is on screen and the mic permission is granted). */
        fun start(activity: android.app.Activity, circles: Set<String>) {
            ContextCompat.startForegroundService(
                activity,
                Intent(activity, IntercomService::class.java).setAction(ACTION_START).putExtra(EXTRA_CIRCLES, circles.toTypedArray()),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, IntercomService::class.java).setAction(ACTION_STOP))
        }
    }
}
