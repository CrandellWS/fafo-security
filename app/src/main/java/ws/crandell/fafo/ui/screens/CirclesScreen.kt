package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.Member
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.core.Defaults
import ws.crandell.fafo.net.IncomingInvite
import ws.crandell.fafo.pairing.TrustedDevice

/** Callbacks of the Circles screen (F8). */
interface CirclesActions {
    fun setDeviceLabel(label: String)
    fun createCircle(name: String)
    fun setPermissions(circleId: String, permissions: Set<Permission>)
    fun renameMember(circleId: String, fingerprint: String, label: String)
    fun removeMember(circleId: String, fingerprint: String)
    fun removeCircle(circleId: String)
    fun invite(circleId: String, fingerprint: String)
    fun pairInto(circleId: String)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CirclesScreen(
    circles: List<Circle>,
    trusted: List<TrustedDevice>,
    reachable: Set<String>,
    deviceLabel: String,
    actions: CirclesActions,
    modifier: Modifier = Modifier,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable(deviceLabel) { mutableStateOf(deviceLabel) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenTitle("Circles")
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(Defaults.LABEL_MAX) },
            label = { Text("This device is called") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("deviceLabel"),
        )
        TextButton(onClick = { actions.setDeviceLabel(label) }, enabled = label.isNotBlank() && label != deviceLabel) { Text("Save name") }
        HorizontalDivider()
        circles.forEach { c -> CircleCard(c, trusted, reachable, actions) }
        HorizontalDivider()
        Text("New circle", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it.take(Defaults.LABEL_MAX) }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.weight(1f).testTag("newCircleName"),
            )
            Button(onClick = { actions.createCircle(newName); newName = "" }, enabled = newName.isNotBlank()) { Text("Create") }
        }
        AssistChip(onClick = { newName = "Work" }, label = { Text("Work (Intercom + Reset)") })
        Text("New circles start with Intercom only. Reset and Disarm need your screen lock to grant.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CircleCard(c: Circle, trusted: List<TrustedDevice>, reachable: Set<String>, actions: CirclesActions) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Member?>(null) }
    Card(Modifier.fillMaxWidth().testTag("circle_${c.name}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium)
            Text("Members of ${c.name} may:", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Permission.entries.forEach { p ->
                    FilterChip(
                        selected = p in c.permissions,
                        onClick = { actions.setPermissions(c.id, if (p in c.permissions) c.permissions - p else c.permissions + p) },
                        label = { Text(p.title) },
                        modifier = Modifier.testTag("perm_${c.name}_${p.title}"),
                    )
                }
            }
            if (c.members.isEmpty()) Text("No members yet.", style = MaterialTheme.typography.bodySmall)
            c.members.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (m.fingerprint in reachable) "● " else "○ ", color = if (m.fingerprint in reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    Column(Modifier.weight(1f)) {
                        Text(m.label)
                        Text(m.permissions.sortedBy { it.bit }.joinToString(", ") { it.title }.ifEmpty { "no powers" }, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { renaming = m }) { Text("Rename") }
                    TextButton(onClick = { actions.removeMember(c.id, m.fingerprint) }) { Text("Remove") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.pairInto(c.id) }) { Text("Add trusted device") }
                val candidates = trusted.filter { t -> c.member(t.fingerprint) == null }
                if (candidates.isNotEmpty()) OutlinedButton(onClick = { adding = true }) { Text("Add paired device") }
            }
            if (!c.builtIn) TextButton(onClick = { actions.removeCircle(c.id) }) { Text("Remove circle") }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Invite to ${c.name}") },
            text = {
                Column {
                    trusted.filter { c.member(it.fingerprint) == null }.forEach { t ->
                        TextButton(onClick = { adding = false; actions.invite(c.id, t.fingerprint) }) { Text(t.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    renaming?.let { m ->
        var text by remember(m) { mutableStateOf(m.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename ${m.label}") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(Defaults.LABEL_MAX) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { actions.renameMember(c.id, m.fingerprint, text); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

/** AC8.6: what accepting lets the inviter do to this phone, each power a toggle the invitee can turn off. */
@Composable
fun InviteDialog(invite: IncomingInvite, onAnswer: (Set<Permission>?) -> Unit) {
    var chosen by remember(invite.id) { mutableStateOf(invite.offered) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${invite.peerLabel} invites you to ${invite.circleName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${invite.peerLabel} will be able to:")
                invite.offered.sortedBy { it.bit }.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.powerText, Modifier.weight(1f))
                        Switch(
                            checked = p in chosen,
                            onCheckedChange = { on -> chosen = if (on) chosen + p else chosen - p },
                            modifier = Modifier.testTag("invite_${p.title}"),
                        )
                    }
                }
                if (invite.offered.isEmpty()) Text("nothing (no powers offered)")
                if (chosen.any { it in Permission.GUARDED }) Text("Reset and Disarm need your screen lock.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onAnswer(chosen) }, modifier = Modifier.testTag("inviteAccept")) { Text("Accept") } },
        dismissButton = { TextButton(onClick = { onAnswer(null) }) { Text("Decline") } },
    )
}
