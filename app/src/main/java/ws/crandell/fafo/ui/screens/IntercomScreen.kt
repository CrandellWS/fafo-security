package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.intercom.IntercomUi
import ws.crandell.fafo.ui.components.Banner
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.FafoIcons
import ws.crandell.fafo.ui.components.SectionHeader
import ws.crandell.fafo.ui.components.StateBadge
import ws.crandell.fafo.ui.theme.FafoColors
import ws.crandell.fafo.ui.theme.FafoType

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
    onOpenDevices: () -> Unit = {},
) {
    val intercomCircles = intercomCircles(circles)
    var roomChoice by remember { mutableStateOf(intercomCircles.map { it.id }.toSet()) }
    var circleId by remember { mutableStateOf(intercomCircles.firstOrNull()?.id) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FafoDims.Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (alarming) {
            Banner("Alarm: intercom is off until the incident ends", "", FafoColors.RedText, {}, icon = FafoIcons.ShieldAlert)
        }
        ui.note?.let { FafoCard(Modifier.testTag("intercomNote"), accent = FafoColors.Amber) { Text(it, style = MaterialTheme.typography.bodyLarge, color = FafoColors.White) } }

        if (intercomCircles.isEmpty()) {
            FafoCard(accent = FafoColors.Steel) {
                Text("No circle allows Intercom yet", style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
                Text("Turn on Intercom for a circle on the Devices tab.", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
                OutlinedButton(onClick = onOpenDevices, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) { Text("Open Devices", color = FafoColors.White) }
            }
        } else {
            SectionHeader("Push-to-talk")
            PushToTalkPanel(ui, intercomCircles, reachable, alarming, actions::talkStart, actions::talkStop, circleId, { circleId = it })
            val members = intercomCircles.firstOrNull { it.id == circleId }?.members.orEmpty()

            SectionHeader("Room mode")
            FafoCard(accent = if (ui.roomCircles != null) FafoColors.Guard else null) {
                Text(
                    "Let the chosen circles listen to this room and talk back. A notification shows the whole time; nothing is recorded.",
                    style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    intercomCircles.forEach { c ->
                        FafoChip(
                            selected = c.id in roomChoice,
                            onClick = { roomChoice = if (c.id in roomChoice) roomChoice - c.id else roomChoice + c.id },
                            enabled = ui.roomCircles == null,
                            label = c.name,
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
                if (ui.roomCircles != null) {
                    val names = intercomCircles.filter { it.id in ui.roomCircles }.joinToString(", ") { it.name }
                    StateBadge("ROOM MODE ON · $names can listen", FafoColors.Guard)
                }
                if (ui.listeners.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    ui.listeners.forEach { StateBadge("$it listening", FafoColors.Amber) }
                }
            }

            SectionHeader("Listen to a room")
            if (ui.listeningTo != null) {
                FafoCard(accent = FafoColors.Guard) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Listening to ${ui.listeningTo}", style = MaterialTheme.typography.titleMedium, color = FafoColors.White, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = actions::stopListening, modifier = Modifier.heightIn(min = 48.dp)) { Text("Stop", color = FafoColors.White) }
                    }
                }
            } else {
                members.forEach { m ->
                    OutlinedButton(
                        onClick = { circleId?.let { actions.listen(m.fingerprint, it) } }, enabled = !alarming,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("Listen to ${m.label}", color = if (alarming) FafoColors.TextMuted else FafoColors.White) }
                }
                Text("Works only when that phone has room mode on.", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
            }
        }
    }
}

/** Circles that allow Intercom for at least someone (F10). */
fun intercomCircles(circles: List<Circle>): List<Circle> =
    circles.filter { c -> Permission.INTERCOM in c.permissions || c.members.any { Permission.INTERCOM in it.permissions } }

/**
 * Push-to-talk (AC10.4): circle, target (one device or All, reachable dots) and the hold-to-talk button,
 * plus who is talking. Shared by the Intercom tab and the lock-screen Talk screen (round 5, spec F13).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PushToTalkPanel(
    ui: IntercomUi,
    intercomCircles: List<Circle>,
    reachable: Set<String>,
    alarming: Boolean,
    onTalkStart: (targets: List<String>, circleId: String) -> Unit,
    onTalkStop: () -> Unit,
    circleId: String?,
    onCircle: (String) -> Unit,
) {
    var target by remember(circleId) { mutableStateOf<String?>(null) } // null = All
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            intercomCircles.forEach { c -> FafoChip(selected = c.id == circleId, onClick = { onCircle(c.id) }, label = c.name) }
        }
        val members = intercomCircles.firstOrNull { it.id == circleId }?.members.orEmpty()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FafoChip(selected = target == null, onClick = { target = null }, label = "All", modifier = Modifier.testTag("target_All"))
            members.forEach { m ->
                FafoChip(
                    selected = target == m.fingerprint,
                    onClick = { target = m.fingerprint },
                    label = (if (m.fingerprint in reachable) "● " else "○ ") + m.label,
                    modifier = Modifier.testTag("target_${m.label}"),
                )
            }
        }
        val targets = target?.let { listOf(it) } ?: members.map { it.fingerprint }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            TalkButton(ui.talking, alarming, Modifier.pointerInput(targets, circleId, alarming) {
                detectTapGestures(onPress = {
                    val c = circleId
                    if (alarming || c == null) return@detectTapGestures
                    onTalkStart(targets, c)
                    tryAwaitRelease()
                    onTalkStop()
                })
            })
        }
        ui.talker?.let {
            Banner("$it is talking", "", FafoColors.Guard, {}, Modifier.testTag("talker"), icon = FafoIcons.Mic)
        }
    }
}

/** 200dp round hold-to-talk button: idle navy with a red ring, talking solid red. */
@Composable
private fun TalkButton(talking: Boolean, disabled: Boolean, modifier: Modifier) {
    val bg = when {
        disabled -> FafoColors.Surface1
        talking -> FafoColors.Red
        else -> FafoColors.Surface2
    }
    Box(
        modifier.size(200.dp).clip(CircleShape).background(bg)
            .border(if (talking) 10.dp else 4.dp, if (disabled) FafoColors.Outline else if (talking) FafoColors.RedText.copy(alpha = 0.6f) else FafoColors.Red, CircleShape)
            .semantics { role = Role.Button }
            .testTag("talkButton"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(FafoIcons.Mic, contentDescription = null, tint = if (disabled) FafoColors.TextMuted else FafoColors.White, modifier = Modifier.size(56.dp))
            Text(
                if (talking) "TALKING…" else "HOLD\nTO TALK",
                style = FafoType.action.copy(fontSize = FafoType.action.fontSize * 0.8f, lineHeight = FafoType.action.lineHeight * 0.8f),
                color = if (disabled) FafoColors.TextMuted else FafoColors.White, textAlign = TextAlign.Center,
            )
        }
    }
}
