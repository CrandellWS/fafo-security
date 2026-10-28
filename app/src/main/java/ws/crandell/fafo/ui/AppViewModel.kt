package ws.crandell.fafo.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ws.crandell.fafo.camera.IncidentSidecar
import ws.crandell.fafo.camera.PhotoStore
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.core.isAlarming
import ws.crandell.fafo.core.stateLabel
import ws.crandell.fafo.storage.CeSettings
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.storage.DpSettings
import ws.crandell.fafo.storage.DpState
import ws.crandell.fafo.storage.DpStore
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.ui.screens.HomeUiState

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val dp = DpStore.get(app)
    private val ce = CeStore.get(app)

    private val _plugged = MutableStateFlow(DeviceStatus.isPlugged(app))
    val plugged: StateFlow<Boolean> = _plugged

    private val _onboarding = MutableStateFlow(OnboardingStatus())
    val onboarding: StateFlow<OnboardingStatus> = _onboarding

    val dpSettings: StateFlow<DpSettings> = dp.settings.stateIn(viewModelScope, SharingStarted.Eagerly, DpSettings())
    val dpState: StateFlow<DpState> = dp.state.stateIn(viewModelScope, SharingStarted.Eagerly, DpState())
    val ceSettings: StateFlow<CeSettings?> = ce.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val flags = ce.onboardingFlags.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** AC1.1: live plugged state from ACTION_BATTERY_CHANGED, registered for the ViewModel's lifetime. */
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _plugged.value = DeviceStatus.pluggedFrom(intent)
        }
    }

    init {
        ContextCompat.registerReceiver(
            app, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        viewModelScope.launch { flags.collect { refresh() } }
    }

    val home: StateFlow<HomeUiState> = combine(
        _plugged, _onboarding, AlarmStatusBus.state, dpState, AlarmStatusBus.cameraActive,
    ) { plugged, onboarding, live, dpState, camera ->
        val state = if (live == AlarmState.Disarmed && dpState.armed) AlarmState.Armed(dpState.awaitingPower) else live
        HomeUiState(
            plugged = plugged,
            secureLock = onboarding.secureLock,
            stateLabel = stateLabel(state, System.currentTimeMillis()),
            armed = state is AlarmState.Armed,
            alarming = state.isAlarming,
            photosOff = !camera,
            awaitingPower = (state as? AlarmState.Armed)?.awaitingPower == true,
            checklist = onboarding.missing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    /** Re-read permissions and system settings (call from onResume). */
    fun refresh() {
        val f = flags.value
        _onboarding.value = OnboardingStatus.read(
            ctx,
            introSeen = CeStore.FLAG_INTRO in f,
            builtInSeen = CeStore.FLAG_BUILT_IN in f,
            selfTestDone = CeStore.FLAG_SELF_TEST in f,
        )
        _plugged.value = DeviceStatus.isPlugged(ctx)
    }

    val photoStore = PhotoStore(PhotoStore.root(app.filesDir))
    private val _incidents = MutableStateFlow<List<IncidentSidecar>>(emptyList())
    val incidents: StateFlow<List<IncidentSidecar>> = _incidents

    fun loadIncidents() = viewModelScope.launch(Dispatchers.IO) { _incidents.value = photoStore.list() }
    fun deletePhoto(id: String, name: String) = viewModelScope.launch(Dispatchers.IO) {
        photoStore.deletePhoto(id, name); _incidents.value = photoStore.list()
    }
    fun deleteIncident(id: String) = viewModelScope.launch(Dispatchers.IO) {
        photoStore.deleteIncident(id); _incidents.value = photoStore.list()
    }

    fun markOnboarding(flag: String) = viewModelScope.launch { ce.markOnboarding(flag) }

    fun updateDp(t: (DpSettings) -> DpSettings) = viewModelScope.launch { dp.update(t) }
    fun updateCe(t: (CeSettings) -> CeSettings) = viewModelScope.launch { ce.update(t) }

    override fun onCleared() {
        ctx.unregisterReceiver(batteryReceiver)
    }
}
