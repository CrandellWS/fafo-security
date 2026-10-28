package ws.crandell.fafo.mail

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Base64
import javax.net.SocketFactory
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A line-based connection to an SMTP server; [startTls] upgrades it in place (STARTTLS). */
interface SmtpChannel : Closeable {
    val isTls: Boolean
    fun readLine(): String?
    fun write(bytes: ByteArray)
    fun startTls(host: String): SmtpChannel
}

fun interface SmtpConnector {
    fun open(host: String, port: Int, implicitTls: Boolean): SmtpChannel
}

/**
 * Real sockets (round 3 photo email: the user's own SMTP server; spec F11). TLS uses the platform trust
 * store with hostname verification (`endpointIdentificationAlgorithm = HTTPS`). [socketFactory] lets the
 * dispatcher bind to one network (validated open Wi-Fi). 20 s connect and read timeouts.
 */
class SocketSmtpConnector(
    private val socketFactory: SocketFactory = SocketFactory.getDefault(),
    private val tls: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
    private val timeoutMs: Int = 20_000,
) : SmtpConnector {
    override fun open(host: String, port: Int, implicitTls: Boolean): SmtpChannel {
        val raw = socketFactory.createSocket()
        raw.soTimeout = timeoutMs
        raw.connect(InetSocketAddress(host, port), timeoutMs)
        val ch = SocketChannel(raw, false, host, port)
        return if (implicitTls) ch.startTls(host) else ch
    }

    private inner class SocketChannel(private val socket: Socket, override val isTls: Boolean, private val host: String, private val port: Int) : SmtpChannel {
        private val input: InputStream = BufferedInputStream(socket.getInputStream())
        private val output: OutputStream = socket.getOutputStream()

        override fun readLine(): String? {
            val buf = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return if (buf.size() == 0) null else buf.toString(Charsets.UTF_8.name())
                if (b == '\n'.code) return buf.toString(Charsets.UTF_8.name()).trimEnd('\r')
                buf.write(b)
                if (buf.size() > 4096) throw IOException("SMTP line too long")
            }
        }

        override fun write(bytes: ByteArray) { output.write(bytes); output.flush() }

        override fun startTls(host: String): SmtpChannel {
            val s = tls.createSocket(socket, host, port, true) as SSLSocket
            s.sslParameters = s.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            s.soTimeout = timeoutMs
            s.startHandshake()
            return SocketChannel(s, true, host, port)
        }

        override fun close() { runCatching { socket.close() } }
    }
}

/**
 * Minimal SMTP submission client: EHLO, STARTTLS when chosen (fail closed if not offered), AUTH PLAIN or
 * LOGIN only on a TLS channel, MAIL FROM, RCPT TO per recipient, DATA with dot-stuffing, QUIT.
 */
class SmtpClient(
    private val connector: SmtpConnector,
    private val host: String,
    private val port: Int,
    private val security: SmtpSecurity,
    private val user: String,
    private val password: () -> String?,
    private val heloName: String = "fafo.local",
) : MailSender {
    private class Reply(val code: Int, val lines: List<String>)
    private class Fail(val error: MailError) : Exception()

    override suspend fun send(from: String, to: List<String>, message: ByteArray): SendResult = try {
        sendBlocking(from, to, message)
        SendResult.Sent
    } catch (f: Fail) {
        SendResult.Failed(f.error)
    } catch (e: SocketTimeoutException) {
        SendResult.Failed(MailError.Timeout())
    } catch (e: SSLException) {
        val msg = e.message.orEmpty()
        SendResult.Failed(
            MailError.Tls(
                if ("hostname" in msg.lowercase() || "certificate" in msg.lowercase()) "TLS certificate not valid for $host"
                else "Secure connection to $host failed",
            ),
        )
    } catch (e: IOException) {
        SendResult.Failed(MailError.Network("No connection to $host:$port"))
    }

    private fun sendBlocking(from: String, to: List<String>, message: ByteArray) {
        if (to.isEmpty()) throw Fail(MailError.Config("No recipients"))
        val pw = password() ?: throw Fail(MailError.Config("No app password saved"))
        var ch = connector.open(host, port, security == SmtpSecurity.SSL_TLS)
        try {
            expect(ch, 220)
            var caps = ehlo(ch)
            if (security == SmtpSecurity.STARTTLS) {
                if (caps.none { it.uppercase().startsWith("STARTTLS") }) {
                    throw Fail(MailError.Tls("$host does not offer STARTTLS; not sending the password in the clear"))
                }
                cmd(ch, "STARTTLS", 220)
                ch = ch.startTls(host)
                caps = ehlo(ch)
            }
            // Never authenticate on a plaintext connection.
            if (!ch.isTls) throw Fail(MailError.Tls("Connection to $host is not encrypted"))
            auth(ch, caps, pw)
            cmd(ch, "MAIL FROM:<$from>", 250)
            for (r in to) cmd(ch, "RCPT TO:<$r>", 250, 251)
            cmd(ch, "DATA", 354)
            ch.write(dotStuff(message))
            ch.write(".\r\n".toByteArray())
            expect(ch, 250)
            runCatching { ch.write("QUIT\r\n".toByteArray()); ch.readLine() }
        } finally {
            ch.close()
        }
    }

    private fun ehlo(ch: SmtpChannel): List<String> = cmd(ch, "EHLO $heloName", 250).lines.drop(1)

    private fun auth(ch: SmtpChannel, caps: List<String>, pw: String) {
        val authLine = caps.firstOrNull { it.uppercase().startsWith("AUTH") }?.uppercase().orEmpty()
        val b64 = Base64.getEncoder()
        val r = if ("PLAIN" in authLine || "LOGIN" !in authLine) {
            send(ch, "AUTH PLAIN " + b64.encodeToString("\u0000$user\u0000$pw".toByteArray()))
        } else {
            val first = send(ch, "AUTH LOGIN")
            if (first.code != 334) first else {
                val second = send(ch, b64.encodeToString(user.toByteArray()))
                if (second.code != 334) second else send(ch, b64.encodeToString(pw.toByteArray()))
            }
        }
        if (r.code == 235) return
        if (r.code == 535 || r.code == 534 || r.code == 530) throw Fail(MailError.Auth())
        throw Fail(error(r))
    }

    private fun cmd(ch: SmtpChannel, line: String, vararg ok: Int): Reply {
        val r = send(ch, line)
        if (r.code !in ok) throw Fail(error(r))
        return r
    }

    private fun send(ch: SmtpChannel, line: String): Reply {
        ch.write("$line\r\n".toByteArray())
        return read(ch)
    }

    private fun expect(ch: SmtpChannel, code: Int): Reply {
        val r = read(ch)
        if (r.code != code) throw Fail(error(r))
        return r
    }

    private fun read(ch: SmtpChannel): Reply {
        val lines = mutableListOf<String>()
        while (true) {
            val l = ch.readLine() ?: throw IOException("Connection closed")
            if (l.length < 3) throw IOException("Bad SMTP reply")
            lines += l.substring(minOf(4, l.length))
            if (l.length == 3 || l[3] != '-') return Reply(l.substring(0, 3).toIntOrNull() ?: throw IOException("Bad SMTP reply"), lines)
        }
    }

    private fun error(r: Reply): MailError =
        MailError.Rejected(r.code, "Server said ${r.code}: ${r.lines.lastOrNull().orEmpty().take(120)}")

    companion object {
        /** RFC 5321 4.5.2: a line starting with "." gets one more; the message always ends in CRLF. */
        fun dotStuff(message: ByteArray): ByteArray {
            val s = String(message, Charsets.ISO_8859_1).replace("\r\n", "\n").replace("\n", "\r\n")
            val stuffed = s.split("\r\n").joinToString("\r\n") { if (it.startsWith(".")) ".$it" else it }
            val withEnd = if (stuffed.endsWith("\r\n")) stuffed else "$stuffed\r\n"
            return withEnd.toByteArray(Charsets.ISO_8859_1)
        }
    }
}
