package ws.crandell.fafo.net

import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.crypto.Hkdf
import ws.crandell.fafo.crypto.WireReader
import ws.crandell.fafo.crypto.WireWriter
import ws.crandell.fafo.pairing.IdentityKeys
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Ephemeral ECDH, injected so tests can swap it. Software keys in memory only (Keystore ECDH needs API 31). */
interface EphemeralCrypto {
    fun newKeyPair(): KeyPair
    fun agree(mine: KeyPair, peerPublicX509: ByteArray): ByteArray

    object Jca : EphemeralCrypto {
        override fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }

        override fun agree(mine: KeyPair, peerPublicX509: ByteArray): ByteArray {
            val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicX509))
            return KeyAgreement.getInstance("ECDH").run {
                init(mine.private)
                doPhase(peer, true)
                generateSecret()
            }
        }
    }
}

/** Looks up a trusted peer's identity public key (X.509) by fingerprint; null = unknown. */
fun interface TrustLookup {
    fun identityPublic(fingerprint: String): ByteArray?
}

class HandshakeFailed(message: String) : Exception(message)

/** Keys and identities a finished handshake hands to [SecureChannel] and the audio path (F10). */
class SessionKeys(
    val peerFingerprint: String,
    val initiator: Boolean,
    val sessionId: ByteArray,
    val controlSend: ByteArray,
    val controlReceive: ByteArray,
    val audioSend: ByteArray,
    val audioReceive: ByteArray,
)

/**
 * AC9.4: SIGMA-style session handshake, initiator proves itself first, so a listening phone never reveals
 * its identity to a stranger.
 *
 * 1. I -> R  `v || ephI || nI`
 * 2. R -> I  `v || ephR || nR` (nothing identifying)
 * 3. I -> R  enc_hs(i2r)[ fpI || sig_I("fafo-v1 initiator" || th(1,2)) ]
 * 4. R checks fpI is trusted and the signature verifies, else closes silently. Then
 *    R -> I  enc_hs(r2i)[ fpR || sig_R("fafo-v1 responder" || th(1,2,3)) ]
 * Final keys: HKDF over the ECDH secret with th(1..4) as salt, one label per key. The version byte is
 * inside every transcript hash, so changing it breaks both signatures (no downgrade).
 */
object SessionHandshake {
    const val VERSION = 1
    private const val NONCE = 32
    private const val MAX_FIELD = 512

    private fun th(vararg msgs: ByteArray) = Crypto.sha256(*msgs.map { Crypto.lp(it) }.toTypedArray())

    private fun gcm(mode: Int, key: ByteArray, aad: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, ByteArray(12)))
            updateAAD(aad)
            doFinal(data)
        }

    private fun hello(eph: KeyPair, nonce: ByteArray) =
        WireWriter().u8(VERSION).bytes(eph.public.encoded).bytes(nonce).build()

    private class Hello(val version: Int, val eph: ByteArray, val nonce: ByteArray)

    private fun parseHello(b: ByteArray): Hello = try {
        WireReader(b).run { Hello(u8(), bytes(MAX_FIELD), bytes(64)) }
    } catch (e: Exception) {
        throw HandshakeFailed("bad hello")
    }

    private fun proof(key: ByteArray, aad: ByteArray, fp: String, sig: ByteArray) =
        gcm(Cipher.ENCRYPT_MODE, key, aad, WireWriter().str(fp).bytes(sig).build())

    private fun openProof(key: ByteArray, aad: ByteArray, ct: ByteArray): Pair<String, ByteArray> = try {
        WireReader(gcm(Cipher.DECRYPT_MODE, key, aad, ct)).run { str(64) to bytes(MAX_FIELD) }
    } catch (e: Exception) {
        throw HandshakeFailed("bad proof")
    }

    private fun keys(secret: ByteArray, salt: ByteArray, peer: String, initiator: Boolean): SessionKeys {
        fun k(label: String) = Hkdf.derive(salt, secret, "fafo-v1 $label")
        val ci2r = k("ctl i2r")
        val cr2i = k("ctl r2i")
        val ai2r = k("audio i2r")
        val ar2i = k("audio r2i")
        val sid = Hkdf.derive(salt, secret, "fafo-v1 sid", 8)
        return if (initiator) SessionKeys(peer, true, sid, ci2r, cr2i, ai2r, ar2i)
        else SessionKeys(peer, false, sid, cr2i, ci2r, ar2i, ai2r)
    }

    /** Initiator side, step by step (so tests can tamper between steps). */
    class Initiator(
        private val identity: IdentityKeys,
        private val trust: TrustLookup,
        /** If set, the responder must be exactly this peer. */
        private val expectedPeer: String? = null,
        private val crypto: EphemeralCrypto = EphemeralCrypto.Jca,
        private val version: Int = VERSION,
    ) {
        private val eph = crypto.newKeyPair()
        private val nonce = Crypto.randomBytes(NONCE)
        private lateinit var m1: ByteArray
        private lateinit var m2: ByteArray
        private lateinit var m3: ByteArray
        private lateinit var secret: ByteArray

        fun start(): ByteArray = WireWriter().u8(version).bytes(eph.public.encoded).bytes(nonce).build().also { m1 = it }

        fun onResponderHello(msg: ByteArray): ByteArray {
            m2 = msg
            val h = parseHello(msg)
            if (h.version != VERSION) throw HandshakeFailed("version")
            secret = try { crypto.agree(eph, h.eph) } catch (e: Exception) { throw HandshakeFailed("bad key") }
            val th12 = th(m1, m2)
            val hsKey = Hkdf.derive(th12, secret, "fafo-v1 hs i2r")
            val sig = identity.signIdentity("fafo-v1 initiator".toByteArray() + th12)
            m3 = proof(hsKey, th12, identity.fingerprint(), sig)
            return m3
        }

        fun onResponderProof(msg: ByteArray): SessionKeys {
            val th123 = th(m1, m2, m3)
            val hsKey = Hkdf.derive(th123, secret, "fafo-v1 hs r2i")
            val (fp, sig) = openProof(hsKey, th123, msg)
            if (expectedPeer != null && fp != expectedPeer) throw HandshakeFailed("unexpected peer")
            val pub = trust.identityPublic(fp) ?: throw HandshakeFailed("unknown peer")
            if (!Crypto.verifyEcdsa(pub, "fafo-v1 responder".toByteArray() + th123, sig)) throw HandshakeFailed("bad signature")
            return keys(secret, th(m1, m2, m3, msg), fp, initiator = true)
        }
    }

    class Responder(
        private val identity: IdentityKeys,
        private val trust: TrustLookup,
        private val crypto: EphemeralCrypto = EphemeralCrypto.Jca,
    ) {
        private val eph = crypto.newKeyPair()
        private val nonce = Crypto.randomBytes(NONCE)
        private lateinit var m1: ByteArray
        private lateinit var m2: ByteArray
        private lateinit var secret: ByteArray
        private var peer: String? = null

        fun onInitiatorHello(msg: ByteArray): ByteArray {
            m1 = msg
            val h = parseHello(msg)
            if (h.version != VERSION) throw HandshakeFailed("version")
            secret = try { crypto.agree(eph, h.eph) } catch (e: Exception) { throw HandshakeFailed("bad key") }
            m2 = hello(eph, nonce)
            return m2
        }

        /** Returns the reply and the keys; throws (send nothing, close) on an unknown key or bad signature. */
        fun onInitiatorProof(msg: ByteArray): Pair<ByteArray, SessionKeys> {
            val th12 = th(m1, m2)
            val hsKey = Hkdf.derive(th12, secret, "fafo-v1 hs i2r")
            val (fp, sig) = openProof(hsKey, th12, msg)
            val pub = trust.identityPublic(fp) ?: throw HandshakeFailed("unknown peer")
            if (!Crypto.verifyEcdsa(pub, "fafo-v1 initiator".toByteArray() + th12, sig)) throw HandshakeFailed("bad signature")
            peer = fp
            val th123 = th(m1, m2, msg)
            val replyKey = Hkdf.derive(th123, secret, "fafo-v1 hs r2i")
            val mySig = identity.signIdentity("fafo-v1 responder".toByteArray() + th123)
            val m4 = proof(replyKey, th123, identity.fingerprint(), mySig)
            return m4 to keys(secret, th(m1, m2, msg, m4), fp, initiator = false)
        }
    }
}
