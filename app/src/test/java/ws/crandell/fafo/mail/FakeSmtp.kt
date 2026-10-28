package ws.crandell.fafo.mail

import java.io.IOException

/**
 * An in-memory SMTP server for SmtpClient tests: answers commands like a real submission server, records
 * the transcript, and models TLS (implicit or after STARTTLS) as a flag the client can see.
 */
class FakeSmtp(
    var offerStartTls: Boolean = true,
    var authMechs: String = "PLAIN LOGIN",
    var authOk: Boolean = true,
    var rcptCode: Int = 250,
    var dataCode: Int = 250,
    var greeting: Int = 220,
    var dropAfter: String? = null,
) : SmtpConnector {
    val transcript = mutableListOf<String>()
    var data = ""
    var tlsUsedForAuth: Boolean? = null
    var opened = 0

    override fun open(host: String, port: Int, implicitTls: Boolean): SmtpChannel { opened++; return Channel(implicitTls) }

    private inner class Channel(override val isTls: Boolean) : SmtpChannel {
        private val out = ArrayDeque<String>()
        private var inData = false
        private val dataBuf = StringBuilder()
        private var loginStep = 0

        init { out += "$greeting fake.example ESMTP" }

        override fun readLine(): String? = out.removeFirstOrNull()

        override fun write(bytes: ByteArray) {
            val text = String(bytes, Charsets.ISO_8859_1)
            if (inData) {
                dataBuf.append(text)
                if (dataBuf.endsWith("\r\n.\r\n") || text == ".\r\n") {
                    inData = false
                    data = dataBuf.toString().removeSuffix(".\r\n")
                    out += "$dataCode ok"
                }
                return
            }
            for (cmd in text.split("\r\n").filter { it.isNotEmpty() }) handle(cmd)
        }

        private fun handle(cmd: String) {
            transcript += if (cmd.startsWith("AUTH PLAIN ")) "AUTH PLAIN ***" else cmd
            if (dropAfter != null && cmd.startsWith(dropAfter!!)) throw IOException("dropped")
            val u = cmd.uppercase()
            when {
                loginStep == 1 -> { loginStep = 2; out += "334 UGFzc3dvcmQ6" }
                loginStep == 2 -> { loginStep = 0; out += if (authOk) "235 ok" else "535 bad credentials" }
                u.startsWith("EHLO") -> {
                    out += "250-fake.example"
                    if (offerStartTls && !isTls) out += "250-STARTTLS"
                    out += "250-AUTH $authMechs"
                    out += "250 SIZE 35882577"
                }
                u == "STARTTLS" -> out += "220 go ahead"
                u.startsWith("AUTH PLAIN") -> { tlsUsedForAuth = isTls; out += if (authOk) "235 ok" else "535 bad credentials" }
                u == "AUTH LOGIN" -> { tlsUsedForAuth = isTls; loginStep = 1; out += "334 VXNlcm5hbWU6" }
                u.startsWith("MAIL FROM") -> out += "250 ok"
                u.startsWith("RCPT TO") -> out += "$rcptCode rcpt"
                u == "DATA" -> { inData = true; out += "354 go" }
                u == "QUIT" -> out += "221 bye"
                else -> out += "500 what"
            }
        }

        override fun startTls(host: String): SmtpChannel = Channel(true).also { it.out.clear() }

        override fun close() {}
    }
}
