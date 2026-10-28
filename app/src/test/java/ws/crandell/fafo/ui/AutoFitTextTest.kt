package ws.crandell.fafo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ws.crandell.fafo.ui.components.AutoFitText
import ws.crandell.fafo.ui.components.FittedFontSize
import ws.crandell.fafo.ui.components.FittedLayout
import ws.crandell.fafo.ui.components.StateKind
import ws.crandell.fafo.ui.components.StatusHero
import ws.crandell.fafo.ui.theme.FafoTheme
import ws.crandell.fafo.ui.theme.FafoType

/** Bill 2026-10-28: state words, countdown, buttons and titles stay on ONE line at any width or font size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-port-420dpi")
class AutoFitTextTest {
    @get:Rule val rule = createComposeRule()

    private fun layoutOf(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config[FittedLayout]()

    private val words = listOf("Disarmed", "Arming", "Armed", "Plug me back in", "Alarm")

    /** Every state word on one line, never ellipsized, at [scale]; returns the fitted sizes. */
    private fun stateWordsAt(scale: Float): Map<String, Float> {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                FafoTheme { Column { words.forEach { w -> StatusHero(StateKind.DISARMED, w, wordTag = "w_$w") } } }
            }
        }
        rule.waitForIdle()
        return words.associateWith { w ->
            val l = layoutOf("w_$w")
            assertThat(l.lines).isEqualTo(1)
            assertThat(l.ellipsized).isFalse()
            l.fontSize
        }
    }

    @Test fun stateWordsFitAtFontScale10() { stateWordsAt(1.0f) }
    @Test fun stateWordsFitAtFontScale13() { stateWordsAt(1.3f) }

    @Test fun stateWordsFitAtFontScale15() { stateWordsAt(1.5f) }

    /**
     * Bill's case: a large Display size makes the phone narrower in dp (Android 14+ scales font size
     * non-linearly, so 56sp headlines barely grow with Font size alone). At 320dp DISARMED must shrink.
     */
    @Config(qualifiers = "w320dp-h700dp-port-420dpi")
    @Test fun disarmedShrinksOnANarrowDisplayAtLargeFont() {
        val sizes = stateWordsAt(1.5f)
        assertThat(sizes.getValue("Disarmed")).isLessThan(FafoType.stateDisplay.fontSize.value)
    }

    @Test fun shortTextKeepsItsSize() {
        rule.setContent { FafoTheme { AutoFitText("OK", style = FafoType.stateDisplay, modifier = Modifier.testTag("fit")) } }
        rule.waitForIdle()
        assertThat(layoutOf("fit").fontSize).isEqualTo(FafoType.stateDisplay.fontSize.value)
    }

    @Test fun countdownMeasuredAgainstTheWidestDigitsDoesNotJump() {
        rule.setContent {
            FafoTheme {
                Column {
                    AutoFitText("1", style = FafoType.countdown, sample = "88", modifier = Modifier.width(80.dp).testTag("one"))
                    AutoFitText("88", style = FafoType.countdown, sample = "88", modifier = Modifier.width(80.dp).testTag("eight"))
                }
            }
        }
        rule.waitForIdle()
        assertThat(layoutOf("one").fontSize).isEqualTo(layoutOf("eight").fontSize)
        assertThat(layoutOf("one").fontSize).isLessThan(FafoType.countdown.fontSize.value)
    }

    @Test fun tooLongAtTheMinimumEllipsizesInsteadOfWrapping() {
        rule.setContent {
            FafoTheme { AutoFitText("A very long title that cannot fit in forty dp", style = FafoType.action, modifier = Modifier.width(40.dp).testTag("fit")) }
        }
        rule.waitForIdle()
        assertThat(layoutOf("fit").lines).isEqualTo(1)
        assertThat(layoutOf("fit").ellipsized).isTrue()
    }

    @Test fun textIsExposedToAccessibility() {
        rule.setContent { FafoTheme { AutoFitText("DISARMED", modifier = Modifier.testTag("fit")) } }
        rule.onNodeWithTag("fit").assertTextEquals("DISARMED")
    }
}
