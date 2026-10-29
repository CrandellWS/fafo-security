package ws.crandell.fafo.quick

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The armed notification's Flashlight action (round 5, spec F13). Not exported: only FAFO's own
 * PendingIntent reaches it. directBootAware, so it works on the lock screen before the first unlock too.
 * Switching a light needs no screen lock; nothing here touches the alarm.
 */
class TorchToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) Flashlight.toggle(context)
    }

    companion object {
        const val ACTION_TOGGLE = "ws.crandell.fafo.action.TOGGLE_FLASHLIGHT"

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 20,
            Intent(context, TorchToggleReceiver::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
