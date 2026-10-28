package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.ui.OnboardingStep

enum class Screen { HOME, SETTINGS, INCIDENTS, TRUSTED, CIRCLES, INTERCOM, ONBOARDING }

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
    val alarming: Boolean = false,
    val photosOff: Boolean = false,
    val awaitingPower: Boolean = false,
    val checklist: List<OnboardingStep> = emptyList(),
)

/** Text on the Arm button: spec section 2, DISARMED to ARMED. */
fun armButtonLabel(s: HomeUiState): String = when {
    !s.secureLock -> "Set a screen lock first"
    !s.plugged -> "Plug in to arm"
    else -> "Arm"
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onOpen: (Screen) -> Unit,
    onStep: (OnboardingStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("FAFO Security", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            state.stateLabel,
            modifier = Modifier.testTag("stateLabel"),
            style = MaterialTheme.typography.titleLarge,
            color = if (state.alarming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (state.plugged) "Plugged in" else "Not plugged in",
            modifier = Modifier.testTag("pluggedLabel"),
        )
        if (state.awaitingPower) Text("Reset remotely: plug in to protect again.")
        if (state.armed && state.photosOff) Text("Photos off", color = MaterialTheme.colorScheme.error)

        if (state.armed || state.alarming) {
            Button(
                onClick = onDisarm,
                enabled = !state.confirming,
                modifier = Modifier.fillMaxWidth().height(96.dp).testTag("disarmButton"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) { Text("Disarm", fontSize = 28.sp) }
        } else {
            Button(
                onClick = onArm,
                enabled = state.plugged && state.secureLock && !state.confirming,
                modifier = Modifier.fillMaxWidth().height(96.dp).testTag("armButton"),
            ) { Text(armButtonLabel(state), fontSize = 24.sp) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onOpen(Screen.SETTINGS) }) { Text("Settings") }
            OutlinedButton(onClick = { onOpen(Screen.INCIDENTS) }) { Text("Incidents") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onOpen(Screen.TRUSTED) }) { Text("Trusted devices") }
            OutlinedButton(onClick = { onOpen(Screen.CIRCLES) }) { Text("Circles") }
        }
        OutlinedButton(onClick = { onOpen(Screen.INTERCOM) }) { Text("Intercom") }

        if (state.checklist.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().testTag("checklist")) {
                Column(Modifier.padding(12.dp)) {
                    Text("Setup checklist", style = MaterialTheme.typography.titleMedium)
                    state.checklist.forEach { step ->
                        TextButton(onClick = { onStep(step) }, modifier = Modifier.testTag("check_${step.name}")) {
                            Text("To do: ${step.title}")
                        }
                    }
                }
            }
        }
    }
}
