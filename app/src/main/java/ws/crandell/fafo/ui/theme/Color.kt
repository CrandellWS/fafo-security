package ws.crandell.fafo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * FAFO palette, taken from the shield logo (navy #0B1F35, red #D61018, white) plus a near-black. Every
 * text / background pair used by the screens is at least 4.5:1 (most are 7:1 or more).
 */
object FafoColors {
    /** Near-black app background. */
    val Ink = Color(0xFF070D16)
    /** Brand navy: top bar, bottom bar, launcher icon background. */
    val Navy = Color(0xFF0B1F35)
    /** Cards. */
    val Surface1 = Color(0xFF0F2238)
    /** Raised cards, selected chips, dialogs. */
    val Surface2 = Color(0xFF16304D)
    val Outline = Color(0xFF2C4868)

    /** Brand red: primary actions and the alarm screen (white on it is 5.3:1). */
    val Red = Color(0xFFD61018)
    /** Pressed red and the low point of the alarm pulse (white on it is 7.2:1). */
    val RedDeep = Color(0xFFB00D14)
    /** Red for text and icons on the dark surfaces (6.4:1 on Ink). */
    val RedText = Color(0xFFFF5A5F)

    val White = Color(0xFFFFFFFF)
    /** Secondary text (11:1 on Ink, 9:1 on Surface1). */
    val TextMuted = Color(0xFFB8C4D4)

    /** Disarmed state, inactive icons. */
    val Steel = Color(0xFF8FA3BA)
    /** Armed state ("protected"). */
    val Guard = Color(0xFF2FD07A)
    /** Pre-alarm state, warnings, setup steps still to do. */
    val Amber = Color(0xFFFFB020)
    /** ARMING countdown (status yellow). */
    val Yellow = Color(0xFFFFD21F)
}

/** The four alarm states, told apart by color and by shape (never by color alone). */
@Immutable
data class StateColors(
    val disarmed: Color = FafoColors.Steel,
    val arming: Color = FafoColors.Yellow,
    val armed: Color = FafoColors.Guard,
    val preAlarm: Color = FafoColors.Amber,
    val triggered: Color = FafoColors.Red,
)

val LocalFafoStateColors = staticCompositionLocalOf { StateColors() }
