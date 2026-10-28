package ws.crandell.fafo.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ws.crandell.fafo.ui.components.ShieldMark
import kotlin.math.abs

/**
 * Sanity check that the traced vector shield (tools/trace-shield.py) still looks like the approved PNG:
 * same silhouette (alpha IoU) and nearly the same colors. Not pixel-exact; it catches large drift.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ShieldGoldenTest {
    private fun render(stripes: Color, eye: Color): android.graphics.Bitmap =
        ws.crandell.fafo.ui.components.renderShieldMark(512, stripes, eye).asAndroidBitmap()

    @Test fun vectorMatchesTheApprovedPng() {
        val ref = javaClass.classLoader!!.getResourceAsStream("shield/fafo-shield2-FINAL-512.png").use { BitmapFactory.decodeStream(it) }
        val img = render(ws.crandell.fafo.ui.theme.FafoBrand.Red, ws.crandell.fafo.ui.theme.FafoBrand.Red)
        val dx = (ref.width - img.width) / 2
        var inter = 0; var union = 0; var colorErr = 0L; var both = 0
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val a = img.getPixel(x, y); val b = ref.getPixel(x + dx, y)
            val ia = (a ushr 24) > 128; val ib = (b ushr 24) > 128
            if (ia && ib) {
                inter++; both++
                colorErr += abs(((a shr 16) and 255) - ((b shr 16) and 255)) + abs(((a shr 8) and 255) - ((b shr 8) and 255)) + abs((a and 255) - (b and 255))
            }
            if (ia || ib) union++
        }
        assertThat(inter.toDouble() / union).isGreaterThan(0.97)
        // Mean absolute RGB error per pixel (0..765); edges and anti-aliasing account for most of it.
        assertThat(colorErr.toDouble() / both).isLessThan(25.0)
    }

    @Test fun stripesAndEyeRecolor() {
        val img = render(Color(0xFF22C55E), Color.White)
        // A pixel inside the upper stripe (traced from x 135..887, y 130..355 at 1024) is green.
        val p = img.getPixel((512 * 0.5f - (512 - img.width) / 2).toInt(), (512 * 0.17f).toInt())
        assertThat((p shr 8) and 255).isGreaterThan(150)
        assertThat((p shr 16) and 255).isLessThan(100)
    }
}
