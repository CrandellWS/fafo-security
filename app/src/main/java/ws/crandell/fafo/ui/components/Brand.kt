package ws.crandell.fafo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.R
import ws.crandell.fafo.ui.theme.Barlow
import ws.crandell.fafo.ui.theme.FafoBrand
import ws.crandell.fafo.ui.theme.FafoColors
import ws.crandell.fafo.ui.theme.LocalFafoTokens

/** Width / height of the shield art (fafo_shield.webp). */
private const val SHIELD_ASPECT = 0.908f

/**
 * The approved FAFO shield. [haloOnRed] adds a soft dark glow behind it so the white sticker edge stands out
 * on the red alarm screen.
 */
@Composable
fun ShieldLogo(height: Dp, modifier: Modifier = Modifier, haloOnRed: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (haloOnRed) {
            Box(
                Modifier.size(height * 1.25f).background(
                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.25f), Color.Transparent)),
                ),
            )
        }
        Image(
            painterResource(R.drawable.fafo_shield),
            contentDescription = "FAFO Security shield",
            modifier = Modifier.height(height).size(height * SHIELD_ASPECT, height),
        )
    }
}

/** Which hand-built wordmark (~/Pictures/fafo-logo/wordmark, converted by tools/svg2vd.py) to draw. */
enum class WordmarkVariant { AUTO, DARK_WHITE, LIGHT, MONO_WHITE }

/** Width / height of the wordmark art (SVG viewBox 1096.03 x 335.19). */
const val WORDMARK_ASPECT = 3.2699f

@Composable
private fun WordmarkVariant.resolve(): WordmarkVariant =
    if (this != WordmarkVariant.AUTO) this else if (LocalFafoTokens.current.isDark) WordmarkVariant.DARK_WHITE else WordmarkVariant.LIGHT

/**
 * The FAFO SECURITY wordmark (Bill 2026-10-28): white letters with red accents on dark, navy on light,
 * all white on red / status backgrounds ([onRed] or [WordmarkVariant.MONO_WHITE]). [height] sets the size.
 */
@Composable
fun FafoWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    variant: WordmarkVariant = WordmarkVariant.AUTO,
    onRed: Boolean = false,
) {
    val v = if (onRed) WordmarkVariant.MONO_WHITE else variant.resolve()
    val res = when (v) {
        WordmarkVariant.LIGHT -> R.drawable.wordmark_light
        WordmarkVariant.MONO_WHITE -> R.drawable.wordmark_mono_white
        else -> R.drawable.wordmark_dark_white
    }
    Image(
        painterResource(res), contentDescription = "FAFO Security",
        modifier = modifier.size(height * WORDMARK_ASPECT, height),
    )
}

/**
 * Shield plus wordmark. Wide: side by side (Help and Setup headers); stacked: shield over the wordmark
 * (splash / first-run hero). Built in Compose because the lockup SVGs embed the shield as a bitmap.
 */
@Composable
fun FafoLockup(
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    wordmarkHeight: Dp = 28.dp,
    variant: WordmarkVariant = WordmarkVariant.AUTO,
    eye: androidx.compose.ui.graphics.Color? = null,
) {
    val m = modifier.semantics(mergeDescendants = true) { contentDescription = "FAFO Security" }
    if (stacked) {
        Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(wordmarkHeight * 0.45f)) {
            LockupShield(wordmarkHeight * 3.4f, eye)
            FafoWordmark(height = wordmarkHeight, variant = variant)
        }
    } else {
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(wordmarkHeight * 0.4f)) {
            LockupShield(wordmarkHeight * 1.9f, eye)
            FafoWordmark(height = wordmarkHeight, variant = variant)
        }
    }
}

@Composable
private fun LockupShield(height: Dp, eye: androidx.compose.ui.graphics.Color?) = ShieldLogo(height)
