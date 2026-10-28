package ws.crandell.fafo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.ui.PairingState
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
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenTitle("Trusted devices")
        Text("Pair your own phones over Bluetooth. Both phones need FAFO Security open on this screen, close together.")
        if (!nearbyGranted) {
            Text("FAFO needs the Nearby devices permission to find your other phone.")
            Button(onClick = onRequestNearby, modifier = Modifier.testTag("nearbyButton")) { Text("Allow Nearby devices") }
        } else {
            extra()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShowThisPhone, modifier = Modifier.testTag("showThisPhone")) { Text("Show this phone") }
                OutlinedButton(onClick = onFindOther, modifier = Modifier.testTag("findOther")) { Text("Find the other phone") }
            }
        }
        if (devices.isEmpty()) Text("No trusted devices yet.", modifier = Modifier.testTag("noDevices"))
        devices.forEach { d ->
            Card(Modifier.fillMaxWidth().testTag("device_${d.fingerprint}")) {
                Column(Modifier.padding(12.dp)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                    Text(Crypto.displayFingerprint(d.fingerprint), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("Added ${DateFormat.getDateInstance().format(Date(d.addedAt))}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onRemove(d) }) { Text("Remove device") }
                }
            }
        }
    }
    PairingDialog(pairing, onAnswerCode, onCancel, onDismiss)
}

@Composable
private fun PairingDialog(state: PairingState, onAnswerCode: (Boolean) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        PairingState.Idle -> Unit
        is PairingState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Pairing") },
            text = { Text(state.text) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        )
        is PairingState.ConfirmCode -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Compare the codes") },
            text = {
                Column {
                    Text("Does ${state.peerName} show this code?")
                    Text(state.code.chunked(3).joinToString(" "), fontSize = 40.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("pairCode"))
                }
            },
            confirmButton = { Button(onClick = { onAnswerCode(true) }, modifier = Modifier.testTag("codeMatches")) { Text("Codes match") } },
            dismissButton = { TextButton(onClick = { onAnswerCode(false) }) { Text("Don't match") } },
        )
        is PairingState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Paired") },
            text = { Text(state.text) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is PairingState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Not paired") },
            text = { Text(state.text) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}
