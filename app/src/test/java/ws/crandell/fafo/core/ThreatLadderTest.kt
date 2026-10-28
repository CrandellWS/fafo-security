package ws.crandell.fafo.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.core.LadderEffect.Chirp
import ws.crandell.fafo.core.LadderEffect.Escalate

/** Bill 2026-10-28 bump ladder: yellow, orange, pre-alarm; back to green after the Bump reset time (default 15 s). */
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

    @Test fun defaultResetIs15Seconds() {
        assertThat(Defaults.BUMP_RESET_S).isEqualTo(15)
        assertThat(Defaults.BUMP_RESET_CHOICES_S).containsExactly(10, 15, 30, 60).inOrder()
        assertThat(l.coolMs).isEqualTo(15_000L)
    }

    @Test fun coolsBackToGreenAfterEachResetTime() {
        for (s in Defaults.BUMP_RESET_CHOICES_S) {
            val ladder = ThreatLadder(coolMsSetting = { s * 1000L })
            ladder.onMotion(Motion.BUMP, 0, true)
            ladder.onMotion(Motion.BUMP, 1_000, true)
            assertThat(ladder.level).isEqualTo(ThreatLevel.ORANGE)
            assertThat(ladder.tick(1_000 + s * 1000L - 1)).isFalse()
            assertThat(ladder.level).isEqualTo(ThreatLevel.ORANGE)
            assertThat(ladder.tick(1_000 + s * 1000L)).isTrue()
            assertThat(ladder.level).isEqualTo(ThreatLevel.GREEN)
            // After cooling, one bump is yellow again, not orange.
            ladder.onMotion(Motion.BUMP, 2_000 + s * 1000L, true)
            assertThat(ladder.level).isEqualTo(ThreatLevel.YELLOW)
        }
    }

    @Test fun resetTimeIsReadLive() {
        var s = 60
        val ladder = ThreatLadder(coolMsSetting = { s * 1000L })
        ladder.onMotion(Motion.BUMP, 0, true)
        assertThat(ladder.tick(10_000)).isFalse()
        s = 10
        assertThat(ladder.tick(10_000)).isTrue()
    }

    @Test fun msUntilCoolFollowsTheSettingMidCoolDown() {
        var s = 15
        val ladder = ThreatLadder(coolMsSetting = { s * 1000L })
        assertThat(ladder.msUntilCool(0)).isNull()
        ladder.onMotion(Motion.BUMP, 0, true)
        assertThat(ladder.msUntilCool(5_000)).isEqualTo(10_000L)
        // Lengthened while yellow: the old 15 s wake-up must not be the last check (the service loops on this).
        s = 60
        assertThat(ladder.tick(15_000)).isFalse()
        assertThat(ladder.msUntilCool(15_000)).isEqualTo(45_000L)
        // Shortened past the elapsed time: due now.
        s = 10
        assertThat(ladder.msUntilCool(15_000)).isEqualTo(0L)
        assertThat(ladder.tick(15_000)).isTrue()
        assertThat(ladder.msUntilCool(15_000)).isNull()
    }

    @Test fun motionRefreshesTheCoolTimer() {
        l.onMotion(Motion.BUMP, 0, true)
        l.onMotion(Motion.BUMP, 10_000, true)
        assertThat(l.tick(15_000)).isFalse()
        assertThat(l.tick(25_000)).isTrue()
    }

    @Test fun tickOnGreenDoesNothing() = assertThat(l.tick(1_000_000)).isFalse()
}
