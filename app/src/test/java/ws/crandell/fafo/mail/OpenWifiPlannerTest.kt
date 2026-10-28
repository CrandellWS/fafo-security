package ws.crandell.fafo.mail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenWifiPlannerTest {
    @Test fun triggersOnlyWhenEverythingHolds() {
        assertThat(OpenWifiPlanner.shouldTrigger(true, true, 34, 60_000, false)).isTrue()
        assertThat(OpenWifiPlanner.shouldTrigger(false, true, 34, 60_000, false)).isFalse() // nothing queued
        assertThat(OpenWifiPlanner.shouldTrigger(true, false, 34, 60_000, false)).isFalse() // setting off
        assertThat(OpenWifiPlanner.shouldTrigger(true, true, 32, 60_000, false)).isFalse() // needs Android 13
        assertThat(OpenWifiPlanner.shouldTrigger(true, true, 34, 59_999, false)).isFalse() // not a minute yet
        assertThat(OpenWifiPlanner.shouldTrigger(true, true, 34, 60_000, true)).isFalse() // mobile data works
    }

    @Test fun onlyOpenNetworksStrongestFirstAtMostFive() {
        val seen = listOf(
            WifiSeen("Home", "[WPA2-PSK-CCMP][ESS]", -40),
            WifiSeen("Corp", "[WPA2-EAP-CCMP][ESS]", -45),
            WifiSeen("New", "[RSN-SAE-CCMP][ESS]", -45),
            WifiSeen("Old", "[WEP][ESS]", -45),
            WifiSeen("Cafe", "[ESS]", -60),
            WifiSeen("Library", "[RSN-OWE-CCMP][ESS]", -50),
            WifiSeen("Far", "[ESS]", -85),
            WifiSeen("", "[ESS]", -30),
            WifiSeen("A", "[ESS]", -61), WifiSeen("B", "[ESS]", -62), WifiSeen("C", "[ESS]", -63), WifiSeen("D", "[ESS]", -64),
            WifiSeen("Cafe", "[ESS]", -70),
        )
        assertThat(OpenWifiPlanner.pick(seen)).containsExactly("Library", "Cafe", "A", "B", "C").inOrder()
    }

    @Test fun owePlusSaeTransitionCountsAsSecured() = assertThat(OpenWifiPlanner.isOpen("[RSN-OWE-CCMP][RSN-SAE-CCMP]")).isFalse()

    @Test fun suggestionsAreRemovedWhenDoneTimedOutOrDisarmed() {
        assertThat(OpenWifiPlanner.shouldRemove(pendingMail = false, suggestedAt = 0, now = 1, armedOrAlarming = true)).isTrue()
        assertThat(OpenWifiPlanner.shouldRemove(true, 0, OpenWifiPlanner.LIFETIME_MS, true)).isTrue()
        assertThat(OpenWifiPlanner.shouldRemove(true, 0, 1, armedOrAlarming = false)).isTrue()
        assertThat(OpenWifiPlanner.shouldRemove(true, 0, 1, true)).isFalse()
    }
}
