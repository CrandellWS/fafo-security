package ws.crandell.fafo.ui

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ws.crandell.fafo.ble.BlePairing
import ws.crandell.fafo.link.MessageLink
import ws.crandell.fafo.pairing.CredentialGate
import ws.crandell.fafo.pairing.GateResult
import ws.crandell.fafo.pairing.IdentityKeys
import ws.crandell.fafo.pairing.PairingAborted
import ws.crandell.fafo.pairing.PairingFinisher
import ws.crandell.fafo.pairing.PairingProtocol
import ws.crandell.fafo.pairing.PairingResult
import ws.crandell.fafo.pairing.PairingUi
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.pairing.TrustedDeviceStore

sealed interface PairingState {
    data object Idle : PairingState
    data class Working(val text: String) : PairingState
    data class ConfirmCode(val code: String, val peerName: String) : PairingState
    data class Done(val text: String) : PairingState
    data class Failed(val text: String) : PairingState
}

/** Runs F6 pairing for the Trusted devices screen. Lives in the ViewModel; the prompt goes to [gate]. */
class PairingController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val gate: CredentialGate,
    private val keys: () -> IdentityKeys,
    private val store: () -> TrustedDeviceStore,
    private val myName: suspend () -> String,
    /** Runs on the authenticated link after both sides confirmed (M10: the first CircleInvite). */
    var afterPaired: suspend (initiator: Boolean, link: MessageLink, device: TrustedDevice) -> Unit = { _, _, _ -> },
) {
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state
    private var job: Job? = null
    private var ble: BlePairing? = null
    private var answer: CompletableDeferred<Boolean>? = null

    private val ui = object : PairingUi {
        override suspend fun confirmCode(code: String, peerName: String): Boolean {
            val d = CompletableDeferred<Boolean>()
            answer = d
            _state.value = PairingState.ConfirmCode(code, peerName)
            val ok = d.await()
            _state.value = PairingState.Working(if (ok) "Confirm with your screen lock" else "Cancelling")
            return ok
        }
    }

    /** Responder: make this phone visible to the other phone. */
    fun showThisPhone() = start(initiator = false, device = null)

    /** Initiator: find the other phone (API 31+ scan) or use the device the API 30 picker returned. */
    fun findOther(device: BluetoothDevice? = null) = start(initiator = true, device = device)

    fun answerCode(match: Boolean) {
        answer?.complete(match)
    }

    fun cancel() {
        job?.cancel()
        ble?.stop()
        _state.value = PairingState.Idle
    }

    fun dismiss() {
        if (_state.value !is PairingState.Working && _state.value !is PairingState.ConfirmCode) _state.value = PairingState.Idle
    }

    private fun start(initiator: Boolean, device: BluetoothDevice?) {
        job?.cancel()
        ble?.stop()
        val b = BlePairing(context)
        ble = b
        job = scope.launch {
            var link: MessageLink? = null
            try {
                if (!b.bluetoothOn) throw PairingAborted("Turn on Bluetooth first")
                // AC6.1: no secure lock, no pairing. The credential prompt itself comes right after the code
                // comparison, before anything is stored (PairingFinisher).
                if (!gate.hasSecureLock()) throw PairingAborted("Set a screen lock first")
                val k = withContext(Dispatchers.IO) { keys().also { it.identityPublic(); it.commandPublic() } }
                link = if (initiator) {
                    _state.value = PairingState.Working("Looking for the other phone")
                    val d = device ?: b.scanForResponder()
                    _state.value = PairingState.Working("Connecting")
                    b.connect(d)
                } else {
                    _state.value = PairingState.Working("Visible. On the other phone, tap \"Find the other phone\".")
                    b.awaitInitiator()
                }
                _state.value = PairingState.Working("Exchanging keys")
                val protocol = PairingProtocol(k, myName(), PairingFinisher(gate, store()) { System.currentTimeMillis() }, ui)
                val hook: suspend (MessageLink, TrustedDevice) -> Unit = { l, d -> afterPaired(initiator, l, d) }
                val result = if (initiator) protocol.runInitiator(link, hook) else protocol.runResponder(link, hook)
                _state.value = when (result) {
                    is PairingResult.Paired -> PairingState.Done("Paired with ${result.device.name}")
                    PairingResult.CodeMismatch -> PairingState.Failed("Codes did not match. Nothing was saved.")
                    PairingResult.PeerDeclined -> PairingState.Failed("The other phone did not confirm. Nothing was saved.")
                    is PairingResult.Refused -> PairingState.Failed(
                        if (result.gate == GateResult.NoSecureLock) "Set a screen lock first" else "Screen lock not confirmed. Nothing was saved.",
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _state.value = PairingState.Failed("Timed out. Keep both phones close and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "pairing failed", e)
                _state.value = PairingState.Failed(e.message ?: "Pairing failed")
            } finally {
                link?.close()
                b.stop()
            }
        }
    }

    companion object {
        private const val TAG = "Pairing"
    }
}
