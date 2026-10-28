package ws.crandell.fafo.pairing

import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.crypto.WireReader
import ws.crandell.fafo.crypto.WireWriter

/**
 * What one side reveals during pairing. Spec AC6.2: "public key" means the identity public key and the
 * command public key together, so the 6-digit code authenticates both. The name is a display suggestion.
 */
data class PeerKeys(val identityPublic: ByteArray, val commandPublic: ByteArray, val name: String) {
    fun encode(): ByteArray = WireWriter().bytes(identityPublic).bytes(commandPublic).str(name.take(24)).build()
    val fingerprint: String get() = Crypto.fingerprint(identityPublic)

    override fun equals(other: Any?) = other is PeerKeys && identityPublic.contentEquals(other.identityPublic) &&
        commandPublic.contentEquals(other.commandPublic) && name == other.name
    override fun hashCode() = identityPublic.contentHashCode()

    companion object {
        fun decode(b: ByteArray): PeerKeys = WireReader(b).run { PeerKeys(bytes(), bytes(), str(96)) }
    }
}

/** SAS numeric comparison code (AC6.2). Inputs are role-ordered: A = initiator, B = responder. */
object PairingCode {
    fun derive(pkA: ByteArray, pkB: ByteArray, nA: ByteArray, nB: ByteArray): String {
        val h = Crypto.sha256("fafo-v1 sas".toByteArray(), Crypto.lp(pkA, pkB, nA, nB))
        val v = ((h[0].toLong() and 0xff) shl 24) or ((h[1].toLong() and 0xff) shl 16) or
            ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)
        return "%06d".format(v % 1_000_000)
    }

    fun commitment(pkB: ByteArray, nB: ByteArray): ByteArray = Crypto.sha256("fafo-v1 commit".toByteArray(), Crypto.lp(pkB, nB))
}

class PairingAborted(message: String) : Exception(message)

/** Pairing wire messages over the GATT link. */
sealed interface PairMsg {
    fun encode(): ByteArray

    data class Commit(val hash: ByteArray) : PairMsg {
        override fun encode() = WireWriter().u8(1).bytes(hash).build()
    }
    data class Hello(val keys: ByteArray, val nonce: ByteArray) : PairMsg {
        override fun encode() = WireWriter().u8(2).bytes(keys).bytes(nonce).build()
    }
    data class Reveal(val keys: ByteArray, val nonce: ByteArray) : PairMsg {
        override fun encode() = WireWriter().u8(3).bytes(keys).bytes(nonce).build()
    }
    /** Both users confirmed the code and passed their device credential. */
    data class Confirmed(val ok: Boolean) : PairMsg {
        override fun encode() = WireWriter().u8(4).bool(ok).build()
    }
    /** M10: the first CircleInvite rides on the authenticated pairing link (AC8.6). */
    data class Invite(val payload: ByteArray) : PairMsg {
        override fun encode() = WireWriter().u8(5).bytes(payload).build()
    }
    data class InviteReply(val payload: ByteArray) : PairMsg {
        override fun encode() = WireWriter().u8(6).bytes(payload).build()
    }

    companion object {
        fun decode(b: ByteArray): PairMsg = WireReader(b).run {
            when (u8()) {
                1 -> Commit(bytes(64))
                2 -> Hello(bytes(), bytes(64))
                3 -> Reveal(bytes(), bytes(64))
                4 -> Confirmed(bool())
                5 -> Invite(bytes())
                6 -> InviteReply(bytes())
                else -> throw PairingAborted("unknown pairing message")
            }
        }
    }
}

/**
 * Commit-then-reveal (AC6.2). Responder: sends H(pkB || nB) first, receives pkA || nA, then reveals.
 * Initiator: receives the commitment, sends pkA || nA, receives the reveal and checks it against the
 * commitment. Without the commitment a man in the middle could grind nonces until both codes match.
 */
class PairingResponder(private val mine: PeerKeys, private val nonce: ByteArray = Crypto.randomBytes(32)) {
    private val pkB = mine.encode()
    var peer: PeerKeys? = null
        private set
    var code: String? = null
        private set

    fun commit(): PairMsg.Commit = PairMsg.Commit(PairingCode.commitment(pkB, nonce))

    fun onHello(m: PairMsg.Hello): PairMsg.Reveal {
        check(peer == null) { "hello already received" }
        val p = PeerKeys.decode(m.keys)
        peer = p
        code = PairingCode.derive(m.keys, pkB, m.nonce, nonce)
        return PairMsg.Reveal(pkB, nonce)
    }
}

class PairingInitiator(private val mine: PeerKeys, private val nonce: ByteArray = Crypto.randomBytes(32)) {
    private val pkA = mine.encode()
    private var commitment: ByteArray? = null
    var peer: PeerKeys? = null
        private set
    var code: String? = null
        private set

    fun onCommit(m: PairMsg.Commit): PairMsg.Hello {
        check(commitment == null) { "commit already received" }
        commitment = m.hash
        return PairMsg.Hello(pkA, nonce)
    }

    fun onReveal(m: PairMsg.Reveal) {
        val c = commitment ?: throw PairingAborted("reveal before commitment")
        if (!Crypto.constantTimeEquals(c, PairingCode.commitment(m.keys, m.nonce))) {
            throw PairingAborted("reveal does not match the commitment")
        }
        peer = PeerKeys.decode(m.keys)
        code = PairingCode.derive(pkA, m.keys, nonce, m.nonce)
    }
}
