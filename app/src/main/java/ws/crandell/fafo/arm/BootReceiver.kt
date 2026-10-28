package ws.crandell.fafo.arm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ws.crandell.fafo.storage.DpState

/**
 * AC2.6: resume an active incident (or the armed state) after a reboot. directBootAware, reads only
 * device-protected storage. Idempotent: BOOT_COMPLETED after the first unlock re-reads the flags (already
 * cleared by the unlock) and does nothing if the service is running or nothing is set.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED && intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = ArmDeps.dpStore(app).readState()
                if (shouldStart(state, serviceRunning = ArmService.instance != null)) {
                    val i = Intent(app, ArmService::class.java).setAction(ArmService.ACTION_BOOT)
                    ContextCompat.startForegroundService(app, i)
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "boot resume failed", e)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        fun shouldStart(state: DpState, serviceRunning: Boolean): Boolean =
            !serviceRunning && (state.incidentActive || state.armed)
    }
}
