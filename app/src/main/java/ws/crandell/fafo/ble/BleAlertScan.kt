package ws.crandell.fafo.ble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log

/**
 * AC9.7: background BLE scan with a PendingIntent, filtered on alarming FAFO beacons (service data state
 * byte 1). Works without a foreground service; the system wakes [BleAlertReceiver] on a match.
 */
@SuppressLint("MissingPermission")
object BleAlertScan {
    private const val TAG = "BleAlertScan"
    const val ACTION = "ws.crandell.fafo.action.BLE_ALERT"

    /** Only alarming beacons: state byte must be 1. */
    fun alarmFilter(): ScanFilter = ScanFilter.Builder()
        .setServiceData(ParcelUuid(BleIds.SERVICE), byteArrayOf(1), byteArrayOf(-1))
        .build()

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7,
        Intent(context, BleAlertReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // the system adds the scan results
    )

    fun register(context: Context): Boolean {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return false
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        return runCatching { scanner.startScan(listOf(alarmFilter()), settings, pendingIntent(context)) == 0 }
            .onFailure { Log.w(TAG, "background scan", it) }
            .getOrDefault(false)
    }

    fun unregister(context: Context) {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(pendingIntent(context)) }
    }
}
