package ws.crandell.fafo.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import ws.crandell.fafo.link.LinkClosed
import ws.crandell.fafo.link.MessageLink
import java.util.UUID

/**
 * GATT client side of a FAFO link: connect, raise the MTU, find the service, subscribe to TX, then write
 * chunks to RX. Callers check BLUETOOTH_CONNECT first.
 */
@SuppressLint("MissingPermission")
class GattClientLink private constructor(
    private val rxUuid: UUID,
    private val txUuid: UUID,
) : MessageLink {
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private val ready = CompletableDeferred<Unit>()
    private val inbox = Channel<ByteArray>(64)
    private val joiner = Chunker.Joiner()
    private val sendLock = Mutex()
    @Volatile private var pendingWrite: CompletableDeferred<Int>? = null
    @Volatile private var closed = false

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (!g.requestMtu(BleIds.WANTED_MTU)) g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                fail("disconnected ($status)")
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(BleIds.SERVICE)
            val rxc = service?.getCharacteristic(rxUuid)
            val txc = service?.getCharacteristic(txUuid)
            val cccd = txc?.getDescriptor(BleIds.CCCD)
            if (status != BluetoothGatt.GATT_SUCCESS || rxc == null || txc == null || cccd == null) {
                fail("FAFO service not found")
                return
            }
            rx = rxc
            g.setCharacteristicNotification(txc, true)
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
            if (!started) fail("subscribe failed")
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) ready.complete(Unit) else fail("subscribe status $status")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == txUuid) onChunk(value)
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33 && characteristic.uuid == txUuid) onChunk(characteristic.value ?: return)
        }
    }

    private fun onChunk(chunk: ByteArray) {
        val msg = runCatching { joiner.add(chunk) }.getOrElse { fail("bad chunk"); return } ?: return
        if (inbox.trySend(msg).isFailure) fail("inbox full")
    }

    private fun fail(why: String) {
        closed = true
        ready.completeExceptionally(LinkClosed(why))
        pendingWrite?.complete(BluetoothGatt.GATT_FAILURE)
        inbox.close()
        runCatching { gatt?.close() }
        gatt = null
    }

    override suspend fun send(msg: ByteArray) = sendLock.withLock {
        val g = gatt ?: throw LinkClosed()
        val c = rx ?: throw LinkClosed()
        for (chunk in Chunker.split(msg, (mtu - 3 - 1).coerceAtLeast(16))) {
            if (closed) throw LinkClosed()
            val done = CompletableDeferred<Int>()
            pendingWrite = done
            val queued = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                c.value = chunk
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
            if (!queued) throw LinkClosed("write failed")
            val status = withTimeout(5_000) { done.await() }
            if (status != BluetoothGatt.GATT_SUCCESS) throw LinkClosed("write status $status")
        }
    }

    override suspend fun receive(): ByteArray = inbox.receiveCatching().getOrNull() ?: throw LinkClosed()

    override fun close() {
        closed = true
        inbox.close()
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
    }

    companion object {
        /** Connects and subscribes; returns once the link is ready. */
        suspend fun connect(
            context: Context,
            device: BluetoothDevice,
            purpose: FafoGattServer.Purpose,
            timeoutMs: Long = 20_000,
        ): GattClientLink {
            val link = GattClientLink(purpose.rx, purpose.tx)
            link.gatt = device.connectGatt(context, false, link.callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw LinkClosed("connectGatt failed")
            try {
                withTimeout(timeoutMs) { link.ready.await() }
            } catch (e: Exception) {
                link.close()
                throw e
            }
            return link
        }
    }
}
