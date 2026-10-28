package ws.crandell.fafo.net

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress

class LocalAddressPolicyTest {
    private fun ip(s: String): InetAddress = InetAddress.getByName(s) // literals only: no DNS lookup

    @Test fun acceptsEveryPrivateRange() {
        listOf(
            "10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.254", "192.168.1.20", "192.168.0.1",
            "169.254.10.10", "fe80::1", "febf::1", "fc00::1", "fd12:3456:789a::1",
        ).forEach { assertWithMessage(it).that(LocalAddressPolicy.isAllowed(ip(it))).isTrue() }
    }

    @Test fun refusesPublicLoopbackMulticastAndUnspecified() {
        listOf(
            "8.8.8.8", "1.1.1.1", "172.15.255.255", "172.32.0.1", "192.169.0.1", "11.0.0.1", "100.64.0.1",
            "127.0.0.1", "0.0.0.0", "224.0.0.251", "239.255.255.250", "255.255.255.255",
            "2001:4860:4860::8888", "::1", "::", "ff02::fb", "fec0::1", "fe00::1",
        ).forEach { assertWithMessage(it).that(LocalAddressPolicy.isAllowed(ip(it))).isFalse() }
    }

    @Test fun ipv4MappedJudgedByEmbeddedAddress() {
        fun mapped(a: Int, b: Int, c: Int, d: Int): InetAddress {
            val bytes = ByteArray(16).also { it[10] = -1; it[11] = -1; it[12] = a.toByte(); it[13] = b.toByte(); it[14] = c.toByte(); it[15] = d.toByte() }
            return Inet6Address.getByAddress(null, bytes, -1)
        }
        assertThat(LocalAddressPolicy.isAllowed(mapped(8, 8, 8, 8))).isFalse()
        assertThat(LocalAddressPolicy.isAllowed(mapped(192, 168, 1, 2))).isTrue()
        assertThat(LocalAddressPolicy.isAllowed(ip("::ffff:8.8.4.4"))).isFalse()
    }

    @Test fun socketAddressesAndRequire() {
        assertThat(LocalAddressPolicy.isAllowed(InetSocketAddress(ip("192.168.1.5"), 4000))).isTrue()
        assertThat(LocalAddressPolicy.isAllowed(InetSocketAddress.createUnresolved("example.invalid", 80))).isFalse()
        assertThat(LocalAddressPolicy.isAllowed(null as InetAddress?)).isFalse()
        assertThrows(SecurityException::class.java) { LocalAddressPolicy.require(ip("8.8.8.8")) }
        LocalAddressPolicy.require(ip("10.1.2.3"))
    }
}
