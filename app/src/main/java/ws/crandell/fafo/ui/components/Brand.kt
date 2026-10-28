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
import ws.crandell.fafo.ui.theme.FafoColors

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

/**
 * "FAFO SECURITY" as styled text (there is no wordmark file yet). [onRed] draws both words white so there is
 * never red on red.
 */
@Composable
fun FafoWordmark(modifier: Modifier = Modifier, size: TextUnit = 22.sp, stacked: Boolean = false, onRed: Boolean = false) {
    val fafo = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontSize = size, letterSpacing = 0.08.em, color = FafoColors.White)
    val sec = TextStyle(
        fontFamily = Barlow, fontWeight = FontWeight.Bold, letterSpacing = 0.18.em,
        fontSize = if (stacked) size * 0.42f else size, color = if (onRed) FafoColors.White else FafoColors.RedText,
    )
    val m = modifier.semantics(mergeDescendants = true) { contentDescription = "FAFO Security" }
    if (stacked) {
        Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FAFO", style = fafo.copy(lineHeight = size * 0.95f))
            Text("SECURITY", style = sec)
        }
    } else {
        Row(m, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("FAFO", style = fafo)
            Text("SECURITY", style = sec)
        }
    }
}
