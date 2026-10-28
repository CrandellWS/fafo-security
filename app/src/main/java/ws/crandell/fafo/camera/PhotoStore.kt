package ws.crandell.fafo.camera

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PhotoMeta(
    val file: String,
    val takenAt: Long,
    val camera: String,
    val batteryPct: Int,
    val plugged: Boolean,
    val bigChange: Boolean,
    val sizeBytes: Long,
    /** v1 never sends anything: every photo stays "pending" (AC5.6). */
    val status: String = "pending",
)

@Serializable
data class IncidentSidecar(
    val id: String,
    val startedAt: Long,
    val closedAt: Long? = null,
    val closeReason: String? = null,
    val closedBy: String? = null,
    val photos: List<PhotoMeta> = emptyList(),
    val log: List<String> = emptyList(),
)

/**
 * Offline photo queue in credential-protected storage: `filesDir/incidents/<incidentId>/` with JPEGs and
 * one `incident.json` sidecar (AC5.4). Plain java.io so it is unit-testable on the JVM.
 * Nothing photo-related is ever written to device-protected storage (AC5.5).
 */
class PhotoStore(private val root: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun dir(id: String) = File(root, sanitize(id))
    private fun sidecarFile(id: String) = File(dir(id), SIDECAR)
    fun photoFile(id: String, name: String) = File(dir(id), sanitize(name))

    private fun sanitize(s: String) = s.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    @Synchronized fun read(id: String): IncidentSidecar? {
        val f = sidecarFile(id)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(IncidentSidecar.serializer(), f.readText()) }.getOrNull()
    }

    @Synchronized private fun write(s: IncidentSidecar) {
        val d = dir(s.id)
        d.mkdirs()
        val tmp = File(d, "$SIDECAR.tmp")
        tmp.writeText(json.encodeToString(IncidentSidecar.serializer(), s))
        tmp.renameTo(sidecarFile(s.id))
    }

    @Synchronized fun open(id: String, startedAt: Long): IncidentSidecar =
        read(id) ?: IncidentSidecar(id, startedAt).also { write(it) }

    @Synchronized fun list(): List<IncidentSidecar> =
        (root.listFiles()?.filter { it.isDirectory } ?: emptyList())
            .mapNotNull { read(it.name) }
            .sortedByDescending { it.startedAt }

    @Synchronized fun close(id: String, reason: String, by: String?, at: Long) {
        val s = read(id) ?: IncidentSidecar(id, at)
        if (s.closedAt != null) return
        write(s.copy(closedAt = at, closeReason = reason, closedBy = by))
    }

    @Synchronized fun log(id: String, message: String) {
        val s = read(id) ?: return
        write(s.copy(log = (s.log + message).takeLast(200)))
    }

    private fun infos(): List<IncidentInfo> = list().map { s ->
        IncidentInfo(s.id, s.startedAt, s.closedAt != null, s.photos.map { PhotoInfo(s.id, it.file, it.sizeBytes, it.takenAt, it.bigChange) })
    }

    fun totalBytes(): Long = list().sumOf { s -> s.photos.sumOf { it.sizeBytes } }

    /**
     * Adds a photo, pruning first per [PrunePolicy]. Returns false (photo not saved) when the cap cannot be
     * met without touching protected frames.
     */
    @Synchronized fun add(id: String, startedAt: Long, jpeg: ByteArray, meta: PhotoMeta, capBytes: Long): Boolean {
        open(id, startedAt)
        val plan = PrunePolicy.plan(infos(), capBytes, jpeg.size.toLong())
        plan.deletes.forEach { deletePhoto(it.incidentId, it.name) }
        if (!plan.fits) {
            log(id, "${meta.takenAt}: photo skipped, storage cap reached")
            return false
        }
        val f = photoFile(id, meta.file)
        f.writeBytes(jpeg)
        val s = read(id)!!
        write(s.copy(photos = s.photos + meta.copy(sizeBytes = jpeg.size.toLong())))
        return true
    }

    @Synchronized fun deletePhoto(id: String, name: String) {
        photoFile(id, name).delete()
        val s = read(id) ?: return
        write(s.copy(photos = s.photos.filterNot { it.file == name }))
    }

    @Synchronized fun deleteIncident(id: String) {
        dir(id).deleteRecursively()
    }

    companion object {
        const val SIDECAR = "incident.json"
        fun root(filesDir: File) = File(filesDir, "incidents")
    }
}
