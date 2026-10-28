package ws.crandell.fafo.net

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress

/**
 * AC9.2: the only addresses FAFO ever talks to. Private IPv4 (10/8, 172.16/12, 192.168/16), IPv4
 * link-local (169.254/16), IPv6 link-local (fe80::/10) and ULA (fc00::/7). Everything else (public,
 * loopback, multicast, unspecified, IPv4-mapped public) is refused. Checked on TCP connect, TCP accept,
 * every UDP send/receive and every NSD-resolved address. Pure Kotlin (bytes only, no DNS lookups).
 */
object LocalAddressPolicy {

    fun isAllowed(address: InetAddress?): Boolean {
        if (address == null) return false
        val b = address.address
        return when (address) {
            is Inet4Address -> isPrivateV4(b)
            is Inet6Address -> isAllowedV6(b)
            else -> false
        }
    }

    fun isAllowed(socketAddress: SocketAddress?): Boolean =
        (socketAddress as? InetSocketAddress)?.let { !it.isUnresolved && isAllowed(it.address) } ?: false

    /** Throws [SecurityException] for a refused address. */
    fun require(address: InetAddress?) {
        if (!isAllowed(address)) throw SecurityException("refused non-local address")
    }

    fun require(socketAddress: SocketAddress?) {
        if (!isAllowed(socketAddress)) throw SecurityException("refused non-local address")
    }

    private fun u(b: Byte) = b.toInt() and 0xff

    private fun isPrivateV4(b: ByteArray): Boolean {
        if (b.size != 4) return false
        val a0 = u(b[0])
        val a1 = u(b[1])
        return a0 == 10 ||
            (a0 == 172 && a1 in 16..31) ||
            (a0 == 192 && a1 == 168) ||
            (a0 == 169 && a1 == 254)
    }

    private fun isAllowedV6(b: ByteArray): Boolean {
        if (b.size != 16) return false
        // IPv4-mapped (::ffff:a.b.c.d) and IPv4-compatible forms: judge the embedded IPv4 address.
        val firstTenZero = (0 until 10).all { b[it].toInt() == 0 }
        if (firstTenZero && u(b[10]) == 0xff && u(b[11]) == 0xff) return isPrivateV4(b.copyOfRange(12, 16))
        val b0 = u(b[0])
        val b1 = u(b[1])
        val linkLocal = b0 == 0xfe && (b1 and 0xc0) == 0x80
        val ula = (b0 and 0xfe) == 0xfc
        return linkLocal || ula
    }
}
