package ws.crandell.fafo.net

import ws.crandell.fafo.circles.CirclePolicy
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.crypto.Crypto

enum class Command(val code: Int, val wire: String) {
    RESET(1, "Reset"), DISARM(2, "Disarm");

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code }
    }
}

/**
 * AC9.8: what the command key signs. Every field is length-prefixed so none can bleed into another:
 * `"fafo-v1 cmd" || type || circleId || targetFingerprint || incidentId || challenge`.
 */
object CommandPayload {
    fun build(cmd: Command, circleId: String, targetFingerprint: String, incidentId: String, challenge: ByteArray): ByteArray =
        "fafo-v1 cmd".toByteArray() + Crypto.lp(
            cmd.wire.toByteArray(), circleId.toByteArray(), targetFingerprint.toByteArray(),
            incidentId.toByteArray(), challenge,
        )
}

/** Target side: random 128-bit challenges, valid 60 s, single use, bound to peer / circle / command / incident. */
class ChallengeBook(private val clock: () -> Long, private val validMs: Long = 60_000) {
    private data class Entry(val peer: String, val circleId: String, val cmd: Command, val incidentId: String, val issuedAt: Long)

    private val entries = HashMap<String, Entry>()

    @Synchronized
    fun issue(peer: String, circleId: String, cmd: Command, incidentId: String): ByteArray {
        prune()
        val c = Crypto.randomBytes(16)
        entries[Crypto.hex(c)] = Entry(peer, circleId, cmd, incidentId, clock())
        return c
    }

    /** True once for a live challenge issued to exactly these fields; it is gone afterwards. */
    @Synchronized
    fun consume(challenge: ByteArray, peer: String, circleId: String, cmd: Command, incidentId: String): Boolean {
        val e = entries.remove(Crypto.hex(challenge)) ?: return false
        if (clock() - e.issuedAt > validMs) return false
        return e.peer == peer && e.circleId == circleId && e.cmd == cmd && e.incidentId == incidentId
    }

    private fun prune() {
        val now = clock()
        entries.entries.removeAll { now - it.value.issuedAt > validMs }
    }
}

enum class Verdict { OK, NO_INCIDENT, WRONG_INCIDENT, NOT_PERMITTED, UNKNOWN_PEER, BAD_CHALLENGE, BAD_SIGNATURE }

/**
 * Checks a received Reset / Disarm on the target. Order: incident, circle permission, challenge (consumed
 * even if the signature then fails, so a challenge is never usable twice), command-key signature.
 */
class CommandVerifier(
    private val myFingerprint: String,
    private val book: ChallengeBook,
    private val circles: () -> List<Circle>,
    private val commandPublic: (String) -> ByteArray?,
) {
    fun verify(
        peer: String,
        cmd: Command,
        circleId: String,
        incidentId: String,
        challenge: ByteArray,
        signature: ByteArray,
        currentIncident: String?,
    ): Verdict {
        if (currentIncident == null) return Verdict.NO_INCIDENT
        if (incidentId != currentIncident) return Verdict.WRONG_INCIDENT
        val perm = if (cmd == Command.RESET) Permission.RESET else Permission.DISARM
        if (!CirclePolicy.allows(circles(), circleId, peer, perm)) return Verdict.NOT_PERMITTED
        val pub = commandPublic(peer) ?: return Verdict.UNKNOWN_PEER
        if (!book.consume(challenge, peer, circleId, cmd, incidentId)) return Verdict.BAD_CHALLENGE
        val payload = CommandPayload.build(cmd, circleId, myFingerprint, incidentId, challenge)
        return if (Crypto.verifyEcdsa(pub, payload, signature)) Verdict.OK else Verdict.BAD_SIGNATURE
    }

    /** Challenges are handed out only to peers that hold the permission for a live incident. */
    fun mayChallenge(peer: String, cmd: Command, circleId: String, incidentId: String, currentIncident: String?): Verdict {
        if (currentIncident == null) return Verdict.NO_INCIDENT
        if (incidentId != currentIncident) return Verdict.WRONG_INCIDENT
        val perm = if (cmd == Command.RESET) Permission.RESET else Permission.DISARM
        return if (CirclePolicy.allows(circles(), circleId, peer, perm)) Verdict.OK else Verdict.NOT_PERMITTED
    }
}
