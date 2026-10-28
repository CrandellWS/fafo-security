package ws.crandell.fafo.camera

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ws.crandell.fafo.core.Clock
import ws.crandell.fafo.core.Defaults

enum class Lens { FRONT, BACK }

class CapturedPhoto(val jpeg: ByteArray, val thumbnail: IntArray?)

/** Takes one JPEG (max 1600 px long edge, quality 80). Returns null on failure (camera busy etc.). */
fun interface Capturer {
    suspend fun capture(lens: Lens): CapturedPhoto?
}

data class PhotoSettings(
    val enabled: Boolean = Defaults.PHOTO_ON_TRIGGER,
    val intervalS: Int = Defaults.PHOTO_INTERVAL_S,
    /** FRONT / BACK / ALTERNATE */
    val camera: String = Defaults.CAMERA,
    val capMb: Int = Defaults.PHOTO_CAP_MB,
)

data class DeviceSnapshot(val batteryPct: Int, val plugged: Boolean)

/**
 * F5: first photo right away when the incident starts, then one every N s while it is open
 * (timer inside the service, no WorkManager, no exact alarms). Photos pause under 500 MB free; a camera
 * failure is logged and retried on the next tick. Nothing here can stop the siren.
 */
class PhotoController(
    private val scope: CoroutineScope,
    private val capturer: Capturer,
    private val store: () -> PhotoStore,
    private val settings: suspend () -> PhotoSettings,
    private val freeBytes: () -> Long,
    private val device: () -> DeviceSnapshot,
    private val clock: Clock,
    private val onPaused: (Boolean) -> Unit = {},
) {
    private var job: Job? = null
    private var lastKeptThumb: IntArray? = null
    private var bigChanges = 0
    var shots = 0
        private set

    val running: Boolean get() = job?.isActive == true

    fun start(incidentId: String, startedAt: Long) {
        if (running) return
        job = scope.launch {
            val s = settings()
            if (!s.enabled) return@launch
            val st = store()
            st.open(incidentId, startedAt)
            var n = 0
            var paused = false
            while (isActive) {
                if (freeBytes() < Defaults.LOW_STORAGE_FLOOR_BYTES) {
                    if (!paused) {
                        paused = true
                        onPaused(true)
                        st.log(incidentId, "${clock.now()}: photos paused, storage low")
                    }
                } else {
                    if (paused) { paused = false; onPaused(false) }
                    shoot(st, incidentId, startedAt, s, n++)
                }
                delay(s.intervalS * 1000L)
            }
        }
    }

    private suspend fun shoot(st: PhotoStore, id: String, startedAt: Long, s: PhotoSettings, n: Int) {
        val lens = when (s.camera) {
            "BACK" -> Lens.BACK
            "ALTERNATE" -> if (n % 2 == 0) Lens.FRONT else Lens.BACK
            else -> Lens.FRONT
        }
        val now = clock.now()
        val photo = try {
            capturer.capture(lens)
        } catch (e: Exception) {
            null
        }
        if (photo == null) {
            st.log(id, "$now: capture failed (${lens.name}), retrying next tick")
            return
        }
        val big = bigChanges < PrunePolicy.MAX_BIG_CHANGE && BigChange.isBigChange(lastKeptThumb, photo.thumbnail)
        if (big) bigChanges++
        val d = device()
        val meta = PhotoMeta(
            file = "photo_${now}_${lens.name.lowercase()}.jpg",
            takenAt = now,
            camera = lens.name,
            batteryPct = d.batteryPct,
            plugged = d.plugged,
            bigChange = big,
            sizeBytes = photo.jpeg.size.toLong(),
        )
        if (st.add(id, startedAt, photo.jpeg, meta, s.capMb * 1024L * 1024L)) {
            shots++
            lastKeptThumb = photo.thumbnail
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        lastKeptThumb = null
        bigChanges = 0
        onPaused(false)
    }
}
