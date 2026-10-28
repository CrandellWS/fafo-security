package ws.crandell.fafo.net

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.Member
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.testutil.FakeIdentityKeys

class CommandAuthTest {
    private var now = 1_000L
    private val target = FakeIdentityKeys()
    private val sender = FakeIdentityKeys()
    private val home = Circle("home", "Home", setOf(Permission.RESET, Permission.DISARM), listOf(Member(sender.fingerprint(), "Register", setOf(Permission.ALERTS, Permission.RESET, Permission.DISARM))))
    private val work = Circle("work", "Work", setOf(Permission.RESET), listOf(Member(sender.fingerprint(), "Register", setOf(Permission.RESET))))
    private val book = ChallengeBook({ now })
    private val verifier = CommandVerifier(target.fingerprint(), book, { listOf(home, work) }) { fp -> if (fp == sender.fingerprint()) sender.commandPublic() else null }

    private fun sign(cmd: Command, circle: String, incident: String, challenge: ByteArray, keys: FakeIdentityKeys = sender): ByteArray =
        keys.commandSigner().run { update(CommandPayload.build(cmd, circle, target.fingerprint(), incident, challenge)); sign() }

    private fun send(cmd: Command, circle: String = "home", incident: String = "inc1", current: String? = "inc1", keys: FakeIdentityKeys = sender): Verdict {
        val c = book.issue(sender.fingerprint(), circle, cmd, incident)
        return verifier.verify(sender.fingerprint(), cmd, circle, incident, c, sign(cmd, circle, incident, c, keys), current)
    }

    @Test fun validResetAndDisarm() {
        assertThat(send(Command.RESET)).isEqualTo(Verdict.OK)
        assertThat(send(Command.DISARM)).isEqualTo(Verdict.OK)
    }

    @Test fun missingOrBadSignatureRefused() {
        val c = book.issue(sender.fingerprint(), "home", Command.RESET, "inc1")
        assertThat(verifier.verify(sender.fingerprint(), Command.RESET, "home", "inc1", c, ByteArray(0), "inc1")).isEqualTo(Verdict.BAD_SIGNATURE)
        // Signed by the identity key instead of the command key (a stolen phone without its owner's credential).
        val c2 = book.issue(sender.fingerprint(), "home", Command.RESET, "inc1")
        val wrongKey = sender.signIdentity(CommandPayload.build(Command.RESET, "home", target.fingerprint(), "inc1", c2))
        assertThat(verifier.verify(sender.fingerprint(), Command.RESET, "home", "inc1", c2, wrongKey, "inc1")).isEqualTo(Verdict.BAD_SIGNATURE)
        assertThat(send(Command.RESET, keys = FakeIdentityKeys())).isEqualTo(Verdict.BAD_SIGNATURE)
    }

    @Test fun staleAndReusedChallengesRefused() {
        val c = book.issue(sender.fingerprint(), "home", Command.RESET, "inc1")
        val sig = sign(Command.RESET, "home", "inc1", c)
        assertThat(verifier.verify(sender.fingerprint(), Command.RESET, "home", "inc1", c, sig, "inc1")).isEqualTo(Verdict.OK)
        assertThat(verifier.verify(sender.fingerprint(), Command.RESET, "home", "inc1", c, sig, "inc1")).isEqualTo(Verdict.BAD_CHALLENGE)
        val old = book.issue(sender.fingerprint(), "home", Command.RESET, "inc1")
        now += 60_001
        assertThat(verifier.verify(sender.fingerprint(), Command.RESET, "home", "inc1", old, sign(Command.RESET, "home", "inc1", old), "inc1"))
            .isEqualTo(Verdict.BAD_CHALLENGE)
    }

    @Test fun challengeBoundToCommandType() {
        // A challenge issued for Reset cannot be spent on Disarm.
        val c = book.issue(sender.fingerprint(), "home", Command.RESET, "inc1")
        assertThat(verifier.verify(sender.fingerprint(), Command.DISARM, "home", "inc1", c, sign(Command.DISARM, "home", "inc1", c), "inc1"))
            .isEqualTo(Verdict.BAD_CHALLENGE)
    }

    @Test fun circleWithoutPermissionRefused() {
        // Work grants Reset only: Disarm is refused, Reset works (Reset never disarms).
        assertThat(send(Command.DISARM, circle = "work")).isEqualTo(Verdict.NOT_PERMITTED)
        assertThat(send(Command.RESET, circle = "work")).isEqualTo(Verdict.OK)
        assertThat(send(Command.RESET, circle = "nope")).isEqualTo(Verdict.NOT_PERMITTED)
    }

    @Test fun wrongOrNoIncidentRefused() {
        assertThat(send(Command.RESET, incident = "old", current = "inc1")).isEqualTo(Verdict.WRONG_INCIDENT)
        assertThat(send(Command.RESET, current = null)).isEqualTo(Verdict.NO_INCIDENT)
    }

    @Test fun signatureCoversTarget() {
        // A signature made for another target phone does not verify here.
        val c = book.issue(sender.fingerprint(), "home", Command.RESET, "inc1")
        val elsewhere = sender.commandSigner().run { update(CommandPayload.build(Command.RESET, "home", "someone-else", "inc1", c)); sign() }
        assertThat(verifier.verify(sender.fingerprint(), Command.RESET, "home", "inc1", c, elsewhere, "inc1")).isEqualTo(Verdict.BAD_SIGNATURE)
    }
}
