package ws.crandell.fafo.pairing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.testutil.FakeGate
import ws.crandell.fafo.testutil.FakeIdentityKeys
import ws.crandell.fafo.testutil.MemoryLink

class PairingTest {
    @get:Rule val tmp = TemporaryFolder()

    private val pkA = Crypto.randomBytes(91)
    private val pkB = Crypto.randomBytes(91)
    private val nA = Crypto.randomBytes(32)
    private val nB = Crypto.randomBytes(32)

    @Test fun codeIsSixDigitsAndDeterministic() {
        val c = PairingCode.derive(pkA, pkB, nA, nB)
        assertThat(c).matches("\\d{6}")
        assertThat(PairingCode.derive(pkA.copyOf(), pkB.copyOf(), nA.copyOf(), nB.copyOf())).isEqualTo(c)
    }

    @Test fun codeChangesWhenAnyInputChanges() {
        val c = PairingCode.derive(pkA, pkB, nA, nB)
        fun flip(b: ByteArray) = b.copyOf().also { it[0] = (it[0] + 1).toByte() }
        // Collisions are possible in principle (1 in a million); these fixed inputs are checked once.
        val variants = listOf(
            PairingCode.derive(flip(pkA), pkB, nA, nB),
            PairingCode.derive(pkA, flip(pkB), nA, nB),
            PairingCode.derive(pkA, pkB, flip(nA), nB),
            PairingCode.derive(pkA, pkB, nA, flip(nB)),
            PairingCode.derive(pkB, pkA, nA, nB),
        )
        val distinct = variants.count { it != c }
        assertThat(distinct).isAtLeast(4)
    }

    @Test fun bothSidesDeriveTheSameCode() {
        val a = PairingInitiator(PeerKeys(pkA, Crypto.randomBytes(91), "Register"), nA)
        val b = PairingResponder(PeerKeys(pkB, Crypto.randomBytes(91), "Back room"), nB)
        val hello = a.onCommit(b.commit())
        val reveal = b.onHello(hello)
        a.onReveal(reveal)
        assertThat(a.code).isEqualTo(b.code)
        assertThat(a.peer!!.name).isEqualTo("Back room")
        assertThat(b.peer!!.name).isEqualTo("Register")
    }

    @Test fun commandKeyIsCoveredByTheCode() {
        val cmd1 = Crypto.randomBytes(91)
        val cmd2 = Crypto.randomBytes(91)
        val k1 = PeerKeys(pkA, cmd1, "A").encode()
        val k2 = PeerKeys(pkA, cmd2, "A").encode()
        assertThat(PairingCode.derive(k1, pkB, nA, nB)).isNotEqualTo(PairingCode.derive(k2, pkB, nA, nB))
    }

    @Test fun revealNotMatchingCommitmentAborts() {
        val a = PairingInitiator(PeerKeys(pkA, Crypto.randomBytes(91), "A"), nA)
        val b = PairingResponder(PeerKeys(pkB, Crypto.randomBytes(91), "B"), nB)
        a.onCommit(b.commit())
        // A man in the middle swaps in another nonce after seeing nA.
        val forged = PairMsg.Reveal(PeerKeys(pkB, Crypto.randomBytes(91), "B").encode(), Crypto.randomBytes(32))
        assertThrows(PairingAborted::class.java) { a.onReveal(forged) }
        assertThat(a.code).isNull()
    }

    @Test fun revealBeforeCommitAborts() {
        val a = PairingInitiator(PeerKeys(pkA, Crypto.randomBytes(91), "A"), nA)
        assertThrows(PairingAborted::class.java) { a.onReveal(PairMsg.Reveal(pkB, nB)) }
    }

    @Test fun messagesRoundTrip() {
        val msgs = listOf(PairMsg.Commit(nA), PairMsg.Hello(pkA, nA), PairMsg.Reveal(pkB, nB), PairMsg.Confirmed(true), PairMsg.Invite(pkA))
        msgs.forEach { m ->
            val d = PairMsg.decode(m.encode())
            assertThat(d.encode()).isEqualTo(m.encode())
        }
    }

    private class Side(name: String, dir: java.io.File, val gate: FakeGate = FakeGate(), var match: Boolean = true) {
        val keys = FakeIdentityKeys()
        val store = TrustedDeviceStore(java.io.File(dir, "$name.json"))
        var shownCode: String? = null
        val protocol = PairingProtocol(keys, name, PairingFinisher(gate, store) { 1000L }, object : PairingUi {
            override suspend fun confirmCode(code: String, peerName: String): Boolean { shownCode = code; return match }
        })
    }

    private fun run(a: Side, b: Side): Pair<PairingResult, PairingResult> = runBlocking {
        val (la, lb) = MemoryLink.pair()
        val ra = async { a.protocol.runInitiator(la) }
        val rb = async { b.protocol.runResponder(lb) }
        ra.await() to rb.await()
    }

    @Test fun fullExchangeStoresBothPeersWithBothKeys() {
        val a = Side("Register", tmp.root)
        val b = Side("Back room", tmp.root)
        val (ra, rb) = run(a, b)
        assertThat(ra).isInstanceOf(PairingResult.Paired::class.java)
        assertThat(rb).isInstanceOf(PairingResult.Paired::class.java)
        assertThat(a.shownCode).isEqualTo(b.shownCode)
        val storedB = a.store.get(b.keys.fingerprint())!!
        assertThat(storedB.commandPublic).isEqualTo(b.keys.commandPublic())
        assertThat(storedB.name).isEqualTo("Back room")
        assertThat(b.store.get(a.keys.fingerprint())!!.identityPublic).isEqualTo(a.keys.identityPublic())
        // AC6.1 / AC6.3: both owners passed their own credential.
        assertThat(a.gate.calls).containsExactly("Add trusted device")
        assertThat(b.gate.calls).containsExactly("Add trusted device")
    }

    @Test fun refusedWithoutCredentialSuccessAndPeerDoesNotStoreEither() {
        val a = Side("A", tmp.root, FakeGate(GateResult.Failed("cancelled")))
        val b = Side("B", tmp.root)
        val (ra, rb) = run(a, b)
        assertThat(ra).isInstanceOf(PairingResult.Refused::class.java)
        assertThat(rb).isEqualTo(PairingResult.PeerDeclined)
        assertThat(a.store.all()).isEmpty()
        assertThat(b.store.all()).isEmpty()
    }

    @Test fun noSecureLockBlocksPairing() {
        val a = Side("A", tmp.root)
        val b = Side("B", tmp.root, FakeGate(GateResult.NoSecureLock))
        val (_, rb) = run(a, b)
        assertThat(rb).isEqualTo(PairingResult.Refused(GateResult.NoSecureLock))
        assertThat(a.store.all()).isEmpty()
    }

    @Test fun codeMismatchStoresNothingAndSkipsCredential() {
        val a = Side("A", tmp.root)
        val b = Side("B", tmp.root, match = false)
        val (ra, rb) = run(a, b)
        assertThat(rb).isEqualTo(PairingResult.CodeMismatch)
        assertThat(ra).isEqualTo(PairingResult.PeerDeclined)
        assertThat(b.gate.calls).isEmpty()
        assertThat(a.store.all() + b.store.all()).isEmpty()
    }

    @Test fun removeNeedsCredential() = runBlocking {
        val gate = FakeGate()
        val store = TrustedDeviceStore(java.io.File(tmp.root, "t.json"))
        val keys = FakeIdentityKeys()
        val f = PairingFinisher(gate, store) { 5L }
        f.finish(PeerKeys(keys.identityPublic(), keys.commandPublic(), "X"), true)
        assertThat(store.all()).hasSize(1)
        gate.result = GateResult.Failed("no")
        assertThat(f.remove(keys.fingerprint())).isFalse()
        assertThat(store.all()).hasSize(1)
        gate.result = GateResult.Success()
        assertThat(f.remove(keys.fingerprint())).isTrue()
        assertThat(store.all()).isEmpty()
        // The list survives a reload from disk.
        assertThat(TrustedDeviceStore(java.io.File(tmp.root, "t.json")).all()).isEmpty()
    }
}
