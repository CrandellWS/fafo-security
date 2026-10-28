package ws.crandell.fafo.net

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.crypto.Crypto

class BeaconTagTest {
    private val key = Crypto.randomBytes(32)
    private val other = Crypto.randomBytes(32)
    private val slot = BeaconTag.NSD_SLOT_MS

    @Test fun matchesAcrossSlotEdge() {
        val edge = 1_000L * slot
        val tagJustBefore = BeaconTag.current(key, edge - 1, slot)
        assertThat(BeaconTag.matches(key, tagJustBefore, edge + 1, slot)).isTrue()
        // Two slots later the old tag no longer matches.
        assertThat(BeaconTag.matches(key, tagJustBefore, edge + slot + 1, slot)).isFalse()
    }

    @Test fun differsPerKeyAndPerSlot() {
        val t = 5_000_000_000L
        assertThat(BeaconTag.current(key, t, slot)).isNotEqualTo(BeaconTag.current(other, t, slot))
        assertThat(BeaconTag.current(key, t, slot)).isNotEqualTo(BeaconTag.current(key, t + slot, slot))
        assertThat(BeaconTag.current(key, t, slot)).hasLength(8)
        assertThat(BeaconTag.matches(other, BeaconTag.current(key, t, slot), t, slot)).isFalse()
    }

    @Test fun findsOwner() {
        val t = 7_000_000_000L
        val keys = mapOf("a" to key, "b" to other)
        assertThat(BeaconTag.findOwner(keys, BeaconTag.current(other, t, BeaconTag.BLE_SLOT_MS), t, BeaconTag.BLE_SLOT_MS)).isEqualTo("b")
        assertThat(BeaconTag.findOwner(keys, Crypto.randomBytes(8), t, BeaconTag.BLE_SLOT_MS)).isNull()
    }
}
