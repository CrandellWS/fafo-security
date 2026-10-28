package ws.crandell.fafo.mail.flavor

import ws.crandell.fafo.mail.MailProviderKind

/** play flavor: the user's own SMTP server, or Gmail through the user's Google account (gmail.send). */
object FlavorMail {
    const val FLAVOR = "play"
    val providers: List<MailProviderKind> = listOf(MailProviderKind.SMTP, MailProviderKind.GMAIL)
}
