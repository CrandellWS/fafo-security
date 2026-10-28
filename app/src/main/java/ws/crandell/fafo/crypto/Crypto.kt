package ws.crandell.fafo.crypto

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** JCA helpers only (no extra crypto dependency). Pure JVM, testable without Android. */
object Crypto {
    val random = SecureRandom()

    fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** Length-prefixed concatenation, so field boundaries are unambiguous inside hashes and signatures. */
    fun lp(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val d = DataOutputStream(out)
        parts.forEach { d.writeInt(it.size); d.write(it) }
        return out.toByteArray()
    }

    /** Key fingerprint: hex of the first 16 bytes of SHA-256 over the X.509 identity public key. */
    fun fingerprint(identityPublic: ByteArray): String = hex(sha256(identityPublic).copyOf(16))

    /** Fingerprint grouped for display: "a1b2 c3d4 ...". */
    fun displayFingerprint(fp: String) = fp.chunked(4).joinToString(" ")

    fun ecPublicKey(x509: ByteArray): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(x509))

    fun verifyEcdsa(publicX509: ByteArray, data: ByteArray, sig: ByteArray): Boolean = try {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(ecPublicKey(publicX509))
            update(data)
            verify(sig)
        }
    } catch (e: Exception) {
        false
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)
}

/** Minimal binary writer/reader for protocol messages: u8 type, then u16/u32 length-prefixed fields. */
class WireWriter {
    private val out = ByteArrayOutputStream()
    private val d = DataOutputStream(out)
    fun u8(v: Int) = apply { d.writeByte(v) }
    fun u32(v: Int) = apply { d.writeInt(v) }
    fun u64(v: Long) = apply { d.writeLong(v) }
    fun bytes(b: ByteArray) = apply { d.writeInt(b.size); d.write(b) }
    fun str(s: String) = bytes(s.toByteArray(Charsets.UTF_8))
    fun bool(b: Boolean) = u8(if (b) 1 else 0)
    fun build(): ByteArray = out.toByteArray()
}

class WireReader(data: ByteArray) {
    private val d = DataInputStream(data.inputStream())
    fun u8(): Int = d.readUnsignedByte()
    fun u32(): Int = d.readInt()
    fun u64(): Long = d.readLong()
    fun bytes(max: Int = 65536): ByteArray {
        val n = d.readInt()
        require(n in 0..max) { "field too large" }
        return ByteArray(n).also { d.readFully(it) }
    }
    fun str(max: Int = 1024) = String(bytes(max), Charsets.UTF_8)
    fun bool() = u8() != 0
}
