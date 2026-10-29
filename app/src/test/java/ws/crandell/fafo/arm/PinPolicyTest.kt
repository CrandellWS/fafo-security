package ws.crandell.fafo.arm

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.IncidentCause

/** Pin app while armed (round 5, spec F14): when the status screen pins and unpins. */
class PinPolicyTest {
    private val arming = AlarmState.Arming(0, 5_000)
    private val armed = AlarmState.Armed()
    private val pre = AlarmState.PreAlarm("i", 0, 5_000, IncidentCause.UNPLUG)
    private val triggered = AlarmState.Triggered("i", 0)

    @Test fun onlyTheArmTapWithTheSettingOnAsks() {
        assertThat(PinPolicy.requestOnArm(settingOn = true)).isTrue()
        assertThat(PinPolicy.requestOnArm(settingOn = false)).isFalse()
    }

    @Test fun pinsWhileArmingOrArmedOnce() {
        assertThat(PinPolicy.shouldPin(true, arming, pinned = false)).isTrue()
        assertThat(PinPolicy.shouldPin(true, armed, pinned = false)).isTrue()
        assertThat(PinPolicy.shouldPin(true, armed, pinned = true)).isFalse()
    }

    @Test fun neverPinsWithoutTheArmTapOrInOtherStates() {
        assertThat(PinPolicy.shouldPin(false, armed, pinned = false)).isFalse()
        for (s in listOf(AlarmState.Disarmed, pre, triggered, AlarmState.IncidentPersisted("i", 0))) {
            assertThat(PinPolicy.shouldPin(true, s, pinned = false)).isFalse()
        }
    }

    @Test fun aPendingRequestWaitsForArmingButNotForAnAlarm() {
        assertThat(PinPolicy.dropRequest(AlarmState.Disarmed)).isFalse()
        assertThat(PinPolicy.dropRequest(arming)).isFalse()
        assertThat(PinPolicy.dropRequest(pre)).isTrue()
        assertThat(PinPolicy.dropRequest(triggered)).isTrue()
    }

    @Test fun unpinsOnDisarmOnly() {
        assertThat(PinPolicy.shouldUnpin(pinned = true, AlarmState.Disarmed)).isTrue()
        assertThat(PinPolicy.shouldUnpin(pinned = false, AlarmState.Disarmed)).isFalse()
        for (s in listOf(arming, armed, pre, triggered)) assertThat(PinPolicy.shouldUnpin(pinned = true, s)).isFalse()
    }

    @Test fun pinnedStatusScreenStaysAfterARemoteReset() {
        assertThat(PinPolicy.closeWhenAlarmEnds(pinned = true, armed)).isFalse()
        assertThat(PinPolicy.closeWhenAlarmEnds(pinned = true, AlarmState.Disarmed)).isTrue()
        assertThat(PinPolicy.closeWhenAlarmEnds(pinned = false, armed)).isTrue()
    }
}
