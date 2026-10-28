package ws.crandell.fafo.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import ws.crandell.fafo.link.LinkClosed
import ws.crandell.fafo.link.MessageLink
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * GATT server for the FAFO service (F6, F9 over Bluetooth). One characteristic pair per purpose: the client
 * writes to RX, the server notifies on TX. A link opens when the client subscribes to TX. Callers check the
 * Bluetooth permissions before [start].
 */
@SuppressLint("MissingPermission")
class FafoGattServer(
    private val context: Context,
    /** Called on a binder thread when a client subscribes to a TX characteristic. */
    private val onLink: (purpose: Purpose, link: MessageLink, device: BluetoothDevice) -> Unit,
) {
    enum class Purpose(val rx: UUID, val tx: UUID) {
        PAIRING(BleIds.PAIR_RX, BleIds.PAIR_TX),
        SESSION(BleIds.SESSION_RX, BleIds.SESSION_TX),
    }

    private var server: BluetoothGattServer? = null
    private val links = ConcurrentHashMap<String, ServerLink>()
    private val mtus = ConcurrentHashMap<String, Int>()
    private val chars = HashMap<UUID, BluetoothGattCharacteristic>()

    fun start(purposes: Set<Purpose>): Boolean {
        if (server != null) return true
        val bm = context.getSystemService(BluetoothManager::class.java) ?: return false
        val s = bm.openGattServer(context, callback) ?: return false
        val service = BluetoothGattService(BleIds.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        for (p in purposes) {
            val rx = BluetoothGattCharacteristic(
                p.rx,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
            val tx = BluetoothGattCharacteristic(
                p.tx,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
            tx.addDescriptor(
                BluetoothGattDescriptor(
                    BleIds.CCCD,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                ),
            )
            service.addCharacteristic(rx)
            service.addCharacteristic(tx)
            chars[p.rx] = rx
            chars[p.tx] = tx
        }
        s.addService(service)
        server = s
        return true
    }

    fun stop() {
        links.values.forEach { it.closeLocal() }
        links.clear()
        runCatching { server?.close() }
        server = null
    }

    private fun key(device: BluetoothDevice, purpose: Purpose) = "${device.address}/${purpose.name}"

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Purpose.entries.forEach { p -> links.remove(key(device, p))?.closeLocal() }
                mtus.remove(device.address)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            mtus[device.address] = mtu
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            val purpose = Purpose.entries.firstOrNull { it.rx == characteristic.uuid }
            val link = purpose?.let { links[key(device, it)] }
            val ok = link != null && value != null && !preparedWrite && offset == 0
            if (responseNeeded) {
                server?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
            }
            if (ok) link!!.onChunk(value!!)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            val purpose = Purpose.entries.firstOrNull { it.tx == descriptor.characteristic.uuid }
            val ok = purpose != null && descriptor.uuid == BleIds.CCCD
            if (responseNeeded) {
                server?.sendResponse(device, requestId, if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null)
            }
            if (!ok) return
            val enable = value != null && value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            val k = key(device, purpose!!)
            if (enable) {
                links.remove(k)?.closeLocal()
                val link = ServerLink(device, purpose)
                links[k] = link
                onLink(purpose, link, device)
            } else {
                links.remove(k)?.closeLocal()
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Purpose.entries.forEach { p -> links[key(device, p)]?.onSent(status) }
        }
    }

    private inner class ServerLink(private val device: BluetoothDevice, private val purpose: Purpose) : MessageLink {
        override val remote: Any get() = device
        private val inbox = Channel<ByteArray>(64)
        private val joiner = Chunker.Joiner()
        private val sendLock = Mutex()
        @Volatile private var pending: CompletableDeferred<Int>? = null
        @Volatile private var closed = false

        fun onChunk(chunk: ByteArray) {
            val msg = runCatching { joiner.add(chunk) }.getOrElse { closeLocal(); return } ?: return
            if (inbox.trySend(msg).isFailure) closeLocal()
        }

        fun onSent(status: Int) {
            pending?.complete(status)
        }

        override suspend fun send(msg: ByteArray) = sendLock.withLock {
            val s = server ?: throw LinkClosed()
            val tx = chars[purpose.tx] ?: throw LinkClosed()
            val payload = (mtus[device.address] ?: 23) - 3 - 1
            for (chunk in Chunker.split(msg, payload.coerceAtLeast(16))) {
                if (closed) throw LinkClosed()
                val done = CompletableDeferred<Int>()
                pending = done
                val queued = if (Build.VERSION.SDK_INT >= 33) {
                    s.notifyCharacteristicChanged(device, tx, false, chunk) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    tx.value = chunk
                    @Suppress("DEPRECATION")
                    s.notifyCharacteristicChanged(device, tx, false)
                }
                if (!queued) throw LinkClosed("notify failed")
                val status = withTimeout(5_000) { done.await() }
                if (status != BluetoothGatt.GATT_SUCCESS) throw LinkClosed("notify status $status")
            }
        }

        override suspend fun receive(): ByteArray =
            inbox.receiveCatching().getOrNull() ?: throw LinkClosed()

        fun closeLocal() {
            closed = true
            inbox.close()
            pending?.complete(BluetoothGatt.GATT_FAILURE)
        }

        override fun close() {
            closeLocal()
            links.remove(key(device, purpose), this)
            runCatching { server?.cancelConnection(device) }.onFailure { Log.w(TAG, "cancelConnection", it) }
        }
    }

    companion object {
        private const val TAG = "FafoGattServer"
    }
}
