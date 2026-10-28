package ws.crandell.fafo.siren

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ws.crandell.fafo.ui.status.LightLevel

/** The camera flash as a light. Pure interface so the strobe is tested on the JVM. */
interface TorchBackend {
    /** False when the phone has no back flash. */
    val available: Boolean
    /** Returns false when the torch could not be set right now (camera in use, too hot). Never throws. */
    fun set(on: Boolean): Boolean
}

/** On / off times of each pattern (Bill 2026-10-28): fast strobe at RED, slow warning blink at ORANGE. */
object TorchPattern {
    const val ALARM_ON_MS = 120L
    const val ALARM_OFF_MS = 130L
    const val WARN_ON_MS = 300L
    const val WARN_OFF_MS = 1_200L
    /** Torch off this long before a photo so the capture session gets the camera. */
    const val CAPTURE_GAP_MS = 300L

    fun of(level: LightLevel): Pair<Long, Long>? = when (level) {
        LightLevel.OFF -> null
        LightLevel.WARN -> WARN_ON_MS to WARN_OFF_MS
        LightLevel.ALARM -> ALARM_ON_MS to ALARM_OFF_MS
    }
}

/**
 * Camera torch as the alarm light: strobe at RED, slow blink at ORANGE, off otherwise or when the setting
 * is off. Pauses around trigger photos. If the torch is unavailable (camera busy, thermal) it skips that
 * flash and tries again next cycle; it never throws into the alarm path. [stop] always leaves it off.
 */
class TorchStrobe(
    private val scope: CoroutineScope,
    private val backend: TorchBackend,
    private val enabled: () -> Boolean,
) {
    private var job: Job? = null
    private var level = LightLevel.OFF
    @Volatile private var paused = false
    private var lit = false
    var failures = 0
        private set

    val running: Boolean get() = job?.isActive == true

    fun setLevel(next: LightLevel) {
        val want = if (!backend.available || !enabled()) LightLevel.OFF else next
        if (want == level && (want == LightLevel.OFF || running)) return
        level = want
        job?.cancel()
        off()
        val pattern = TorchPattern.of(want) ?: return
        job = scope.launch {
            while (isActive) {
                if (!paused) light(true)
                delay(pattern.first)
                // off(), not light(false): while paused for a photo the torch is already off, and the
                // camera owns the flash unit, so leave it alone.
                off()
                delay(pattern.second)
            }
        }
    }

    /** Before a photo: torch off, then a short gap so the camera can open. */
    suspend fun pauseForCapture() {
        if (level == LightLevel.OFF) return
        paused = true
        off()
        delay(TorchPattern.CAPTURE_GAP_MS)
    }

    fun resumeAfterCapture() { paused = false }

    fun stop() {
        job?.cancel()
        job = null
        level = LightLevel.OFF
        off()
    }

    /** Only undoes our own flash: never switches off a flashlight the owner turned on. */
    private fun off() { if (lit) light(false) }

    private fun light(on: Boolean) {
        if (!backend.set(on)) { if (on) failures++; lit = false } else lit = on
    }
}

/** Android: the first back camera with a flash, via CameraManager.setTorchMode (no CAMERA permission needed). */
class AndroidTorch(context: Context) : TorchBackend {
    private val cm = context.getSystemService(CameraManager::class.java)
    private val id: String? = runCatching {
        cm?.cameraIdList?.firstOrNull {
            val c = cm.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    override val available: Boolean get() = id != null

    private var warned = false

    override fun set(on: Boolean): Boolean {
        val cam = id ?: return false
        return try {
            cm?.setTorchMode(cam, on)
            true
        } catch (e: CameraAccessException) {
            if (!warned) Log.w("TorchStrobe", "torch unavailable: ${e.reason}"); warned = true
            false
        } catch (e: RuntimeException) {
            if (!warned) Log.w("TorchStrobe", "torch failed", e); warned = true
            false
        }
    }

    companion object {
        fun hasFlash(context: Context) = AndroidTorch(context).available
    }
}
