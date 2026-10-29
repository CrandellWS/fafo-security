package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.intercom.IntercomUi
import ws.crandell.fafo.ui.components.Banner
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.FafoIcons
import ws.crandell.fafo.ui.theme.FafoColors

/** What the Talk screen can do. Push-to-talk only: no Disarm, no settings, no room mode, no listening. */
interface TalkActions {
    fun talkStart(targets: List<String>, circleId: String)
    fun talkStop()
    /** Microphone not allowed yet: ask (Android shows its own prompt, after the screen lock if locked). */
    fun allowMic()
    /** Open FAFO itself: Android asks for the screen lock first when the phone is locked. */
    fun openApp()
    fun close()
}

/**
 * Talk (round 5, spec F13): the push-to-talk screen opened from the armed notification's Talk action or the
 * FAFO Talk tile. It shows over the lock screen, so it offers push-to-talk and nothing else: the mic is
 * open only while the button is held, and any Disarm still needs the real screen lock in the app.
 */
@Composable
fun TalkScreen(
    ui: IntercomUi,
    circles: List<Circle>,
    reachable: Set<String>,
    alarming: Boolean,
    ceLocked: Boolean,
    micAllowed: Boolean,
    actions: TalkActions,
) {
    val intercomCircles = intercomCircles(circles)
    var circleId by remember(intercomCircles.map { it.id }) { mutableStateOf(intercomCircles.firstOrNull()?.id) }
    Column(
        Modifier.fillMaxSize().background(FafoColors.Ink).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = FafoDims.Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FAFO Talk", style = MaterialTheme.typography.headlineSmall, color = FafoColors.White, modifier = Modifier.weight(1f))
            TextButton(onClick = actions::close, modifier = Modifier.heightIn(min = 48.dp).testTag("talkClose")) {
                Text("Close", color = FafoColors.White)
            }
        }
        Text(
            "Hold to talk to your own phones. Nothing here can disarm FAFO or change its settings.",
            style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
        )
        when {
            ceLocked -> FafoCard(Modifier.testTag("talkCeLocked"), accent = FafoColors.Amber) {
                Text("Unlock once after a restart to use Talk.", style = MaterialTheme.typography.bodyLarge, color = FafoColors.White)
                Text(
                    "Your circles and keys stay locked until the first unlock.",
                    style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
                )
            }
            intercomCircles.isEmpty() -> FafoCard(Modifier.testTag("talkNoCircles"), accent = FafoColors.Steel) {
                Text("No circle allows Intercom yet", style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
                Text("Turn on Intercom for a circle on the Devices tab in FAFO.", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
            }
            !micAllowed -> FafoCard(Modifier.testTag("talkNoMic"), accent = FafoColors.Amber) {
                Text("Allow the microphone to talk", style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
                Text(
                    "Used only while you hold the button. Nothing is recorded.",
                    style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
                )
                OutlinedButton(onClick = actions::allowMic, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).testTag("talkAllowMic")) {
                    Text("Allow microphone", color = FafoColors.White)
                }
            }
            else -> {
                if (alarming) {
                    Banner("Alarm: push-to-talk is off until the incident ends", "", FafoColors.RedText, {}, icon = FafoIcons.ShieldAlert)
                }
                ui.note?.let {
                    FafoCard(Modifier.testTag("intercomNote"), accent = FafoColors.Amber) {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = FafoColors.White)
                    }
                }
                PushToTalkPanel(ui, intercomCircles, reachable, alarming, actions::talkStart, actions::talkStop, circleId, { circleId = it })
            }
        }
        OutlinedButton(onClick = actions::openApp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("talkOpenApp")) {
            Text("Open FAFO (asks for your screen lock)", color = FafoColors.White)
        }
    }
}
