package ws.crandell.fafo.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.core.AlarmEffect.CancelArmingTimer
import ws.crandell.fafo.core.AlarmEffect.PersistArmed
import ws.crandell.fafo.core.AlarmEffect.RecordArmedAt
import ws.crandell.fafo.core.AlarmEffect.Refused
import ws.crandell.fafo.core.AlarmEffect.StartArmingTimer
import ws.crandell.fafo.core.AlarmEffect.StopService
import ws.crandell.fafo.core.AlarmEvent.ArmCancelled
import ws.crandell.fafo.core.AlarmEvent.ArmRequested
import ws.crandell.fafo.core.AlarmEvent.ArmingElapsed

/** Bill 2026-10-28: a yellow ARMING countdown, cancellable, before ARMED. */
class ArmingTest {
    private val clock = FakeClock()
    private var config = AlarmConfig(armingMs = 5_000)
    private fun machine(initial: AlarmState = AlarmState.Disarmed) = AlarmStateMachine(clock, { config }, { "inc" }, initial)

    private fun arming(): AlarmStateMachine = machine().also { it.handle(ArmRequested(plugged = true, secureLock = true)) }

    @Test fun armGoesToArmingWithATimer() {
        val m = machine()
        val fx = m.handle(ArmRequested(plugged = true, secureLock = true))
        val now = clock.now()
        assertThat(m.state).isEqualTo(AlarmState.Arming(now, now + 5_000))
        assertThat(fx).containsExactly(StartArmingTimer(5_000, now + 5_000))
        assertThat(m.state.wireName).isEqualTo("ARMING")
        assertThat(m.state.isAlarming).isFalse()
    }

    @Test fun armingIsNeverPersisted() {
        val fx = machine().handle(ArmRequested(plugged = true, secureLock = true))
        assertThat(fx.filterIsInstance<PersistArmed>()).isEmpty()
    }

    @Test fun elapsedGoesToArmedAndRecordsTheArmTime() {
        val m = arming()
        clock.advance(5_000)
        val fx = m.handle(ArmingElapsed(plugged = true, secureLock = true))
        assertThat(m.state).isEqualTo(AlarmState.Armed())
        assertThat(fx).containsExactly(PersistArmed(true, false), RecordArmedAt).inOrder()
    }

    @Test fun earlyTimerIsIgnored() {
        val m = arming()
        clock.advance(4_000)
        assertThat(m.handle(ArmingElapsed(true, true))).isEmpty()
        assertThat(m.state).isInstanceOf(AlarmState.Arming::class.java)
    }

    @Test fun cancelGoesToDisarmedWithoutAuth() {
        val m = arming()
        val fx = m.handle(ArmCancelled)
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsExactly(CancelArmingTimer, StopService).inOrder()
    }

    @Test fun disarmTapDuringArmingIsACancel() {
        val m = arming()
        assertThat(m.handle(AlarmEvent.DisarmTapped)).containsExactly(CancelArmingTimer, StopService)
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
    }

    @Test fun unplugDuringArmingCancelsWithoutAnIncident() {
        val m = arming()
        val fx = m.handle(AlarmEvent.PowerDisconnected(clock.now()))
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsExactly(CancelArmingTimer, Refused(RefusalReason.NOT_PLUGGED), StopService)
        assertThat(fx.filterIsInstance<AlarmEffect.PersistIncident>()).isEmpty()
        assertThat(fx).doesNotContain(AlarmEffect.StartSiren)
    }

    @Test fun elapsedWhileUnpluggedIsRefused() {
        val m = arming()
        clock.advance(5_000)
        val fx = m.handle(ArmingElapsed(plugged = false, secureLock = true))
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsExactly(Refused(RefusalReason.NOT_PLUGGED), StopService)
    }

    @Test fun elapsedWithoutSecureLockIsRefused() {
        val m = arming()
        clock.advance(5_000)
        val fx = m.handle(ArmingElapsed(plugged = true, secureLock = false))
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsExactly(Refused(RefusalReason.NO_SECURE_LOCK), StopService)
    }

    @Test fun rebootDuringArmingEndsDisarmed() {
        // ARMING is never persisted, so the boot path sees armed=false.
        val m = machine()
        val fx = m.handle(AlarmEvent.BootCompleted(false, false, null, null, false, plugged = true, locked = true))
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsExactly(StopService)
    }

    @Test fun remoteCommandsRefusedDuringArming() {
        val m = arming()
        assertThat(m.handle(AlarmEvent.RemoteDisarm("Pixel", "home", true))).containsExactly(Refused(RefusalReason.WRONG_STATE))
        assertThat(m.handle(AlarmEvent.RemoteReset("Pixel", "home", true, true))).containsExactly(Refused(RefusalReason.WRONG_STATE))
        assertThat(m.state).isInstanceOf(AlarmState.Arming::class.java)
    }

    @Test fun unlockAndSecondArmIgnoredDuringArming() {
        val m = arming()
        assertThat(m.handle(AlarmEvent.UserPresent)).isEmpty()
        assertThat(m.handle(ArmRequested(true, true))).isEmpty()
        assertThat(m.handle(AlarmEvent.PowerConnected(clock.now()))).isEmpty()
        assertThat(m.state).isInstanceOf(AlarmState.Arming::class.java)
    }

    @Test fun cancelOutsideArmingDoesNothing() {
        assertThat(machine().handle(ArmCancelled)).isEmpty()
        assertThat(machine(AlarmState.Armed()).handle(ArmCancelled)).isEmpty()
    }

    @Test fun zeroArmingArmsAtOnce() {
        config = AlarmConfig(armingMs = 0)
        val m = machine()
        assertThat(m.handle(ArmRequested(true, true))).containsExactly(PersistArmed(true, false), RecordArmedAt)
        assertThat(m.state).isEqualTo(AlarmState.Armed())
    }

    @Test fun armingLabelCountsDown() {
        val s = AlarmState.Arming(0, 5_000)
        assertThat(stateLabel(s, 1)).isEqualTo("Arming (5 s)")
        assertThat(stateLabel(s, 4_100)).isEqualTo("Arming (1 s)")
    }
}
