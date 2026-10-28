package ws.crandell.fafo

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Where the app may open a socket or an HTTP connection. LAN/Bluetooth peer traffic lives in net/ (guarded
 * by LocalAddressPolicy) and the intercom's audio sockets; opt-in photo email lives in mail/SmtpClient.kt
 * (the user's own SMTP server) and, in the play flavor only, GmailSender.kt. Anything else is a new egress
 * path and must be reviewed against spec section 6 first.
 */
class EgressTest {
    private val appDir = listOf(File("."), File("app")).first { File(it, "src/main").isDirectory }

    private val egress = Regex(
        """\b(Socket|ServerSocket|DatagramSocket|SSLSocket)\s*\(|SSLSocketFactory|HttpsURLConnection|HttpURLConnection|openConnection\s*\(|\bjava\.net\.URL\b|\bURL\s*\("""
    )
    private val allowed = listOf(
        "src/main/java/ws/crandell/fafo/net/",
        "src/main/java/ws/crandell/fafo/intercom/AudioSockets.kt",
        "src/main/java/ws/crandell/fafo/mail/SmtpClient.kt",
        "src/play/java/ws/crandell/fafo/mail/GmailSender.kt",
    )

    @Test fun socketsOnlyInReviewedFiles() {
        val offenders = listOf("main", "oss", "play").flatMap { set ->
            File(appDir, "src/$set").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.flatMap { f ->
                val rel = f.relativeTo(appDir).invariantSeparatorsPath
                if (allowed.any { rel.startsWith(it) }) emptySequence()
                else f.readLines().asSequence().withIndex()
                    .filter { (_, l) -> !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*") && egress.containsMatchIn(l) }
                    .map { (i, l) -> "$rel:${i + 1}: ${l.trim()}" }
            }.toList()
        }
        assertThat(offenders).isEmpty()
    }

    @Test fun patternCatchesASocket() {
        assertThat(egress.containsMatchIn("val s = Socket(host, 25)")).isTrue()
        assertThat(egress.containsMatchIn("URL(u).openConnection() as HttpsURLConnection")).isTrue()
    }
}
