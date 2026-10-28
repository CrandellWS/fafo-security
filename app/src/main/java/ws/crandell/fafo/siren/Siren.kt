package ws.crandell.fafo.siren

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ws.crandell.fafo.arm.AlarmSound
import ws.crandell.fafo.arm.ChirpKind
import ws.crandell.fafo.core.PreAlarmSchedule

/** Everything the siren needs from the platform; a fake replaces it in JVM tests. */
interface AudioBackend {
    /** Loop [samples] on USAGE_ALARM at [gain] (0..1) until [stop]. */
    fun playLoop(samples: ShortArray, gain: Float)
    /** Play [samples] once on USAGE_ALARM at [gain]. */
    fun playOnce(samples: ShortArray, gain: Float)
    fun stop()
    fun requestFocus()
    fun abandonFocus()
    /** Sets STREAM_ALARM to max. Returns false when refused (DND without policy access). */
    fun setAlarmVolumeMax(): Boolean
    fun isAlarmVolumeMax(): Boolean
    fun vibrate(pattern: LongArray, repeat: Boolean)
    fun cancelVibrate()
    fun release() {}
}

/**
 * Siren, pre-alarm beeps and chirps (spec F2, AC2.3-AC2.5, AC4.3), all on the USAGE_ALARM path.
 * The siren stops only through [stop] (owner unlock / Disarm / remote command via the state machine);
 * volume buttons and notification dismissal never reach it. A watchdog restores the alarm volume.
 */
class Siren(
    private val backend: AudioBackend,
    private val scope: CoroutineScope,
    private val sirenSound: () -> String = { "Klaxon" },
    private val forceMaxVolume: () -> Boolean = { true },
    private val onVolumeRefused: () -> Unit = {},
) : AlarmSound {

    var mode: Mode = Mode.IDLE
        private set

    enum class Mode { IDLE, PRE_ALARM, SIREN }

    private var beepJob: Job? = null
    private var watchdogJob: Job? = null

    /** Beep offsets actually played (for tests and diagnostics). */
    val beepLog = mutableListOf<Long>()

    override fun startPreAlarm(durationMs: Long) {
        stopInternal()
        mode = Mode.PRE_ALARM
        backend.requestFocus()
        val offsets = PreAlarmSchedule.beepOffsets(durationMs)
        val beep = Tones.beep()
        beepJob = scope.launch {
            var t = 0L
            for (o in offsets) {
                delay(o - t)
                t = o
                if (!isActive) break
                beepLog += o
                backend.playOnce(beep, PRE_ALARM_GAIN)
            }
        }
    }

    override fun startSiren() {
        stopInternal()
        mode = Mode.SIREN
        if (forceMaxVolume() && !backend.setAlarmVolumeMax()) onVolumeRefused()
        backend.requestFocus()
        backend.playLoop(Tones.siren(sirenSound()), SIREN_GAIN)
        backend.vibrate(longArrayOf(0, 800, 400), repeat = true)
        if (forceMaxVolume()) {
            watchdogJob = scope.launch {
                while (isActive) {
                    delay(WATCHDOG_MS)
                    if (!backend.isAlarmVolumeMax() && !backend.setAlarmVolumeMax()) onVolumeRefused()
                }
            }
        }
    }

    override fun stop() {
        if (mode == Mode.IDLE && beepJob == null) return
        stopInternal()
        backend.abandonFocus()
    }

    private fun stopInternal() {
        beepJob?.cancel(); beepJob = null
        watchdogJob?.cancel(); watchdogJob = null
        if (mode == Mode.SIREN) backend.cancelVibrate()
        backend.stop()
        mode = Mode.IDLE
    }

    /** Chirps never play over the siren (motion is off in PRE_ALARM / TRIGGERED anyway). */
    override fun chirp(kind: ChirpKind) {
        if (mode == Mode.SIREN) return
        when (kind) {
            ChirpKind.BUMP -> backend.playOnce(Tones.chirpShort(), CHIRP_GAIN)
            ChirpKind.PICKUP -> {
                backend.playOnce(Tones.chirpTriple(), PICKUP_GAIN)
                backend.vibrate(longArrayOf(0, 120, 80, 120, 80, 120), repeat = false)
            }
            ChirpKind.STILL_ARMED, ChirpKind.REMOTE -> backend.playOnce(Tones.chirpShort(), CHIRP_GAIN)
        }
    }

    /** Self-test (F7 step 8): 1 s chirp then 1 s siren sample at alarm volume. */
    fun selfTest() {
        scope.launch {
            val chirp = Tones.chirpTriple()
            backend.playOnce(chirp, PICKUP_GAIN)
            delay(1000)
            if (forceMaxVolume() && !backend.setAlarmVolumeMax()) onVolumeRefused()
            val s = Tones.siren(sirenSound())
            val oneSecond = s.copyOf(minOf(s.size, Tones.SAMPLE_RATE))
            backend.playOnce(oneSecond, SIREN_GAIN)
        }
    }

    override fun release() {
        stopInternal()
        backend.release()
    }

    companion object {
        const val SIREN_GAIN = 1.0f
        const val PRE_ALARM_GAIN = 0.45f
        const val PICKUP_GAIN = 0.6f
        const val CHIRP_GAIN = 0.35f
        const val WATCHDOG_MS = 1000L
    }
}
