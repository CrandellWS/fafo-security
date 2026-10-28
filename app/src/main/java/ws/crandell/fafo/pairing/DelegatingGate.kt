package ws.crandell.fafo.pairing

import java.security.Signature

/**
 * A [CredentialGate] that forwards to whatever activity is on screen. Long-running flows (pairing) live in
 * the ViewModel; the prompt needs the current FragmentActivity.
 */
class DelegatingGate : CredentialGate {
    @Volatile var target: CredentialGate? = null

    override fun hasSecureLock(): Boolean = target?.hasSecureLock() ?: false

    override suspend fun authenticate(title: String, subtitle: String): GateResult =
        target?.authenticate(title, subtitle) ?: GateResult.Failed("app not on screen")

    override suspend fun unlockCommandKey(title: String, subtitle: String, signer: Signature): GateResult =
        target?.unlockCommandKey(title, subtitle, signer) ?: GateResult.Failed("app not on screen")
}
