package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.Defaults
import ws.crandell.fafo.ui.OnboardingStep
import ws.crandell.fafo.ui.components.ActionKind
import ws.crandell.fafo.ui.components.AutoFitText
import ws.crandell.fafo.ui.components.Banner
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.FafoIcons
import ws.crandell.fafo.ui.components.PrimaryActionButton
import ws.crandell.fafo.ui.components.QuickTile
import ws.crandell.fafo.ui.components.StateBadge
import ws.crandell.fafo.ui.components.StateKind
import ws.crandell.fafo.ui.components.StatusHero
import ws.crandell.fafo.ui.components.kind
import ws.crandell.fafo.ui.components.stateColor
import ws.crandell.fafo.ui.theme.FafoType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.ui.theme.FafoColors

/** Destinations Home can open (bottom-bar tabs or pushed screens). */
enum class Screen { HOME, SETTINGS, INCIDENTS, TRUSTED, CIRCLES, INTERCOM, ONBOARDING, HELP }

data class HomeUiState(
    /** The live alarm state the labels are derived from (screens color by it). */
    val alarmState: AlarmState = AlarmState.Disarmed,
    /** AC1.7: the screen-lock / fingerprint confirmation for Arm or Disarm is running. */
    val confirming: Boolean = false,
    val plugged: Boolean = false,
    val secureLock: Boolean = false,
    /** Human-readable state: Disarmed, Armed, Plug me back in (n s), ALARM. */
    val stateLabel: String = "Disarmed",
    val armed: Boolean = false,
    /** The ARMING countdown is running ([armingLeftS] seconds left). */
    val arming: Boolean = false,
    val armingLeftS: Long = 0,
    val alarming: Boolean = false,
    val photosOff: Boolean = false,
    val awaitingPower: Boolean = false,
    val checklist: List<OnboardingStep> = emptyList(),
    val graceS: Int = Defaults.UNLOCK_GRACE_S,
    val incidents: Int = 0,
    val paired: Int = 0,
)

/** Text on the Arm button: spec section 2, DISARMED to ARMED. */
fun armButtonLabel(s: HomeUiState): String = when {
    !s.secureLock -> "Set a screen lock first"
    !s.plugged -> "Plug in to arm"
    else -> "Arm"
}

private fun stateWord(s: HomeUiState): String = when (s.alarmState.kind()) {
    StateKind.DISARMED -> if (s.armed) "Armed" else "Disarmed"
    StateKind.ARMING -> "Arming"
    StateKind.ARMED -> "Armed"
    StateKind.PRE_ALARM -> if ((s.alarmState as? AlarmState.PreAlarm)?.cause == ws.crandell.fafo.core.IncidentCause.MOTION) "Hands off" else "Plug me back in"
    StateKind.TRIGGERED -> "Alarm"
}

private fun HomeUiState.kindShown(): StateKind =
    if (alarmState == AlarmState.Disarmed && armed) StateKind.ARMED else alarmState.kind()

@Composable
fun HomeScreen(
    state: HomeUiState,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onOpen: (Screen) -> Unit,
    onStep: (OnboardingStep) -> Unit,
    modifier: Modifier = Modifier,
    onCancelArming: () -> Unit = {},
    onOpenStatus: (() -> Unit)? = null,
) {
    val kind = state.kindShown()
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FafoDims.Gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(FafoDims.Gap),
    ) {
        when {
            !state.secureLock -> Banner(
                "Screen lock required", "Fix", FafoColors.RedText, { onOpen(Screen.ONBOARDING) },
                Modifier.testTag("checklist"), icon = Icons.Filled.Lock,
            )
            state.checklist.isNotEmpty() -> Banner(
                "${state.checklist.size} setup ${if (state.checklist.size == 1) "step" else "steps"} left", "Finish setup",
                FafoColors.Amber, { onOpen(Screen.ONBOARDING) }, Modifier.testTag("checklist"), icon = FafoIcons.Setup,
            )
        }

        StatusHero(kind, stateWord(state), wordTag = "stateLabel") {
            when (kind) {
                StateKind.DISARMED -> {
                    PlugLine(state.plugged, if (state.plugged && state.secureLock) "ready to arm" else null)
                }
                StateKind.ARMING -> {
                    AutoFitText(
                        "${state.armingLeftS}", style = FafoType.countdown.copy(fontSize = 72.sp, lineHeight = 76.sp),
                        color = stateColor(StateKind.ARMING), sample = "88",
                        modifier = Modifier.testTag("armingCountdown").semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text("Armed in ${state.armingLeftS} s. Leave it plugged in.", style = MaterialTheme.typography.bodyLarge, color = FafoColors.White)
                    PlugLine(state.plugged, null)
                }
                StateKind.ARMED -> {
                    PlugLine(state.plugged, if (state.awaitingPower) null else "unplugging sets it off")
                    if (state.awaitingPower) Text("Reset remotely: plug in to protect again.", style = MaterialTheme.typography.bodyLarge, color = FafoColors.Amber)
                }
                StateKind.PRE_ALARM -> {
                    val left = state.stateLabel.substringAfter("(", "").removeSuffix(")")
                    Text(if (left.isEmpty()) "Siren soon" else "Siren in $left", style = MaterialTheme.typography.titleLarge, color = FafoColors.Amber)
                    PlugLine(state.plugged, null)
                }
                StateKind.TRIGGERED -> {
                    Text("The siren is sounding. Disarm, or lock and unlock with your PIN.", style = MaterialTheme.typography.bodyLarge, color = FafoColors.White)
                    PlugLine(state.plugged, null)
                }
            }
            if ((state.armed || state.alarming) && state.photosOff) {
                Spacer(Modifier.height(4.dp))
                StateBadge("Photos off", FafoColors.Amber)
            }
        }

        if (state.arming) {
            PrimaryActionButton(
                "Cancel arming", ActionKind.DISARM, onCancelArming, Modifier.testTag("cancelArmingButton"),
            )
        } else if (state.armed || state.alarming) {
            PrimaryActionButton(
                "Disarm", ActionKind.DISARM, onDisarm, Modifier.testTag("disarmButton"), busy = state.confirming,
            )
        } else {
            PrimaryActionButton(
                armButtonLabel(state), ActionKind.ARM, onArm, Modifier.testTag("armButton"),
                enabled = state.plugged && state.secureLock, busy = state.confirming,
            )
        }
        if (onOpenStatus != null && (state.arming || state.armed || state.alarming)) {
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenStatus, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("openStatus"),
            ) { Text("Full-screen status (traffic light)", style = MaterialTheme.typography.titleMedium, color = FafoColors.White) }
        }
        Text(
            when {
                state.arming -> "Cancel needs no fingerprint: cancelling leaves the phone unprotected."
                state.armed || state.alarming -> "The FAFO notification stays while armed. Disarm asks for your fingerprint or screen lock" +
                    disarmGraceNote(state.graceS)
                else -> "Arm asks for your fingerprint or screen lock" + graceNote(state.graceS)
            },
            style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(FafoDims.Gap)) {
            QuickTile(FafoIcons.Camera, "Incidents", if (state.incidents == 0) "None yet" else "${state.incidents} saved", { onOpen(Screen.INCIDENTS) }, Modifier.weight(1f).testTag("tileIncidents"))
            QuickTile(FafoIcons.Devices, "Devices", if (state.paired == 0) "Pair a phone" else "${state.paired} paired", { onOpen(Screen.CIRCLES) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FafoDims.Gap)) {
            QuickTile(FafoIcons.Mic, "Intercom", "Push-to-talk", { onOpen(Screen.INTERCOM) }, Modifier.weight(1f))
            QuickTile(FafoIcons.Help, "Help", "Guide, Samsung tips", { onOpen(Screen.HELP) }, Modifier.weight(1f))
        }

        if (state.checklist.isNotEmpty()) {
            FafoCard {
                Text("Next setup steps", style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
                state.checklist.take(3).forEach { step ->
                    TextButton(
                        onClick = { onStep(step) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("check_${step.name}"),
                    ) {
                        Text(step.title, style = MaterialTheme.typography.bodyLarge, color = FafoColors.Amber, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FafoColors.Amber)
                    }
                }
                if (state.checklist.size > 3) {
                    TextButton(onClick = { onOpen(Screen.ONBOARDING) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("See all ${state.checklist.size} in Setup", color = FafoColors.TextMuted)
                    }
                }
            }
        }
    }
}

/** Bill 2026-10-28: after arming, only a lock and unlock AFTER the arm time skips the Disarm prompt. */
private fun disarmGraceNote(graceS: Int): String =
    if (graceS == 0) "." else ", unless you locked and unlocked the phone after arming, in the last ${graceLabel(graceS)}."

private fun graceNote(graceS: Int): String =
    if (graceS == 0) "." else ", unless you unlocked the phone in the last ${graceLabel(graceS)}."

@Composable
private fun PlugLine(plugged: Boolean, extra: String?) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            FafoIcons.Bolt, contentDescription = null, tint = if (plugged) FafoColors.Guard else FafoColors.Amber,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (plugged) "Plugged in" else "Not plugged in",
            style = MaterialTheme.typography.bodyLarge,
            color = if (plugged) FafoColors.White else FafoColors.Amber,
            modifier = Modifier.testTag("pluggedLabel"),
        )
        if (extra != null) {
            Text(" · $extra", style = MaterialTheme.typography.bodyLarge, color = FafoColors.TextMuted, modifier = Modifier.weight(1f))
        }
    }
}
