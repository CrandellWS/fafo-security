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
import ws.crandell.fafo.auth.ArmAuthUi
import ws.crandell.fafo.auth.ArmAuthenticator
import ws.crandell.fafo.auth.AuthAction
import ws.crandell.fafo.auth.GuardedAction
import ws.crandell.fafo.auth.KeystoreArmAuthKey
import ws.crandell.fafo.auth.DisarmPolicy
import ws.crandell.fafo.system.BootClock
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.flow.collectLatest
import ws.crandell.fafo.ui.screens.graceLabel
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
        viewModelScope.launch { ceSettings.collect { refresh() } }
    }

    /** The activity on screen sets [DelegatingGate.target] so ViewModel flows can show BiometricPrompt. */
    val gate = DelegatingGate()

    /** AC1.7: Arm / Disarm / grace changes need the screen lock or a strong biometric outside the grace window. */
    private val armAuth = ArmAuthenticator(
        KeystoreArmAuthKey(), gate, { dpSettings.value.unlockGraceS },
        disarmKey = KeystoreArmAuthKey(KeystoreArmAuthKey.DISARM_PREFIX),
    )
    private val armGuard = GuardedAction(armAuth::authorize)
    val armAuthUi: StateFlow<ArmAuthUi> = armGuard.ui

    /**
     * Disarm (Bill 2026-10-28): no prompt only after a lock and unlock that happened after arming, inside the
     * grace window, with the grace key proving a real unlock (DisarmPolicy). Otherwise BiometricPrompt.
     */
    fun guardedDisarm(block: () -> Unit) = viewModelScope.launch {
        val trail = dp.readTrail()
        val skip = DisarmPolicy.canSkipPrompt(trail, BootClock.bootId(ctx), BootClock.now(), dpSettings.value.unlockGraceS)
        armGuard.run(AuthAction.DISARM, { _message.value = it }, { armAuth.authorizeDisarm(skip) }) { block() }
    }

    /** Runs [block] (start / stop the armed service) only after the owner is confirmed. */
    fun guarded(action: AuthAction, block: () -> Unit) = viewModelScope.launch {
        armGuard.run(action, { _message.value = it }) { block() }
    }

    /** Settings > Security: a new grace window needs the confirmation; Cancel keeps the old value and key. */
    fun changeGrace(newGraceS: Int) = viewModelScope.launch {
        if (newGraceS == dpSettings.value.unlockGraceS) return@launch
        armGuard.run(AuthAction.CHANGE_GRACE, { _message.value = it }) {
            armAuth.rekey(newGraceS)
            dp.update { it.copy(unlockGraceS = newGraceS) }
            _message.value = "Grace window set to ${graceLabel(newGraceS)}"
        }
    }

    /** Ticks four times a second while a countdown (ARMING, PRE_ALARM) is on screen. */
    private val tick = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            AlarmStatusBus.state.collectLatest { s ->
                while (s is AlarmState.Arming || s is AlarmState.PreAlarm) {
                    tick.value = System.currentTimeMillis()
                    delay(250)
                }
            }
        }
    }

    val home: StateFlow<HomeUiState> = combine(
        combine(_plugged, armGuard.ui, tick) { p, a, t -> Triple(p, a, t) },
        _onboarding, AlarmStatusBus.state, dpState, AlarmStatusBus.cameraActive,
    ) { (plugged, auth, now), onboarding, live, dpState, camera ->
        val state = if (live == AlarmState.Disarmed && dpState.armed) AlarmState.Armed(dpState.awaitingPower) else live
        HomeUiState(
            alarmState = state,
            confirming = auth is ArmAuthUi.Confirming,
            plugged = plugged,
            secureLock = onboarding.secureLock,
            stateLabel = stateLabel(state, maxOf(now, System.currentTimeMillis())),
            armed = state is AlarmState.Armed,
            arming = state is AlarmState.Arming,
            armingLeftS = (state as? AlarmState.Arming)?.let { ((it.endsAt - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000 } ?: 0,
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
            mailReady = ceSettings.value?.let { it.mailEnabled && it.mailTestOk } == true,
            openWifiWanted = ceSettings.value?.let { it.mailEnabled && it.mailOpenWifi } == true,
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

    fun updateDp(t: (DpSettings) -> DpSettings) = viewModelScope.launch {
        val before = dpSettings.value.theme
        dp.update(t)
        val after = dp.readSettings().theme
        // Android 12+: the launch screen follows a forced Light / Dark theme from the next start on.
        if (after != before) ws.crandell.fafo.ui.theme.applyAppNightMode(ctx, ws.crandell.fafo.ui.theme.ThemeMode.parse(after))
    }
    fun updateCe(t: (CeSettings) -> CeSettings) = viewModelScope.launch { ce.update(t) }

    // ---- Photo email (round 3) ----------------------------------------------------------------------

    private val _mailTest = MutableStateFlow<ws.crandell.fafo.ui.screens.MailTestUi>(ws.crandell.fafo.ui.screens.MailTestUi.Idle)
    val mailTest: StateFlow<ws.crandell.fafo.ui.screens.MailTestUi> = _mailTest
    val mailStatus = ws.crandell.fafo.mail.MailDispatcher.status

    val mailActions = object : ws.crandell.fafo.ui.screens.MailActions {
        override fun update(t: (CeSettings) -> CeSettings) = updateCe(t).let { }
        override fun savePassword(password: String) {
            viewModelScope.launch(Dispatchers.IO) {
                val sealed = runCatching { ws.crandell.fafo.mail.SecretBox.seal(ws.crandell.fafo.mail.KeystoreSecretCipher(), password) }.getOrNull()
                if (sealed == null) { _message.value = "Could not store the password securely"; return@launch }
                ce.update { it.copy(mailSecret = sealed, mailTestOk = false) }
            }
        }
        override fun removePassword() { updateCe { it.copy(mailSecret = "", mailTestOk = false) } }
        override fun sendTest() { sendTestEmail() }
        override fun sendQueuedNow() = ws.crandell.fafo.mail.MailDispatcher.sendNow()
        override fun discardQueued() = ws.crandell.fafo.mail.MailDispatcher.discardAll(ctx)
    }

    /** Settings > Photo email > Send test email: a small message with the shield, sent directly (no queue). */
    fun sendTestEmail() = viewModelScope.launch(Dispatchers.IO) {
        val s = ce.read()
        val cfg = s.mailConfig()
        if (!cfg.complete) {
            _mailTest.value = ws.crandell.fafo.ui.screens.MailTestUi.Error("Fill in the addresses and the mail server details first")
            return@launch
        }
        _mailTest.value = ws.crandell.fafo.ui.screens.MailTestUi.Sending
        val sender = ws.crandell.fafo.mail.MailDispatcher.senderFor(ctx, cfg, null)
        if (sender == null) {
            _mailTest.value = ws.crandell.fafo.ui.screens.MailTestUi.Error("This build cannot send with that account")
            return@launch
        }
        val msg = ws.crandell.fafo.mail.MimeBuilder.build(
            cfg.sender, cfg.recipients, "FAFO Security test email from ${s.deviceLabel}",
            "This is a test from FAFO Security on ${s.deviceLabel}.\n\nIf the alarm goes off, trigger photos are emailed here.",
            listOf(ws.crandell.fafo.mail.Attachment("fafo-shield.jpg", shieldJpeg())),
        )
        when (val r = sender.send(cfg.sender, cfg.recipients, msg)) {
            ws.crandell.fafo.mail.SendResult.Sent -> {
                ce.update { it.copy(mailTestOk = true) }
                _mailTest.value = ws.crandell.fafo.ui.screens.MailTestUi.Ok
            }
            is ws.crandell.fafo.mail.SendResult.Failed -> _mailTest.value = ws.crandell.fafo.ui.screens.MailTestUi.Error(r.error.message)
        }
    }

    private fun shieldJpeg(): ByteArray {
        val shield = ws.crandell.fafo.ui.components.renderShieldMark(256)
        val bmp = android.graphics.Bitmap.createBitmap(shield.width + 32, shield.height + 32, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(shield.asAndroidBitmap(), 16f, 16f, null)
        }
        return java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    override fun onCleared() {
        ctx.unregisterReceiver(batteryReceiver)
    }
}
