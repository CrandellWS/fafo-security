package ws.crandell.fafo.arm

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.AlarmStatusBus

/** Records pin / unpin; Robolectric has no lock task mode. */
class FakeScreenPin : ScreenPin {
    var pinned = false
    var pins = 0
    var unpins = 0
    override fun isPinned(context: Context) = pinned
    override fun pin(activity: Activity) { pins++; pinned = true }
    override fun unpin(activity: Activity) { unpins++; pinned = false }
}

/** Pin app while armed (round 5, spec F14) on the status screen. */
@RunWith(AndroidJUnit4::class)
class ScreenPinActivityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pin = FakeScreenPin()

    @Before fun setUp() { ArmDeps.screenPin = pin }

    @After fun tearDown() {
        AlarmStatusBus.publish(AlarmState.Disarmed)
        ArmDeps.reset()
    }

    private fun open(pinRequest: Boolean) = Robolectric.buildActivity(
        AlarmActivity::class.java,
        if (pinRequest) AlarmActivity.pinIntent(context) else android.content.Intent(context, AlarmActivity::class.java),
    ).setup().get()

    @Test fun armTapPinsOnceFafoIsArming() {
        // The Arm tap opens the screen before the service publishes ARMING: the request waits.
        AlarmStatusBus.publish(AlarmState.Disarmed)
        val activity = open(pinRequest = true)
        ShadowLooper.idleMainLooper()
        assertThat(pin.pins).isEqualTo(0)
        AlarmStatusBus.publish(AlarmState.Arming(0, 5_000))
        ShadowLooper.idleMainLooper()
        assertThat(pin.pins).isEqualTo(1)
        assertThat(activity.isPinnedState).isTrue()
        AlarmStatusBus.publish(AlarmState.Armed())
        ShadowLooper.idleMainLooper()
        assertThat(pin.pins).isEqualTo(1)
    }

    @Test fun neverPinsWithoutTheArmTap() {
        AlarmStatusBus.publish(AlarmState.Armed())
        open(pinRequest = false)
        ShadowLooper.idleMainLooper()
        assertThat(pin.pins).isEqualTo(0)
    }

    @Test fun neverPinsWhenAnAlarmCameFirst() {
        AlarmStatusBus.publish(AlarmState.Triggered("i", 0))
        open(pinRequest = true)
        ShadowLooper.idleMainLooper()
        AlarmStatusBus.publish(AlarmState.Armed())
        ShadowLooper.idleMainLooper()
        assertThat(pin.pins).isEqualTo(0)
    }

    @Test fun disarmUnpinsAndCloses() {
        AlarmStatusBus.publish(AlarmState.Armed())
        val activity = open(pinRequest = true)
        ShadowLooper.idleMainLooper()
        assertThat(pin.pinned).isTrue()
        AlarmStatusBus.publish(AlarmState.Disarmed)
        ShadowLooper.idleMainLooper()
        assertThat(pin.unpins).isEqualTo(1)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test fun pinnedScreenStaysThroughARemoteResetAndUnpinKeepsItArmed() {
        AlarmStatusBus.publish(AlarmState.Armed())
        val activity = open(pinRequest = true)
        ShadowLooper.idleMainLooper()
        AlarmStatusBus.publish(AlarmState.Triggered("i", 0))
        ShadowLooper.idleMainLooper()
        AlarmStatusBus.publish(AlarmState.Armed(awaitingPower = true))
        ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isFalse()
        activity.unpin()
        assertThat(pin.pinned).isFalse()
        assertThat(activity.isPinnedState).isFalse()
        assertThat(activity.isFinishing).isFalse()
    }

    @Test fun disarmWhilePinnedEndsThePinFirst() {
        AlarmStatusBus.publish(AlarmState.Armed())
        val activity = open(pinRequest = true)
        ShadowLooper.idleMainLooper()
        activity.requestDisarm()
        assertThat(pin.unpins).isEqualTo(1)
    }
}
