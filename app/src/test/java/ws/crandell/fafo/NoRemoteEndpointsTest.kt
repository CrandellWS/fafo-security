package ws.crandell.fafo

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Spec section 6: no hardcoded non-local URLs or hosts anywhere under app/src/main. Allowlist: XML
 * namespace URIs only. Public IPv4 literals fail; private/link-local ones are allowed.
 */
class NoRemoteEndpointsTest {
    private val root = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private val urlPattern = Regex("""(?i)\b(https?|wss?|ftp)://[^\s"'<>)]*""")
    private val allowedUrlPrefixes = listOf(
        "http://schemas.android.com/apk/res/android",
        "http://schemas.android.com/apk/res-auto",
        "http://schemas.android.com/tools",
        "http://schemas.android.com/aapt",
    )
    private val ipv4Pattern = Regex("""(?<![\d.])(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?![\d.])""")

    private fun sourceFiles() = root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml", "json", "txt", "properties") }

    private fun isPrivateOrUnspecified(a: Int, b: Int): Boolean =
        a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) || a == 0

    /**
     * Bundled font licenses must be shipped verbatim, and the SIL OFL text names its own web pages. Allowed
     * only inside assets/licenses/ and only these exact URLs: license text, never fetched by the app.
     */
    private val licenseUrls = setOf("http://scripts.sil.org/OFL", "https://github.com/jpt/barlow")
    private fun isLicenseText(f: File, url: String) =
        f.invariantSeparatorsPath.contains("assets/licenses/") && url in licenseUrls

    @Test fun noRemoteUrls() {
        val offenders = sourceFiles().flatMap { f ->
            urlPattern.findAll(f.readText()).map { it.value }
                .filterNot { url -> allowedUrlPrefixes.any { url.startsWith(it) } || isLicenseText(f, url) }
                .map { "${f.path}: $it" }
        }.toList()
        assertThat(offenders).isEmpty()
    }

    @Test fun noPublicIpLiterals() {
        val offenders = sourceFiles().flatMap { f ->
            ipv4Pattern.findAll(f.readText()).filter { m ->
                val parts = m.groupValues.drop(1).map { it.toInt() }
                parts.all { it in 0..255 } && !isPrivateOrUnspecified(parts[0], parts[1])
            }.map { "${f.path}: ${it.value}" }
        }.toList()
        assertThat(offenders).isEmpty()
    }

    @Test fun scannerCatchesARemoteUrl() {
        // Guard against a broken regex silently passing everything.
        assertThat(urlPattern.containsMatchIn("val u = \"https://example.invalid/x\"")).isTrue()
        assertThat(ipv4Pattern.containsMatchIn("8.8.8.8")).isTrue()
    }

    @Test fun noWebView() {
        val offenders = sourceFiles().filter { it.readText().contains("android.webkit.WebView") }.toList()
        assertThat(offenders).isEmpty()
    }
}
