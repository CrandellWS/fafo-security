package ws.crandell.fafo.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import ws.crandell.fafo.link.LinkClosed
import ws.crandell.fafo.link.MessageLink

/**
 * Pairing over Bluetooth LE (F6). The responder advertises the FAFO service UUID (no name, no label) and
 * runs the GATT server; the initiator scans for that UUID (API 31+, `neverForLocation`) or gets the
 * device from the CompanionDeviceManager picker (API 30), then connects as a GATT client.
 */
@SuppressLint("MissingPermission")
class BlePairing(private val context: Context) {
    private val bm = context.getSystemService(BluetoothManager::class.java)
    private var server: FafoGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null

    val bluetoothOn: Boolean get() = bm?.adapter?.isEnabled == true

    /** Responder: become visible and wait for the initiator to connect and subscribe. */
    suspend fun awaitInitiator(timeoutMs: Long = 120_000): MessageLink {
        val got = CompletableDeferred<MessageLink>()
        val s = FafoGattServer(context) { purpose, link, _ ->
            if (purpose == FafoGattServer.Purpose.PAIRING && !got.complete(link)) link.close()
        }
        server = s
        if (!s.start(setOf(FafoGattServer.Purpose.PAIRING))) throw LinkClosed("Bluetooth is off")
        startAdvertising()
        try {
            return withTimeout(timeoutMs) { got.await() }
        } finally {
            stopAdvertising()
        }
    }

    private fun startAdvertising() {
        val adv = bm?.adapter?.bluetoothLeAdvertiser ?: throw LinkClosed("Bluetooth LE advertising not available")
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BleIds.SERVICE))
            .build()
        val cb = object : AdvertiseCallback() {}
        advertiseCallback = cb
        adv.startAdvertising(settings, data, cb)
    }

    private fun stopAdvertising() {
        val cb = advertiseCallback ?: return
        runCatching { bm?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb) }
        advertiseCallback = null
    }

    /** Initiator on API 31+: scan for the FAFO service UUID and return the first device seen. */
    suspend fun scanForResponder(timeoutMs: Long = 60_000): BluetoothDevice {
        val scanner = bm?.adapter?.bluetoothLeScanner ?: throw LinkClosed("Bluetooth is off")
        val found = CompletableDeferred<BluetoothDevice>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                found.complete(result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                found.completeExceptionally(LinkClosed("scan failed ($errorCode)"))
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleIds.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, cb)
        try {
            return withTimeout(timeoutMs) { found.await() }
        } finally {
            runCatching { scanner.stopScan(cb) }
        }
    }

    suspend fun connect(device: BluetoothDevice): MessageLink =
        GattClientLink.connect(context, device, FafoGattServer.Purpose.PAIRING)

    fun stop() {
        stopAdvertising()
        server?.stop()
        server = null
    }
}
