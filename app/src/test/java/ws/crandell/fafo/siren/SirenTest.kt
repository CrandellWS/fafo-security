package ws.crandell.fafo.siren

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import ws.crandell.fafo.arm.ChirpKind
import ws.crandell.fafo.arm.apply
import ws.crandell.fafo.core.AlarmConfig
import ws.crandell.fafo.core.AlarmEvent
import ws.crandell.fafo.core.AlarmStateMachine
import ws.crandell.fafo.core.FakeClock
import ws.crandell.fafo.core.PreAlarmSchedule

class FakeAudioBackend : AudioBackend {
    val log = mutableListOf<String>()
    var looping = false
    var volumeMax = false
    var refuseVolume = false
    var focused = false
    override fun playLoop(samples: ShortArray, gain: Float) { looping = true; log += "loop:$gain" }
    override fun playOnce(samples: ShortArray, gain: Float) { log += "once:$gain" }
    override fun stop() { looping = false; log += "stop" }
    override fun requestFocus() { focused = true }
    override fun abandonFocus() { focused = false }
    override fun setAlarmVolumeMax(): Boolean {
        if (refuseVolume) return false
        volumeMax = true
        return true
    }
    override fun isAlarmVolumeMax() = volumeMax
    override fun vibrate(pattern: LongArray, repeat: Boolean) { log += "vibrate:$repeat" }
    override fun cancelVibrate() { log += "vibrateOff" }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SirenTest {
    private val scope = TestScope()
    private val backend = FakeAudioBackend()
    private var refused = false
    private val siren = Siren(backend, scope, onVolumeRefused = { refused = true })
    private val clock = FakeClock()

    private fun machine(preAlarmMs: Long) =
        AlarmStateMachine(clock, { AlarmConfig(sensitivityMs = 100, preAlarmMs = preAlarmMs, armingMs = 0) })

    /** Drive the machine and route its effects into the siren exactly like ArmService does. */
    private fun AlarmStateMachine.send(e: AlarmEvent) = handle(e).forEach { siren.apply(it) }

    private fun AlarmStateMachine.armAndUnplug() {
        send(AlarmEvent.ArmRequested(true, true))
        send(AlarmEvent.PowerDisconnected(clock.now()))
        send(AlarmEvent.WindowElapsed(plugged = false, locked = true))
    }

    @Test fun sirenStartsOnTriggerAtMaxVolumeAndLoops() {
        val m = machine(0)
        m.armAndUnplug()
        assertThat(backend.looping).isTrue()
        assertThat(backend.volumeMax).isTrue()
        assertThat(backend.focused).isTrue()
        assertThat(backend.log).contains("loop:${Siren.SIREN_GAIN}")
        assertThat(siren.mode).isEqualTo(Siren.Mode.SIREN)
    }

    @Test fun sirenStopsOnUserPresent() {
        val m = machine(0)
        m.armAndUnplug()
        m.send(AlarmEvent.UserPresent)
        assertThat(backend.looping).isFalse()
        assertThat(siren.mode).isEqualTo(Siren.Mode.IDLE)
    }

    @Test fun notificationDismissAndReconnectDoNotStopSiren() {
        val m = machine(0)
        m.armAndUnplug()
        // A dismiss only re-posts the notification: no event reaches the machine. Replugging is an event.
        m.send(AlarmEvent.PowerConnected(clock.now()))
        assertThat(backend.looping).isTrue()
    }

    @Test fun preAlarmBeepsFollowScheduleAndStopOnPowerConnected() {
        val m = machine(5000)
        m.armAndUnplug()
        scope.runCurrent()
        assertThat(siren.mode).isEqualTo(Siren.Mode.PRE_ALARM)
        scope.advanceTimeBy(3000)
        scope.runCurrent()
        val expected = PreAlarmSchedule.beepOffsets(5000).filter { it <= 3000 }
        assertThat(siren.beepLog).isEqualTo(expected)
        assertThat(backend.log.filter { it == "once:${Siren.PRE_ALARM_GAIN}" }).hasSize(expected.size)
        assertThat(Siren.PRE_ALARM_GAIN).isLessThan(Siren.SIREN_GAIN)

        m.send(AlarmEvent.PowerConnected(clock.now()))
        val count = siren.beepLog.size
        scope.advanceTimeBy(5000)
        scope.runCurrent()
        assertThat(siren.beepLog.size).isEqualTo(count)
        assertThat(siren.mode).isEqualTo(Siren.Mode.IDLE)
    }

    @Test fun preAlarmThenFullSiren() {
        val m = machine(1000)
        m.armAndUnplug()
        scope.advanceTimeBy(999)
        scope.runCurrent()
        clock.advance(1000)
        m.send(AlarmEvent.PreAlarmElapsed)
        assertThat(siren.mode).isEqualTo(Siren.Mode.SIREN)
        scope.advanceTimeBy(5000)
        scope.runCurrent()
        // No pre-alarm beeps continue under the siren.
        assertThat(siren.beepLog).isNotEmpty()
        assertThat(siren.beepLog.all { it < 1000L }).isTrue()
    }

    @Test fun volumeWatchdogRestoresAlarmVolume() {
        val m = machine(0)
        m.armAndUnplug()
        backend.volumeMax = false // user pressed volume down
        scope.advanceTimeBy(Siren.WATCHDOG_MS + 10)
        scope.runCurrent()
        assertThat(backend.volumeMax).isTrue()
        siren.stop()
    }

    @Test fun volumeRefusedUnderDndIsReported() {
        backend.refuseVolume = true
        siren.startSiren()
        assertThat(refused).isTrue()
        assertThat(backend.looping).isTrue() // the siren still plays
        siren.stop()
    }

    @Test fun chirpsAreQuieterAndSilencedUnderSiren() {
        siren.chirp(ChirpKind.BUMP)
        siren.chirp(ChirpKind.PICKUP)
        assertThat(backend.log).containsAtLeast("once:${Siren.CHIRP_GAIN}", "once:${Siren.PICKUP_GAIN}")
        assertThat(Siren.PICKUP_GAIN).isGreaterThan(Siren.CHIRP_GAIN)
        assertThat(Siren.PICKUP_GAIN).isLessThan(Siren.SIREN_GAIN)
        siren.startSiren()
        val before = backend.log.size
        siren.chirp(ChirpKind.BUMP)
        assertThat(backend.log.size).isEqualTo(before)
        siren.stop()
    }

    @Test fun tonesAreGenerated() {
        for (name in listOf("Klaxon", "Wail", "Pulse")) assertThat(Tones.siren(name).size).isGreaterThan(1000)
        assertThat(Tones.durationMs(Tones.chirpTriple())).isGreaterThan(Tones.durationMs(Tones.chirpShort()))
    }
}
