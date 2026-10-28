package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.ui.OnboardingStatus
import ws.crandell.fafo.ui.OnboardingStep

fun actionLabel(step: OnboardingStep): String = when (step) {
    OnboardingStep.INTRO -> "Got it"
    OnboardingStep.SECURE_LOCK -> "Open lock settings"
    OnboardingStep.NOTIFICATIONS, OnboardingStep.CAMERA -> "Allow"
    OnboardingStep.FULL_SCREEN, OnboardingStep.DND -> "Open settings"
    OnboardingStep.BATTERY -> "Allow"
    OnboardingStep.BUILT_IN -> "Open security settings"
    OnboardingStep.SELF_TEST -> "Run self-test"
}

@Composable
fun OnboardingScreen(
    status: OnboardingStatus,
    onAction: (OnboardingStep) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSamsungBattery: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Set up FAFO Security")
        OnboardingStep.entries.forEachIndexed { i, step ->
            val done = status.isDone(step)
            Card(Modifier.fillMaxWidth().testTag("step_${step.name}")) {
                Column(Modifier.padding(12.dp)) {
                    Text("${i + 1}. ${step.title}", style = MaterialTheme.typography.titleMedium)
                    Text(step.detail, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (done) "Done" else "To do",
                            modifier = Modifier.testTag("status_${step.name}").padding(top = 12.dp),
                            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        if (!done || step == OnboardingStep.SELF_TEST) {
                            Button(onClick = { onAction(step) }) { Text(actionLabel(step)) }
                        }
                        if (step == OnboardingStep.BATTERY) {
                            TextButton(onClick = onSamsungBattery) { Text("Samsung steps") }
                        }
                    }
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done for now") }
    }
}
