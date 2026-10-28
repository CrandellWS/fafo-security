package ws.crandell.fafo.arm

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import ws.crandell.fafo.core.AlarmEffect
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.core.Clock
import ws.crandell.fafo.siren.AndroidAudioBackend
import ws.crandell.fafo.siren.Siren
import ws.crandell.fafo.storage.DpSettings
import ws.crandell.fafo.storage.DpStore

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

    fun reset() {
        clock = Clock.SYSTEM
        dpStore = { DpStore.get(it) }
        sound = defaultSound
    }
}
