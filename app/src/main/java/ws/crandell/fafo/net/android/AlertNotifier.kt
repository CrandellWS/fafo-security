package ws.crandell.fafo.net.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ws.crandell.fafo.R
import ws.crandell.fafo.arm.Notifications
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.net.Command
import ws.crandell.fafo.net.Message
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.ui.RemoteCommandActivity
import java.text.DateFormat
import java.util.Date

/** AC9.7: "Back room: ALARM (unplugged 21:04)" with Reset / Disarm when that phone's circle grants them. */
object AlertNotifier {
    fun post(context: Context, peer: String, label: String, alert: Message.Alert) {
        if (!DeviceStatus.hasNotificationPermission(context)) return
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(alert.startedAt))
        val text = if (alert.state == "PRE_ALARM") "pre-alarm, unplugged $time" else "ALARM (unplugged $time)"
        val id = Notifications.ID_ALERT_BASE + (peer.hashCode() and 0xff)
        val granted = Permission.fromBits(alert.granted)
        val b = NotificationCompat.Builder(context, Notifications.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("$label: $text")
            .setContentText("Nearby alert from your trusted device")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(Notifications.openAppIntent(context))
        if (Permission.RESET in granted || Permission.DISARM in granted) {
            b.addAction(0, "Reset", commandIntent(context, peer, label, alert, Command.RESET, id * 2))
        }
        if (Permission.DISARM in granted) {
            b.addAction(0, "Disarm", commandIntent(context, peer, label, alert, Command.DISARM, id * 2 + 1))
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
    }

    private fun commandIntent(context: Context, peer: String, label: String, alert: Message.Alert, cmd: Command, rc: Int): PendingIntent =
        PendingIntent.getActivity(
            context, rc,
            Intent(context, RemoteCommandActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RemoteCommandActivity.EXTRA_PEER, peer)
                .putExtra(RemoteCommandActivity.EXTRA_LABEL, label)
                .putExtra(RemoteCommandActivity.EXTRA_CIRCLE, alert.circleId)
                .putExtra(RemoteCommandActivity.EXTRA_INCIDENT, alert.incidentId)
                .putExtra(RemoteCommandActivity.EXTRA_COMMAND, cmd.code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun postInvite(context: Context, label: String, circle: String) {
        if (!DeviceStatus.hasNotificationPermission(context)) return
        val n = NotificationCompat.Builder(context, Notifications.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("$label invites you to $circle")
            .setContentText("Open FAFO Security to answer (expires in 10 minutes)")
            .setAutoCancel(true)
            .setContentIntent(Notifications.openAppIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(Notifications.ID_ALERT_BASE + 300, n) }
    }
}
