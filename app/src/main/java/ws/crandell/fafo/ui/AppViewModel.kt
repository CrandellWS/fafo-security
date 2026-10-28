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
import ws.crandell.fafo.pairing.DelegatingGate
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.CircleManager
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.net.IncomingInvite
import ws.crandell.fafo.net.android.PeerNetwork
import ws.crandell.fafo.pairing.GateResult
import ws.crandell.fafo.pairing.PairingFinisher
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.trust.TrustGraph
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

    /** The activity on screen sets [DelegatingGate.target] so ViewModel flows can show BiometricPrompt. */
    val gate = DelegatingGate()
    private val trustedStore by lazy { TrustGraph.trusted(app) }
    val trustedDevices: StateFlow<List<TrustedDevice>> by lazy { trustedStore.devices }

    val pairing = PairingController(
        app, viewModelScope, gate, TrustGraph::keys, { trustedStore }, { ce.read().deviceLabel },
    )

    private val circleStore by lazy { TrustGraph.circles(app) }
    val circles: StateFlow<List<Circle>> by lazy { circleStore.circles }
    private val circleManager by lazy { CircleManager(circleStore, gate) }
    val reachable: StateFlow<Set<String>> = PeerNetwork.reachable
    val invites: StateFlow<List<IncomingInvite>> = PeerNetwork.invites.invites

    /** The circle "Add trusted device" pairs into (AC6.6, default Home). */
    private val _pairingCircle = MutableStateFlow<String?>(null)
    val pairingCircle: StateFlow<String?> = _pairingCircle
    fun choosePairingCircle(id: String) { _pairingCircle.value = id }

    private val _message = MutableStateFlow<String?>(null)
    /** One-shot user feedback (toast). */
    val message: StateFlow<String?> = _message
    fun messageShown() { _message.value = null }

    init {
        pairing.afterPaired = { initiator, link, device ->
            PairingInvite(
                app, TrustGraph.keys(), trustedStore, circleManager, PeerNetwork.invites,
                myLabel = { ce.read().deviceLabel },
                beaconKey = { PeerNetwork.beaconKeys(app).key() },
                status = pairing::status,
            ).run(initiator, link, device, _pairingCircle.value ?: circleStore.all().first().id)
        }
    }

    fun removeDevice(d: TrustedDevice) = viewModelScope.launch {
        if (PairingFinisher(gate, trustedStore) { System.currentTimeMillis() }.remove(d.fingerprint)) {
            PeerNetwork.forget(getApplication(), d.fingerprint)
        }
    }

    fun answerInvite(inv: IncomingInvite, accepted: Set<Permission>?) = viewModelScope.launch {
        if (accepted == null) return@launch PeerNetwork.invites.answer(inv.id, null)
        val ok = circleManager.acceptInvite(inv.circleId, inv.circleName, inv.offered, accepted, inv.peer, inv.peerLabel)
        if (ok) PeerNetwork.invites.answer(inv.id, accepted intersect inv.offered) else _message.value = "Screen lock not confirmed"
    }

    val circlesActions = object : ws.crandell.fafo.ui.screens.CirclesActions {
        override fun setDeviceLabel(label: String) { updateCe { it.copy(deviceLabel = label.trim().take(ws.crandell.fafo.core.Defaults.LABEL_MAX)) } }
        override fun createCircle(name: String) {
            if (circleManager.create(name) == null) _message.value = "A circle needs a new name of 1-24 characters"
        }
        override fun setPermissions(circleId: String, permissions: Set<Permission>) { viewModelScope.launch { circleManager.setCirclePermissions(circleId, permissions) } }
        override fun renameMember(circleId: String, fingerprint: String, label: String) {
            if (!circleManager.renameMember(circleId, fingerprint, label)) _message.value = "Names are 1-24 characters and unique in a circle"
        }
        override fun removeMember(circleId: String, fingerprint: String) { viewModelScope.launch { circleManager.removeMember(circleId, fingerprint) } }
        override fun removeCircle(circleId: String) { viewModelScope.launch { circleManager.removeCircle(circleId) } }
        override fun invite(circleId: String, fingerprint: String) {
            viewModelScope.launch {
                val name = circleStore.get(circleId)?.name ?: return@launch
                // AC8.5: adding a member needs the credential; the member joins when they accept.
                if (gate.authenticate("Add to $name", "Confirm with your screen lock") !is GateResult.Success) return@launch
                val hub = PeerNetwork.hub
                _message.value = if (hub != null && hub.invite(fingerprint, circleId)) "Invite sent. It expires in 10 minutes." else "That device is not reachable now"
            }
        }
        override fun pairInto(circleId: String) { _pairingCircle.value = circleId }
    }

    fun markOnboarding(flag: String) = viewModelScope.launch { ce.markOnboarding(flag) }

    fun updateDp(t: (DpSettings) -> DpSettings) = viewModelScope.launch { dp.update(t) }
    fun updateCe(t: (CeSettings) -> CeSettings) = viewModelScope.launch { ce.update(t) }

    override fun onCleared() {
        ctx.unregisterReceiver(batteryReceiver)
    }
}
