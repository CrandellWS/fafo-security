package ws.crandell.fafo.quick

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.Member
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.intercom.IntercomUi
import ws.crandell.fafo.ui.screens.TalkActions
import ws.crandell.fafo.ui.screens.TalkScreen
import ws.crandell.fafo.ui.theme.FafoTheme
import ws.crandell.fafo.ui.theme.ThemeMode

/** Round 5, spec F13: the lock-screen Talk screen is push-to-talk only. */
@RunWith(AndroidJUnit4::class)
class TalkScreenTest {
    @get:Rule val rule = createComposeRule()

    private val home = Circle(
        "home", "Home", Permission.entries.toSet(),
        listOf(Member(PIXEL, "Bill's Pixel", setOf(Permission.INTERCOM))), builtIn = true,
    )

    private class Recorder : TalkActions {
        val calls = mutableListOf<String>()
        override fun talkStart(targets: List<String>, circleId: String) { calls += "start:$circleId:${targets.joinToString()}" }
        override fun talkStop() { calls += "stop" }
        override fun allowMic() { calls += "mic" }
        override fun openApp() { calls += "open" }
        override fun close() { calls += "close" }
    }

    private fun show(
        actions: TalkActions, circles: List<Circle> = listOf(home), ceLocked: Boolean = false,
        mic: Boolean = true, alarming: Boolean = false,
    ) = rule.setContent {
        FafoTheme(ThemeMode.DARK) { TalkScreen(IntercomUi(), circles, setOf(PIXEL), alarming, ceLocked, mic, actions) }
    }

    @Test fun holdToTalkSendsToTheCircleAndNothingCanDisarm() {
        val r = Recorder()
        show(r)
        rule.onNodeWithTag("talkButton").performTouchInput { down(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("talkButton").performTouchInput { up() }
        rule.waitForIdle()
        assertThat(r.calls).containsExactly("start:home:$PIXEL", "stop").inOrder()
        assertThat(rule.onAllNodesWithText("Disarm").fetchSemanticsNodes()).isEmpty()
        assertThat(rule.onAllNodesWithText("Room mode", substring = true).fetchSemanticsNodes()).isEmpty()
        assertThat(rule.onAllNodesWithText("Listen", substring = true).fetchSemanticsNodes()).isEmpty()
    }

    @Test fun beforeTheFirstUnlockItOnlySaysSo() {
        show(Recorder(), circles = emptyList(), ceLocked = true)
        rule.onNodeWithTag("talkCeLocked").assertIsDisplayed()
        assertThat(rule.onAllNodesWithText("HOLD", substring = true).fetchSemanticsNodes()).isEmpty()
    }

    @Test fun asksForTheMicrophoneFirst() {
        val r = Recorder()
        show(r, mic = false)
        rule.onNodeWithTag("talkAllowMic").performClick()
        assertThat(r.calls).containsExactly("mic")
    }

    @Test fun noIntercomCircle() {
        show(Recorder(), circles = listOf(home.copy(permissions = emptySet(), members = listOf(home.members[0].copy(permissions = emptySet())))))
        rule.onNodeWithTag("talkNoCircles").assertIsDisplayed()
    }

    @Test fun duringAnAlarmTheButtonDoesNothing() {
        val r = Recorder()
        show(r, alarming = true)
        rule.onNodeWithTag("talkButton").performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertThat(r.calls).isEmpty()
    }

    @Test fun openAppAndClose() {
        val r = Recorder()
        show(r)
        rule.onNodeWithTag("talkOpenApp").performClick()
        rule.onNodeWithTag("talkClose").performClick()
        assertThat(r.calls).containsExactly("open", "close").inOrder()
    }

    @Test fun activityShowsOverTheLockScreenAndClosesWithTheScreen() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        // create() only: onStart would start the local network for real.
        val controller = Robolectric.buildActivity(TalkActivity::class.java).create()
        val activity = controller.get()
        assertThat(shadowOf(activity).getShowWhenLocked()).isTrue()
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF).setPackage(app.packageName))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertThat(activity.isFinishing).isTrue()
        controller.destroy()
    }

    private companion object {
        const val PIXEL = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
    }
}
