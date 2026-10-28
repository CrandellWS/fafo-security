package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.ui.PairingState
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.FafoIcons
import ws.crandell.fafo.ui.components.SectionHeader
import ws.crandell.fafo.ui.theme.FafoBrand
import ws.crandell.fafo.ui.theme.FafoColors
import ws.crandell.fafo.ui.theme.FafoType
import java.text.DateFormat
import java.util.Date

/** F6: trusted devices with name, key fingerprint and date added; pairing; removal (credential). */
@Composable
fun TrustedScreen(
    devices: List<TrustedDevice>,
    pairing: PairingState,
    nearbyGranted: Boolean,
    onRequestNearby: () -> Unit,
    onShowThisPhone: () -> Unit,
    onFindOther: () -> Unit,
    onAnswerCode: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRemove: (TrustedDevice) -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FafoDims.Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(FafoDims.Gap),
    ) {
        Text(
            "Pair your own phones over Bluetooth. Open FAFO on this screen on both phones and hold them close together.",
            style = MaterialTheme.typography.bodyLarge, color = FafoColors.TextMuted,
        )
        if (!nearbyGranted) {
            FafoCard(accent = FafoColors.Amber) {
                Text("Nearby devices permission", style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
                Text(
                    "FAFO needs it to find your other phone. It is never used for location.",
                    style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onRequestNearby,
                    colors = ButtonDefaults.buttonColors(containerColor = FafoColors.Red, contentColor = FafoBrand.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("nearbyButton"),
                ) { Text("Allow Nearby devices") }
            }
        } else {
            extra()
            Button(
                onClick = onShowThisPhone,
                colors = ButtonDefaults.buttonColors(containerColor = FafoColors.Red, contentColor = FafoBrand.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("showThisPhone"),
            ) { Text("Show this phone", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(
                onClick = onFindOther,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("findOther"),
            ) { Text("Find the other phone", style = MaterialTheme.typography.titleMedium, color = FafoColors.White) }
        }
        SectionHeader("Paired phones (${devices.size})")
        if (devices.isEmpty()) {
            Text(
                "No trusted devices yet.", style = MaterialTheme.typography.bodyLarge, color = FafoColors.TextMuted,
                modifier = Modifier.testTag("noDevices"),
            )
        }
        devices.forEach { d ->
            FafoCard(Modifier.testTag("device_${d.fingerprint}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(FafoColors.Surface2), contentAlignment = Alignment.Center) {
                        Icon(FafoIcons.Devices, contentDescription = null, tint = FafoColors.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
                        Text("Added ${DateFormat.getDateInstance().format(Date(d.addedAt))}", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
                    }
                }
                Text(
                    Crypto.displayFingerprint(d.fingerprint), style = FafoType.mono, color = FafoColors.TextMuted,
                    modifier = Modifier.padding(top = 10.dp),
                )
                TextButton(onClick = { onRemove(d) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Remove device", color = FafoColors.RedText)
                }
            }
        }
    }
    PairingDialog(pairing, onAnswerCode, onCancel, onDismiss)
}

@Composable
fun PairingDialog(state: PairingState, onAnswerCode: (Boolean) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        PairingState.Idle -> Unit
        is PairingState.Working -> FafoDialog(
            onDismissRequest = {},
            title = "Pairing",
            icon = { CircularProgressIndicator(Modifier.size(36.dp), color = FafoColors.RedText, trackColor = FafoColors.RedText.copy(alpha = 0.3f)) },
            text = { Text(state.text, style = MaterialTheme.typography.bodyLarge, color = FafoColors.White) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", color = FafoColors.White) } },
        )
        is PairingState.ConfirmCode -> FafoDialog(
            onDismissRequest = {},
            title = "Compare the codes",
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Does ${state.peerName} show this code?", style = MaterialTheme.typography.bodyLarge, color = FafoColors.White)
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FafoColors.Ink).padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            state.code.chunked(3).joinToString(" "), style = FafoType.mono.copy(fontSize = 44.sp, lineHeight = 52.sp),
                            color = FafoColors.White, textAlign = TextAlign.Center, modifier = Modifier.testTag("pairCode"),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Tap Codes match only if both phones show the same six digits.", style = MaterialTheme.typography.bodyMedium, color = FafoColors.TextMuted)
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAnswerCode(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = FafoColors.Red, contentColor = FafoBrand.White),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("codeMatches"),
                ) { Text("Codes match") }
            },
            dismissButton = { TextButton(onClick = { onAnswerCode(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Don't match", color = FafoColors.White) } },
        )
        is PairingState.Done -> FafoDialog(
            onDismissRequest = onDismiss,
            title = "Paired",
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FafoColors.Guard, modifier = Modifier.size(40.dp)) },
            text = { Text(state.text, style = MaterialTheme.typography.bodyLarge, color = FafoColors.White) },
            confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("OK", color = FafoColors.White) } },
        )
        is PairingState.Failed -> FafoDialog(
            onDismissRequest = onDismiss,
            title = "Not paired",
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = FafoColors.RedText, modifier = Modifier.size(40.dp)) },
            text = { Text(state.text, style = MaterialTheme.typography.bodyLarge, color = FafoColors.White) },
            confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("OK", color = FafoColors.White) } },
        )
    }
}
