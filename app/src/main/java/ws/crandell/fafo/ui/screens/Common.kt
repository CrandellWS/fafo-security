package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

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
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { c ->
                FilterChip(
                    selected = c == selected,
                    onClick = { onSelect(c) },
                    label = { Text(label(c)) },
                    modifier = Modifier.testTag("${tag}_${label(c)}"),
                )
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag(tag))
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
}
