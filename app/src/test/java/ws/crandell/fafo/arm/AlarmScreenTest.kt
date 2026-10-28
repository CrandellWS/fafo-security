package ws.crandell.fafo.arm

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.ui.theme.FafoColors

class AlarmScreenTest {
    @Test fun pulseIsSlowOneHertz() {
        assertThat(alarmPulse(0)).isEqualTo(FafoColors.Red)
        assertThat(alarmPulse(500)).isEqualTo(FafoColors.RedDeep)
        assertThat(alarmPulse(1_000)).isEqualTo(FafoColors.Red) // one full cycle per second, no strobe
        assertThat(alarmPulse(1_250)).isEqualTo(alarmPulse(250))
    }

    @Test fun countdownRoundsUpLikeTheStateLabel() {
        val s = AlarmState.PreAlarm("i", 0, 5_000)
        assertThat(secondsLeft(s, 0)).isEqualTo(5)
        assertThat(secondsLeft(s, 4_200)).isEqualTo(1)
        assertThat(secondsLeft(s, 6_000)).isEqualTo(0)
    }
}
