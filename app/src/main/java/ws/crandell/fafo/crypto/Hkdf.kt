package ws.crandell.fafo.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HKDF-SHA256 (RFC 5869) over JCA `HmacSHA256`; Java 17 has no HKDF class. */
object Hkdf {
    private const val HASH_LEN = 32

    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val s = if (salt == null || salt.isEmpty()) ByteArray(HASH_LEN) else salt
        return hmac(s, ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * HASH_LEN)
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var i = 1
        while (pos < length) {
            t = hmac(prk, t + info + byteArrayOf(i.toByte()))
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            i++
        }
        return out
    }

    fun derive(salt: ByteArray?, ikm: ByteArray, info: String, length: Int = 32): ByteArray =
        expand(extract(salt, ikm), info.toByteArray(Charsets.UTF_8), length)

    fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }
}
