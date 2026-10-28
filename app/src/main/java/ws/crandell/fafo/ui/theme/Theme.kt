package ws.crandell.fafo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/** Settings > Display: follow the phone (default), or force Light or Dark (round 3, Bill 2026-10-28). */
enum class ThemeMode { SYSTEM, LIGHT, DARK;
    companion object {
        fun parse(s: String?): ThemeMode = entries.firstOrNull { it.name == s } ?: SYSTEM
    }
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun FafoTokens.colorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = FafoBrand.Red,
        onPrimary = FafoBrand.White,
        primaryContainer = FafoBrand.RedDeep,
        onPrimaryContainer = FafoBrand.White,
        secondary = steel,
        onSecondary = bg,
        secondaryContainer = surface2,
        onSecondaryContainer = onBg,
        tertiary = guard,
        onTertiary = bg,
        background = bg,
        onBackground = onBg,
        surface = bg,
        onSurface = onBg,
        surfaceVariant = surface2,
        onSurfaceVariant = muted,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface1,
        surfaceContainer = surface1,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface2,
        surfaceBright = surface2,
        surfaceDim = bg,
        inverseSurface = onBg,
        inverseOnSurface = bg,
        inversePrimary = FafoBrand.RedDeep,
        outline = outline,
        outlineVariant = outline,
        error = redText,
        onError = bg,
        errorContainer = FafoBrand.RedDeep,
        onErrorContainer = FafoBrand.White,
        scrim = FafoBrand.InkFixed,
    )
}

val FafoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FafoTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val tokens = if (mode.isDark()) DarkTokens else LightTokens
    CompositionLocalProvider(
        LocalFafoTokens provides tokens,
        LocalFafoStateColors provides StateColors.of(tokens),
    ) {
        MaterialTheme(colorScheme = tokens.colorScheme(), typography = FafoTypography, shapes = FafoShapes, content = content)
    }
}
