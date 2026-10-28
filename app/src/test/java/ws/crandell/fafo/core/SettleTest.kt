package ws.crandell.fafo.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Device feedback 2026-10-28 (Bill, A17): picked up, set down and left alone, the phone still alarmed about
 * 3 s later. A MOTION pre-alarm now cancels back to ARMED once the phone is still for the settle window and
 * still plugged in; a pickup that keeps moving, or an unplug, still alarms.
 */
class SettleTest {
    private val clock = FakeClock()
    private var config = AlarmConfig(preAlarmMs = 10_000, armingMs = 0)
    private fun machine(initial: AlarmState = AlarmState.Armed()) = AlarmStateMachine(clock, { config }, { "m1" }, initial)

    private fun motionPreAlarm(): AlarmStateMachine = machine().also {
        it.handle(AlarmEvent.MotionEscalated(locked = true))
        assertThat((it.state as AlarmState.PreAlarm).cause).isEqualTo(IncidentCause.MOTION)
    }

    // --- the state machine ---

    @Test fun settledWhilePluggedCancelsBackToArmed() {
        val m = motionPreAlarm()
        val fx = m.handle(AlarmEvent.MotionSettled(plugged = true))
        assertThat(m.state).isEqualTo(AlarmState.Armed())
        assertThat(fx).containsExactly(
            AlarmEffect.StopSound,
            AlarmEffect.StopPhotos,
            AlarmEffect.StopAlerts,
            AlarmEffect.CloseIncident("m1", CloseReason.SETTLED),
            AlarmEffect.PersistIncident(active = false),
        ).inOrder()
        // Still armed: no PersistArmed(false), no StopService.
        assertThat(fx).doesNotContain(AlarmEffect.StopService)
    }

    @Test fun settledWhileUnpluggedStillAlarms() {
        val m = motionPreAlarm()
        assertThat(m.handle(AlarmEvent.MotionSettled(plugged = false))).isEmpty()
        assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
        clock.advance(10_000)
        assertThat(m.handle(AlarmEvent.PreAlarmElapsed)).containsExactly(AlarmEffect.StartSiren)
        assertThat(m.state).isInstanceOf(AlarmState.Triggered::class.java)
    }

    @Test fun keptMovingNeverSettlesAndRunsIntoTheSiren() {
        val m = motionPreAlarm()
        clock.advance(10_000)
        m.handle(AlarmEvent.PreAlarmElapsed)
        assertThat(m.state).isInstanceOf(AlarmState.Triggered::class.java)
        // A settle after the siren started changes nothing.
        assertThat(m.handle(AlarmEvent.MotionSettled(plugged = true))).isEmpty()
        assertThat(m.state).isInstanceOf(AlarmState.Triggered::class.java)
    }

    /** Review 2026-10-28: the plugged reading can lag the unplug; an unplug seen by the machine latches. */
    @Test fun unplugDuringMotionPreAlarmLatchesEvenIfThePluggedReadingLags() {
        val m = motionPreAlarm()
        assertThat(m.handle(AlarmEvent.PowerDisconnected(at = clock.now()))).isEmpty()
        assertThat((m.state as AlarmState.PreAlarm).unplugged).isTrue()
        // Stale "plugged" reading, or the thief plugs it back in and holds it still: still no cancel.
        assertThat(m.handle(AlarmEvent.MotionSettled(plugged = true))).isEmpty()
        m.handle(AlarmEvent.PowerConnected(at = clock.now()))
        assertThat(m.handle(AlarmEvent.MotionSettled(plugged = true))).isEmpty()
        clock.t += 10_000
        assertThat(m.handle(AlarmEvent.PreAlarmElapsed)).containsExactly(AlarmEffect.StartSiren)
    }

    @Test fun unpluggedBeforeTheEscalationAlsoLatches() {
        val m = machine()
        m.handle(AlarmEvent.PowerDisconnected(at = clock.now()))
        m.handle(AlarmEvent.MotionEscalated(locked = true))
        val s = m.state as AlarmState.PreAlarm
        assertThat(s.cause).isEqualTo(IncidentCause.MOTION)
        assertThat(s.unplugged).isTrue()
        assertThat(m.handle(AlarmEvent.MotionSettled(plugged = true))).isEmpty()
        assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
    }

    @Test fun unplugPreAlarmIgnoresSettle() {
        val m = machine()
        m.handle(AlarmEvent.PowerDisconnected(0))
        m.handle(AlarmEvent.WindowElapsed(plugged = false, locked = true))
        assertThat((m.state as AlarmState.PreAlarm).cause).isEqualTo(IncidentCause.UNPLUG)
        assertThat(m.handle(AlarmEvent.MotionSettled(plugged = true))).isEmpty()
        assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
    }

    @Test fun settleOutsideAPreAlarmDoesNothing() {
        val m = machine()
        assertThat(m.handle(AlarmEvent.MotionSettled(plugged = true))).isEmpty()
        assertThat(m.state).isEqualTo(AlarmState.Armed())
    }

    @Test fun motionPreAlarmFollowsThePreAlarmSetting() {
        // Bill set 10 s: the motion pre-alarm is 10 s, not the 5 s floor.
        val s = motionPreAlarm().state as AlarmState.PreAlarm
        assertThat(s.endsAt - s.startedAt).isEqualTo(10_000)
    }

    @Test fun afterSettlingAnotherPickupStartsANewPreAlarm() {
        val m = motionPreAlarm()
        m.handle(AlarmEvent.MotionSettled(plugged = true))
        m.handle(AlarmEvent.MotionEscalated(locked = true))
        assertThat(m.state).isInstanceOf(AlarmState.PreAlarm::class.java)
    }

    // --- the settle watch ---

    @Test fun watchFiresAfterTheSettleWindowOfStillSamples() {
        val w = SettleWatch(2_000)
        w.begin()
        val fired = (0L..2_000L step 20).map { w.onSample(it, still = true) }
        assertThat(fired.dropLast(1)).doesNotContain(true)
        assertThat(fired.last()).isTrue()
    }

    @Test fun anyMovementRestartsTheWindow() {
        val w = SettleWatch(2_000)
        w.begin()
        for (t in 0L until 1_900L step 20) assertThat(w.onSample(t, still = true)).isFalse()
        assertThat(w.onSample(1_900, still = false)).isFalse()
        for (t in 1_920L until 3_920L step 20) assertThat(w.onSample(t, still = true)).isFalse()
        assertThat(w.onSample(3_920, still = true)).isTrue()
    }

    @Test fun neverFiresBeforeBeginOrAfterEnd() {
        val w = SettleWatch(2_000)
        for (t in 0L..5_000L step 20) assertThat(w.onSample(t, still = true)).isFalse()
        w.begin()
        w.end()
        for (t in 5_000L..9_000L step 20) assertThat(w.onSample(t, still = true)).isFalse()
    }

    @Test fun stillnessBeforeBeginDoesNotCount() {
        // A bump-escalated pre-alarm on a phone that was already lying still still waits a full window.
        val w = SettleWatch(2_000)
        for (t in 0L..5_000L step 20) w.onSample(t, still = true)
        w.begin()
        assertThat(w.onSample(5_020, still = true)).isFalse()
        assertThat(w.onSample(7_000, still = true)).isFalse()
        assertThat(w.onSample(7_020, still = true)).isTrue()
    }

    @Test fun keepsFiringWhileStillSoAReplugIsPickedUp() {
        val w = SettleWatch(2_000)
        w.begin()
        val fires = (0L..6_000L step 20).count { w.onSample(it, still = true) }
        assertThat(fires).isEqualTo(3)
    }

    @Test fun settleWindowIsShorterThanTheMotionMinimum() {
        assertThat(SettleWatch.SETTLE_MS).isLessThan(MIN_MOTION_PRE_ALARM_MS)
    }

    // --- the ladder after settling ---

    @Test fun ladderStaysOrangeAfterSettlingThenCools() {
        val l = ThreatLadder(coolMsSetting = { 15_000 })
        l.settled(1_000)
        assertThat(l.level).isEqualTo(ThreatLevel.ORANGE)
        assertThat(l.tick(15_999)).isFalse()
        assertThat(l.tick(16_000)).isTrue()
        assertThat(l.level).isEqualTo(ThreatLevel.GREEN)
    }

    @Test fun pickupAfterSettlingEscalatesAgain() {
        val l = ThreatLadder()
        l.settled(0)
        assertThat(l.onMotion(Motion.PICKUP, 1_000, mayAlarm = true)).containsExactly(LadderEffect.Escalate)
    }
}
