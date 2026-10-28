package ws.crandell.fafo.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.crandell.fafo.net.Command
import ws.crandell.fafo.net.CommandOutcome
import ws.crandell.fafo.net.android.PeerNetwork
import ws.crandell.fafo.pairing.BiometricCredentialGate
import ws.crandell.fafo.pairing.GateResult
import ws.crandell.fafo.trust.TrustGraph
import ws.crandell.fafo.ui.theme.FafoTheme

/**
 * Sends Reset or Disarm to a trusted device's alarm (AC9.8): asks the target for a challenge, then the
 * owner unlocks the auth-bound command key with BiometricPrompt and the key signs the command. Opened
 * only from an alert notification action (user tap, device unlocked).
 */
class RemoteCommandActivity : FragmentActivity() {
    private var status by mutableStateOf("Connecting")
    private var done by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val peer = intent.getStringExtra(EXTRA_PEER)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "device"
        val circle = intent.getStringExtra(EXTRA_CIRCLE)
        val incident = intent.getStringExtra(EXTRA_INCIDENT)
        val cmd = Command.of(intent.getIntExtra(EXTRA_COMMAND, 0))
        setContent {
            FafoTheme {
                Scaffold { p ->
                    Column(Modifier.fillMaxSize().padding(p).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("${cmd?.wire ?: "Command"}: $label", style = MaterialTheme.typography.headlineSmall)
                        Text(status)
                        if (done) Button(onClick = { finish() }) { Text("Close") }
                    }
                }
            }
        }
        if (peer == null || circle == null || incident == null || cmd == null || savedInstanceState != null) {
            if (savedInstanceState == null) finishWith("Nothing to send")
            return
        }
        val gate = BiometricCredentialGate(this)
        lifecycleScope.launch {
            val hub = PeerNetwork.acquire(this@RemoteCommandActivity, OWNER)
            if (hub == null) return@launch finishWith("Network not available")
            status = "Asking $label for a challenge"
            val outcome = hub.sendCommand(peer, cmd, circle, incident) { payload ->
                status = "Confirm with your screen lock"
                val signer = withContext(Dispatchers.IO) { TrustGraph.keys().commandSigner() }
                val what = if (cmd == Command.RESET) "Stop $label's alarm (it stays armed)" else "End $label's alarm and disarm it"
                when (val r = gate.unlockCommandKey("${cmd.wire} $label", what, signer)) {
                    is GateResult.Success -> {
                        val s = r.signature ?: signer
                        status = "Sending"
                        withContext(Dispatchers.IO) { s.update(payload); s.sign() }
                    }
                    else -> null
                }
            }
            finishWith(
                when (outcome) {
                    CommandOutcome.Applied -> if (cmd == Command.RESET) "$label was reset. It stays armed." else "$label was disarmed."
                    CommandOutcome.Cancelled -> "Cancelled. Nothing was sent."
                    CommandOutcome.Unreachable -> "$label is not reachable. Get closer or join the same Wi-Fi."
                    is CommandOutcome.Refused -> "$label refused: ${outcome.reason.lowercase().replace('_', ' ')}"
                },
            )
        }
    }

    private fun finishWith(text: String) {
        status = text
        done = true
    }

    override fun onDestroy() {
        PeerNetwork.release(OWNER)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PEER = "peer"
        const val EXTRA_LABEL = "label"
        const val EXTRA_CIRCLE = "circle"
        const val EXTRA_INCIDENT = "incident"
        const val EXTRA_COMMAND = "command"
        private const val OWNER = "remote-command"
    }
}
