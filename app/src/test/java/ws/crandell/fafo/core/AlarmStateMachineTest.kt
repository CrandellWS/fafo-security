package ws.crandell.fafo.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.core.AlarmEffect.CancelWindowTimer
import ws.crandell.fafo.core.AlarmEffect.CloseIncident
import ws.crandell.fafo.core.AlarmEffect.PersistArmed
import ws.crandell.fafo.core.AlarmEffect.PersistIncident
import ws.crandell.fafo.core.AlarmEffect.Refused
import ws.crandell.fafo.core.AlarmEffect.StartPhotos
import ws.crandell.fafo.core.AlarmEffect.StartPreAlarm
import ws.crandell.fafo.core.AlarmEffect.StartSiren
import ws.crandell.fafo.core.AlarmEffect.StartWindowTimer
import ws.crandell.fafo.core.AlarmEffect.StopService
import ws.crandell.fafo.core.AlarmEffect.StopSound
import ws.crandell.fafo.core.AlarmEvent.ArmRequested
import ws.crandell.fafo.core.AlarmEvent.BootCompleted
import ws.crandell.fafo.core.AlarmEvent.PowerConnected
import ws.crandell.fafo.core.AlarmEvent.PowerDisconnected
import ws.crandell.fafo.core.AlarmEvent.WindowElapsed

class FakeClock(var t: Long = 1_000_000L) : Clock {
    override fun now() = t
    fun advance(ms: Long) { t += ms }
}

class AlarmStateMachineTest {
    private val clock = FakeClock()
    private var config = AlarmConfig()
    private var ids = 0
    private fun machine(initial: AlarmState = AlarmState.Disarmed) =
        AlarmStateMachine(clock, { config }, { "inc${++ids}" }, initial)

    private fun armed(m: AlarmStateMachine = machine()): AlarmStateMachine {
        m.handle(ArmRequested(plugged = true, secureLock = true))
        assertThat(m.state).isInstanceOf(AlarmState.Arming::class.java)
        clock.advance(config.armingMs)
        m.handle(AlarmEvent.ArmingElapsed(plugged = true, secureLock = true))
        assertThat(m.state).isEqualTo(AlarmState.Armed())
        return m
    }

    /** Unplug, let the window run out while locked. */
    private fun unplugLocked(m: AlarmStateMachine): List<AlarmEffect> {
        m.handle(PowerDisconnected(clock.now()))
        clock.advance(config.sensitivityMs)
        return m.handle(WindowElapsed(plugged = false, locked = true))
    }

    // --- F1 arming ---

    @Test fun armRefusedWhenUnplugged() {
        val m = machine()
        val fx = m.handle(ArmRequested(plugged = false, secureLock = true))
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsExactly(Refused(RefusalReason.NOT_PLUGGED))
    }

    @Test fun armRefusedWithoutSecureLock() {
        val m = machine()
        val fx = m.handle(ArmRequested(plugged = true, secureLock = false))
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsExactly(Refused(RefusalReason.NO_SECURE_LOCK))
    }

    @Test fun armPersistsArmedFlagAfterArming() {
        val m = machine()
        m.handle(ArmRequested(plugged = true, secureLock = true))
        clock.advance(config.armingMs)
        val fx = m.handle(AlarmEvent.ArmingElapsed(plugged = true, secureLock = true))
        assertThat(fx).containsExactly(PersistArmed(true, false), AlarmEffect.RecordArmedAt).inOrder()
    }

    @Test fun disarmTappedFromArmed() {
        val m = armed()
        val fx = m.handle(AlarmEvent.DisarmTapped)
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsAtLeast(PersistArmed(false), StopService)
    }

    @Test fun unlockAloneDoesNotDisarmByDefault() {
        val m = armed()
        m.handle(AlarmEvent.UserPresent)
        assertThat(m.state).isEqualTo(AlarmState.Armed())
    }

    @Test fun unlockDisarmsWhenSettingOn() {
        config = config.copy(disarmOnUnlock = true)
        val m = armed()
        m.handle(AlarmEvent.UserPresent)
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
    }

    // --- F2 unplug + window, every sensitivity ---

    @Test fun everySensitivityStartsItsWindowAndTriggersAfter() {
        for (s in Defaults.SENSITIVITY_CHOICES_MS) {
            config = AlarmConfig(sensitivityMs = s)
            val m = armed()
            val fx = m.handle(PowerDisconnected(clock.now()))
            assertThat(fx).containsExactly(StartWindowTimer(s))
            clock.advance(s)
            m.handle(WindowElapsed(plugged = false, locked = true))
            assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
        }
    }

    @Test fun reconnectInsideWindowCancels() {
        for (s in Defaults.SENSITIVITY_CHOICES_MS) {
            config = AlarmConfig(sensitivityMs = s)
            val m = armed()
            m.handle(PowerDisconnected(clock.now()))
            clock.advance(s / 2)
            val fx = m.handle(PowerConnected(clock.now()))
            assertThat(fx).containsExactly(CancelWindowTimer)
            assertThat(m.state).isEqualTo(AlarmState.Armed())
            // A stale timer firing afterwards does nothing.
            m.handle(WindowElapsed(plugged = false, locked = true))
            assertThat(m.state).isEqualTo(AlarmState.Armed())
        }
    }

    @Test fun windowElapsedButPluggedAgainDoesNotTrigger() {
        val m = armed()
        m.handle(PowerDisconnected(clock.now()))
        m.handle(WindowElapsed(plugged = true, locked = true))
        assertThat(m.state).isEqualTo(AlarmState.Armed())
    }

    @Test fun unplugWhileUnlockedWarnsInstead() {
        val m = armed()
        m.handle(PowerDisconnected(clock.now()))
        val fx = m.handle(WindowElapsed(plugged = false, locked = false))
        assertThat(fx).containsExactly(AlarmEffect.StillArmedWarning)
        assertThat(m.state).isEqualTo(AlarmState.Armed())
    }

    @Test fun incidentPersistedBeforeAnySound() {
        val m = armed()
        val fx = unplugLocked(m)
        val persistIdx = fx.indexOfFirst { it is PersistIncident && it.active }
        val soundIdx = fx.indexOfFirst { it is StartPreAlarm || it == StartSiren }
        assertThat(persistIdx).isAtLeast(0)
        assertThat(persistIdx).isLessThan(soundIdx)
        assertThat(fx).contains(StartPhotos) // first photo at the start of PRE_ALARM
    }

    @Test fun preAlarmZeroGoesStraightToTriggered() {
        config = AlarmConfig(preAlarmMs = 0)
        val m = armed()
        val fx = unplugLocked(m)
        assertThat(m.state).isInstanceOf(AlarmState.Triggered::class.java)
        assertThat(fx.indexOfFirst { it is PersistIncident }).isLessThan(fx.indexOf(StartSiren))
        assertThat(fx).contains(StartPhotos)
        assertThat(fx.none { it is StartPreAlarm }).isTrue()
    }

    @Test fun countdownReachesTriggeredAtExactlyTheChosenDuration() {
        for (sec in Defaults.PRE_ALARM_CHOICES_S.filter { it > 0 }) {
            config = AlarmConfig(preAlarmMs = sec * 1000L)
            val m = armed()
            unplugLocked(m)
            val start = clock.now()
            clock.advance(sec * 1000L - 1)
            m.handle(AlarmEvent.PreAlarmElapsed)
            assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
            clock.t = start + sec * 1000L
            val fx = m.handle(AlarmEvent.PreAlarmElapsed)
            assertThat(m.state).isInstanceOf(AlarmState.Triggered::class.java)
            assertThat(fx).containsExactly(StartSiren)
        }
    }

    @Test fun reconnectDuringPreAlarmReturnsToArmed() {
        val m = armed()
        unplugLocked(m)
        clock.advance(1000)
        val fx = m.handle(PowerConnected(clock.now()))
        assertThat(m.state).isEqualTo(AlarmState.Armed())
        assertThat(fx).containsAtLeast(StopSound, CloseIncident("inc1", CloseReason.CANCELLED), PersistIncident(false))
    }

    @Test fun userPresentCancelsPreAlarmAtAnyPoint() {
        for (at in listOf(0L, 1000L, 4999L)) {
            val m = armed()
            unplugLocked(m)
            clock.advance(at)
            val fx = m.handle(AlarmEvent.UserPresent)
            assertThat(m.state).isEqualTo(AlarmState.Disarmed)
            assertThat(fx).contains(StopSound)
        }
    }

    @Test fun reconnectDuringTriggeredKeepsSiren() {
        config = AlarmConfig(preAlarmMs = 0)
        val m = armed()
        unplugLocked(m)
        val fx = m.handle(PowerConnected(clock.now()))
        assertThat(fx).isEmpty()
        assertThat(m.state).isInstanceOf(AlarmState.Triggered::class.java)
    }

    @Test fun unlockStopsTriggered() {
        config = AlarmConfig(preAlarmMs = 0)
        val m = armed()
        unplugLocked(m)
        val fx = m.handle(AlarmEvent.UserPresent)
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsAtLeast(StopSound, PersistIncident(false), PersistArmed(false), StopService)
    }

    // --- Boot ---

    @Test fun bootWithActiveIncidentResumesSirenNoCamera() {
        val m = machine()
        val fx = m.handle(BootCompleted(true, true, "x", 5L, false, plugged = true, locked = true))
        assertThat(m.state).isEqualTo(AlarmState.IncidentPersisted("x", 5L))
        assertThat(fx).containsExactly(StartSiren)
        assertThat(fx).doesNotContain(StartPhotos)
        assertThat(fx).doesNotContain(AlarmEffect.AddCameraType)
    }

    @Test fun bootWithNothingSetDoesNothing() {
        val m = machine()
        val fx = m.handle(BootCompleted(false, false, null, null, false, plugged = true, locked = true))
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
        assertThat(fx).containsExactly(StopService)
    }

    @Test fun bootArmedPluggedResumesArmed() {
        val m = machine()
        val fx = m.handle(BootCompleted(true, false, null, null, false, plugged = true, locked = true))
        assertThat(m.state).isEqualTo(AlarmState.Armed())
        assertThat(fx).isEmpty()
    }

    @Test fun bootArmedUnpluggedAndLockedRunsWindow() {
        val m = machine()
        val fx = m.handle(BootCompleted(true, false, null, null, false, plugged = false, locked = true))
        assertThat(fx).containsExactly(StartWindowTimer(config.sensitivityMs))
        m.handle(WindowElapsed(plugged = false, locked = true))
        assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
    }

    @Test fun secondBootBroadcastIsIgnored() {
        val m = machine()
        m.handle(BootCompleted(true, true, "x", 5L, false, plugged = false, locked = true))
        val fx = m.handle(BootCompleted(true, true, "x", 5L, false, plugged = false, locked = true))
        assertThat(fx).isEmpty()
    }

    @Test fun incidentPersistedEndsOnUnlockOnly() {
        val m = machine()
        m.handle(BootCompleted(true, true, "x", 5L, false, plugged = false, locked = true))
        assertThat(m.handle(PowerConnected(clock.now()))).isEmpty()
        m.handle(AlarmEvent.UserPresent)
        assertThat(m.state).isEqualTo(AlarmState.Disarmed)
    }

    @Test fun notificationTapAddsCameraOnlyWhenCeUnlocked() {
        val m = machine()
        m.handle(BootCompleted(true, true, "x", 5L, false, plugged = false, locked = true))
        assertThat(m.handle(AlarmEvent.NotificationTapped(ceUnlocked = false))).isEmpty()
        assertThat(m.handle(AlarmEvent.NotificationTapped(ceUnlocked = true)))
            .containsExactly(AlarmEffect.AddCameraType, StartPhotos)
    }

    @Test fun motionAndArmIgnoredWhileAlarming() {
        val m = armed()
        unplugLocked(m)
        assertThat(m.handle(ArmRequested(true, true))).isEmpty()
    }
}
