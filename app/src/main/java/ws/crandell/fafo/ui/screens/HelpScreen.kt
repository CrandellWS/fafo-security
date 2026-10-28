package ws.crandell.fafo.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.FafoIcons
import ws.crandell.fafo.ui.components.FafoWordmark
import ws.crandell.fafo.ui.components.SectionHeader
import ws.crandell.fafo.ui.components.ShieldLogo
import ws.crandell.fafo.ui.help.HelpContent
import ws.crandell.fafo.ui.help.HelpIcon
import ws.crandell.fafo.ui.help.HelpLink
import ws.crandell.fafo.ui.help.HelpTopic
import ws.crandell.fafo.ui.theme.FafoColors

fun helpIcon(i: HelpIcon): ImageVector = when (i) {
    HelpIcon.START -> FafoIcons.Setup
    HelpIcon.SHIELD -> FafoIcons.ShieldCheck
    HelpIcon.GRACE -> Icons.Filled.Lock
    HelpIcon.SIREN -> FafoIcons.Siren
    HelpIcon.MOTION -> FafoIcons.Motion
    HelpIcon.CAMERA -> FafoIcons.Camera
    HelpIcon.POWER -> FafoIcons.Power
    HelpIcon.PAIR -> FafoIcons.Devices
    HelpIcon.CIRCLES -> FafoIcons.Devices
    HelpIcon.REMOTE -> FafoIcons.ShieldAlert
    HelpIcon.MIC -> FafoIcons.Mic
    HelpIcon.WIFI -> FafoIcons.Wifi
    HelpIcon.LOCK -> Icons.Filled.Lock
    HelpIcon.SAMSUNG -> FafoIcons.Battery
    HelpIcon.LIMITS -> Icons.Filled.Warning
    HelpIcon.PRIVACY -> FafoIcons.Privacy
}

/** Help tab: brand hero, then every topic as an expandable card. [expanded] is the open topic. */
@Composable
fun HelpScreen(
    expanded: String?,
    onToggle: (String) -> Unit,
    onLink: (HelpLink) -> Unit,
    modifier: Modifier = Modifier,
    topics: List<HelpTopic> = HelpContent.topics,
) {
    val list = rememberLazyListState()
    // "Learn more" from Setup opens a topic: bring it into view.
    LaunchedEffect(expanded) {
        val i = topics.indexOfFirst { it.id == expanded }
        if (i >= 0) list.animateScrollToItem(i + 2)
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = list,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = FafoDims.Gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShieldLogo(104.dp)
                Spacer(Modifier.height(12.dp))
                FafoWordmark(size = 40.sp, stacked = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Free Advanced Features Offline. Everything stays on your phones.",
                    style = MaterialTheme.typography.bodyLarge, color = FafoColors.TextMuted, textAlign = TextAlign.Center,
                )
            }
        }
        item { SectionHeader("Guide") }
        items(topics, key = { it.id }) { t -> TopicCard(t, t.id == expanded, { onToggle(t.id) }, onLink) }
    }
}

@Composable
private fun TopicCard(t: HelpTopic, open: Boolean, onToggle: () -> Unit, onLink: (HelpLink) -> Unit) {
    FafoCard(Modifier.testTag("help_${t.id}").animateContentSize(), color = if (open) FafoColors.Surface2 else FafoColors.Surface1) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(FafoColors.Surface2), contentAlignment = Alignment.Center) {
                Icon(helpIcon(t.icon), contentDescription = null, tint = FafoColors.RedText, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                ws.crandell.fafo.ui.components.AutoFitText(t.title, style = MaterialTheme.typography.titleMedium, color = FafoColors.White, minFontSize = 13.sp)
                Text(t.summary, style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null, tint = FafoColors.Steel,
            )
        }
        if (open) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                t.sections.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.heading?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = FafoColors.White) }
                        if (s.text.isNotEmpty()) Text(s.text, style = MaterialTheme.typography.bodyLarge, color = FafoColors.TextMuted)
                        s.steps.forEachIndexed { i, step -> NumberedLine(i + 1, step) }
                    }
                }
                t.links.forEach { link ->
                    OutlinedButton(onClick = { onLink(link) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("link_${link.name}")) {
                        Text(link.label)
                    }
                }
            }
        }
    }
}
