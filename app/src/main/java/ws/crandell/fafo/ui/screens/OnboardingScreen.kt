package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.ui.OnboardingStatus
import ws.crandell.fafo.ui.OnboardingStep
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.SectionHeader
import ws.crandell.fafo.ui.components.ShieldLogo
import ws.crandell.fafo.ui.theme.FafoBrand
import ws.crandell.fafo.ui.theme.FafoColors

fun actionLabel(step: OnboardingStep): String = when (step) {
    OnboardingStep.INTRO -> "Got it"
    OnboardingStep.SECURE_LOCK -> "Set screen lock"
    OnboardingStep.NOTIFICATIONS, OnboardingStep.CAMERA -> "Allow"
    OnboardingStep.FULL_SCREEN, OnboardingStep.DND -> "Open settings"
    OnboardingStep.BATTERY -> "Allow"
    OnboardingStep.BUILT_IN -> "Open settings"
    OnboardingStep.SELF_TEST -> "Run self-test"
    OnboardingStep.PHOTO_EMAIL -> "Set up email"
    OnboardingStep.OPEN_WIFI -> "Allow"
    OnboardingStep.FLASH -> "Test flash"
}

/** One short line per step; the full story is in Help ("Learn more"). */
fun stepSummary(step: OnboardingStep): String = when (step) {
    OnboardingStep.INTRO -> "Protects this phone only, with a notification whenever it is armed. Turn off Smart Lock / Extend Unlock."
    OnboardingStep.SECURE_LOCK -> "A PIN, pattern or password. Without one a thief just swipes to unlock."
    OnboardingStep.NOTIFICATIONS -> "Needed for the armed and alarm notifications."
    OnboardingStep.CAMERA -> "Photos of whoever unplugs the phone. They stay on this phone."
    OnboardingStep.FULL_SCREEN -> "Lets the red alarm screen open by itself over the lock screen."
    OnboardingStep.DND -> "Lets the alarm get loud under Do Not Disturb."
    OnboardingStep.BATTERY -> "Stops the phone from putting FAFO to sleep."
    OnboardingStep.BUILT_IN -> "Turn on Theft Detection Lock, Offline Device Lock and Identity Check."
    OnboardingStep.SELF_TEST -> "A 1 s chirp, a 1 s siren and a test photo (not saved)."
    OnboardingStep.PHOTO_EMAIL -> "Optional. Trigger photos go to your inbox through your own mail account. Send a test email to finish."
    OnboardingStep.OPEN_WIFI -> "Optional, Android 13+. Android asks once whether FAFO may suggest Wi-Fi networks: allow it now."
    OnboardingStep.FLASH -> "The flash strobes during the alarm. Turn it off in Settings > Alarm."
}

/** Samsung battery steps, shown inline under the Battery step (they used to hide in a dialog). */
val SAMSUNG_BATTERY_STEPS = listOf(
    "Settings > Apps > FAFO Security > Battery > Unrestricted.",
    "Battery > Background usage limits: take FAFO out of Sleeping and Deep sleeping apps; add it to Never sleeping apps.",
    "Turn off \"Put unused apps to sleep\". Check again after firmware updates.",
)

/**
 * Setup tab (spec F7): the to-do checklist, always reachable from the bottom bar. To-do steps first, done
 * steps below, F7 order inside each group. Each step links to its Help topic.
 */
@Composable
fun OnboardingScreen(
    status: OnboardingStatus,
    onAction: (OnboardingStep) -> Unit,
    onLearnMore: (OnboardingStep) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAppSettings: () -> Unit = {},
    samsungExpandedInitially: Boolean = false,
) {
    val steps = status.shown.filterNot { it.optional }
    val todo = steps.filterNot(status::isDone)
    val done = steps.filter(status::isDone)
    val recommended = status.recommended
    val optionalDone = status.shown.filter { it.optional && status.isDone(it) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FafoDims.Gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ws.crandell.fafo.ui.components.FafoLockup(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp), wordmarkHeight = 26.dp)
        FafoCard(accent = if (todo.isEmpty()) FafoColors.Guard else FafoColors.Amber, shape = RoundedCornerShape(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldLogo(64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    ws.crandell.fafo.ui.components.AutoFitText(
                        if (todo.isEmpty()) "All set" else "${done.size} of ${steps.size} done",
                        style = MaterialTheme.typography.headlineMedium, color = FafoColors.White, minFontSize = 16.sp,
                        modifier = Modifier.testTag("setupProgress"),
                    )
                    Text(
                        if (todo.isEmpty()) "FAFO can sound the alarm and show the red screen while the phone is locked."
                        else "Finish these so the alarm works while the phone is locked.",
                        style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { done.size / steps.size.toFloat() },
                color = FafoColors.Guard, trackColor = FafoColors.Surface2, strokeCap = StrokeCap.Round, gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(10.dp),
            )
        }
        if (todo.isNotEmpty()) SectionHeader("To do (${todo.size})")
        todo.forEach { step -> StepItem(step, false, onAction, onLearnMore, onOpenAppSettings, samsungExpandedInitially, status) }
        if (recommended.isNotEmpty()) SectionHeader("Recommended (${recommended.size})")
        recommended.forEach { step -> StepItem(step, false, onAction, onLearnMore, onOpenAppSettings, false, status) }
        if (done.isNotEmpty() || optionalDone.isNotEmpty()) SectionHeader("Done (${done.size + optionalDone.size})")
        (done + optionalDone).forEach { step -> StepItem(step, true, onAction, onLearnMore, onOpenAppSettings, false, status) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepItem(
    step: OnboardingStep,
    done: Boolean,
    onAction: (OnboardingStep) -> Unit,
    onLearnMore: (OnboardingStep) -> Unit,
    onOpenAppSettings: () -> Unit,
    samsungExpandedInitially: Boolean,
    status: OnboardingStatus = OnboardingStatus(),
) {
    var samsung by rememberSaveable(step) { mutableStateOf(samsungExpandedInitially) }
    FafoCard(Modifier.testTag("step_${step.name}"), color = if (done) FafoColors.Ink else FafoColors.Surface1) {
        Row(verticalAlignment = Alignment.Top) {
            StepMark(done)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
                Text(
                    when {
                        step == OnboardingStep.FLASH -> if (status.hasFlash) "Ready" else "No flash on this phone"
                        done -> "Done"
                        step.optional -> "Recommended"
                        else -> "To do"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (done) FafoColors.Guard else FafoColors.Amber,
                    modifier = Modifier.testTag("status_${step.name}"),
                )
                if (!done) Text(stepSummary(step), style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (!done || step == OnboardingStep.SELF_TEST || (step == OnboardingStep.FLASH && status.hasFlash)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, start = 42.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) {
                    OutlinedButton(onClick = { onAction(step) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel(step)) }
                } else {
                    Button(
                        onClick = { onAction(step) },
                        colors = ButtonDefaults.buttonColors(containerColor = FafoColors.Red, contentColor = FafoBrand.White),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("action_${step.name}"),
                    ) { Text(actionLabel(step)) }
                }
                TextButton(onClick = { onLearnMore(step) }, modifier = Modifier.heightIn(min = 48.dp).testTag("learn_${step.name}")) {
                    Text("Learn more", color = FafoColors.TextMuted)
                }
            }
        }
        if (step == OnboardingStep.BATTERY && !done) {
            TextButton(onClick = { samsung = !samsung }, modifier = Modifier.padding(start = 34.dp).heightIn(min = 48.dp).testTag("samsungSteps")) {
                Text(if (samsung) "Hide Samsung steps" else "Samsung steps", color = FafoColors.Amber)
            }
            if (samsung) {
                Column(Modifier.padding(start = 42.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SAMSUNG_BATTERY_STEPS.forEachIndexed { i, s -> NumberedLine(i + 1, s) }
                    OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open FAFO's app settings") }
                }
            }
        }
    }
}

@Composable
private fun StepMark(done: Boolean) {
    if (done) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(FafoColors.Guard), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = FafoColors.Ink, modifier = Modifier.size(20.dp))
        }
    } else {
        Box(Modifier.size(28.dp).clip(CircleShape).border(3.dp, FafoColors.Amber, CircleShape))
    }
}

@Composable
fun NumberedLine(n: Int, text: String) {
    Row {
        Box(Modifier.size(24.dp).clip(CircleShape).background(FafoColors.Surface2), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = FafoColors.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = FafoColors.White, modifier = Modifier.weight(1f))
    }
}
