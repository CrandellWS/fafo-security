package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.Member
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.core.Defaults
import ws.crandell.fafo.net.IncomingInvite
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.SectionHeader
import ws.crandell.fafo.ui.theme.FafoColors

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

@Composable
fun fafoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FafoColors.RedText,
    unfocusedBorderColor = FafoColors.Outline,
    focusedLabelColor = FafoColors.RedText,
    unfocusedLabelColor = FafoColors.TextMuted,
    cursorColor = FafoColors.RedText,
    focusedTextColor = FafoColors.White,
    unfocusedTextColor = FafoColors.White,
)

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
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FafoDims.Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(FafoDims.Gap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(Defaults.LABEL_MAX) },
                label = { Text("This phone is called") },
                singleLine = true,
                colors = fafoFieldColors(),
                modifier = Modifier.weight(1f).testTag("deviceLabel"),
            )
            OutlinedButton(
                onClick = { actions.setDeviceLabel(label) }, enabled = label.isNotBlank() && label != deviceLabel,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("Save") }
        }
        circles.forEach { c -> CircleCard(c, trusted, reachable, actions) }
        SectionHeader("New circle")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it.take(Defaults.LABEL_MAX) }, singleLine = true,
                label = { Text("Name") }, colors = fafoFieldColors(), modifier = Modifier.weight(1f).testTag("newCircleName"),
            )
            Button(
                onClick = { actions.createCircle(newName); newName = "" }, enabled = newName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FafoColors.Red, contentColor = FafoColors.White),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("Create") }
        }
        FafoChip(selected = false, onClick = { newName = "Work" }, label = "Work (Intercom + Reset)")
        Text(
            "New circles start with Intercom only. Reset and Disarm need your screen lock to grant.",
            style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CircleCard(c: Circle, trusted: List<TrustedDevice>, reachable: Set<String>, actions: CirclesActions) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Member?>(null) }
    var menu by remember { mutableStateOf(false) }
    FafoCard(Modifier.testTag("circle_${c.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, style = MaterialTheme.typography.titleLarge, color = FafoColors.White, modifier = Modifier.weight(1f))
            Text("${c.members.size} ${if (c.members.size == 1) "member" else "members"}", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
            if (!c.builtIn) {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Circle options", tint = FafoColors.TextMuted)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Remove circle") }, onClick = { menu = false; actions.removeCircle(c.id) })
                    }
                }
            }
        }
        Text("Members may:", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Permission.entries.forEach { p ->
                FafoChip(
                    selected = p in c.permissions,
                    onClick = { actions.setPermissions(c.id, if (p in c.permissions) c.permissions - p else c.permissions + p) },
                    label = p.title,
                    modifier = Modifier.testTag("perm_${c.name}_${p.title}"),
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = FafoColors.Outline)
        if (c.members.isEmpty()) Text("No members yet.", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
        c.members.forEach { m -> MemberRow(m, m.fingerprint in reachable, { renaming = m }, { actions.removeMember(c.id, m.fingerprint) }) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = { actions.pairInto(c.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add trusted device", color = FafoColors.White)
            }
            val candidates = trusted.filter { t -> c.member(t.fingerprint) == null }
            if (candidates.isNotEmpty()) {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add paired device", color = FafoColors.White) }
            }
        }
    }
    if (adding) {
        FafoDialog(
            onDismissRequest = { adding = false },
            title = "Invite to ${c.name}",
            text = {
                Column {
                    trusted.filter { c.member(it.fingerprint) == null }.forEach { t ->
                        TextButton(onClick = { adding = false; actions.invite(c.id, t.fingerprint) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(t.name, color = FafoColors.White)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel", color = FafoColors.White) } },
        )
    }
    renaming?.let { m ->
        var text by remember(m) { mutableStateOf(m.label) }
        FafoDialog(
            onDismissRequest = { renaming = null },
            title = "Rename ${m.label}",
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(Defaults.LABEL_MAX) }, singleLine = true, colors = fafoFieldColors()) },
            confirmButton = { TextButton(onClick = { actions.renameMember(c.id, m.fingerprint, text); renaming = null }) { Text("Save", color = FafoColors.White) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = FafoColors.White) } },
        )
    }
}

@Composable
private fun MemberRow(m: Member, online: Boolean, onRename: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("member_${m.label}"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (online) FafoColors.Guard else FafoColors.Surface2))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.label, style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
            Text(
                (if (online) "Reachable · " else "Offline · ") +
                    m.permissions.sortedBy { it.bit }.joinToString(", ") { it.title }.ifEmpty { "no powers" },
                style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${m.label}", tint = FafoColors.TextMuted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

/** AC8.6: what accepting lets the inviter do to this phone, each power a toggle the invitee can turn off. */
@Composable
fun InviteDialog(invite: IncomingInvite, onAnswer: (Set<Permission>?) -> Unit) {
    var chosen by remember(invite.id) { mutableStateOf(invite.offered) }
    FafoDialog(
        onDismissRequest = {},
        title = "${invite.peerLabel} invites you to ${invite.circleName}",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${invite.peerLabel} will be able to:", style = MaterialTheme.typography.bodyLarge, color = FafoColors.White)
                invite.offered.sortedBy { it.bit }.forEach { p ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.powerText.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium, color = FafoColors.White, modifier = Modifier.weight(1f))
                        Switch(
                            checked = p in chosen,
                            onCheckedChange = { on -> chosen = if (on) chosen + p else chosen - p },
                            colors = fafoSwitchColors(),
                            modifier = Modifier.testTag("invite_${p.title}"),
                        )
                    }
                }
                if (invite.offered.isEmpty()) Text("nothing (no powers offered)")
                if (chosen.any { it in Permission.GUARDED }) {
                    Text("Reset and Disarm need your screen lock.", style = MaterialTheme.typography.bodyMedium, color = FafoColors.Amber)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAnswer(chosen) },
                colors = ButtonDefaults.buttonColors(containerColor = FafoColors.Red, contentColor = FafoColors.White),
                modifier = Modifier.heightIn(min = 48.dp).testTag("inviteAccept"),
            ) { Text("Accept") }
        },
        dismissButton = { TextButton(onClick = { onAnswer(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Decline", color = FafoColors.White) } },
    )
}

/** Devices tab: one place for circles and paired phones, switched with a two-way control. */
enum class DevicesPane(val label: String) { CIRCLES("Circles"), PAIRED("Paired phones") }

@Composable
fun DevicesSwitcher(pane: DevicesPane, onPane: (DevicesPane) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(horizontal = FafoDims.Gutter).padding(top = 14.dp)) {
        DevicesPane.entries.forEachIndexed { i, p ->
            SegmentedButton(
                selected = p == pane,
                onClick = { onPane(p) },
                shape = SegmentedButtonDefaults.itemShape(i, DevicesPane.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = FafoColors.Red, activeContentColor = FafoColors.White, activeBorderColor = FafoColors.Red,
                    inactiveContainerColor = FafoColors.Surface1, inactiveContentColor = FafoColors.White, inactiveBorderColor = FafoColors.Outline,
                ),
                modifier = Modifier.heightIn(min = 48.dp).testTag("pane_${p.name}"),
            ) { Text(p.label, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

