package ws.crandell.fafo.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreAlarmScheduleTest {
    @Test fun zeroDurationHasNoBeeps() {
        assertThat(PreAlarmSchedule.beepOffsets(0)).isEmpty()
    }

    @Test fun intervalsAreNonIncreasingForEveryChoice() {
        for (s in Defaults.PRE_ALARM_CHOICES_S.filter { it > 0 }) {
            val beeps = PreAlarmSchedule.beepOffsets(s * 1000L)
            assertThat(beeps.first()).isEqualTo(0L)
            assertThat(beeps.last()).isLessThan(s * 1000L)
            val gaps = beeps.zipWithNext { a, b -> b - a }
            gaps.zipWithNext { a, b -> assertThat(b).isAtMost(a) }
        }
    }

    @Test fun slowAtStartFastAtEnd() {
        val beeps = PreAlarmSchedule.beepOffsets(30_000)
        assertThat(beeps[1] - beeps[0]).isEqualTo(1000L)
        val last = beeps.takeLast(3).zipWithNext { a, b -> b - a }
        assertThat(last.all { it == 200L }).isTrue()
    }
}
