package ws.crandell.fafo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ws.crandell.fafo.storage.CeSettings
import ws.crandell.fafo.storage.DpSettings
import ws.crandell.fafo.ui.screens.HomeScreen
import ws.crandell.fafo.ui.screens.HomeUiState
import ws.crandell.fafo.ui.screens.OnboardingScreen
import ws.crandell.fafo.ui.screens.SettingsScreen

@RunWith(AndroidJUnit4::class)
class UiTest {
    @get:Rule val rule = createComposeRule()

    private fun home(state: HomeUiState) = rule.setContent { HomeScreen(state, {}, {}, {}, {}) }

    @Test fun armDisabledWhenUnplugged() {
        home(HomeUiState(plugged = false, secureLock = true))
        rule.onNodeWithTag("armButton").assertIsNotEnabled()
        rule.onNodeWithText("Plug in to arm").assertIsDisplayed()
    }

    @Test fun armEnabledWhenPlugged() {
        home(HomeUiState(plugged = true, secureLock = true))
        rule.onNodeWithTag("armButton").assertIsEnabled()
        rule.onNodeWithText("Arm").assertIsDisplayed()
    }

    @Test fun armDisabledWithoutSecureLock() {
        home(HomeUiState(plugged = true, secureLock = false))
        rule.onNodeWithTag("armButton").assertIsNotEnabled()
        rule.onNodeWithText("Set a screen lock first").assertIsDisplayed()
    }

    @Test fun settingsShowSpecDefaults() {
        rule.setContent { SettingsScreen(DpSettings(), CeSettings(deviceLabel = "Phone"), {}, {}) }
        rule.onNodeWithTag("sensitivity_0.5 s").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("prealarm_5 s").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("siren_Klaxon").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("motion_Medium").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("photoInterval_10 s").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("camera_Front").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("photoCap_100 MB").performScrollTo().assertIsSelected()
        rule.onNodeWithText("Longer = fewer false alarms from loose cables or plug swaps.").assertExists()
    }

    @Test fun settingsShowGraceWindowDefault() {
        var picked: Int? = null
        rule.setContent { SettingsScreen(DpSettings(), CeSettings(deviceLabel = "Phone"), {}, {}, onGrace = { picked = it }) }
        rule.onNodeWithTag("grace_30 s").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("grace_Off").performScrollTo().performClick()
        // The setting itself changes only after the confirmation (AppViewModel.changeGrace).
        assertThat(picked).isEqualTo(0)
        rule.onNodeWithTag("grace_30 s").assertIsSelected()
    }

    @Test fun onboardingChecklistReflectsPermissions() {
        val status = OnboardingStatus(secureLock = true, notifications = true, camera = false, fullScreen = false)
        rule.setContent { OnboardingScreen(status, {}, {}) }
        rule.onNodeWithTag("status_NOTIFICATIONS").performScrollTo().assertTextEquals("Done")
        rule.onNodeWithTag("status_SECURE_LOCK").performScrollTo().assertTextEquals("Done")
        rule.onNodeWithTag("status_CAMERA").performScrollTo().assertTextEquals("To do")
        rule.onNodeWithTag("status_FULL_SCREEN").performScrollTo().assertTextEquals("To do")
    }

    @Test fun homeChecklistListsMissingSteps() {
        val status = OnboardingStatus(secureLock = true, notifications = true, camera = false)
        assertThat(status.missing).contains(OnboardingStep.CAMERA)
        assertThat(status.missing).doesNotContain(OnboardingStep.NOTIFICATIONS)
        home(HomeUiState(plugged = true, secureLock = true, checklist = status.missing))
        rule.onNodeWithTag("check_CAMERA").performScrollTo().assertIsDisplayed()
    }
}
