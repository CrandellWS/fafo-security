package ws.crandell.fafo.mail.flavor

import ws.crandell.fafo.mail.MailProviderKind

/** oss flavor: the user's own SMTP server only. No Google libraries in this build. */
object FlavorMail {
    const val FLAVOR = "oss"
    val providers: List<MailProviderKind> = listOf(MailProviderKind.SMTP)

    /** No Google libraries in this build: Gmail is never offered. */
    fun gmailSender(context: android.content.Context, account: String, network: android.net.Network?): ws.crandell.fafo.mail.MailSender? = null

    /** Never called in this build (no Gmail option is shown). */
    fun connectGmail(activity: androidx.activity.ComponentActivity, onDone: (account: String?, error: String?) -> Unit) =
        onDone(null, "This build has no Google sign-in. Use your mail server (SMTP).")
}
