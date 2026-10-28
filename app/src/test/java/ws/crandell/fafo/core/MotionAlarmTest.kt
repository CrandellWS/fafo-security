package ws.crandell.fafo.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Round 3: the bump ladder can start a MOTION pre-alarm while locked (replaces AC4.2 chirp-only). */
class MotionAlarmTest {
    private val clock = FakeClock()
    private var config = AlarmConfig(preAlarmMs = 1_000, armingMs = 0)
    private fun armed(initial: AlarmState = AlarmState.Armed()) = AlarmStateMachine(clock, { config }, { "m1" }, initial)

    @Test fun escalationWhileLockedStartsAMotionPreAlarmOfAtLeastFiveSeconds() {
        val m = armed()
        val fx = m.handle(AlarmEvent.MotionEscalated(locked = true))
        val s = m.state as AlarmState.PreAlarm
        assertThat(s.cause).isEqualTo(IncidentCause.MOTION)
        assertThat(s.endsAt - s.startedAt).isEqualTo(MIN_MOTION_PRE_ALARM_MS)
        assertThat(fx.first()).isEqualTo(AlarmEffect.PersistIncident(true, "m1", clock.now()))
        assertThat(fx).contains(AlarmEffect.StartPhotos)
    }

    @Test fun longerPreAlarmSettingIsKept() {
        config = config.copy(preAlarmMs = 30_000)
        val m = armed()
        m.handle(AlarmEvent.MotionEscalated(true))
        val s = m.state as AlarmState.PreAlarm
        assertThat(s.endsAt - s.startedAt).isEqualTo(30_000)
    }

    @Test fun zeroPreAlarmStillGivesFiveSecondsForMotion() {
        config = config.copy(preAlarmMs = 0)
        val m = armed()
        m.handle(AlarmEvent.MotionEscalated(true))
        assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
    }

    @Test fun unlockedNeverAlarms() {
        val m = armed()
        assertThat(m.handle(AlarmEvent.MotionEscalated(locked = false))).isEmpty()
        assertThat(m.state).isEqualTo(AlarmState.Armed())
    }

    @Test fun replugDoesNotCancelAMotionPreAlarm() {
        val m = armed()
        m.handle(AlarmEvent.MotionEscalated(true))
        assertThat(m.handle(AlarmEvent.PowerConnected(clock.now()))).isEmpty()
        assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
    }

    @Test fun unlockEndsAMotionPreAlarm() {
        val m = armed()
        m.handle(AlarmEvent.MotionEscalated(true))
        m.handle(AlarmEvent.UserPresent)
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
    }

    @Test fun motionPreAlarmRunsOutIntoTheSiren() {
        val m = armed()
        m.handle(AlarmEvent.MotionEscalated(true))
        clock.advance(MIN_MOTION_PRE_ALARM_MS)
        assertThat(m.handle(AlarmEvent.PreAlarmElapsed)).containsExactly(AlarmEffect.StartSiren)
    }

    @Test fun ignoredWhenNotArmedOrWaitingForPower() {
        assertThat(armed(AlarmState.Disarmed).handle(AlarmEvent.MotionEscalated(true))).isEmpty()
        assertThat(armed(AlarmState.Arming(0, 5_000)).handle(AlarmEvent.MotionEscalated(true))).isEmpty()
        assertThat(armed(AlarmState.Armed(awaitingPower = true)).handle(AlarmEvent.MotionEscalated(true))).isEmpty()
    }

    @Test fun escalationDuringTheUnplugWindowCancelsTheWindow() {
        val m = armed(AlarmState.Armed(unplugPendingSince = clock.now()))
        assertThat(m.handle(AlarmEvent.MotionEscalated(true)).first()).isEqualTo(AlarmEffect.CancelWindowTimer)
    }

    @Test fun motionLabelSaysUnlock() {
        assertThat(stateLabel(AlarmState.PreAlarm("i", 0, 5_000, IncidentCause.MOTION), 0)).isEqualTo("Unlock to stop (5 s)")
    }
}
