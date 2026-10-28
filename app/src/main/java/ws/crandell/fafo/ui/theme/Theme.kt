package ws.crandell.fafo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Red = Color(0xFFB71C1C)
private val RedLight = Color(0xFFFF8A80)

@Composable
fun FafoTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = RedLight, secondary = Color(0xFFFFCC80))
    } else {
        lightColorScheme(primary = Red, secondary = Color(0xFFE65100))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
