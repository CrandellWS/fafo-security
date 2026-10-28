package ws.crandell.fafo.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.activity.SystemBarStyle
import android.graphics.Color as AndroidColor
import ws.crandell.fafo.ui.components.FafoCard
import ws.crandell.fafo.ui.components.FafoDims
import ws.crandell.fafo.ui.components.FafoTopBar
import ws.crandell.fafo.ui.theme.FafoColors
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
    private var step by mutableStateOf(0)
    private var ok by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val peer = intent.getStringExtra(EXTRA_PEER)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "device"
        val circle = intent.getStringExtra(EXTRA_CIRCLE)
        val incident = intent.getStringExtra(EXTRA_INCIDENT)
        val cmd = Command.of(intent.getIntExtra(EXTRA_COMMAND, 0))
        setContent {
            FafoTheme {
                RemoteCommandScreen(cmd, label, status, step, done, ok) { finish() }
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
            step = 1
            status = "Asking $label for a challenge"
            val outcome = hub.sendCommand(peer, cmd, circle, incident) { payload ->
                step = 2
                status = "Confirm with your screen lock"
                val signer = withContext(Dispatchers.IO) { TrustGraph.keys().commandSigner() }
                val what = if (cmd == Command.RESET) "Stop $label's alarm (it stays armed)" else "End $label's alarm and disarm it"
                when (val r = gate.unlockCommandKey("${cmd.wire} $label", what, signer)) {
                    is GateResult.Success -> {
                        val s = r.signature ?: signer
                        step = 3
                        status = "Sending"
                        withContext(Dispatchers.IO) { s.update(payload); s.sign() }
                    }
                    else -> null
                }
            }
            ok = outcome == CommandOutcome.Applied
            if (ok) step = 4
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

private val STEPS = listOf("Connect", "Get a challenge", "Confirm it's you", "Send", "Done")

/** Pure screen for [RemoteCommandActivity]: what is being sent, the steps so far, the result. */
@Composable
fun RemoteCommandScreen(cmd: Command?, label: String, status: String, step: Int, done: Boolean, ok: Boolean, onClose: () -> Unit) {
    val what = when (cmd) {
        Command.RESET -> "Reset $label"
        null -> "Command for $label"
        else -> "Disarm $label"
    }
    Scaffold(
        containerColor = FafoColors.Ink,
        topBar = { FafoTopBar(title = null) },
    ) { p ->
        Column(
            Modifier.fillMaxSize().padding(p).padding(horizontal = FafoDims.Gutter, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(what.uppercase(), style = MaterialTheme.typography.headlineMedium, color = FafoColors.White)
            Text(
                if (cmd == Command.RESET) "Stops $label's alarm. It stays armed." else "Ends $label's alarm and disarms it.",
                style = MaterialTheme.typography.bodyLarge, color = FafoColors.TextMuted,
            )
            FafoCard(accent = if (done) (if (ok) FafoColors.Guard else FafoColors.RedText) else FafoColors.Amber) {
                STEPS.forEachIndexed { i, name ->
                    val state = when {
                        i < step || (i == step && done && ok) -> 2
                        i == step && !done -> 1
                        else -> 0
                    }
                    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (state) {
                            2 -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FafoColors.Guard, modifier = Modifier.size(24.dp))
                            1 -> CircularProgressIndicator(Modifier.size(22.dp), color = FafoColors.Amber, strokeWidth = 3.dp)
                            else -> Box(Modifier.size(24.dp).clip(CircleShape).background(FafoColors.Surface2))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(name, style = MaterialTheme.typography.titleMedium, color = if (state == 0) FafoColors.TextMuted else FafoColors.White)
                    }
                }
            }
            Text(
                status, style = MaterialTheme.typography.titleMedium,
                color = when { !done -> FafoColors.White; ok -> FafoColors.Guard; else -> FafoColors.RedText },
                modifier = Modifier.testTag("remoteStatus"),
            )
            if (done) {
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = FafoColors.White, contentColor = FafoColors.Ink),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Close", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

