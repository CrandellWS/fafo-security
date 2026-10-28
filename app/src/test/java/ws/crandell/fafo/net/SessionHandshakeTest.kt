package ws.crandell.fafo.net

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import ws.crandell.fafo.link.LinkClosed
import ws.crandell.fafo.testutil.FakeIdentityKeys
import ws.crandell.fafo.testutil.MemoryLink

class SessionHandshakeTest {
    private val a = FakeIdentityKeys()
    private val b = FakeIdentityKeys()
    private val stranger = FakeIdentityKeys()
    private val trustOfA = TrustLookup { fp -> if (fp == b.fingerprint()) b.identityPublic() else null }
    private val trustOfB = TrustLookup { fp -> if (fp == a.fingerprint()) a.identityPublic() else null }

    private fun bytesContain(hay: ByteArray, needle: ByteArray): Boolean =
        (0..hay.size - needle.size).any { i -> needle.indices.all { hay[i + it] == needle[it] } }

    @Test fun trustedPeersAgreeOnKeys() {
        val i = SessionHandshake.Initiator(a, trustOfA, expectedPeer = b.fingerprint())
        val r = SessionHandshake.Responder(b, trustOfB)
        val m2 = r.onInitiatorHello(i.start())
        val m3 = i.onResponderHello(m2)
        val (m4, rk) = r.onInitiatorProof(m3)
        val ik = i.onResponderProof(m4)
        assertThat(ik.peerFingerprint).isEqualTo(b.fingerprint())
        assertThat(rk.peerFingerprint).isEqualTo(a.fingerprint())
        assertThat(ik.controlSend).isEqualTo(rk.controlReceive)
        assertThat(ik.controlReceive).isEqualTo(rk.controlSend)
        assertThat(ik.audioSend).isEqualTo(rk.audioReceive)
        assertThat(ik.sessionId).isEqualTo(rk.sessionId)
        // Control and audio keys never coincide (separate HKDF labels).
        assertThat(ik.controlSend).isNotEqualTo(ik.audioSend)
        assertThat(ik.controlSend).isNotEqualTo(ik.controlReceive)
    }

    @Test fun responderRevealsNothingBeforeInitiatorVerifies() {
        val r = SessionHandshake.Responder(b, trustOfB)
        val m2 = r.onInitiatorHello(SessionHandshake.Initiator(stranger, trustOfA).start())
        assertThat(bytesContain(m2, b.identityPublic())).isFalse()
        assertThat(bytesContain(m2, b.fingerprint().toByteArray())).isFalse()
    }

    @Test fun unknownInitiatorGetsNoReply() {
        val i = SessionHandshake.Initiator(stranger, TrustLookup { b.identityPublic() })
        val r = SessionHandshake.Responder(b, trustOfB)
        val m3 = i.onResponderHello(r.onInitiatorHello(i.start()))
        assertThrows(HandshakeFailed::class.java) { r.onInitiatorProof(m3) }
    }

    @Test fun unknownResponderRejected() {
        val i = SessionHandshake.Initiator(a, TrustLookup { null })
        val r = SessionHandshake.Responder(b, trustOfB)
        val (m4, _) = r.onInitiatorProof(i.onResponderHello(r.onInitiatorHello(i.start())))
        assertThrows(HandshakeFailed::class.java) { i.onResponderProof(m4) }
    }

    @Test fun badSignatureRejected() {
        // The initiator claims A's fingerprint but signs with another key.
        val impostor = object : ws.crandell.fafo.pairing.IdentityKeys by stranger {
            override fun fingerprint() = a.fingerprint()
        }
        val i = SessionHandshake.Initiator(impostor, trustOfA)
        val r = SessionHandshake.Responder(b, trustOfB)
        val m3 = i.onResponderHello(r.onInitiatorHello(i.start()))
        assertThrows(HandshakeFailed::class.java) { r.onInitiatorProof(m3) }
    }

    @Test fun changedTranscriptByteFails() {
        val i = SessionHandshake.Initiator(a, trustOfA)
        val r = SessionHandshake.Responder(b, trustOfB)
        val m1 = i.start()
        val m2 = r.onInitiatorHello(m1).copyOf()
        m2[m2.size - 1] = (m2[m2.size - 1] + 1).toByte() // tamper with nonceR in transit
        val m3 = i.onResponderHello(m2)
        assertThrows(HandshakeFailed::class.java) { r.onInitiatorProof(m3) }
    }

    @Test fun versionDowngradeFails() {
        // An initiator speaking an older version is refused outright.
        val old = SessionHandshake.Initiator(a, trustOfA, version = 0)
        assertThrows(HandshakeFailed::class.java) { SessionHandshake.Responder(b, trustOfB).onInitiatorHello(old.start()) }
        // A man in the middle rewriting the version byte of a v1 hello breaks the signed transcript.
        val i = SessionHandshake.Initiator(a, trustOfA)
        val r = SessionHandshake.Responder(b, trustOfB)
        val m1 = i.start().copyOf()
        m1[0] = 2
        assertThrows(HandshakeFailed::class.java) { r.onInitiatorHello(m1) }
    }

    @Test fun expectedPeerEnforced() {
        val c = FakeIdentityKeys()
        val i = SessionHandshake.Initiator(a, TrustLookup { fp -> if (fp == c.fingerprint()) c.identityPublic() else null }, expectedPeer = b.fingerprint())
        val r = SessionHandshake.Responder(c, trustOfB)
        val (m4, _) = r.onInitiatorProof(i.onResponderHello(r.onInitiatorHello(i.start())))
        assertThrows(HandshakeFailed::class.java) { i.onResponderProof(m4) }
    }

    @Test fun channelRoundTripOverLink() = runBlocking<Unit> {
        val (la, lb) = MemoryLink.pair()
        val ca = async { SecureChannel.initiate(la, SessionHandshake.Initiator(a, trustOfA)) }
        val cb = async { SecureChannel.respond(lb, SessionHandshake.Responder(b, trustOfB)) }
        val x = ca.await()
        val y = cb.await()
        x.send(Message.Hello("Register", null))
        assertThat(y.receive()).isEqualTo(Message.Hello("Register", null))
        y.send(Message.Ping)
        assertThat(x.receive()).isEqualTo(Message.Ping)
        // A replayed frame (the Hello again) is refused and closes the session.
        lb.let { }
        x.close()
        assertThrows(LinkClosed::class.java) { runBlocking { y.receive() } }
    }

    @Test fun sessionExpiresAfterOneHour() = runBlocking<Unit> {
        var now = 0L
        val (la, lb) = MemoryLink.pair()
        val ca = async { SecureChannel.initiate(la, SessionHandshake.Initiator(a, trustOfA)) { now } }
        val cb = async { SecureChannel.respond(lb, SessionHandshake.Responder(b, trustOfB)) { now } }
        val x = ca.await(); cb.await()
        now = SecureChannel.MAX_AGE_MS
        assertThat(x.expired).isTrue()
        assertThrows(LinkClosed::class.java) { runBlocking { x.send(Message.Ping) } }
    }
}
