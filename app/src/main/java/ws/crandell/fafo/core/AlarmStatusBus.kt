package ws.crandell.fafo.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory, process-wide view of the live alarm state, published by the armed service for the UI. */
object AlarmStatusBus {
    private val _state = MutableStateFlow<AlarmState>(AlarmState.Disarmed)
    val state: StateFlow<AlarmState> = _state

    private val _photosPaused = MutableStateFlow(false)
    val photosPaused: StateFlow<Boolean> = _photosPaused

    private val _cameraActive = MutableStateFlow(false)
    val cameraActive: StateFlow<Boolean> = _cameraActive

    /** Setting the alarm volume was refused (DND without policy access); shown in onboarding. */
    private val _volumeRefused = MutableStateFlow(false)
    val volumeRefused: StateFlow<Boolean> = _volumeRefused
    fun setVolumeRefused(v: Boolean) { _volumeRefused.value = v }

    fun publish(state: AlarmState) { _state.value = state }

    /** The bump ladder while ARMED (status screen stripes). GREEN in every other state. */
    private val _threat = MutableStateFlow(ThreatLevel.GREEN)
    val threat: StateFlow<ThreatLevel> = _threat
    fun setThreat(level: ThreatLevel) { _threat.value = level }

    /**
     * PHOTO TAKEN (device feedback 2026-10-28): the latest trigger photo of the open incident, so the alarm and
     * hands-off screens can flash and show it. Null when no incident is open or no photo was saved yet.
     */
    private val _photoShot = MutableStateFlow<PhotoShot?>(null)
    val photoShot: StateFlow<PhotoShot?> = _photoShot
    fun setPhotoShot(shot: PhotoShot?) { _photoShot.value = shot }

    /**
     * AC10.8: set by the intercom while it runs; the armed service calls it on entering PRE_ALARM / TRIGGERED
     * (EndIntercom) so room mode ends and listeners drop before the siren. In memory only, direct-boot safe.
     */
    @Volatile var endIntercom: (() -> Unit)? = null
    fun setPhotosPaused(paused: Boolean) { _photosPaused.value = paused }
    fun setCameraActive(active: Boolean) { _cameraActive.value = active }
}

fun stateLabel(state: AlarmState, now: Long): String = when (state) {
    AlarmState.Disarmed -> "Disarmed"
    is AlarmState.Arming -> "Arming (${((state.endsAt - now).coerceAtLeast(0) + 999) / 1000} s)"
    is AlarmState.Armed -> if (state.awaitingPower) "Armed, waiting for power" else "Armed"
    is AlarmState.PreAlarm -> (if (state.cause == IncidentCause.MOTION) "Unlock to stop (" else "Plug me back in (") + "${((state.endsAt - now).coerceAtLeast(0) + 999) / 1000} s)"
    is AlarmState.Triggered, is AlarmState.IncidentPersisted -> "ALARM"
}

/**
 * One saved trigger photo: [path] of the JPEG (CE storage), [count] photos saved in this incident so far,
 * [at] wall-clock time it was saved (the screens show the banner for [PHOTO_BANNER_MS] after it).
 */
data class PhotoShot(val incidentId: String, val path: String, val count: Int, val at: Long)
