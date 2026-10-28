package ws.crandell.fafo.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Bill 2026-10-28: Disarm skips the prompt only after a lock and unlock that happened after arming. */
class DisarmPolicyTest {
    private val boot = 7L
    private val grace = 30

    private fun skip(armed: Long?, off: Long?, present: Long?, now: Long, g: Int = grace, trailBoot: Long = boot) =
        DisarmPolicy.canSkipPrompt(UnlockTrail(armed, off, present, trailBoot), boot, now, g)

    @Test fun noUnlockSinceArmingPrompts() = assertThat(skip(armed = 1_000, off = null, present = null, now = 2_000)).isFalse()

    /** The bug Bill hit: unlocked, armed, tapped Disarm still inside the grace window of the earlier unlock. */
    @Test fun unlockBeforeArmPrompts() = assertThat(skip(armed = 10_000, off = 1_000, present = 5_000, now = 12_000)).isFalse()

    @Test fun screenOffBeforeArmThenUnlockAfterPrompts() = assertThat(skip(armed = 10_000, off = 9_000, present = 11_000, now = 12_000)).isFalse()

    @Test fun lockAndUnlockAfterArmInsideGraceSkips() = assertThat(skip(armed = 10_000, off = 20_000, present = 25_000, now = 30_000)).isTrue()

    @Test fun screenOffAtTheArmInstantCounts() = assertThat(skip(armed = 10_000, off = 10_000, present = 11_000, now = 12_000)).isTrue()

    @Test fun lockAndUnlockAfterArmOutsideGracePrompts() = assertThat(skip(armed = 10_000, off = 20_000, present = 25_000, now = 25_000 + 30_001)).isFalse()

    @Test fun exactlyAtTheGraceEdgeSkips() = assertThat(skip(armed = 10_000, off = 20_000, present = 25_000, now = 25_000 + 30_000)).isTrue()

    @Test fun graceZeroAlwaysPrompts() = assertThat(skip(armed = 10_000, off = 20_000, present = 25_000, now = 25_001, g = 0)).isFalse()

    @Test fun bootIdChangedPrompts() = assertThat(skip(armed = 10_000, off = 20_000, present = 25_000, now = 26_000, trailBoot = 6)).isFalse()

    @Test fun userPresentWithoutScreenOffPrompts() = assertThat(skip(armed = 10_000, off = null, present = 25_000, now = 26_000)).isFalse()

    @Test fun unlockNotAfterTheScreenOffPrompts() = assertThat(skip(armed = 10_000, off = 25_000, present = 25_000, now = 26_000)).isFalse()

    @Test fun negativeClockDeltaPrompts() = assertThat(skip(armed = 10_000, off = 20_000, present = 25_000, now = 24_000)).isFalse()

    @Test fun neverArmedPrompts() = assertThat(skip(armed = null, off = 20_000, present = 25_000, now = 26_000)).isFalse()
}
