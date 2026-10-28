package ws.crandell.fafo.arm

import android.content.Context
import ws.crandell.fafo.core.Clock
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

object NoSound : AlarmSound {
    override fun startPreAlarm(durationMs: Long) {}
    override fun startSiren() {}
    override fun stop() {}
    override fun chirp(kind: ChirpKind) {}
}

/** Service locator for the armed service, replaceable in tests. Every factory is direct-boot safe. */
object ArmDeps {
    var clock: Clock = Clock.SYSTEM
    var dpStore: (Context) -> DpStore = { DpStore.get(it) }
    var sound: (Context) -> AlarmSound = { NoSound }

    fun reset() {
        clock = Clock.SYSTEM
        dpStore = { DpStore.get(it) }
        sound = { NoSound }
    }
}
