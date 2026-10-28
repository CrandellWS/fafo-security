package ws.crandell.fafo.mail

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class OutboxState {
    PENDING,
    FAILED_PERMANENT,
    /** Sent: kept as a small record (no photos) so the per-incident cap and message numbers still hold. */
    SENT,
}

/** One email waiting to go: the photos of one incident, by file name. */
@Serializable
data class OutboxEntry(
    val id: String,
    val incidentId: String,
    val photos: List<String>,
    val createdAt: Long,
    /** Earliest time to send (batching window, then backoff). */
    val nextAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val state: OutboxState = OutboxState.PENDING,
    /** Still collecting photos (the batch window is open). */
    val open: Boolean = false,
    /** Message number within the incident, for "(2 of 5)" style subjects. */
    val seq: Int = 1,
)

/**
 * Photo email queue (round 3), credential-protected files at `filesDir/outbox/<id>.json`, each written
 * atomically (temp file + rename) so a crash never leaves half an entry. Enqueue policy: the first photo of
 * an incident goes alone and at once; later photos collect into a batch sent [BATCH_MS] after it opened or
 * when it holds [BATCH_MAX] photos; at most [MAX_PER_INCIDENT] messages per incident. Retries back off
 * 30 s, 1, 2, 5, then 15 min. Pure file I/O plus a clock, so it is tested on the JVM.
 */
class Outbox(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized fun all(): List<OutboxEntry> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { json.decodeFromString(OutboxEntry.serializer(), f.readText()) }.getOrNull()
        }.sortedBy { it.createdAt }
    }

    fun pending(): List<OutboxEntry> = all().filter { it.state == OutboxState.PENDING }

    /** Entries ready to send now (batch window closed, backoff over). */
    fun due(now: Long): List<OutboxEntry> = pending().filter { now >= it.nextAt }

    /** Photos referenced by pending mail: the photo cap must not prune them (PrunePolicy). */
    fun pinned(): Set<Pair<String, String>> = pending().flatMap { e -> e.photos.map { e.incidentId to it } }.toSet()

    @Synchronized fun enqueuePhoto(incidentId: String, photo: String, now: Long) {
        pruneSent(now)
        val mine = all().filter { it.incidentId == incidentId }
        val openBatch = mine.firstOrNull { it.open && it.state == OutboxState.PENDING }
        when {
            mine.isEmpty() -> write(OutboxEntry(newId(now), incidentId, listOf(photo), now, now, seq = 1))
            openBatch != null -> {
                val photos = openBatch.photos + photo
                val full = photos.size >= BATCH_MAX
                write(openBatch.copy(photos = photos, open = !full, nextAt = if (full) now else openBatch.nextAt))
            }
            mine.size >= MAX_PER_INCIDENT -> Unit // enough mail for one incident; the photos stay on the phone
            else -> write(OutboxEntry(newId(now), incidentId, listOf(photo), now, now + BATCH_MS, open = true, seq = mine.size + 1))
        }
    }

    /** Sent (or nothing left to send): keep a photo-less record so [MAX_PER_INCIDENT] counts every message. */
    @Synchronized fun markSent(e: OutboxEntry) {
        write(e.copy(photos = emptyList(), open = false, state = OutboxState.SENT, lastError = null))
    }

    /** Sent records older than [SENT_KEEP_MS] are no longer needed for the cap. */
    private fun pruneSent(now: Long) {
        all().filter { it.state == OutboxState.SENT && now - it.createdAt > SENT_KEEP_MS }.forEach { file(it.id).delete() }
    }

    @Synchronized fun markFailed(e: OutboxEntry, error: MailError, now: Long) {
        val attempts = e.attempts + 1
        val next = if (error.retryable) e.copy(attempts = attempts, lastError = error.message, nextAt = now + backoffMs(attempts), open = false)
        else e.copy(attempts = attempts, lastError = error.message, state = OutboxState.FAILED_PERMANENT, open = false)
        write(next)
    }

    /** The network came back (validated): retry everything now instead of waiting out the backoff. */
    @Synchronized fun resetBackoff(now: Long) {
        pending().filter { !it.open && it.attempts > 0 }.forEach { write(it.copy(nextAt = minOf(it.nextAt, now))) }
    }

    @Synchronized fun discardAll() { dir.listFiles().orEmpty().forEach { it.delete() } }

    private fun file(id: String) = File(dir, "$id.json")

    private fun write(e: OutboxEntry) {
        dir.mkdirs()
        val tmp = File(dir, "${e.id}.tmp")
        tmp.writeText(json.encodeToString(OutboxEntry.serializer(), e))
        if (!tmp.renameTo(file(e.id))) { file(e.id).delete(); tmp.renameTo(file(e.id)) }
    }

    private var counter = 0
    private fun newId(now: Long) = "m${now}_${counter++}"

    companion object {
        const val BATCH_MS = 60_000L
        const val BATCH_MAX = 5
        const val MAX_PER_INCIDENT = 20
        const val MIN_GAP_MS = 30_000L
        const val SENT_KEEP_MS = 7 * 24 * 60 * 60_000L
        private val BACKOFF_MS = listOf(30_000L, 60_000L, 120_000L, 300_000L, 900_000L)
        fun backoffMs(attempts: Int): Long = BACKOFF_MS[(attempts - 1).coerceIn(0, BACKOFF_MS.lastIndex)]
        fun root(filesDir: File) = File(filesDir, "outbox")
    }
}
