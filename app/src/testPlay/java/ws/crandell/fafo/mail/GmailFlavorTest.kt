package ws.crandell.fafo.mail

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import ws.crandell.fafo.mail.flavor.FlavorMail

/** play flavor only: Gmail is send-only and talks to exactly one endpoint. */
@RunWith(AndroidJUnit4::class)
class GmailFlavorTest {
    @Test fun sendOnlyScopeAndTheOneEndpoint() {
        assertThat(GmailApi.SCOPE).endsWith("/auth/gmail.send")
        assertThat(GmailApi.SEND_URL).startsWith("https://gmail.googleapis.com/upload/gmail/v1/users/me/messages/send")
        assertThat(GmailApi.request("bill@example.invalid").requestedScopes.map { it.scopeUri }).containsExactly(GmailApi.SCOPE)
    }

    @Test fun noSenderWithoutAConnectedAccount() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertThat(FlavorMail.gmailSender(ctx, "", null)).isNull()
        assertThat(FlavorMail.gmailSender(ctx, "bill@example.invalid", null)).isInstanceOf(GmailSender::class.java)
    }
}
