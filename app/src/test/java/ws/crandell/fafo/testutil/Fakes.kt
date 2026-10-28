package ws.crandell.fafo.testutil

import kotlinx.coroutines.channels.Channel
import ws.crandell.fafo.link.LinkClosed
import ws.crandell.fafo.link.MessageLink
import ws.crandell.fafo.pairing.CredentialGate
import ws.crandell.fafo.pairing.GateResult
import ws.crandell.fafo.pairing.IdentityKeys
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Mac

/** Software stand-in for the Keystore identity + command keys (Keystore does not exist on the JVM). */
class FakeIdentityKeys : IdentityKeys {
    val identity: KeyPair = newEc()
    val command: KeyPair = newEc()

    override fun identityPublic(): ByteArray = identity.public.encoded
    override fun commandPublic(): ByteArray = command.public.encoded
    override fun signIdentity(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(identity.private); update(data); sign()
    }
    override fun commandSigner(): Signature = Signature.getInstance("SHA256withECDSA").apply { initSign(command.private) }

    companion object {
        fun newEc(): KeyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1")); generateKeyPair()
        }
    }
}

class FakeGate(var result: GateResult = GateResult.Success()) : CredentialGate {
    val calls = mutableListOf<String>()
    override fun hasSecureLock(): Boolean = result != GateResult.NoSecureLock
    override suspend fun authenticate(title: String, subtitle: String): GateResult { calls += title; return result }
    override suspend fun unlockCommandKey(title: String, subtitle: String, signer: Signature): GateResult {
        calls += title
        return if (result is GateResult.Success) GateResult.Success(signer) else result
    }
    val confirmMacs = mutableListOf<Mac?>()
    /** Called when a confirmUser prompt succeeds (e.g. a fake key records the fresh authentication). */
    var onConfirmed: () -> Unit = {}
    override suspend fun confirmUser(title: String, subtitle: String, mac: Mac?): GateResult {
        calls += title
        confirmMacs += mac
        val r = result
        return if (r is GateResult.Success) { onConfirmed(); GateResult.Success(mac = mac) } else r
    }
}

/** Two in-memory [MessageLink] ends wired to each other. */
class MemoryLink private constructor(private val inbox: Channel<ByteArray>, private val outbox: Channel<ByteArray>) : MessageLink {
    val sent = mutableListOf<ByteArray>()
    override suspend fun send(msg: ByteArray) { sent += msg; outbox.send(msg) }
    override suspend fun receive(): ByteArray = inbox.receiveCatching().getOrNull() ?: throw LinkClosed()
    override fun close() { inbox.close(); outbox.close() }

    companion object {
        fun pair(): Pair<MemoryLink, MemoryLink> {
            val a = Channel<ByteArray>(Channel.UNLIMITED)
            val b = Channel<ByteArray>(Channel.UNLIMITED)
            return MemoryLink(a, b) to MemoryLink(b, a)
        }
    }
}
