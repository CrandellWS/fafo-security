package ws.crandell.fafo.quick

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ws.crandell.fafo.arm.ArmDeps
import ws.crandell.fafo.siren.TorchBackend

/**
 * The plain flashlight behind the armed notification's Flashlight action and the FAFO Light tile (round 5,
 * spec F13). Same back-camera torch as the alarm light ([ws.crandell.fafo.siren.TorchStrobe]); no CAMERA
 * permission, no storage, so it works over the lock screen and before the first unlock.
 *
 * [on] follows Android's torch callback, so a flashlight switched by the system tile or another app shows
 * correctly here too.
 */
object Flashlight {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on

    @Volatile private var backend: TorchBackend? = null
    @Volatile private var watching = false

    private fun backend(context: Context): TorchBackend =
        backend ?: ArmDeps.torch(context.applicationContext).also { backend = it }

    fun available(context: Context): Boolean = backend(context).available

    /**
     * Turns the flashlight on if it is off, off if it is on. Returns the new state, or null when this phone
     * has no flash or the torch can't be set right now (camera in use, too hot): then nothing changes.
     */
    fun toggle(context: Context): Boolean? {
        watch(context)
        val b = backend(context)
        if (!b.available) return null
        val next = !_on.value
        if (!b.set(next)) return null
        _on.value = next
        return next
    }

    /** Listen to Android's torch state (once per process). Safe in direct boot. */
    fun watch(context: Context) {
        if (watching) return
        watching = true
        runCatching {
            context.applicationContext.getSystemService(CameraManager::class.java)?.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { _on.value = enabled }
                    override fun onTorchModeUnavailable(cameraId: String) { _on.value = false }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    /** Tests only. */
    fun reset() {
        backend = null
        _on.value = false
    }
}
