package ws.crandell.fafo.mail

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Attachment(val name: String, val bytes: ByteArray, val type: String = "image/jpeg")

/**
 * Builds the photo email: multipart/mixed, a plain text part and base64 JPEG attachments wrapped at 76
 * characters, CRLF line ends, an RFC 2047 encoded subject. Pure Kotlin.
 */
object MimeBuilder {
    private val random = SecureRandom()

    fun build(
        from: String,
        to: List<String>,
        subject: String,
        body: String,
        attachments: List<Attachment>,
        date: Date = Date(),
        domain: String = "fafo.invalid",
    ): ByteArray {
        val boundary = "fafo-" + token()
        val sb = StringBuilder()
        fun line(s: String = "") { sb.append(s).append("\r\n") }
        line("From: $from")
        line("To: ${to.joinToString(", ")}")
        line("Subject: ${encodeHeader(subject)}")
        line("Date: ${rfc5322Date(date)}")
        line("Message-ID: <${token()}@$domain>")
        line("MIME-Version: 1.0")
        line("X-Mailer: FAFO Security")
        line("Content-Type: multipart/mixed; boundary=\"$boundary\"")
        line()
        line("--$boundary")
        line("Content-Type: text/plain; charset=UTF-8")
        line("Content-Transfer-Encoding: base64")
        line()
        wrap(Base64.getEncoder().encodeToString(body.replace("\r\n", "\n").replace("\n", "\r\n").toByteArray()), sb)
        for (a in attachments) {
            line("--$boundary")
            line("Content-Type: ${a.type}; name=\"${a.name}\"")
            line("Content-Transfer-Encoding: base64")
            line("Content-Disposition: attachment; filename=\"${a.name}\"")
            line()
            wrap(Base64.getEncoder().encodeToString(a.bytes), sb)
        }
        line("--$boundary--")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    /** RFC 2047 B-encoding when the subject is not plain ASCII. */
    fun encodeHeader(s: String): String =
        if (s.all { it.code in 32..126 }) s else "=?UTF-8?B?" + Base64.getEncoder().encodeToString(s.toByteArray()) + "?="

    fun rfc5322Date(d: Date): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(d)

    private fun wrap(b64: String, sb: StringBuilder) {
        var i = 0
        while (i < b64.length) {
            sb.append(b64, i, minOf(i + 76, b64.length)).append("\r\n")
            i += 76
        }
    }

    private fun token(): String = ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    /** Raw attachment bytes per message: providers cap at about 25 MB, base64 adds a third. */
    const val MAX_RAW_BYTES = 18L * 1024 * 1024

    /** Splits photos into messages of at most [MAX_RAW_BYTES] raw bytes, keeping their order. */
    fun <T> split(items: List<T>, size: (T) -> Long, max: Long = MAX_RAW_BYTES): List<List<T>> {
        val out = mutableListOf<MutableList<T>>()
        var cur = mutableListOf<T>(); var total = 0L
        for (it in items) {
            val s = size(it)
            if (cur.isNotEmpty() && total + s > max) { out += cur; cur = mutableListOf(); total = 0 }
            cur += it; total += s
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }
}
