package ws.crandell.fafo.arm

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import ws.crandell.fafo.camera.CameraXCapturer
import ws.crandell.fafo.camera.Capturer
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.core.AlarmEffect
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.core.Clock
import ws.crandell.fafo.siren.AndroidAudioBackend
import ws.crandell.fafo.siren.Siren
import ws.crandell.fafo.storage.DpSettings
import ws.crandell.fafo.storage.DpStore
import ws.crandell.fafo.net.android.AlarmNetwork
import ws.crandell.fafo.net.android.PeerNetwork

/** What the armed service does with sound. Implemented by the siren (M5); tests use fakes. */
interface AlarmSound {
    fun startPreAlarm(durationMs: Long)
    fun startSiren()
    fun stop()
    fun chirp(kind: ChirpKind)
    fun release() {}
}

enum class ChirpKind { BUMP, PICKUP, STILL_ARMED, REMOTE }

/**
 * How the armed service maps sound effects onto [AlarmSound]. Shared with the tests so they exercise
 * the exact mapping the service uses. Returns false for effects that are not about sound.
 */
fun AlarmSound.apply(fx: AlarmEffect): Boolean {
    when (fx) {
        is AlarmEffect.StartPreAlarm -> startPreAlarm(fx.durationMs)
        AlarmEffect.StartSiren -> startSiren()
        AlarmEffect.StopSound -> stop()
        AlarmEffect.StillArmedWarning -> chirp(ChirpKind.STILL_ARMED)
        AlarmEffect.RemoteChirp -> chirp(ChirpKind.REMOTE)
        else -> return false
    }
    return true
}

typealias SoundFactory = (Context, CoroutineScope, () -> DpSettings) -> AlarmSound

val defaultSound: SoundFactory = { ctx, scope, settings ->
    Siren(
        AndroidAudioBackend(ctx), scope,
        sirenSound = { settings().sirenSound },
        forceMaxVolume = { settings().forceMaxVolume },
        onVolumeRefused = { AlarmStatusBus.setVolumeRefused(true) },
    )
}

/** Service locator for the armed service, replaceable in tests. Every factory is direct-boot safe. */
object ArmDeps {
    var clock: Clock = Clock.SYSTEM
    var dpStore: (Context) -> DpStore = { DpStore.get(it) }
    var sound: SoundFactory = defaultSound
    var capturer: (Context, LifecycleOwner) -> Capturer = { c, o -> CameraXCapturer(c, o) }
    var freeBytes: (Context) -> Long = { DeviceStatus.freeBytes(it) }
    /** Local network (F9): only attached after the first unlock, never on the boot path. */
    var network: (Context) -> AlarmNetwork = { PeerNetwork.ForAlarm(it) }
    var torch: (Context) -> ws.crandell.fafo.siren.TorchBackend = { ws.crandell.fafo.siren.AndroidTorch(it) }
    /** PHOTO TAKEN shutter click (never while the siren plays). */
    var shutter: (Context) -> Unit = { ws.crandell.fafo.siren.ShutterSound.play() }

    fun reset() {
        shutter = { ws.crandell.fafo.siren.ShutterSound.play() }
        torch = { ws.crandell.fafo.siren.AndroidTorch(it) }
        network = { PeerNetwork.ForAlarm(it) }
        capturer = { c, o -> CameraXCapturer(c, o) }
        freeBytes = { DeviceStatus.freeBytes(it) }
        clock = Clock.SYSTEM
        dpStore = { DpStore.get(it) }
        sound = defaultSound
    }
}
