package ws.crandell.fafo.ble

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ws.crandell.fafo.net.android.AlertNotifier
import ws.crandell.fafo.net.android.PeerNetwork
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.trust.TrustGraph

/**
 * Woken by the background BLE scan (AC9.7). Checks each alarming beacon's tag against trusted peers'
 * beacon keys; unknown tags are ignored. On a match: post the alert, then connect over GATT for the
 * details (the alarming phone sends its Alert, with the granted Reset / Disarm, when the session opens).
 * Needs CE storage (trust list), so it does nothing before the first unlock.
 */
class BleAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BleAlertScan.ACTION || !DeviceStatus.isCeUnlocked(context)) return
        val results: List<ScanResult> = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT).orEmpty()
        }
        val trusted = TrustGraph.trusted(context)
        val keys = trusted.all().mapNotNull { d -> d.beaconKey?.let { d.fingerprint to it } }.toMap()
        val now = System.currentTimeMillis()
        val matches = results.mapNotNull { r ->
            val (fp, beacon) = BleBeacon.owner(r.scanRecord?.getServiceData(ParcelUuid(BleIds.SERVICE)), keys, now) ?: return@mapNotNull null
            if (beacon.alarming) fp to r else null
        }.distinctBy { it.first }
        if (matches.isEmpty()) return
        val circles = TrustGraph.circles(context).all()
        for ((fp, _) in matches) {
            val label = circles.firstNotNullOfOrNull { it.member(fp)?.label } ?: trusted.get(fp)?.name ?: "Trusted device"
            AlertNotifier.postBeacon(context, fp, label)
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val hub = PeerNetwork.acquire(context, OWNER) ?: return@launch
                for ((fp, r) in matches) {
                    BleTransport.noteSeenGlobal(fp, r.device)
                    withTimeoutOrNull(6_000) { hub.connect(fp) }
                }
                delay(2_000) // let the Alert with its Reset / Disarm actions arrive
            } finally {
                PeerNetwork.release(OWNER)
                pending.finish()
            }
        }
    }

    companion object {
        private const val OWNER = "ble-alert"
    }
}
