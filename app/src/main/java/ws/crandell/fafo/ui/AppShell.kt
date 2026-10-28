package ws.crandell.fafo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import ws.crandell.fafo.ui.components.BarItem
import ws.crandell.fafo.ui.components.FafoBottomBar
import ws.crandell.fafo.ui.components.FafoIcons
import ws.crandell.fafo.ui.components.FafoTopBar
import ws.crandell.fafo.ui.theme.FafoColors

/**
 * Bottom navigation (item 3 of the 2026-10-28 UI batch). Five tabs, the Material 3 maximum, so Setup and Help
 * are one tap from every screen and the Setup badge shows unfinished steps even while armed. Settings is
 * the gear in the top bar; Incidents is a tile on Home.
 */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    DEVICES("Devices", FafoIcons.Devices),
    INTERCOM("Intercom", FafoIcons.Mic),
    SETUP("Setup", FafoIcons.Setup),
    HELP("Help", FafoIcons.Help),
}

/** Screens pushed over a tab: they show a back arrow and hide the bottom bar. */
enum class Pushed(val title: String) { SETTINGS("Settings"), INCIDENTS("Incidents") }

/** Top bar title: the wordmark on Home (null), the tab or pushed screen name elsewhere. */
fun shellTitle(tab: Tab, pushed: Pushed?, override: String? = null): String? =
    override ?: pushed?.title ?: if (tab == Tab.HOME) null else tab.label

@Composable
fun AppShell(
    tab: Tab,
    pushed: Pushed?,
    setupBadge: Int,
    onTab: (Tab) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    titleOverride: String? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = FafoColors.Ink,
        topBar = {
            FafoTopBar(
                title = shellTitle(tab, pushed, titleOverride),
                onBack = if (pushed != null) onBack else null,
                onSettings = if (pushed == Pushed.SETTINGS) null else onSettings,
            )
        },
        bottomBar = {
            if (pushed == null) {
                FafoBottomBar(
                    Tab.entries.map { BarItem(it.name, it.label, it.icon, if (it == Tab.SETUP) setupBadge else 0) },
                    tab.name,
                ) { onTab(Tab.valueOf(it)) }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    data, containerColor = FafoColors.Surface2, contentColor = FafoColors.White,
                    actionColor = FafoColors.RedText, shape = RoundedCornerShape(14.dp),
                )
            }
        },
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}
