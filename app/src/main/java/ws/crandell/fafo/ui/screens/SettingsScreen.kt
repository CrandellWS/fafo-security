package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.core.Defaults
import ws.crandell.fafo.storage.CeSettings
import ws.crandell.fafo.storage.DpSettings
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.SectionHeader
import ws.crandell.fafo.ui.theme.FafoColors

fun graceLabel(s: Int): String = when {
    s == 0 -> "Off"
    s >= 120 && s % 60 == 0 -> "${s / 60} min"
    else -> "$s s"
}

fun graceHint(s: Int): String =
    if (s == 0) "Off: Arm and Disarm always ask for your fingerprint or screen lock."
    else "Arm doesn't ask if you unlocked the phone in the last ${graceLabel(s)}. Disarm doesn't ask only if you " +
        "locked and unlocked the phone after arming, in the last ${graceLabel(s)}. Changing this asks for your screen lock."

fun themeLabel(s: String): String = when (s) {
    "LIGHT" -> "Light"
    "DARK" -> "Dark"
    else -> "System"
}

fun sensitivityLabel(ms: Long): String = if (ms % 1000 == 0L) "${ms / 1000} s" else "${ms / 1000.0} s"

/** Settings (gear in the top bar): grouped into Alarm, Security, Motion, Photos, Nearby devices, About. */
@Composable
fun SettingsScreen(
    dp: DpSettings,
    ce: CeSettings?,
    onDp: ((DpSettings) -> DpSettings) -> Unit,
    onCe: ((CeSettings) -> CeSettings) -> Unit,
    modifier: Modifier = Modifier,
    onStayReachable: (Boolean) -> Unit = {},
    onGrace: (Int) -> Unit = {},
    version: String = "",
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FafoDims.Gutter, vertical = 4.dp)) {
        SectionHeader("Alarm")
        ChoiceRow(
            "Unplug sensitivity", Defaults.SENSITIVITY_CHOICES_MS, dp.sensitivityMs, ::sensitivityLabel,
            { v -> onDp { it.copy(sensitivityMs = v) } }, "sensitivity",
            hint = "Longer = fewer false alarms from loose cables or plug swaps.",
        )
        ChoiceRow(
            "Pre-alarm", Defaults.PRE_ALARM_CHOICES_S, dp.preAlarmS, { if (it == 0) "None" else "$it s" },
            { v -> onDp { it.copy(preAlarmS = v) } }, "prealarm",
            hint = "A quieter countdown before the full siren. Plugging back in or unlocking cancels it.",
        )
        ChoiceRow("Siren sound", Defaults.SIREN_SOUNDS, dp.sirenSound, { it }, { v -> onDp { it.copy(sirenSound = v) } }, "siren")
        SwitchRow("Force max alarm volume", dp.forceMaxVolume, { v -> onDp { it.copy(forceMaxVolume = v) } }, "forceMax")
        SwitchRow(
            "Disarm on unlock", dp.disarmOnUnlock, { v -> onDp { it.copy(disarmOnUnlock = v) } }, "disarmOnUnlock",
            subtitle = "While armed, not triggered. Off: unlocking keeps FAFO armed.",
        )

        SectionHeader("Security")
        ChoiceRow(
            "Arming countdown", Defaults.ARMING_CHOICES_S, dp.armingS, { "$it s" },
            { v -> onDp { it.copy(armingS = v) } }, "arming",
            hint = "After you tap Arm, a yellow countdown runs before FAFO is armed. Cancel any time.",
        )
        ChoiceRow(
            "Unlock grace window", Defaults.UNLOCK_GRACE_CHOICES_S, dp.unlockGraceS, ::graceLabel, onGrace, "grace",
            hint = graceHint(dp.unlockGraceS),
        )

        SectionHeader("Motion")
        ChoiceRow(
            "Motion chirps", listOf("OFF", "LOW", "MEDIUM", "HIGH"), dp.motion,
            { it.lowercase().replaceFirstChar(Char::uppercase) }, { v -> onDp { it.copy(motion = v) } }, "motion",
            hint = "A bump chirps and turns the status yellow; repeated bumps go orange. Left alone for a minute it goes back to green.",
        )
        SwitchRow(
            "Motion can sound the alarm", dp.motionCanAlarm, { v -> onDp { it.copy(motionCanAlarm = v) } }, "motionCanAlarm",
            subtitle = "While locked, continued handling or a pickup starts the pre-alarm (at least 5 s to unlock). Off: chirps only.",
        )

        SectionHeader("Display")
        ChoiceRow(
            "Theme", Defaults.THEME_CHOICES, dp.theme, ::themeLabel, { v -> onDp { it.copy(theme = v) } }, "theme",
            hint = "System follows your phone's dark mode. The lock-screen status screen uses the same theme.",
        )

        SectionHeader("Photos")
        if (ce == null) {
            FafoCard(Modifier.testTag("ceLocked"), accent = FafoColors.Amber) {
                Text(
                    "Photo and device settings are available after the first unlock.",
                    style = MaterialTheme.typography.bodyLarge, color = FafoColors.White,
                )
            }
        } else {
            SwitchRow("Photo on trigger", ce.photoOnTrigger, { v -> onCe { it.copy(photoOnTrigger = v) } }, "photoOnTrigger")
            ChoiceRow(
                "Photo interval", Defaults.PHOTO_INTERVAL_CHOICES_S, ce.photoIntervalS, { "$it s" },
                { v -> onCe { it.copy(photoIntervalS = v) } }, "photoInterval",
            )
            ChoiceRow(
                "Camera", listOf("FRONT", "BACK", "ALTERNATE"), ce.camera,
                { it.lowercase().replaceFirstChar(Char::uppercase) }, { v -> onCe { it.copy(camera = v) } }, "camera",
            )
            ChoiceRow(
                "Photo storage cap", Defaults.PHOTO_CAP_CHOICES_MB, ce.photoCapMb, { "$it MB" },
                { v -> onCe { it.copy(photoCapMb = v) } }, "photoCap",
                hint = "Photos pause when less than 500 MB is free; the siren keeps going.",
            )

            SectionHeader("Nearby devices")
            SwitchRow(
                "Stay reachable", ce.stayReachable, { v ->
                    onCe { it.copy(stayReachable = v) }
                    onStayReachable(v)
                }, "stayReachable",
                subtitle = "Alerts and push-to-talk in the background. Shows \"Reachable by: ...\" the whole time. Bluetooth alerts arrive either way.",
            )
            SwitchRow("Chime when someone listens", ce.chimeOnListen, { v -> onCe { it.copy(chimeOnListen = v) } }, "chime")
        }

        SectionHeader("About")
        Text(
            "FAFO Security${if (version.isNotEmpty()) " $version" else ""}. No servers, no analytics, no ads.",
            style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
        )
        Text(
            "Barlow Condensed font: SIL Open Font License 1.1.",
            style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
        )
        Spacer(Modifier.height(24.dp))
    }
}
