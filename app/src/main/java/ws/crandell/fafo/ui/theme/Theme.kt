package ws.crandell.fafo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/**
 * Dark-only for v1, whatever the system setting is: the app is used at night, on a nightstand and in a
 * pocket, and the logo was drawn for a dark field.
 */
private val FafoDark = darkColorScheme(
    primary = FafoColors.Red,
    onPrimary = FafoColors.White,
    primaryContainer = FafoColors.RedDeep,
    onPrimaryContainer = FafoColors.White,
    secondary = FafoColors.Steel,
    onSecondary = FafoColors.Ink,
    secondaryContainer = FafoColors.Surface2,
    onSecondaryContainer = FafoColors.White,
    tertiary = FafoColors.Guard,
    onTertiary = FafoColors.Ink,
    background = FafoColors.Ink,
    onBackground = FafoColors.White,
    surface = FafoColors.Ink,
    onSurface = FafoColors.White,
    surfaceVariant = FafoColors.Surface2,
    onSurfaceVariant = FafoColors.TextMuted,
    surfaceContainerLowest = FafoColors.Ink,
    surfaceContainerLow = FafoColors.Surface1,
    surfaceContainer = FafoColors.Surface1,
    surfaceContainerHigh = FafoColors.Surface2,
    surfaceContainerHighest = FafoColors.Surface2,
    surfaceBright = FafoColors.Surface2,
    surfaceDim = FafoColors.Ink,
    inverseSurface = FafoColors.White,
    inverseOnSurface = FafoColors.Ink,
    inversePrimary = FafoColors.RedDeep,
    outline = FafoColors.Outline,
    outlineVariant = FafoColors.Outline,
    error = FafoColors.RedText,
    onError = FafoColors.Ink,
    errorContainer = FafoColors.RedDeep,
    onErrorContainer = FafoColors.White,
    scrim = FafoColors.Ink,
)

val FafoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FafoTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFafoStateColors provides StateColors()) {
        MaterialTheme(colorScheme = FafoDark, typography = FafoTypography, shapes = FafoShapes, content = content)
    }
}
