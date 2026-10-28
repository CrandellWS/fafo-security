package ws.crandell.fafo.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/** Status and navigation bar icons that match the resolved theme (light icons on dark, dark on light). */
fun applySystemBars(activity: ComponentActivity, dark: Boolean) {
    val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    activity.enableEdgeToEdge(style, style)
}

/**
 * Android 12+: tell the system the app's own night mode, so the launch screen and window background match a
 * forced Light / Dark setting on the next start. SYSTEM follows the phone.
 */
fun applyAppNightMode(context: android.content.Context, mode: ThemeMode) {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    val ui = context.getSystemService(android.app.UiModeManager::class.java) ?: return
    val want = when (mode) {
        ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> android.app.UiModeManager.MODE_NIGHT_YES
    }
    runCatching { ui.setApplicationNightMode(want) }
}
