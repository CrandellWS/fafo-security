package ws.crandell.fafo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Brand colors that never change with the theme (the shield, red buttons, the alarm field). */
object FafoBrand {
    /** Shield navy #0B1F35. */
    val Navy = Color(0xFF0B1F35)
    /** Brand red: primary actions and the alarm screen (white on it is 5.3:1). */
    val Red = Color(0xFFD61018)
    /** Low point of the alarm flash (white on it is 7.2:1). */
    val RedDeep = Color(0xFFB00D14)
    val White = Color(0xFFFFFFFF)
    /** Dark text on bright fills (amber banner, badges). */
    val InkFixed = Color(0xFF070D16)

    /** Status-screen stripe fills (graphics, 3:1 against the field; the state word always shows too). */
    val StatusGreen = Color(0xFF22C55E)
    val StatusYellow = Color(0xFFFFD21F)
    val StatusOrange = Color(0xFFFF7A00)
    val StatusRed = Color(0xFFD61018)
}

/**
 * Theme tokens (round 3: the app follows the phone's light/dark setting, with a Light/Dark override).
 * Every text/background pair is at least 4.5:1 in both schemes; `ContrastTest` enforces it.
 */
@Immutable
data class FafoTokens(
    val isDark: Boolean,
    /** App background. */
    val bg: Color,
    /** Top and bottom bars. */
    val bar: Color,
    val surface1: Color,
    val surface2: Color,
    val outline: Color,
    /** Main text and icons. */
    val onBg: Color,
    val muted: Color,
    /** Disarmed state, inactive icons (icons only, 3:1). */
    val steel: Color,
    /** Red for text and icons. */
    val redText: Color,
    /** Armed / protected. */
    val guard: Color,
    /** Warnings, setup steps still to do. */
    val amber: Color,
    /** ARMING word. */
    val yellow: Color,
    /** PRE-ALARM word. */
    val orangeText: Color,
)

val DarkTokens = FafoTokens(
    isDark = true,
    bg = Color(0xFF070D16),
    bar = Color(0xFF0B1F35),
    surface1 = Color(0xFF0F2238),
    surface2 = Color(0xFF16304D),
    outline = Color(0xFF2C4868),
    onBg = Color(0xFFFFFFFF),
    muted = Color(0xFFB8C4D4),
    steel = Color(0xFF8FA3BA),
    redText = Color(0xFFFF6B70),
    guard = Color(0xFF2FD07A),
    amber = Color(0xFFFFB020),
    yellow = Color(0xFFFFD21F),
    orangeText = Color(0xFFFF8A3D),
)

val LightTokens = FafoTokens(
    isDark = false,
    bg = Color(0xFFF5F7FA),
    bar = Color(0xFFFFFFFF),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE9EEF4),
    outline = Color(0xFFB4C2D3),
    onBg = Color(0xFF0B1F35),
    muted = Color(0xFF4A5B70),
    steel = Color(0xFF56687F),
    redText = Color(0xFFB80D14),
    guard = Color(0xFF0B7A3E),
    amber = Color(0xFF8A5300),
    yellow = Color(0xFF8A5300),
    orangeText = Color(0xFFB23E00),
)

val LocalFafoTokens = staticCompositionLocalOf { DarkTokens }

/**
 * The FAFO palette as seen by screens. Names are from the round-2 dark design: [Ink] is the background,
 * [White] the main text color (navy in the light theme). Brand constants live in [FafoBrand].
 */
object FafoColors {
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.bg
    val Navy: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.bar
    val Surface1: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.surface1
    val Surface2: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.surface2
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.outline
    val White: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.onBg
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.muted
    val Steel: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.steel
    val RedText: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.redText
    val Guard: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.guard
    val Amber: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.amber
    val Yellow: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.yellow
    val OrangeText: Color @Composable @ReadOnlyComposable get() = LocalFafoTokens.current.orangeText

    val Red: Color get() = FafoBrand.Red
    val RedDeep: Color get() = FafoBrand.RedDeep
}

/** White or dark ink, whichever reads better on [fill] (banners, filled badges). */
fun contentOn(fill: Color): Color =
    if (contrast(FafoBrand.White, fill) >= contrast(FafoBrand.InkFixed, fill)) FafoBrand.White else FafoBrand.InkFixed

/** WCAG 2 contrast ratio. */
fun contrast(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** The five alarm states, told apart by color and by shape (never by color alone). */
@Immutable
data class StateColors(
    val disarmed: Color,
    val arming: Color,
    val armed: Color,
    val preAlarm: Color,
    val triggered: Color,
) {
    companion object {
        fun of(t: FafoTokens) = StateColors(t.steel, t.yellow, t.guard, t.orangeText, t.redText)
    }
}

val LocalFafoStateColors = staticCompositionLocalOf { StateColors.of(DarkTokens) }
