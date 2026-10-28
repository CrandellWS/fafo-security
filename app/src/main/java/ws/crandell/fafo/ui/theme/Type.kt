package ws.crandell.fafo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.R

/** Barlow Condensed (SIL OFL 1.1, bundled in res/font; license in assets/licenses) for state words and titles. */
val Barlow = FontFamily(
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
)

/** Body text is the phone's own sans (Roboto / One UI Sans); nothing on screen is smaller than 13sp labels / 15sp text. */
private val Sans = FontFamily.SansSerif

object FafoType {
    /** "ARMED", "DISARMED", "ALARM". */
    val stateDisplay = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = 0.03.em)
    /** Pre-alarm seconds. */
    val countdown = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontSize = 120.sp, lineHeight = 120.sp)
    /** Big button labels ("ARM", "DISARM"). */
    val action = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = 0.08.em)
    /** Pairing codes and key fingerprints. */
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 22.sp)
}

val FafoTypography = Typography(
    displaySmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = 0.02.em),
    headlineMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.03.em),
    headlineSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 0.03.em),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.02.em),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.12.em),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.04.em),
)
