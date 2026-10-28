package ws.crandell.fafo.mail.flavor

import ws.crandell.fafo.mail.MailProviderKind

/** play flavor: the user's own SMTP server, or Gmail through the user's Google account (gmail.send). */
object FlavorMail {
    const val FLAVOR = "play"
    val providers: List<MailProviderKind> = listOf(MailProviderKind.SMTP, MailProviderKind.GMAIL)

    /** Gmail API sender (filled in by the Gmail commit). */
    fun gmailSender(context: android.content.Context, account: String, network: android.net.Network?): ws.crandell.fafo.mail.MailSender? = null
}
