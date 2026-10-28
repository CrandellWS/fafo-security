package ws.crandell.fafo.intercom

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.Network
import android.util.Log
import ws.crandell.fafo.net.LocalAddressPolicy
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Where encrypted audio packets go: a UDP address on Wi-Fi or an L2CAP channel on Bluetooth. */
sealed interface AudioRoute {
    data class Udp(val address: InetSocketAddress) : AudioRoute
    class Bt(val socket: BluetoothSocket) : AudioRoute {
        private val out = DataOutputStream(socket.outputStream)
        fun send(packet: ByteArray) = synchronized(out) {
            out.writeShort(packet.size)
            out.write(packet)
            out.flush()
        }
    }
}

/**
 * AC10.7 audio transport. One UDP socket bound to the Wi-Fi network and address (every source and
 * destination checked by [LocalAddressPolicy], AC9.2) and one insecure LE L2CAP server channel (the audio
 * is already AES-GCM encrypted end to end). Packets carry the session id in clear only to find the key;
 * [onPacket] gets everything that arrives and drops what fails to decrypt.
 */
@SuppressLint("MissingPermission")
class AudioSockets(private val context: Context, private val onPacket: (ByteArray, AudioRoute) -> Unit) {
    private var udp: DatagramSocket? = null
    private var l2cap: BluetoothServerSocket? = null
    private val btSockets = ConcurrentHashMap.newKeySet<BluetoothSocket>()

    @Synchronized
    fun udpPort(network: Network?, local: InetAddress?): Int {
        udp?.takeIf { !it.isClosed }?.let { return it.localPort }
        if (network == null || local == null) return 0
        val s = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                network.bindSocket(this)
                bind(InetSocketAddress(local, 0))
            }
        } catch (e: Exception) {
            Log.w(TAG, "udp", e)
            return 0
        }
        udp = s
        thread(name = "fafo-udp") {
            val buf = ByteArray(2048)
            while (!s.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                try {
                    s.receive(p)
                } catch (e: Exception) {
                    break
                }
                val from = p.socketAddress as? InetSocketAddress ?: continue
                if (!LocalAddressPolicy.isAllowed(from)) continue
                onPacket(p.data.copyOfRange(p.offset, p.offset + p.length), AudioRoute.Udp(from))
            }
        }
        return s.localPort
    }

    fun send(route: AudioRoute, packet: ByteArray) {
        try {
            when (route) {
                is AudioRoute.Udp -> {
                    if (!LocalAddressPolicy.isAllowed(route.address)) return
                    udp?.send(DatagramPacket(packet, packet.size, route.address))
                }
                is AudioRoute.Bt -> route.send(packet)
            }
        } catch (e: Exception) {
            Log.w(TAG, "send", e)
        }
    }

    /** PSM of the L2CAP server channel (API 29+), 0 when Bluetooth is unavailable. */
    @Synchronized
    fun l2capPsm(): Int {
        l2cap?.let { return it.psm }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return 0
        val server = runCatching { adapter.listenUsingInsecureL2capChannel() }.getOrNull() ?: return 0
        l2cap = server
        thread(name = "fafo-l2cap") {
            while (true) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                readLoop(sock)
            }
        }
        return server.psm
    }

    fun connectL2cap(device: BluetoothDevice, psm: Int): AudioRoute.Bt? = try {
        val sock = device.createInsecureL2capChannel(psm)
        sock.connect()
        readLoop(sock)
        AudioRoute.Bt(sock)
    } catch (e: Exception) {
        Log.w(TAG, "l2cap connect", e)
        null
    }

    private fun readLoop(sock: BluetoothSocket) {
        btSockets += sock
        val route = AudioRoute.Bt(sock)
        thread(name = "fafo-l2cap-read") {
            val din = DataInputStream(sock.inputStream)
            try {
                while (true) {
                    val n = din.readUnsignedShort()
                    if (n > 2048) break
                    val p = ByteArray(n).also { din.readFully(it) }
                    onPacket(p, route)
                }
            } catch (e: Exception) {
                // closed
            } finally {
                btSockets -= sock
                runCatching { sock.close() }
            }
        }
    }

    @Synchronized
    fun close() {
        runCatching { udp?.close() }
        udp = null
        runCatching { l2cap?.close() }
        l2cap = null
        btSockets.forEach { runCatching { it.close() } }
        btSockets.clear()
    }

    companion object {
        private const val TAG = "AudioSockets"
    }
}
