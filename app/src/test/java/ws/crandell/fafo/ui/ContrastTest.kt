package ws.crandell.fafo.ui

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import ws.crandell.fafo.ui.theme.DarkTokens
import ws.crandell.fafo.ui.theme.FafoBrand
import ws.crandell.fafo.ui.theme.FafoTokens
import ws.crandell.fafo.ui.theme.LightTokens
import ws.crandell.fafo.ui.theme.contentOn
import ws.crandell.fafo.ui.theme.contrast

/** Round 3: both themes, every text color on every surface it can sit on, at least 4.5:1 (WCAG AA). */
class ContrastTest {
    private fun FafoTokens.texts() = mapOf(
        "onBg" to onBg, "muted" to muted, "steel" to steel, "redText" to redText, "guard" to guard,
        "amber" to amber, "yellow" to yellow, "orangeText" to orangeText,
    )
    private fun FafoTokens.surfaces() = mapOf("bg" to bg, "bar" to bar, "surface1" to surface1, "surface2" to surface2)

    @Test fun everyTextTokenOnEverySurfaceInBothThemes() {
        for ((theme, t) in listOf("dark" to DarkTokens, "light" to LightTokens)) {
            for ((fn, fg) in t.texts()) for ((bn, bg) in t.surfaces()) {
                assertWithMessage("$theme $fn on $bn").that(contrast(fg, bg)).isAtLeast(4.5)
            }
        }
    }

    @Test fun whiteOnBrandRedAndTheFlashLowPoint() {
        assertWithMessage("white on red").that(contrast(FafoBrand.White, FafoBrand.Red)).isAtLeast(4.5)
        assertWithMessage("white on red deep").that(contrast(FafoBrand.White, FafoBrand.RedDeep)).isAtLeast(4.5)
    }

    @Test fun filledBannersAndBadgesPickReadableText() {
        for (t in listOf(DarkTokens, LightTokens)) {
            for (fill in listOf(t.amber, t.redText, t.guard, t.yellow, t.orangeText, FafoBrand.Red)) {
                assertWithMessage("on ${fill}").that(contrast(contentOn(fill), fill)).isAtLeast(4.5)
            }
        }
    }

    @Test fun disarmButtonInBothThemes() {
        // DISARM is an onBg fill with bg-colored text: white/ink in dark, navy/paper in light.
        for (t in listOf(DarkTokens, LightTokens)) assertWithMessage("disarm").that(contrast(t.bg, t.onBg)).isAtLeast(7.0)
    }

    @Test fun statusStripesStandOutFromTheField() {
        // Graphics need 3:1 against the dark field; on light the navy keyline carries the shape.
        for (c in listOf(FafoBrand.StatusGreen, FafoBrand.StatusYellow, FafoBrand.StatusOrange, FafoBrand.StatusRed)) {
            assertWithMessage("stripe $c on dark").that(contrast(c, DarkTokens.bg)).isAtLeast(3.0)
            assertWithMessage("stripe $c vs navy keyline").that(contrast(c, FafoBrand.Navy)).isAtLeast(3.0)
        }
    }
}
