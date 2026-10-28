package ws.crandell.fafo.reach

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
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.net.android.PeerNetwork
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.trust.TrustGraph

/**
 * AC9.9 "Stay reachable": a `connectedDevice` foreground service that keeps the local link open so
 * alerts and push-to-talk arrive while FAFO is in the background. Persistent notification "Reachable by:
 * Home, Work" with a Stop action. Not directBootAware: it runs only after the first unlock and may resume
 * from BOOT_COMPLETED (never LOCKED_BOOT_COMPLETED). It never opens the microphone.
 */
class ReachableService : LifecycleService() {

    /** Foreground service type last passed to startForeground, for tests. */
    var currentFgsType: Int = 0
        private set

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            lifecycleScope.launch { CeStore.get(this@ReachableService).update { it.copy(stayReachable = false) } }
            stop()
            return START_NOT_STICKY
        }
        if (!DeviceStatus.isCeUnlocked(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        Notifications.ensureChannels(this)
        try {
            ServiceCompat.startForeground(this, Notifications.ID_REACHABLE, notification(this, circleNames()), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            currentFgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        PeerNetwork.acquire(this, OWNER)
        lifecycleScope.launch {
            TrustGraph.circles(this@ReachableService).circles.collect { list ->
                if (DeviceStatus.hasNotificationPermission(this@ReachableService)) {
                    try {
                        NotificationManagerCompat.from(this@ReachableService).notify(Notifications.ID_REACHABLE, notification(this@ReachableService, names(list)))
                    } catch (e: SecurityException) {
                        Log.w(TAG, "notify", e)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun circleNames(): List<String> = names(TrustGraph.circles(this).all())

    private fun names(list: List<Circle>) = list.filter { it.members.isNotEmpty() }.map { it.name }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        PeerNetwork.release(OWNER)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ReachableService"
        private const val OWNER = "reachable"
        const val ACTION_STOP = "ws.crandell.fafo.action.STOP_REACHABLE"

        fun notification(context: Context, circles: List<String>): Notification =
            NotificationCompat.Builder(context, Notifications.CH_REACHABLE)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setContentTitle(if (circles.isEmpty()) "Reachable by: nobody yet" else "Reachable by: ${circles.joinToString(", ")}")
                .setContentText("Your own paired devices can send alerts and push-to-talk")
                .setOngoing(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(Notifications.openAppIntent(context))
                .addAction(
                    0, "Stop",
                    PendingIntent.getService(
                        context, 11, Intent(context, ReachableService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ReachableService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReachableService::class.java))
        }
    }
}
