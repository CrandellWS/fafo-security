package ws.crandell.fafo.net.android

import android.content.Context
import android.net.nsd.NsdServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.junit.runner.RunWith
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.net.BeaconTag
import java.net.InetAddress

/** AC9.11 Robolectric part: the NSD record and the resolved-address check. */
@RunWith(AndroidJUnit4::class)
class WifiTransportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val own = Crypto.randomBytes(32)
    private val peerKey = Crypto.randomBytes(32)
    private val now = 9_000_000_000L
    private val t = WifiTransport(
        context, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), { own }, { mapOf("peer" to peerKey) }, { }, { now },
    )

    @Test fun registersFafoTcpWithRandomNameAndTag() {
        val a = t.serviceInfo(4242)
        val b = t.serviceInfo(4242)
        assertThat(a.serviceType).isEqualTo("_fafo._tcp")
        assertThat(a.port).isEqualTo(4242)
        assertThat(a.serviceName).isNotEqualTo(b.serviceName)
        assertThat(a.serviceName).doesNotContain(android.os.Build.MODEL ?: "\u0000")
        assertThat(String(a.attributes["v"]!!)).isEqualTo("1")
        assertThat(String(a.attributes["t"]!!)).isEqualTo(Crypto.hex(BeaconTag.current(own, now, BeaconTag.NSD_SLOT_MS)))
    }

    private fun resolved(host: String, key: ByteArray): NsdServiceInfo = NsdServiceInfo().apply {
        serviceName = "fafo-x"
        serviceType = "_fafo._tcp"
        port = 5000
        @Suppress("DEPRECATION")
        this.host = InetAddress.getByName(host)
        setAttribute("t", Crypto.hex(BeaconTag.current(key, now, BeaconTag.NSD_SLOT_MS)))
    }

    @Test fun privateAddressAccepted() {
        t.onResolved(resolved("192.168.1.30", peerKey))
        assertThat(t.knownEndpoint("peer")?.address?.hostAddress).isEqualTo("192.168.1.30")
    }

    @Test fun publicAddressRefused() {
        t.onResolved(resolved("8.8.8.8", peerKey))
        assertThat(t.knownEndpoint("peer")).isNull()
    }

    @Test fun ownServiceIgnored() {
        t.onResolved(resolved("192.168.1.31", own))
        assertThat(t.knownEndpoint("peer")).isNull()
        assertThat(t.hasUnknownEndpoints()).isFalse()
    }
}
