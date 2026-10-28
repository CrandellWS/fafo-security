package ws.crandell.fafo.ble

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.net.BeaconTag
import ws.crandell.fafo.net.Transport
import ws.crandell.fafo.net.TransportSelector

class BleBeaconTest {
    private val key = Crypto.randomBytes(32)
    private val now = 3_000_000_000L

    @Test fun roundTripAndOwner() {
        val data = BleBeacon.encode(true, key, now)
        assertThat(data).hasLength(BleBeacon.SIZE)
        val (who, beacon) = BleBeacon.owner(data, mapOf("peer" to key), now + BeaconTag.BLE_SLOT_MS)!!
        assertThat(who).isEqualTo("peer")
        assertThat(beacon.alarming).isTrue()
        assertThat(BleBeacon.decode(BleBeacon.encode(false, key, now))!!.alarming).isFalse()
    }

    @Test fun unknownTagsAndGarbageIgnored() {
        assertThat(BleBeacon.owner(BleBeacon.encode(true, Crypto.randomBytes(32), now), mapOf("peer" to key), now)).isNull()
        assertThat(BleBeacon.owner(byteArrayOf(1, 2, 3), mapOf("peer" to key), now)).isNull()
        assertThat(BleBeacon.owner(null, mapOf("peer" to key), now)).isNull()
        assertThat(BleBeacon.decode(byteArrayOf(7) + ByteArray(8))).isNull()
        // An old tag (three slots ago) no longer matches: strangers cannot replay it for long.
        assertThat(BleBeacon.owner(BleBeacon.encode(true, key, now), mapOf("peer" to key), now + 3 * BeaconTag.BLE_SLOT_MS)).isNull()
    }

    @Test fun legacyAdvertisementFits() {
        // flags (3) + service data AD: length + type + 128-bit UUID + payload
        assertThat(3 + 2 + 16 + BleBeacon.SIZE).isAtMost(31)
    }

    @Test fun transportOrder() {
        assertThat(TransportSelector.order(onWifi = true, seenOnWifi = true, seenOnBluetooth = true, bluetoothOn = true))
            .containsExactly(Transport.WIFI, Transport.BLUETOOTH).inOrder()
        assertThat(TransportSelector.order(onWifi = true, seenOnWifi = false, seenOnBluetooth = true, bluetoothOn = true))
            .containsExactly(Transport.BLUETOOTH)
        assertThat(TransportSelector.order(onWifi = false, seenOnWifi = true, seenOnBluetooth = false, bluetoothOn = true)).isEmpty()
        assertThat(TransportSelector.order(onWifi = false, seenOnWifi = false, seenOnBluetooth = true, bluetoothOn = false)).isEmpty()
    }
}
