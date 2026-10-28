package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.core.Defaults
import ws.crandell.fafo.storage.CeSettings
import ws.crandell.fafo.storage.DpSettings

fun graceLabel(s: Int): String = when {
    s == 0 -> "Off"
    s >= 120 && s % 60 == 0 -> "${s / 60} min"
    else -> "$s s"
}

fun graceHint(s: Int): String =
    if (s == 0) "Off: Arm and Disarm always ask for your fingerprint or screen lock."
    else "If you unlocked the phone in the last ${graceLabel(s)}, Arm and Disarm don't ask again. " +
        "Changing this asks for your screen lock."

fun sensitivityLabel(ms: Long): String = if (ms % 1000 == 0L) "${ms / 1000} s" else "${ms / 1000.0} s"

@Composable
fun SettingsScreen(
    dp: DpSettings,
    ce: CeSettings?,
    onDp: ((DpSettings) -> DpSettings) -> Unit,
    onCe: ((CeSettings) -> CeSettings) -> Unit,
    modifier: Modifier = Modifier,
    onStayReachable: (Boolean) -> Unit = {},
    onGrace: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ScreenTitle("Settings")
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
        SwitchRow("Disarm on unlock (while armed, not triggered)", dp.disarmOnUnlock, { v -> onDp { it.copy(disarmOnUnlock = v) } }, "disarmOnUnlock")
        ChoiceRow(
            "Unlock grace window", Defaults.UNLOCK_GRACE_CHOICES_S, dp.unlockGraceS, ::graceLabel, onGrace, "grace",
            hint = graceHint(dp.unlockGraceS),
        )
        ChoiceRow(
            "Motion chirps", listOf("OFF", "LOW", "MEDIUM", "HIGH"), dp.motion,
            { it.lowercase().replaceFirstChar(Char::uppercase) }, { v -> onDp { it.copy(motion = v) } }, "motion",
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (ce == null) {
            Text("Photo and device settings are available after the first unlock.")
            return@Column
        }
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
        )
        Text("Photos pause when less than 500 MB is free; the siren keeps going.")
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow("Stay reachable (alerts and push-to-talk in the background)", ce.stayReachable, { v ->
            onCe { it.copy(stayReachable = v) }
            onStayReachable(v)
        }, "stayReachable")
        Text("Shows a notification \"Reachable by: ...\" the whole time. Without it, Bluetooth alerts still arrive.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        SwitchRow("Chime when someone listens", ce.chimeOnListen, { v -> onCe { it.copy(chimeOnListen = v) } }, "chime")
    }
}
