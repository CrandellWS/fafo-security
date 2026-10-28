package ws.crandell.fafo.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.core.LadderEffect.Chirp
import ws.crandell.fafo.core.LadderEffect.Escalate

/** Bill 2026-10-28 bump ladder: yellow, orange, pre-alarm; back to green after a quiet minute. */
class ThreatLadderTest {
    private val l = ThreatLadder()

    @Test fun oneBumpGoesYellowWithASoftChirp() {
        assertThat(l.onMotion(Motion.BUMP, 0, mayAlarm = true)).containsExactly(Chirp(loud = false))
        assertThat(l.level).isEqualTo(ThreatLevel.YELLOW)
    }

    @Test fun bumpsCloseTogetherGoOrangeAndLouder() {
        l.onMotion(Motion.BUMP, 0, true)
        assertThat(l.onMotion(Motion.BUMP, 5_000, true)).containsExactly(Chirp(loud = true))
        assertThat(l.level).isEqualTo(ThreatLevel.ORANGE)
    }

    @Test fun spacedBumpsStayYellow() {
        l.onMotion(Motion.BUMP, 0, true)
        assertThat(l.onMotion(Motion.BUMP, 9_000, true)).containsExactly(Chirp(loud = false))
        assertThat(l.level).isEqualTo(ThreatLevel.YELLOW)
    }

    @Test fun continuedHandlingEscalatesToThePreAlarm() {
        l.onMotion(Motion.BUMP, 0, true)
        l.onMotion(Motion.BUMP, 3_000, true)
        assertThat(l.onMotion(Motion.BUMP, 6_000, true)).containsExactly(Escalate)
    }

    @Test fun orangeBumpAfterAPauseOnlyChirps() {
        l.onMotion(Motion.BUMP, 0, true)
        l.onMotion(Motion.BUMP, 3_000, true)
        assertThat(l.onMotion(Motion.BUMP, 20_000, true)).containsExactly(Chirp(loud = true))
        assertThat(l.level).isEqualTo(ThreatLevel.ORANGE)
    }

    @Test fun pickupGoesStraightToThePreAlarm() {
        assertThat(l.onMotion(Motion.PICKUP, 0, true)).containsExactly(Escalate)
    }

    @Test fun unlockedOrSettingOffNeverEscalates() {
        assertThat(l.onMotion(Motion.PICKUP, 0, mayAlarm = false)).containsExactly(Chirp(loud = true))
        l.onMotion(Motion.BUMP, 1_000, false)
        assertThat(l.onMotion(Motion.BUMP, 2_000, false)).containsExactly(Chirp(loud = true))
        assertThat(l.level).isEqualTo(ThreatLevel.ORANGE)
    }

    @Test fun coolsBackToGreenAfterAQuietMinute() {
        l.onMotion(Motion.BUMP, 0, true)
        l.onMotion(Motion.BUMP, 1_000, true)
        assertThat(l.tick(60_999)).isFalse()
        assertThat(l.tick(61_000)).isTrue()
        assertThat(l.level).isEqualTo(ThreatLevel.GREEN)
        // After cooling, one bump is yellow again, not orange.
        l.onMotion(Motion.BUMP, 62_000, true)
        assertThat(l.level).isEqualTo(ThreatLevel.YELLOW)
    }

    @Test fun motionRefreshesTheCoolTimer() {
        l.onMotion(Motion.BUMP, 0, true)
        l.onMotion(Motion.BUMP, 50_000, true)
        assertThat(l.tick(61_000)).isFalse()
    }

    @Test fun tickOnGreenDoesNothing() = assertThat(l.tick(1_000_000)).isFalse()
}
