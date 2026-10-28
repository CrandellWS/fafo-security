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
    is AlarmState.Armed -> if (state.awaitingPower) "Armed, waiting for power" else "Armed"
    is AlarmState.PreAlarm -> "Plug me back in (${((state.endsAt - now).coerceAtLeast(0) + 999) / 1000} s)"
    is AlarmState.Triggered, is AlarmState.IncidentPersisted -> "ALARM"
}
