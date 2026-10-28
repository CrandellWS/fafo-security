package ws.crandell.fafo.pairing

import kotlinx.coroutines.withTimeout
import ws.crandell.fafo.link.MessageLink

/** What the person at this phone sees during pairing. Implemented by the pairing screen. */
interface PairingUi {
    /** Show both 6-digit codes' local half and ask "Does the other phone show the same code?". */
    suspend fun confirmCode(code: String, peerName: String): Boolean
}

/**
 * The whole F6 pairing exchange over an already connected link, for either role. Initiator = the phone
 * that scanned and connected (GATT client); responder = the phone that made itself visible (GATT server).
 *
 * 1. Responder sends the commitment H(pkB || nB); initiator sends pkA || nA; responder reveals pkB || nB;
 *    the initiator checks it against the commitment (AC6.2).
 * 2. Both screens show the code; the person confirms it matches; then each owner passes their own
 *    device credential (AC6.1, AC6.3) and the sides exchange Confirmed.
 * 3. Only when both confirmed is the peer stored, with no powers (AC6.6). [afterPaired] then runs on the
 *    same authenticated link (M10: the first CircleInvite rides on it, AC8.6).
 */
class PairingProtocol(
    private val keys: IdentityKeys,
    private val myName: String,
    private val finisher: PairingFinisher,
    private val ui: PairingUi,
    private val timeoutMs: Long = 120_000,
) {
    private fun myKeys() = PeerKeys(keys.identityPublic(), keys.commandPublic(), myName.take(24))

    suspend fun runInitiator(link: MessageLink, afterPaired: suspend (MessageLink, TrustedDevice) -> Unit = { _, _ -> }): PairingResult {
        val initiator = PairingInitiator(myKeys())
        val commit = receive(link) as? PairMsg.Commit ?: throw PairingAborted("expected commitment")
        link.send(initiator.onCommit(commit).encode())
        val reveal = receive(link) as? PairMsg.Reveal ?: throw PairingAborted("expected reveal")
        initiator.onReveal(reveal)
        return confirm(link, initiator.peer!!, initiator.code!!, afterPaired)
    }

    suspend fun runResponder(link: MessageLink, afterPaired: suspend (MessageLink, TrustedDevice) -> Unit = { _, _ -> }): PairingResult {
        val responder = PairingResponder(myKeys())
        link.send(responder.commit().encode())
        val hello = receive(link) as? PairMsg.Hello ?: throw PairingAborted("expected hello")
        link.send(responder.onHello(hello).encode())
        return confirm(link, responder.peer!!, responder.code!!, afterPaired)
    }

    private suspend fun confirm(
        link: MessageLink,
        peer: PeerKeys,
        code: String,
        afterPaired: suspend (MessageLink, TrustedDevice) -> Unit,
    ): PairingResult {
        if (peer.fingerprint == keys.fingerprint()) throw PairingAborted("cannot pair with itself")
        val matched = ui.confirmCode(code, peer.name)
        val verdict = finisher.verify(peer, matched)
        link.send(PairMsg.Confirmed(verdict is PairingResult.Paired).encode())
        val theirs = receive(link) as? PairMsg.Confirmed ?: throw PairingAborted("expected confirmation")
        if (verdict !is PairingResult.Paired) return verdict
        if (!theirs.ok) return PairingResult.PeerDeclined
        finisher.store(verdict.device)
        afterPaired(link, verdict.device)
        return verdict
    }

    private suspend fun receive(link: MessageLink): PairMsg = withTimeout(timeoutMs) { PairMsg.decode(link.receive()) }
}
