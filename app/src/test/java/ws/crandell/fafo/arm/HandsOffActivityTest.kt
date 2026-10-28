package ws.crandell.fafo.arm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.core.IncidentCause
import ws.crandell.fafo.core.ThreatLevel

/** HANDS OFF (device feedback 2026-10-28): wakes over the lock screen, closes once the warning is over. */
@RunWith(AndroidJUnit4::class)
class HandsOffActivityTest {
    @After fun tearDown() {
        AlarmStatusBus.publish(AlarmState.Disarmed)
        AlarmStatusBus.setThreat(ThreatLevel.GREEN)
    }

    @Test fun showsOverTheLockScreenWhileYellowThenClosesWhenGreen() {
        AlarmStatusBus.publish(AlarmState.Armed())
        AlarmStatusBus.setThreat(ThreatLevel.YELLOW)
        val c = Robolectric.buildActivity(HandsOffActivity::class.java).setup()
        val activity = c.get()
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isFalse()
        assertThat(HandsOffActivity.showing).isTrue()
        val sa = shadowOf(activity)
        assertThat(sa.getShowWhenLocked()).isTrue()
        assertThat(sa.getTurnScreenOn()).isTrue()
        AlarmStatusBus.setThreat(ThreatLevel.GREEN)
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isTrue()
    }

    @Test fun staysThroughThePreAlarmAndClosesOnDisarm() {
        AlarmStatusBus.publish(AlarmState.Armed())
        AlarmStatusBus.setThreat(ThreatLevel.ORANGE)
        val activity = Robolectric.buildActivity(HandsOffActivity::class.java).setup().get()
        AlarmStatusBus.publish(AlarmState.PreAlarm("i", 0, 10_000, IncidentCause.MOTION))
        AlarmStatusBus.setThreat(ThreatLevel.GREEN)
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isFalse()
        AlarmStatusBus.publish(AlarmState.Disarmed)
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isTrue()
    }

    @Test fun timesOutAfterTheBumpResetTimeEvenWithoutAChange() {
        AlarmStatusBus.publish(AlarmState.Armed())
        AlarmStatusBus.setThreat(ThreatLevel.YELLOW)
        val activity = Robolectric.buildActivity(HandsOffActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isFalse()
        // Default bump reset time (15 s) plus slack, by the looper's clock (DataStore read runs for real).
        val end = System.currentTimeMillis() + 5_000
        while (!activity.isFinishing && System.currentTimeMillis() < end) {
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
            Thread.sleep(10)
        }
        assertThat(activity.isFinishing).isTrue()
    }
}
