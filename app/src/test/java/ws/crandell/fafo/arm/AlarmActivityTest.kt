package ws.crandell.fafo.arm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.AlarmStatusBus

/** The full-screen alarm screen opens by itself on every alarm, so it must close once the alarm ends. */
@RunWith(AndroidJUnit4::class)
class AlarmActivityTest {
    @After fun tearDown() = AlarmStatusBus.publish(AlarmState.Disarmed)

    @Test fun closesWhenTheAlarmEnds() {
        AlarmStatusBus.publish(AlarmState.Triggered("inc", 1L))
        val activity = Robolectric.buildActivity(AlarmActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isFalse()
        AlarmStatusBus.publish(AlarmState.Disarmed)
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isTrue()
    }

    @Test fun staysOpenUntilAnAlarmWasSeen() {
        // A tap in a fresh process: the bus still says Disarmed until the service publishes its state.
        AlarmStatusBus.publish(AlarmState.Disarmed)
        val activity = Robolectric.buildActivity(AlarmActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isFalse()
        AlarmStatusBus.publish(AlarmState.IncidentPersisted("inc", 1L))
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isFalse()
        AlarmStatusBus.publish(AlarmState.Armed(awaitingPower = true))
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isTrue()
    }
}
