package ws.crandell.fafo.mail

import android.accounts.Account
import android.content.Context
import android.net.Network
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** play flavor: Gmail API, send-only scope. No client secret: Android OAuth clients are matched by package and signing SHA-1. */
object GmailApi {
    const val SCOPE = "https://www.googleapis.com/auth/gmail.send"
    const val SEND_URL = "https://gmail.googleapis.com/upload/gmail/v1/users/me/messages/send"

    fun request(account: String?): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .apply { if (!account.isNullOrBlank()) setAccount(Account(account, "com.google")) }
        .build()
}

/**
 * Sends the same MIME bytes as SMTP through the Gmail API media upload (message/rfc822). The access token
 * comes silently from the Identity AuthorizationClient once the owner has consented in Settings; when
 * consent is missing or revoked the send fails with "Reconnect Google in Settings".
 */
class GmailSender(private val context: Context, private val account: String, private val network: Network?) : MailSender {
    override suspend fun send(from: String, to: List<String>, message: ByteArray): SendResult = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext SendResult.Failed(MailError.Auth("Reconnect Google in Settings > Photo email"))
        var r = post(token, message)
        if (r == 401) {
            // Expired: ask once more for a fresh token.
            val again = token() ?: return@withContext SendResult.Failed(MailError.Auth("Reconnect Google in Settings > Photo email"))
            r = post(again, message)
        }
        when (r) {
            in 200..299 -> SendResult.Sent
            401, 403 -> SendResult.Failed(MailError.Auth("Google refused the send permission. Reconnect Google in Settings > Photo email."))
            -1 -> SendResult.Failed(MailError.Network("No connection to Gmail"))
            -2 -> SendResult.Failed(MailError.Timeout())
            else -> SendResult.Failed(MailError.Rejected(r, "Gmail said $r"))
        }
    }

    private fun token(): String? = runCatching {
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(GmailApi.request(account)), 20, TimeUnit.SECONDS)
        if (result.hasResolution()) null else result.accessToken
    }.getOrNull()

    /** HTTP status, or -1 network error, -2 timeout. */
    private fun post(token: String, message: ByteArray): Int = try {
        val url = URL(GmailApi.SEND_URL + "?uploadType=media")
        val c = (network?.openConnection(url) ?: url.openConnection()) as HttpsURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "message/rfc822")
            c.setFixedLengthStreamingMode(message.size)
            c.outputStream.use { it.write(message) }
            c.responseCode
        } finally {
            c.disconnect()
        }
    } catch (_: SocketTimeoutException) {
        -2
    } catch (_: IOException) {
        -1
    }
}
