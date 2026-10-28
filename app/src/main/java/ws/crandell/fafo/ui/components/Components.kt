package ws.crandell.fafo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.ui.theme.FafoBrand
import ws.crandell.fafo.ui.theme.FafoColors
import ws.crandell.fafo.ui.theme.contentOn
import ws.crandell.fafo.ui.theme.LocalFafoTokens
import ws.crandell.fafo.ui.theme.FafoType
import ws.crandell.fafo.ui.theme.LocalFafoStateColors

/** Screen gutter and card spacing (4dp grid). */
object FafoDims {
    val Gutter = 20.dp
    val CardPad = 16.dp
    val Gap = 12.dp
    val Touch = 48.dp
    val Primary = 88.dp
}

/** The four alarm states the UI distinguishes. */
enum class StateKind { DISARMED, ARMING, ARMED, PRE_ALARM, TRIGGERED }

fun AlarmState.kind(): StateKind = when (this) {
    AlarmState.Disarmed -> StateKind.DISARMED
    is AlarmState.Arming -> StateKind.ARMING
    is AlarmState.Armed -> StateKind.ARMED
    is AlarmState.PreAlarm -> StateKind.PRE_ALARM
    is AlarmState.Triggered, is AlarmState.IncidentPersisted -> StateKind.TRIGGERED
}

@Composable
fun stateColor(kind: StateKind): Color {
    val c = LocalFafoStateColors.current
    return when (kind) {
        StateKind.DISARMED -> c.disarmed
        StateKind.ARMING -> c.arming
        StateKind.ARMED -> c.armed
        StateKind.PRE_ALARM -> c.preAlarm
        StateKind.TRIGGERED -> FafoColors.RedText
    }
}

fun stateIcon(kind: StateKind): ImageVector = when (kind) {
    StateKind.DISARMED, StateKind.ARMING -> FafoIcons.ShieldOutline
    StateKind.ARMED -> FafoIcons.ShieldCheck
    StateKind.PRE_ALARM, StateKind.TRIGGERED -> FafoIcons.ShieldAlert
}

/** Navy bar: logo + wordmark on Home, a screen title elsewhere; back arrow on pushed screens; gear to Settings. */
@Composable
fun FafoTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    val dark = LocalFafoTokens.current.isDark
    Surface(color = FafoColors.Navy, modifier = modifier.fillMaxWidth(), shadowElevation = if (dark) 0.dp else 1.dp) {
        Row(
            Modifier.padding(top = topInset()).height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(FafoDims.Touch).testTag("back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FafoColors.White)
                }
            } else {
                Spacer(Modifier.width(8.dp))
                ShieldLogo(36.dp)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (title == null) {
                    FafoWordmark(height = 24.dp)
                } else {
                    AutoFitText(title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = FafoColors.White, minFontSize = 16.sp, modifier = Modifier.semantics { heading() })
                }
            }
            if (onSettings != null) {
                IconButton(onClick = onSettings, modifier = Modifier.size(FafoDims.Touch).testTag("settingsButton")) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = FafoColors.White)
                }
            }
        }
    }
}

/** Status-bar inset (edge-to-edge): the navy bar runs under the status bar. */
@Composable
private fun topInset(): Dp = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }

data class BarItem(val key: String, val label: String, val icon: ImageVector, val badge: Int = 0)

/** M3 bottom navigation on navy: red indicator pill, white selected, steel unselected, labels always shown. */
@Composable
fun FafoBottomBar(items: List<BarItem>, selected: String, onSelect: (String) -> Unit) {
    // Every label is measured against the longest one, so all tabs shrink together at large font sizes.
    val longest = items.maxByOrNull { it.label.length }?.label
    NavigationBar(containerColor = FafoColors.Navy, contentColor = FafoColors.White) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.key == selected,
                onClick = { onSelect(item.key) },
                icon = {
                    if (item.badge > 0) {
                        val amber = FafoColors.Amber
                        BadgedBox(badge = { Badge(containerColor = amber, contentColor = contentOn(amber)) { Text("${item.badge}") } }) {
                            Icon(item.icon, contentDescription = null)
                        }
                    } else {
                        Icon(item.icon, contentDescription = null)
                    }
                },
                label = { AutoFitText(item.label, style = MaterialTheme.typography.labelSmall, minFontSize = 9.sp, sample = longest) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FafoBrand.White,
                    selectedTextColor = FafoColors.White,
                    indicatorColor = FafoColors.Red,
                    unselectedIconColor = FafoColors.Steel,
                    unselectedTextColor = FafoColors.Steel,
                ),
                modifier = Modifier.testTag("tab_${item.key}"),
            )
        }
    }
}

/** Section header: small uppercase muted label with room above. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = FafoColors.TextMuted,
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp).semantics { heading() },
    )
}

/** Surface1 card with an optional accent stripe on the left (state color). */
@Composable
fun FafoCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    color: Color = FafoColors.Surface1,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.fillMaxWidth().clip(shape).background(color).border(BorderStroke(1.dp, FafoColors.Outline.copy(alpha = 0.6f)), shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Row(clickable.height(IntrinsicSize.Min)) {
        if (accent != null) Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
        Column(Modifier.weight(1f).padding(FafoDims.CardPad), content = content)
    }
}

/** The big state card on Home: state glyph and word, then [content] (sub-lines, chips). */
@Composable
fun StatusHero(
    kind: StateKind,
    word: String,
    modifier: Modifier = Modifier,
    wordTag: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val color = stateColor(kind)
    FafoCard(modifier, accent = color, shape = RoundedCornerShape(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(stateIcon(kind), contentDescription = null, tint = color, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.width(16.dp))
            // One line at any width or font size (Bill 2026-10-28: "DISARMED" wrapped on the A17).
            AutoFitText(
                word.uppercase(), style = FafoType.stateDisplay, color = color, minFontSize = 20.sp,
                modifier = Modifier.weight(1f).then(if (wordTag != null) Modifier.testTag(wordTag) else Modifier),
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

enum class ActionKind { ARM, DISARM }

/**
 * Full-width 88dp primary button. ARM = red fill, white text; DISARM = white fill, ink text (high contrast and
 * clearly different from Arm). Disabled shows the reason as its label; [busy] shows a spinner and
 * "Confirming it's you".
 */
@Composable
fun PrimaryActionButton(
    label: String,
    kind: ActionKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val (bg, fg) = when (kind) {
        ActionKind.ARM -> FafoColors.Red to FafoBrand.White
        ActionKind.DISARM -> FafoColors.White to FafoColors.Ink
    }
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg, contentColor = fg,
            disabledContainerColor = if (busy) bg.copy(alpha = 0.75f) else FafoColors.Surface2,
            disabledContentColor = if (busy) fg else FafoColors.TextMuted,
        ),
        modifier = modifier.fillMaxWidth().height(FafoDims.Primary),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(26.dp), color = fg, strokeWidth = 3.dp, trackColor = fg.copy(alpha = 0.3f))
            Spacer(Modifier.width(14.dp))
            AutoFitText("Confirming it's you", style = MaterialTheme.typography.titleLarge, minFontSize = 13.sp)
        } else if (enabled) {
            // Drawn in capitals; read out (and matched by tests) as the plain word.
            Box(Modifier.clearAndSetSemantics { text = AnnotatedString(label) }, contentAlignment = Alignment.Center) {
                AutoFitText(label.uppercase(), style = FafoType.action, minFontSize = 16.sp, textAlign = TextAlign.Center)
            }
        } else {
            AutoFitText(label, style = MaterialTheme.typography.titleLarge, minFontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Home shortcut tile (2-column grid). */
@Composable
fun QuickTile(icon: ImageVector, label: String, sub: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FafoCard(modifier.heightIn(min = 96.dp), onClick = onClick) {
        Icon(icon, contentDescription = null, tint = FafoColors.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted, maxLines = 1)
    }
}

/** Pill with a dot, e.g. "ROOM MODE ON", "Reachable". */
@Composable
fun StateBadge(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Row(
        modifier.clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.16f))
            .heightIn(min = 30.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!filled) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (filled) contentOn(color) else color)
    }
}

/** Full-width banner (Amber "N setup steps left", Red "Screen lock required"); tappable when [action] is set. */
@Composable
fun Banner(text: String, action: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color)
    val on = contentOn(color)
    Row(
        (if (action.isNotEmpty()) base.clickable(onClick = onClick) else base).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = on, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        // A sentence, not a headline: up to two lines; the action label shrinks to leave it room.
        Text(text, style = MaterialTheme.typography.titleMedium, color = on, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f))
        if (action.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            AutoFitText(action, style = MaterialTheme.typography.labelLarge, color = on, minFontSize = 11.sp, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = on)
        }
    }
}

/** A list row at least 64dp tall: leading icon, title, subtitle, trailing slot. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val m = modifier.fillMaxWidth().heightIn(min = 64.dp)
    Row(
        (if (onClick != null) m.clickable(onClick = onClick) else m).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
        }
        trailing()
    }
}
