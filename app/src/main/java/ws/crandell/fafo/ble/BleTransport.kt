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
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ws.crandell.fafo.link.MessageLink
import ws.crandell.fafo.net.BeaconTag
import java.util.concurrent.ConcurrentHashMap

/**
 * F9 over Bluetooth LE (AC9.3, AC9.7): a GATT server carrying the same session handshake and frames as
 * Wi-Fi, a connectable advertisement with the beacon (state byte + rotating tag, no name), a low-power
 * scan that maps trusted peers' tags to Bluetooth devices, and GATT client dialing. Callers check the
 * Nearby devices permissions before [start].
 */
@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ownBeaconKey: () -> ByteArray,
    private val peerBeaconKeys: () -> Map<String, ByteArray>,
    private val onIncoming: suspend (MessageLink) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val bm = context.getSystemService(BluetoothManager::class.java)
    private var server: FafoGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertiseJob: Job? = null
    private var scanCallback: ScanCallback? = null
    @Volatile var alarming = false
        set(value) {
            if (field != value) {
                field = value
                restartAdvertising()
            }
        }

    val bluetoothOn: Boolean get() = bm?.adapter?.isEnabled == true

    fun start() {
        if (!bluetoothOn) return
        val s = FafoGattServer(context) { purpose, link, _ ->
            if (purpose == FafoGattServer.Purpose.SESSION) scope.launch { onIncoming(link) } else link.close()
        }
        if (!s.start(setOf(FafoGattServer.Purpose.SESSION))) return
        server = s
        restartAdvertising()
        startScan()
    }

    fun stop() {
        advertiseJob?.cancel()
        stopAdvertising()
        stopScan()
        server?.stop()
        server = null
    }

    /** Re-advertise every 2-minute slot so the tag rotates. */
    private fun restartAdvertising() {
        if (server == null) return
        advertiseJob?.cancel()
        advertiseJob = scope.launch {
            while (isActive) {
                stopAdvertising()
                startAdvertising()
                val now = clock()
                val next = (BeaconTag.slot(now, BeaconTag.BLE_SLOT_MS) + 1) * BeaconTag.BLE_SLOT_MS
                delay((next - now).coerceAtLeast(1_000))
            }
        }
    }

    private fun startAdvertising() {
        val adv = bm?.adapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(if (alarming) AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY else AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(ParcelUuid(BleIds.SERVICE), BleBeacon.encode(alarming, ownBeaconKey(), clock()))
            .build()
        val cb = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) { Log.w(TAG, "advertise failed $errorCode") }
        }
        advertiseCallback = cb
        runCatching { adv.startAdvertising(settings, data, cb) }.onFailure { Log.w(TAG, "advertise", it) }
    }

    private fun stopAdvertising() {
        val cb = advertiseCallback ?: return
        runCatching { bm?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb) }
        advertiseCallback = null
    }

    private fun startScan() {
        val scanner = bm?.adapter?.bluetoothLeScanner ?: return
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onResult)
        }
        scanCallback = cb
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        runCatching { scanner.startScan(listOf(anyBeaconFilter()), settings, cb) }.onFailure { Log.w(TAG, "scan", it) }
    }

    /** Any FAFO beacon (reachable or alarming); the tag is checked in [onResult]. */
    private fun anyBeaconFilter(): ScanFilter = ScanFilter.Builder()
        .setServiceData(ParcelUuid(BleIds.SERVICE), byteArrayOf(0), byteArrayOf(0))
        .build()

    private fun stopScan() {
        val cb = scanCallback ?: return
        runCatching { bm?.adapter?.bluetoothLeScanner?.stopScan(cb) }
        scanCallback = null
    }

    private fun onResult(r: ScanResult) {
        val data = r.scanRecord?.getServiceData(ParcelUuid(BleIds.SERVICE))
        val (fp, _) = BleBeacon.owner(data, peerBeaconKeys(), clock()) ?: return
        seen[fp] = r.device to clock()
    }


    fun reachablePeers(): Set<String> {
        val cutoff = clock() - SEEN_TTL_MS
        return seen.filterValues { it.second >= cutoff }.keys
    }

    suspend fun dial(fp: String): List<MessageLink> {
        val (device, at) = seen[fp] ?: return emptyList()
        if (clock() - at > SEEN_TTL_MS) return emptyList()
        return listOfNotNull(runCatching { GattClientLink.connect(context, device, FafoGattServer.Purpose.SESSION) }.getOrNull())
    }

    companion object {
        /** Process-wide: survives transport restarts so an alert tap can still reach the peer. */
        private val seen = ConcurrentHashMap<String, Pair<BluetoothDevice, Long>>()
        private const val TAG = "BleTransport"
        private const val SEEN_TTL_MS = 5 * 60 * 1000L

        /** A peer's device found by the background alert scan counts as seen too. */
        fun noteSeenGlobal(fp: String, device: BluetoothDevice) {
            seen[fp] = device to System.currentTimeMillis()
        }
    }
}
