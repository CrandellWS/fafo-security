package ws.crandell.fafo.mail.flavor

import ws.crandell.fafo.mail.MailProviderKind

/** oss flavor: the user's own SMTP server only. No Google libraries in this build. */
object FlavorMail {
    const val FLAVOR = "oss"
    val providers: List<MailProviderKind> = listOf(MailProviderKind.SMTP)
}
