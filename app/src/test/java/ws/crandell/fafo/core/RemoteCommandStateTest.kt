package ws.crandell.fafo.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.core.AlarmEvent.ArmRequested
import ws.crandell.fafo.core.AlarmEvent.PowerConnected
import ws.crandell.fafo.core.AlarmEvent.PowerDisconnected
import ws.crandell.fafo.core.AlarmEvent.RemoteDisarm
import ws.crandell.fafo.core.AlarmEvent.RemoteReset
import ws.crandell.fafo.core.AlarmEvent.WindowElapsed

/** Spec section 2, remote reset / disarm (M10 done-checks). */
class RemoteCommandStateTest {
    private val clock = FakeClock()
    private var ids = 0
    private val config = AlarmConfig(armingMs = 0)
    private val m = AlarmStateMachine(clock, { config }, { "inc${++ids}" })

    private fun trigger() {
        m.handle(ArmRequested(plugged = true, secureLock = true))
        m.handle(PowerDisconnected(clock.now()))
        clock.advance(config.sensitivityMs)
        m.handle(WindowElapsed(plugged = false, locked = true))
        assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
    }

    @Test fun resetWhileUnpluggedStaysArmedAwaitingPowerThenRearms() {
        trigger()
        val fx = m.handle(RemoteReset("Register", "home", plugged = false, ceUnlocked = true))
        assertThat(m.state).isEqualTo(AlarmState.Armed(awaitingPower = true))
        assertThat(fx).containsAtLeast(
            AlarmEffect.StopSound,
            AlarmEffect.CloseIncident("inc1", CloseReason.RESET_REMOTE, "Register"),
            AlarmEffect.PersistIncident(active = false),
            AlarmEffect.PersistArmed(armed = true, awaitingPower = true),
            AlarmEffect.RemoteChirp,
        ).inOrder()
        assertThat(fx).doesNotContain(AlarmEffect.StopService)
        assertThat(fx).doesNotContain(AlarmEffect.PersistArmed(armed = false))
        // Unplug detection is paused until power returns.
        assertThat(m.handle(PowerDisconnected(clock.now()))).isEmpty()
        m.handle(PowerConnected(clock.now()))
        assertThat(m.state).isEqualTo(AlarmState.Armed())
        // The next unplug triggers again.
        m.handle(PowerDisconnected(clock.now()))
        clock.advance(config.sensitivityMs)
        m.handle(WindowElapsed(plugged = false, locked = true))
        assertThat(m.state).isEqualTo(AlarmState.PreAlarm("inc2", clock.now(), clock.now() + config.preAlarmMs))
    }

    @Test fun resetWhilePluggedIsPlainArmed() {
        trigger()
        m.handle(AlarmEvent.PreAlarmElapsed.also { clock.advance(config.preAlarmMs) })
        assertThat(m.state).isInstanceOf(AlarmState.Triggered::class.java)
        m.handle(RemoteReset("Register", "home", plugged = true, ceUnlocked = true))
        assertThat(m.state).isEqualTo(AlarmState.Armed())
    }

    @Test fun resetNeverDisarms() {
        trigger()
        m.handle(RemoteReset("Register", "work", plugged = false, ceUnlocked = true))
        assertThat(m.state).isNotEqualTo(AlarmState.Disarmed)
    }

    @Test fun remoteDisarmEndsIncidentAndProtection() {
        trigger()
        val fx = m.handle(RemoteDisarm("Register", "home", ceUnlocked = true))
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).contains(AlarmEffect.CloseIncident("inc1", CloseReason.DISARMED_REMOTE, "Register"))
        assertThat(fx).contains(AlarmEffect.StopService)
    }

    @Test fun refusedBeforeFirstUnlockInIncidentPersisted() {
        val p = AlarmStateMachine(clock, { config }, { "x" }, AlarmState.IncidentPersisted("inc9", 1L))
        assertThat(p.handle(RemoteReset("R", "home", false, ceUnlocked = false))).containsExactly(AlarmEffect.Refused(RefusalReason.BEFORE_FIRST_UNLOCK))
        assertThat(p.handle(RemoteDisarm("R", "home", ceUnlocked = false))).containsExactly(AlarmEffect.Refused(RefusalReason.BEFORE_FIRST_UNLOCK))
        assertThat(p.state).isEqualTo(AlarmState.IncidentPersisted("inc9", 1L))
    }

    @Test fun resetWithoutIncidentRefused() {
        m.handle(ArmRequested(plugged = true, secureLock = true))
        assertThat(m.handle(RemoteReset("R", "home", true, true))).containsExactly(AlarmEffect.Refused(RefusalReason.NO_INCIDENT))
        assertThat(m.state).isEqualTo(AlarmState.Armed())
    }
}
