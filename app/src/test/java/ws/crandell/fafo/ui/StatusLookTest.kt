package ws.crandell.fafo.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.IncidentCause
import ws.crandell.fafo.core.ThreatLevel
import ws.crandell.fafo.ui.status.LightLevel
import ws.crandell.fafo.ui.status.StripeColor
import ws.crandell.fafo.ui.status.statusLook

/** The traffic-light table (Bill 2026-10-28). */
class StatusLookTest {
    private val g = ThreatLevel.GREEN

    @Test fun disarmedBrandStripesWhiteEye() {
        val l = statusLook(AlarmState.Disarmed, g, 0)
        assertThat(l.stripes).isEqualTo(StripeColor.BRAND); assertThat(l.eyeRed).isFalse(); assertThat(l.label).isEqualTo("DISARMED")
    }

    @Test fun armingYellowWhiteEyeWithCountdown() {
        val l = statusLook(AlarmState.Arming(0, 5_000), g, 1_000)
        assertThat(l.stripes).isEqualTo(StripeColor.YELLOW); assertThat(l.eyeRed).isFalse(); assertThat(l.countdown).isEqualTo(4)
    }

    @Test fun armedCalmIsGreenWithARedEye() {
        val l = statusLook(AlarmState.Armed(), g, 0)
        assertThat(l.stripes).isEqualTo(StripeColor.GREEN); assertThat(l.eyeRed).isTrue(); assertThat(l.keepScreenOn).isFalse()
    }

    @Test fun ladderColors() {
        assertThat(statusLook(AlarmState.Armed(), ThreatLevel.YELLOW, 0).stripes).isEqualTo(StripeColor.YELLOW)
        val o = statusLook(AlarmState.Armed(), ThreatLevel.ORANGE, 0)
        assertThat(o.stripes).isEqualTo(StripeColor.ORANGE); assertThat(o.light).isEqualTo(LightLevel.WARN); assertThat(o.glow).isTrue()
    }

    @Test fun preAlarmIsOrangeWithCountdownForEitherCause() {
        val u = statusLook(AlarmState.PreAlarm("i", 0, 5_000), g, 0)
        assertThat(u.stripes).isEqualTo(StripeColor.ORANGE); assertThat(u.label).isEqualTo("PLUG ME BACK IN"); assertThat(u.countdown).isEqualTo(5)
        assertThat(u.keepScreenOn).isTrue()
        assertThat(statusLook(AlarmState.PreAlarm("i", 0, 5_000, IncidentCause.MOTION), g, 0).label).isEqualTo("HANDS OFF")
    }

    @Test fun triggeredIsRedAndFlashes() {
        for (s in listOf(AlarmState.Triggered("i", 0), AlarmState.IncidentPersisted("i", 0))) {
            val l = statusLook(s, g, 0)
            assertThat(l.stripes).isEqualTo(StripeColor.RED); assertThat(l.flash).isTrue(); assertThat(l.light).isEqualTo(LightLevel.ALARM)
        }
    }
}
