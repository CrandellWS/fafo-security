package ws.crandell.fafo.arm

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.ui.theme.FafoColors

class AlarmScreenTest {
    @Test fun backgroundFlashesTwiceASecond() {
        assertThat(alarmPulse(0)).isEqualTo(FafoColors.Red)
        assertThat(alarmPulse(250)).isEqualTo(FafoColors.RedDeep)
        assertThat(alarmPulse(500)).isEqualTo(FafoColors.Red) // 2 Hz: under the 3 flashes/s limit
        assertThat(alarmPulse(1_125)).isEqualTo(alarmPulse(125))
    }

    @Test fun countdownRoundsUpLikeTheStateLabel() {
        val s = AlarmState.PreAlarm("i", 0, 5_000)
        assertThat(secondsLeft(s, 0)).isEqualTo(5)
        assertThat(secondsLeft(s, 4_200)).isEqualTo(1)
        assertThat(secondsLeft(s, 6_000)).isEqualTo(0)
    }
}
