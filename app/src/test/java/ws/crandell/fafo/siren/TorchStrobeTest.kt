package ws.crandell.fafo.siren

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ws.crandell.fafo.ui.status.LightLevel

class FakeTorch(override var available: Boolean = true) : TorchBackend {
    val calls = mutableListOf<Boolean>()
    var on = false
    var busy = false
    override fun set(on: Boolean): Boolean {
        if (busy) return false
        calls += on
        this.on = on
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TorchStrobeTest {
    private fun TestScope.strobe(t: FakeTorch, enabled: Boolean = true) = TorchStrobe(backgroundScope, t) { enabled }

    @Test fun alarmStrobesAboutFourTimesASecond() = runTest {
        val t = FakeTorch(); val s = strobe(t)
        s.setLevel(LightLevel.ALARM)
        advanceTimeBy(990); runCurrent()
        assertThat(t.calls.count { it }).isEqualTo(4) // 250 ms cycle
    }

    @Test fun warnBlinksSlowly() = runTest {
        val t = FakeTorch(); val s = strobe(t)
        s.setLevel(LightLevel.WARN)
        advanceTimeBy(2_990); runCurrent()
        assertThat(t.calls.count { it }).isEqualTo(2) // 1.5 s cycle
    }

    @Test fun disabledSettingNeverTouchesTheTorch() = runTest {
        val t = FakeTorch(); val s = strobe(t, enabled = false)
        s.setLevel(LightLevel.ALARM)
        advanceTimeBy(2_000)
        assertThat(t.calls).isEmpty()
    }

    @Test fun noFlashNeverTouchesTheTorch() = runTest {
        val t = FakeTorch(available = false); val s = strobe(t)
        s.setLevel(LightLevel.ALARM)
        advanceTimeBy(2_000)
        assertThat(t.calls).isEmpty()
    }

    @Test fun stopLeavesItOff() = runTest {
        val t = FakeTorch(); val s = strobe(t)
        s.setLevel(LightLevel.ALARM)
        advanceTimeBy(50); runCurrent()
        assertThat(t.on).isTrue()
        s.stop()
        assertThat(t.on).isFalse()
        advanceTimeBy(1_000)
        assertThat(t.on).isFalse()
    }

    @Test fun calmStateNeverSwitchesOffTheOwnersFlashlight() = runTest {
        val t = FakeTorch(); val s = strobe(t)
        s.setLevel(LightLevel.OFF)
        s.stop()
        assertThat(t.calls).isEmpty()
    }

    @Test fun pausesAroundAPhoto() = runTest {
        val t = FakeTorch(); val s = strobe(t)
        s.setLevel(LightLevel.ALARM)
        advanceTimeBy(10); runCurrent()
        val pause = backgroundScope.launch { s.pauseForCapture() }
        runCurrent()
        assertThat(t.on).isFalse()
        val before = t.calls.count { it }
        advanceTimeBy(1_000); runCurrent()
        assertThat(t.calls.count { it }).isEqualTo(before) // no flashes while the camera shoots
        assertThat(pause.isCompleted).isTrue()
        s.resumeAfterCapture()
        advanceTimeBy(500); runCurrent()
        assertThat(t.calls.count { it }).isGreaterThan(before)
    }

    @Test fun unavailableThenAvailableResumes() = runTest {
        val t = FakeTorch(); val s = strobe(t)
        t.busy = true
        s.setLevel(LightLevel.ALARM)
        advanceTimeBy(600); runCurrent()
        assertThat(t.calls).isEmpty()
        assertThat(s.failures).isGreaterThan(0)
        t.busy = false
        advanceTimeBy(600); runCurrent()
        assertThat(t.calls.count { it }).isAtLeast(2)
    }

    @Test fun levelChangeSwitchesPattern() = runTest {
        val t = FakeTorch(); val s = strobe(t)
        s.setLevel(LightLevel.WARN)
        s.setLevel(LightLevel.ALARM)
        advanceTimeBy(990); runCurrent()
        assertThat(t.calls.count { it }).isEqualTo(4)
        s.setLevel(LightLevel.OFF)
        assertThat(t.on).isFalse()
    }
}
