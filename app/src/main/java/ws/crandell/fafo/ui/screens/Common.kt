package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.ui.theme.FafoColors

/** 48dp chip colors: selected = brand red with white text and a check, unselected = outlined. */
@Composable
fun fafoChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = FafoColors.Surface1,
    labelColor = FafoColors.White,
    selectedContainerColor = FafoColors.Red,
    selectedLabelColor = FafoColors.White,
    selectedLeadingIconColor = FafoColors.White,
    disabledContainerColor = FafoColors.Surface1,
    disabledLabelColor = FafoColors.TextMuted,
)

@Composable
fun fafoChipBorder(selected: Boolean, enabled: Boolean = true) =
    FilterChipDefaults.filterChipBorder(
        enabled = enabled, selected = selected,
        borderColor = FafoColors.Outline, selectedBorderColor = FafoColors.Red, borderWidth = 1.dp, selectedBorderWidth = 1.dp,
    )

/** A 48dp-tall filter chip in the FAFO style. */
@Composable
fun FafoChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (selected) { { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null,
        shape = RoundedCornerShape(12.dp),
        colors = fafoChipColors(),
        border = fafoChipBorder(selected, enabled),
        modifier = modifier.heightIn(min = 48.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    tag: String,
    hint: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            choices.forEach { c ->
                FafoChip(
                    selected = c == selected,
                    onClick = { onSelect(c) },
                    label = label(c),
                    modifier = Modifier.testTag("${tag}_${label(c)}"),
                )
            }
        }
    }
}

@Composable
fun fafoSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = FafoColors.White,
    checkedTrackColor = FafoColors.Red,
    checkedBorderColor = FafoColors.Red,
    uncheckedThumbColor = FafoColors.Steel,
    uncheckedTrackColor = FafoColors.Surface2,
    uncheckedBorderColor = FafoColors.Outline,
)

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
    enabled: Boolean = true,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) FafoColors.White else FafoColors.TextMuted)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = fafoSwitchColors(), modifier = Modifier.testTag(tag))
    }
}

@Composable
fun ScreenTitle(text: String) {
    ws.crandell.fafo.ui.components.AutoFitText(text.uppercase(), style = MaterialTheme.typography.headlineMedium, color = FafoColors.White, minFontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
}

/** AlertDialog in the FAFO style: Surface2, 24dp corners. */
@Composable
fun FafoDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = icon,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = FafoColors.White) },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        containerColor = FafoColors.Surface2,
        textContentColor = FafoColors.TextMuted,
        shape = RoundedCornerShape(24.dp),
    )
}
