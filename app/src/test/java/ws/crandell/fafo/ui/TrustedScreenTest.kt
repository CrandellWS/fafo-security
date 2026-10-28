package ws.crandell.fafo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.ui.screens.TrustedScreen

@RunWith(AndroidJUnit4::class)
class TrustedScreenTest {
    @get:Rule val rule = createComposeRule()

    private val device = TrustedDevice("0123456789abcdef0123456789abcdef", "", "", "Back room", 0L)

    @Test fun listsDevicesWithFingerprint() {
        rule.setContent { TrustedScreen(listOf(device), PairingState.Idle, true, {}, {}, {}, {}, {}, {}, {}) }
        rule.onNodeWithText("Back room").assertIsDisplayed()
        rule.onNodeWithText("0123 4567 89ab cdef 0123 4567 89ab cdef").assertIsDisplayed()
    }

    @Test fun asksForNearbyWhenMissing() {
        rule.setContent { TrustedScreen(emptyList(), PairingState.Idle, false, {}, {}, {}, {}, {}, {}, {}) }
        rule.onNodeWithTag("nearbyButton").assertIsDisplayed()
        rule.onNodeWithTag("showThisPhone").assertDoesNotExist()
    }

    @Test fun codeDialogAnswers() {
        var answer: Boolean? = null
        rule.setContent {
            TrustedScreen(emptyList(), PairingState.ConfirmCode("123456", "Register"), true, {}, {}, {}, { answer = it }, {}, {}, {})
        }
        rule.onNodeWithTag("pairCode").assertIsDisplayed()
        rule.onNodeWithText("123 456").assertIsDisplayed()
        rule.onNodeWithTag("codeMatches").performClick()
        assertThat(answer).isTrue()
    }
}
