package ws.crandell.fafo.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.arm.HandsOffActivity
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.IncidentCause
import ws.crandell.fafo.core.ThreatLevel
import ws.crandell.fafo.ui.status.PHOTO_BANNER_MS
import ws.crandell.fafo.ui.status.PHOTO_FLASH_MS
import ws.crandell.fafo.ui.status.StripeColor
import ws.crandell.fafo.ui.status.handsOffLook
import ws.crandell.fafo.ui.status.photoTakenLook

/** HANDS OFF and PHOTO TAKEN (device feedback 2026-10-28). */
class HandsOffLookTest {
    @Test fun bumpIsYellowHandsOff() {
        val l = handsOffLook(AlarmState.Armed(), ThreatLevel.YELLOW, 0)
        assertThat(l.stripes).isEqualTo(StripeColor.YELLOW)
        assertThat(l.label).isEqualTo("HANDS OFF")
        assertThat(l.keepScreenOn).isTrue()
        assertThat(l.flash).isFalse()
    }

    @Test fun pickupIsOrangeHandsOff() {
        val l = handsOffLook(AlarmState.Armed(), ThreatLevel.ORANGE, 0)
        assertThat(l.stripes).isEqualTo(StripeColor.ORANGE)
        assertThat(l.label).isEqualTo("HANDS OFF")
        assertThat(l.glow).isTrue()
    }

    @Test fun preAlarmIsRedWithTheCountdownButDoesNotFlash() {
        val l = handsOffLook(AlarmState.PreAlarm("i", 0, 10_000, IncidentCause.MOTION), ThreatLevel.GREEN, 2_500)
        assertThat(l.stripes).isEqualTo(StripeColor.RED)
        assertThat(l.label).isEqualTo("HANDS OFF")
        assertThat(l.countdown).isEqualTo(8)
        assertThat(l.flash).isFalse()
        val unplug = handsOffLook(AlarmState.PreAlarm("i", 0, 10_000), ThreatLevel.GREEN, 0)
        assertThat(unplug.label).isEqualTo("PLUG ME BACK IN")
        assertThat(unplug.countdown).isEqualTo(10)
    }

    @Test fun sirenLooksLikeTheAlarmScreen() {
        val l = handsOffLook(AlarmState.Triggered("i", 0), ThreatLevel.GREEN, 0)
        assertThat(l.flash).isTrue()
        assertThat(l.label).isEqualTo("ALARM")
    }

    @Test fun closesOnceTheWarningIsOver() {
        assertThat(HandsOffActivity.shouldClose(AlarmState.Armed(), ThreatLevel.GREEN)).isTrue()
        assertThat(HandsOffActivity.shouldClose(AlarmState.Disarmed, ThreatLevel.GREEN)).isTrue()
        assertThat(HandsOffActivity.shouldClose(AlarmState.Armed(awaitingPower = true), ThreatLevel.ORANGE)).isTrue()
        assertThat(HandsOffActivity.shouldClose(AlarmState.Armed(), ThreatLevel.YELLOW)).isFalse()
        assertThat(HandsOffActivity.shouldClose(AlarmState.Armed(), ThreatLevel.ORANGE)).isFalse()
        assertThat(HandsOffActivity.shouldClose(AlarmState.PreAlarm("i", 0, 5_000, IncidentCause.MOTION), ThreatLevel.GREEN)).isFalse()
        assertThat(HandsOffActivity.shouldClose(AlarmState.Triggered("i", 0), ThreatLevel.GREEN)).isFalse()
    }

    @Test fun photoFlashFadesAndTheBannerLastsTwoSeconds() {
        val start = photoTakenLook(1_000, 1, 1_000, reducedMotion = false)
        assertThat(start.banner).isTrue()
        assertThat(start.flashAlpha).isGreaterThan(0.8f)
        val mid = photoTakenLook(1_000, 1, 1_150, reducedMotion = false)
        assertThat(mid.flashAlpha).isLessThan(start.flashAlpha)
        assertThat(photoTakenLook(1_000, 1, 1_000 + PHOTO_FLASH_MS, false).flashAlpha).isEqualTo(0f)
        assertThat(photoTakenLook(1_000, 1, 1_000 + PHOTO_BANNER_MS - 1, false).banner).isTrue()
        assertThat(photoTakenLook(1_000, 1, 1_000 + PHOTO_BANNER_MS, false).banner).isFalse()
    }

    @Test fun reducedMotionHasNoFlashButKeepsTheLabel() {
        val l = photoTakenLook(0, 2, 50, reducedMotion = true)
        assertThat(l.flashAlpha).isEqualTo(0f)
        assertThat(l.banner).isTrue()
    }

    @Test fun counterWording() {
        assertThat(photoTakenLook(null, 0, 0, false).countLabel).isNull()
        assertThat(photoTakenLook(0, 1, 0, false).countLabel).isEqualTo("1 photo taken")
        assertThat(photoTakenLook(0, 3, 99_000, false).countLabel).isEqualTo("3 photos taken")
        // The counter stays after the banner is gone.
        assertThat(photoTakenLook(0, 3, 99_000, false).banner).isFalse()
    }
}
