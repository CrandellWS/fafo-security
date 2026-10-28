package ws.crandell.fafo.mail

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the owner sees about photo email (Home, notification, incident row). */
data class MailStatus(
    val pending: Int = 0,
    val sentPhotos: Int = 0,
    val lastError: String? = null,
    val failedPermanently: Int = 0,
)

/**
 * One pass over the outbox: sends every due entry (keeping [Outbox.MIN_GAP_MS] between sends), marks it
 * sent or failed with backoff. Pure apart from the files it reads, so it is tested on the JVM; the Android
 * [MailDispatcher] decides when to run it (a host is alive, the network is validated).
 */
class MailDrain(
    private val outbox: Outbox,
    private val config: suspend () -> MailConfig,
    private val senderFor: (MailConfig) -> MailSender?,
    private val photoFile: (incidentId: String, name: String) -> File,
    private val deviceLabel: suspend () -> String,
    private val clock: () -> Long,
) {
    private var lastSendAt = Long.MIN_VALUE / 2
    var sentPhotos = 0
        private set
    var lastError: String? = null
        private set

    /** Returns when the next entry becomes due (null when nothing is waiting). */
    suspend fun drainOnce(): Long? {
        val cfg = config()
        if (!cfg.enabled || !cfg.complete) return null
        val sender = senderFor(cfg) ?: return null
        for (e in outbox.due(clock())) {
            val now = clock()
            if (now - lastSendAt < Outbox.MIN_GAP_MS) return lastSendAt + Outbox.MIN_GAP_MS
            val files = e.photos.map { photoFile(e.incidentId, it) }.filter { it.isFile }
            if (files.isEmpty()) { outbox.markSent(e); continue } // photos were deleted by the owner
            val msg = MimeBuilder.build(
                from = cfg.sender,
                to = cfg.recipients,
                subject = subject(deviceLabel(), e, now),
                body = body(deviceLabel(), e, files.size),
                attachments = files.map { Attachment(it.name, it.readBytes()) },
                date = Date(now),
            )
            lastSendAt = now
            when (val r = sender.send(cfg.sender, cfg.recipients, msg)) {
                SendResult.Sent -> { outbox.markSent(e); sentPhotos += files.size; lastError = null }
                is SendResult.Failed -> { outbox.markFailed(e, r.error, now); lastError = r.error.message }
            }
        }
        return outbox.pending().minOfOrNull { it.nextAt }
    }

    fun status() = MailStatus(
        pending = outbox.pending().sumOf { it.photos.size },
        sentPhotos = sentPhotos,
        lastError = lastError,
        failedPermanently = outbox.all().count { it.state == OutboxState.FAILED_PERMANENT },
    )

    companion object {
        private fun time(at: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(at))

        fun subject(label: String, e: OutboxEntry, now: Long): String =
            "FAFO alarm: $label ${time(e.createdAt)}" + if (e.seq > 1) " (${e.seq})" else ""

        fun body(label: String, e: OutboxEntry, photos: Int): String = buildString {
            appendLine("FAFO Security alarm on $label.")
            appendLine()
            appendLine("Incident: ${e.incidentId}")
            appendLine("Started: ${time(e.createdAt)}")
            appendLine("Photos in this email: $photos")
            appendLine()
            appendLine("Sent by FAFO Security on $label, from the account you set up in Settings > Photo email.")
        }
    }
}
