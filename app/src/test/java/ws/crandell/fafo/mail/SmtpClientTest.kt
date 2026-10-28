package ws.crandell.fafo.mail

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SmtpClientTest {
    private fun client(s: FakeSmtp, sec: SmtpSecurity = SmtpSecurity.STARTTLS, pw: String? = "app-pass") =
        SmtpClient(s, "smtp.example.invalid", sec.defaultPort, sec, "bill@example.invalid", { pw })

    private val msg = "Subject: hi\r\n\r\nline one\r\n.starts with a dot\r\n".toByteArray()

    @Test fun startTlsSendsTheMessageToEveryRecipient() = runTest {
        val s = FakeSmtp()
        val r = client(s).send("bill@example.invalid", listOf("a@example.invalid", "b@example.invalid"), msg)
        assertThat(r).isEqualTo(SendResult.Sent)
        assertThat(s.transcript).containsExactly(
            "EHLO fafo.local", "STARTTLS", "EHLO fafo.local", "AUTH PLAIN ***",
            "MAIL FROM:<bill@example.invalid>", "RCPT TO:<a@example.invalid>", "RCPT TO:<b@example.invalid>", "DATA", "QUIT",
        ).inOrder()
        assertThat(s.tlsUsedForAuth).isTrue()
    }

    @Test fun dataIsDotStuffed() = runTest {
        val s = FakeSmtp()
        client(s).send("f@example.invalid", listOf("a@example.invalid"), msg)
        assertThat(s.data).contains("\r\n..starts with a dot\r\n")
        assertThat(s.data).doesNotContain("\r\n.starts")
    }

    @Test fun implicitTlsSkipsStartTls() = runTest {
        val s = FakeSmtp(offerStartTls = false)
        assertThat(client(s, SmtpSecurity.SSL_TLS).send("f@example.invalid", listOf("a@example.invalid"), msg)).isEqualTo(SendResult.Sent)
        assertThat(s.transcript).doesNotContain("STARTTLS")
        assertThat(s.tlsUsedForAuth).isTrue()
    }

    @Test fun startTlsNotOfferedFailsClosedWithoutAuth() = runTest {
        val s = FakeSmtp(offerStartTls = false)
        val r = client(s).send("f@example.invalid", listOf("a@example.invalid"), msg) as SendResult.Failed
        assertThat(r.error).isInstanceOf(MailError.Tls::class.java)
        assertThat(s.transcript.none { it.startsWith("AUTH") }).isTrue()
    }

    @Test fun authLoginWhenPlainIsNotOffered() = runTest {
        val s = FakeSmtp(authMechs = "LOGIN")
        assertThat(client(s).send("f@example.invalid", listOf("a@example.invalid"), msg)).isEqualTo(SendResult.Sent)
        assertThat(s.transcript).contains("AUTH LOGIN")
    }

    @Test fun badPasswordIsAnAuthErrorAndNotRetried() = runTest {
        val s = FakeSmtp(authOk = false)
        val r = client(s).send("f@example.invalid", listOf("a@example.invalid"), msg) as SendResult.Failed
        assertThat(r.error).isInstanceOf(MailError.Auth::class.java)
        assertThat(r.error.retryable).isFalse()
        assertThat(r.error.message).contains("app password")
    }

    @Test fun temporaryRejectIsRetryablePermanentIsNot() = runTest {
        val t = client(FakeSmtp(rcptCode = 451)).send("f@example.invalid", listOf("a@example.invalid"), msg) as SendResult.Failed
        assertThat(t.error.retryable).isTrue()
        val p = client(FakeSmtp(rcptCode = 550)).send("f@example.invalid", listOf("a@example.invalid"), msg) as SendResult.Failed
        assertThat(p.error.retryable).isFalse()
        assertThat((p.error as MailError.Rejected).code).isEqualTo(550)
    }

    @Test fun droppedConnectionIsARetryableNetworkError() = runTest {
        val r = client(FakeSmtp(dropAfter = "MAIL")).send("f@example.invalid", listOf("a@example.invalid"), msg) as SendResult.Failed
        assertThat(r.error).isInstanceOf(MailError.Network::class.java)
        assertThat(r.error.retryable).isTrue()
    }

    @Test fun noPasswordNeverConnects() = runTest {
        val s = FakeSmtp()
        val r = client(s, pw = null).send("f@example.invalid", listOf("a@example.invalid"), msg) as SendResult.Failed
        assertThat(r.error).isInstanceOf(MailError.Config::class.java)
        assertThat(s.opened).isEqualTo(0)
    }

    @Test fun dotStuffEndsInCrlf() {
        assertThat(String(SmtpClient.dotStuff("a\n.b".toByteArray()))).isEqualTo("a\r\n..b\r\n")
    }
}
