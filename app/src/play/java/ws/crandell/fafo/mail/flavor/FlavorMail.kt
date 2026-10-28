package ws.crandell.fafo.mail.flavor

import android.content.Context
import android.net.Network
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.Identity
import ws.crandell.fafo.mail.GmailApi
import ws.crandell.fafo.mail.GmailSender
import ws.crandell.fafo.mail.MailProviderKind
import ws.crandell.fafo.mail.MailSender

/** play flavor: the user's own SMTP server, or Gmail through the user's Google account (gmail.send). */
object FlavorMail {
    const val FLAVOR = "play"
    val providers: List<MailProviderKind> = listOf(MailProviderKind.SMTP, MailProviderKind.GMAIL)

    fun gmailSender(context: Context, account: String, network: Network?): MailSender? =
        if (account.isBlank()) null else GmailSender(context.applicationContext, account, network)

    /**
     * "Sign in with Google": asks for gmail.send. The first time Google shows the account picker and the
     * consent screen (a PendingIntent we launch); later it answers silently. [onDone] gets the account email
     * or an error message.
     */
    fun connectGmail(activity: ComponentActivity, onDone: (account: String?, error: String?) -> Unit) {
        val client = Identity.getAuthorizationClient(activity)
        client.authorize(GmailApi.request(null))
            .addOnSuccessListener { r ->
                if (!r.hasResolution()) {
                    onDone(r.toGoogleSignInAccount()?.email, if (r.toGoogleSignInAccount()?.email == null) "Google did not return an account" else null)
                    return@addOnSuccessListener
                }
                var launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>? = null
                launcher = activity.activityResultRegistry.register("fafo-gmail", ActivityResultContracts.StartIntentSenderForResult()) { res ->
                    launcher?.unregister()
                    val auth = runCatching { client.getAuthorizationResultFromIntent(res.data) }.getOrNull()
                    val email = auth?.toGoogleSignInAccount()?.email
                    onDone(email, if (email == null) "Google sign-in was cancelled" else null)
                }
                launcher.launch(IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build())
            }
            .addOnFailureListener { onDone(null, "Google sign-in failed: ${it.message ?: it.javaClass.simpleName}") }
    }
}
