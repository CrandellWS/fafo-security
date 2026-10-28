package ws.crandell.fafo.mail

/** How the SMTP connection is secured. There is no plaintext option: AUTH is only ever sent over TLS. */
enum class SmtpSecurity(val defaultPort: Int) { SSL_TLS(465), STARTTLS(587) }

/** Photo email settings (round 3, opt-in, off by default). The app password lives in [SecretBox], not here. */
data class MailConfig(
    val enabled: Boolean = false,
    val recipients: List<String> = emptyList(),
    val provider: MailProviderKind = MailProviderKind.SMTP,
    val host: String = "",
    val port: Int = SmtpSecurity.SSL_TLS.defaultPort,
    val security: SmtpSecurity = SmtpSecurity.SSL_TLS,
    val user: String = "",
    /** Sender address; the username when blank. */
    val from: String = "",
    /** Gmail account chosen in the play flavor. */
    val gmailAccount: String = "",
) {
    val sender: String get() = from.ifBlank { if (provider == MailProviderKind.GMAIL) gmailAccount else user }

    /** True when there is enough to try a send (the password is checked separately). */
    val complete: Boolean get() = recipients.isNotEmpty() && when (provider) {
        MailProviderKind.SMTP -> host.isNotBlank() && port in 1..65535 && user.isNotBlank() && Email.valid(sender)
        MailProviderKind.GMAIL -> gmailAccount.isNotBlank()
    }

    companion object {
        const val MAX_RECIPIENTS = 5
    }
}

object Email {
    private val pattern = Regex("""^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$""")
    fun valid(s: String): Boolean = s.length <= 254 && pattern.matches(s.trim())

    /** Splits a typed list ("a@x.org, b@y.net") into valid, de-duplicated addresses (max 5) and the rejects. */
    fun parseList(text: String): Pair<List<String>, List<String>> {
        val parts = text.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        val ok = parts.filter(::valid).distinctBy { it.lowercase() }.take(MailConfig.MAX_RECIPIENTS)
        return ok to parts.filterNot(::valid)
    }
}

/** Why a send failed, with a message the owner can act on. [retryable] errors are tried again later. */
sealed class MailError(val message: String, val retryable: Boolean) {
    class Auth(message: String = "Server refused the username or app password (535). Gmail, Outlook and Yahoo need an app password.") : MailError(message, false)
    class Tls(message: String) : MailError(message, false)
    class Network(message: String = "No connection to the mail server") : MailError(message, true)
    class Timeout(message: String = "The mail server did not answer in time") : MailError(message, true)
    class Rejected(val code: Int, message: String) : MailError(message, code in 400..499)
    class Config(message: String) : MailError(message, false)

    override fun toString(): String = "${javaClass.simpleName}($message)"
}

sealed interface SendResult {
    data object Sent : SendResult
    data class Failed(val error: MailError) : SendResult
}

/** One way out (SMTP in every build, Gmail in play). [message] is a complete RFC 5322 message. */
fun interface MailSender {
    suspend fun send(from: String, to: List<String>, message: ByteArray): SendResult
}
