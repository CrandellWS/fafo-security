package ws.crandell.fafo.reach

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ws.crandell.fafo.ble.BleAlertScan
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.trust.TrustGraph
import ws.crandell.fafo.ui.NearbyPermissions

/**
 * BOOT_COMPLETED only (after the first unlock; never LOCKED_BOOT_COMPLETED, and not directBootAware):
 * resume "Stay reachable" if it is on (AC9.9) and re-register the background Bluetooth alert scan, which
 * does not survive a reboot (AC9.7). Separate from the direct-boot alarm receiver so that one never
 * touches CE storage.
 */
class ReachableBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !DeviceStatus.isCeUnlocked(context)) return
        // Null when onReceive is called directly (tests), not from a broadcast.
        val pending: PendingResult? = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (NearbyPermissions.missing(context).isEmpty() && TrustGraph.trusted(context).all().isNotEmpty()) {
                    BleAlertScan.register(context)
                }
                if (CeStore.get(context).read().stayReachable) ReachableService.start(context)
            } finally {
                pending?.finish()
            }
        }
    }
}
