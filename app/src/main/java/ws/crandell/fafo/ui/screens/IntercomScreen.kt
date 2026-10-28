package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.intercom.IntercomUi

/** Callbacks of the Intercom screen (F10). */
interface IntercomActions {
    fun setRoomMode(on: Boolean, circles: Set<String>)
    fun listen(fingerprint: String, circleId: String)
    fun stopListening()
    fun talkStart(targets: List<String>, circleId: String)
    fun talkStop()
}

/**
 * F10: intercom room mode (this phone can be listened to by the chosen circles) and push-to-talk to one
 * device or "All". Plain voice between your own devices; nothing is recorded.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntercomScreen(
    ui: IntercomUi,
    circles: List<Circle>,
    reachable: Set<String>,
    alarming: Boolean,
    actions: IntercomActions,
    modifier: Modifier = Modifier,
) {
    val intercomCircles = circles.filter { c -> Permission.INTERCOM in c.permissions || c.members.any { Permission.INTERCOM in it.permissions } }
    var roomChoice by remember { mutableStateOf(intercomCircles.map { it.id }.toSet()) }
    var circleId by remember { mutableStateOf(intercomCircles.firstOrNull()?.id) }
    var target by remember { mutableStateOf<String?>(null) } // null = All
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenTitle("Intercom")
        if (alarming) Text("Alarm: intercom is off until the incident ends.", color = MaterialTheme.colorScheme.error)
        ui.note?.let { Text(it, modifier = Modifier.testTag("intercomNote")) }

        Text("Intercom room mode", style = MaterialTheme.typography.titleMedium)
        Text("Lets the chosen circles listen to this phone and talk back. A notification shows the whole time.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            intercomCircles.forEach { c ->
                FilterChip(
                    selected = c.id in roomChoice,
                    onClick = { roomChoice = if (c.id in roomChoice) roomChoice - c.id else roomChoice + c.id },
                    enabled = ui.roomCircles == null,
                    label = { Text(c.name) },
                )
            }
        }
        SwitchRow(
            if (ui.roomCircles != null) "Room mode on" else "Room mode off",
            ui.roomCircles != null,
            { on -> actions.setRoomMode(on, roomChoice) },
            "roomMode",
            enabled = !alarming && (ui.roomCircles != null || roomChoice.isNotEmpty()),
        )
        if (ui.listeners.isNotEmpty()) Text("${ui.listeners.joinToString(", ")} listening", color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()

        if (intercomCircles.isEmpty()) {
            Text("No circle allows Intercom yet. Turn on Intercom for a circle on the Circles screen.")
            return@Column
        }
        Text("Push-to-talk", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            intercomCircles.forEach { c -> FilterChip(selected = c.id == circleId, onClick = { circleId = c.id; target = null }, label = { Text(c.name) }) }
        }
        val circle = intercomCircles.firstOrNull { it.id == circleId }
        val members = circle?.members.orEmpty()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = target == null, onClick = { target = null }, label = { Text("All") }, modifier = Modifier.testTag("target_All"))
            members.forEach { m ->
                FilterChip(
                    selected = target == m.fingerprint,
                    onClick = { target = m.fingerprint },
                    label = { Text((if (m.fingerprint in reachable) "● " else "○ ") + m.label) },
                    modifier = Modifier.testTag("target_${m.label}"),
                )
            }
        }
        val targets = target?.let { listOf(it) } ?: members.map { it.fingerprint }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (ui.talking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier.fillMaxWidth().height(140.dp).testTag("talkButton").pointerInput(targets, circleId, alarming) {
                detectTapGestures(onPress = {
                    if (alarming || circleId == null) return@detectTapGestures
                    actions.talkStart(targets, circleId!!)
                    tryAwaitRelease()
                    actions.talkStop()
                })
            },
        ) {
            Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                Text(if (ui.talking) "Talking..." else "Hold to talk", fontSize = 28.sp)
            }
        }
        ui.talker?.let { Text("$it is talking", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("talker")) }
        HorizontalDivider()
        Text("Listen to a room", style = MaterialTheme.typography.titleMedium)
        if (ui.listeningTo != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Listening to ${ui.listeningTo}", Modifier.weight(1f))
                TextButton(onClick = actions::stopListening) { Text("Stop") }
            }
        } else {
            members.forEach { m ->
                TextButton(onClick = { circleId?.let { actions.listen(m.fingerprint, it) } }, enabled = !alarming) { Text("Listen to ${m.label}") }
            }
            Text("Works only when that phone has intercom room mode on.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
