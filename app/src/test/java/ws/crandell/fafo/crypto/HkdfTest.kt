package ws.crandell.fafo.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** RFC 5869 appendix A, SHA-256 test cases 1 and 3. */
class HkdfTest {
    private fun ff(n: Int, b: Int) = ByteArray(n) { b.toByte() }

    @Test fun rfc5869Case1() {
        val ikm = ff(22, 0x0b)
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val prk = Hkdf.extract(salt, ikm)
        assertThat(Crypto.hex(prk)).isEqualTo("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5")
        assertThat(Crypto.hex(Hkdf.expand(prk, info, 42)))
            .isEqualTo("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")
    }

    @Test fun rfc5869Case2LongInputs() {
        val ikm = ByteArray(80) { it.toByte() }
        val salt = ByteArray(80) { (0x60 + it).toByte() }
        val info = ByteArray(80) { (0xb0 + it).toByte() }
        val prk = Hkdf.extract(salt, ikm)
        assertThat(Crypto.hex(prk)).isEqualTo("06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244")
        assertThat(Crypto.hex(Hkdf.expand(prk, info, 82))).isEqualTo(
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09" +
                "da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87",
        )
    }

    @Test fun rfc5869Case3EmptySaltAndInfo() {
        val ikm = ff(22, 0x0b)
        val prk = Hkdf.extract(ByteArray(0), ikm)
        assertThat(Crypto.hex(prk)).isEqualTo("19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04")
        assertThat(Crypto.hex(Hkdf.expand(prk, ByteArray(0), 42)))
            .isEqualTo("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8")
    }
}
