package ws.crandell.fafo

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Spec section 6: no hardcoded non-local URLs or hosts in app/src/main or app/src/oss. Allowlist: XML
 * namespace URIs and verbatim font license text. app/src/play may hold exactly the two Gmail strings
 * (the send endpoint and the gmail.send scope) and nothing else. SMTP hosts always come from the user,
 * never from a literal. Public IPv4 literals fail everywhere; private/link-local ones are allowed.
 */
class NoRemoteEndpointsTest {
    private val appDir = listOf(File("."), File("app")).first { File(it, "src/main").isDirectory }
    private fun srcSet(name: String) = File(appDir, "src/$name")

    private val urlPattern = Regex("""(?i)\b(https?|wss?|ftp|smtps?)://[^\s"'<>)]*""")
    private val allowedUrlPrefixes = listOf(
        "http://schemas.android.com/apk/res/android",
        "http://schemas.android.com/apk/res-auto",
        "http://schemas.android.com/tools",
        "http://schemas.android.com/aapt",
    )
    private val playAllowed = setOf(
        "https://gmail.googleapis.com/upload/gmail/v1/users/me/messages/send",
        "https://www.googleapis.com/auth/gmail.send",
    )
    private val ipv4Pattern = Regex("""(?<![\d.])(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?![\d.])""")
    private val hostPattern = Regex("""(?i)"[a-z0-9-]+(\.[a-z0-9-]+)*\.(com|net|org|io|dev|app|co|ws|me|info)"""")

    private fun sourceFiles(set: String) = srcSet(set).walkTopDown()
        .filter { it.isFile && it.extension in setOf("kt", "java", "xml", "json", "txt", "properties") }

    private fun isPrivateOrUnspecified(a: Int, b: Int): Boolean =
        a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) || a == 0

    /**
     * Bundled font licenses must be shipped verbatim, and the SIL OFL text names its own web pages. Allowed
     * only inside assets/licenses/ and only these exact URLs: license text, never fetched by the app.
     */
    private val licenseUrls = setOf("http://scripts.sil.org/OFL", "https://github.com/jpt/barlow")
    private fun isLicenseText(f: File, url: String) =
        f.invariantSeparatorsPath.contains("assets/licenses/") && url in licenseUrls

    private fun urlOffenders(set: String, extraAllowed: Set<String> = emptySet()) = sourceFiles(set).flatMap { f ->
        urlPattern.findAll(f.readText()).map { it.value }
            .filterNot { url -> allowedUrlPrefixes.any { url.startsWith(it) } || isLicenseText(f, url) || url in extraAllowed }
            .map { "${f.path}: $it" }
    }.toList()

    @Test fun noRemoteUrlsInMainOrOss() {
        assertThat(urlOffenders("main")).isEmpty()
        assertThat(urlOffenders("oss")).isEmpty()
    }

    @Test fun playHoldsOnlyTheGmailEndpointAndScope() {
        assertThat(urlOffenders("play", playAllowed)).isEmpty()
    }

    @Test fun noHardcodedHostNames() {
        val offenders = listOf("main", "oss", "play").flatMap { set ->
            sourceFiles(set).flatMap { f -> hostPattern.findAll(f.readText()).map { "${f.path}: ${it.value}" } }.toList()
        }
        assertThat(offenders).isEmpty()
    }

    @Test fun noPublicIpLiterals() {
        val offenders = listOf("main", "oss", "play").flatMap { set ->
            sourceFiles(set).flatMap { f ->
                ipv4Pattern.findAll(f.readText()).filter { m ->
                    val parts = m.groupValues.drop(1).map { it.toInt() }
                    parts.all { it in 0..255 } && !isPrivateOrUnspecified(parts[0], parts[1])
                }.map { "${f.path}: ${it.value}" }
            }.toList()
        }
        assertThat(offenders).isEmpty()
    }

    @Test fun scannerCatchesARemoteUrl() {
        // Guard against a broken regex silently passing everything.
        assertThat(urlPattern.containsMatchIn("val u = \"https://example.invalid/x\"")).isTrue()
        assertThat(urlPattern.containsMatchIn("val u = \"smtps://mail.example.invalid\"")).isTrue()
        assertThat(hostPattern.containsMatchIn("val h = \"smtp.gmail.com\"")).isTrue()
        assertThat(ipv4Pattern.containsMatchIn("8.8.8.8")).isTrue()
    }

    @Test fun noWebView() {
        val offenders = listOf("main", "oss", "play").flatMap { set ->
            sourceFiles(set).filter { it.readText().contains("android.webkit.WebView") }.toList()
        }
        assertThat(offenders).isEmpty()
    }
}
