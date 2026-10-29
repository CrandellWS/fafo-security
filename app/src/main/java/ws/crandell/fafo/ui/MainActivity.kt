package ws.crandell.fafo.ui

import android.Manifest
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ws.crandell.fafo.arm.ArmService
import ws.crandell.fafo.arm.Notifications
import ws.crandell.fafo.auth.AuthAction
import ws.crandell.fafo.ble.BleIds
import ws.crandell.fafo.camera.CameraXCapturer
import ws.crandell.fafo.camera.Lens
import ws.crandell.fafo.net.android.PeerNetwork
import ws.crandell.fafo.pairing.BiometricCredentialGate
import ws.crandell.fafo.siren.AndroidAudioBackend
import ws.crandell.fafo.siren.Siren
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.ui.help.HelpContent
import ws.crandell.fafo.ui.help.HelpLink
import ws.crandell.fafo.ui.screens.CirclesScreen
import ws.crandell.fafo.ui.screens.DevicesPane
import ws.crandell.fafo.ui.screens.DevicesSwitcher
import ws.crandell.fafo.ui.screens.FafoChip
import ws.crandell.fafo.ui.screens.HelpScreen
import ws.crandell.fafo.ui.screens.HomeScreen
import ws.crandell.fafo.ui.screens.IncidentsScreen
import ws.crandell.fafo.ui.screens.InviteDialog
import ws.crandell.fafo.ui.screens.OnboardingScreen
import ws.crandell.fafo.ui.screens.Screen
import ws.crandell.fafo.ui.screens.SettingsScreen
import ws.crandell.fafo.ui.screens.TrustedScreen
import ws.crandell.fafo.ui.theme.FafoColors
import ws.crandell.fafo.ui.theme.FafoTheme
import ws.crandell.fafo.ui.components.LocalShieldEye
import ws.crandell.fafo.ui.components.shieldEyeFor
import androidx.compose.runtime.CompositionLocalProvider
import ws.crandell.fafo.ui.theme.ThemeMode
import ws.crandell.fafo.ui.theme.isDark
import ws.crandell.fafo.ui.theme.applySystemBars
import java.io.File

/** FragmentActivity (a ComponentActivity) so BiometricPrompt can attach. */
class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.refresh()
            if (armAfterPermissions) onArm()
        }

    private val nearbyLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }

    /** API 30: the CompanionDeviceManager picker finds the other phone without location (AC6.5). */
    private val companionLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            val scan: ScanResult? = if (Build.VERSION.SDK_INT >= 33) {
                r.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, ScanResult::class.java)
            } else {
                @Suppress("DEPRECATION")
                r.data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
            }
            if (r.resultCode == RESULT_OK && scan != null) vm.pairing.findOther(scan.device)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System bars follow the theme (System / Light / Dark setting); updated once the setting is read.
        enableEdgeToEdge(
            SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        vm.gate.target = BiometricCredentialGate(this)
        PeerNetwork.gate.target = vm.gate.target
        setContent {
            val dp by vm.dpSettings.collectAsStateWithLifecycle()
            val mode = ThemeMode.parse(dp.theme)
            val dark = mode.isDark()
            LaunchedEffect(dark) { applySystemBars(this@MainActivity, dark) }
            val home by vm.home.collectAsStateWithLifecycle()
            FafoTheme(mode) {
                CompositionLocalProvider(LocalShieldEye provides shieldEyeFor(home.alarmState)) { AppRoot() }
            }
        }
        if (savedInstanceState == null) takeDisarmRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeDisarmRequest(intent)
    }

    /** AC1.7: the notification's Disarm action arrives through the unexported [DISARM_ALIAS] only. */
    private var pendingDisarm = false

    private fun takeDisarmRequest(intent: Intent?) {
        if (intent?.action == ACTION_REQUEST_DISARM && intent.component?.className == DISARM_ALIAS) {
            pendingDisarm = true
            setIntent(Intent(intent).setAction(Intent.ACTION_MAIN))
        }
    }

    override fun onStart() {
        super.onStart()
        // AC10.4 / F9: while the app is open this phone is reachable by its circles.
        PeerNetwork.acquire(this, PeerNetwork.OWNER_UI)
        ws.crandell.fafo.mail.MailDispatcher.acquire(this, ws.crandell.fafo.mail.MailDispatcher.HOST_APP)
    }

    override fun onStop() {
        PeerNetwork.release(PeerNetwork.OWNER_UI)
        ws.crandell.fafo.mail.MailDispatcher.release(ws.crandell.fafo.mail.MailDispatcher.HOST_APP)
        super.onStop()
    }

    override fun onDestroy() {
        if (PeerNetwork.gate.target === vm.gate.target) PeerNetwork.gate.target = null
        if (vm.gate.target is BiometricCredentialGate) vm.gate.target = null
        super.onDestroy()
    }

    fun requestNearby() {
        val perms = NearbyPermissions.missing(this)
        if (perms.isNotEmpty()) nearbyLauncher.launch(perms.toTypedArray())
    }

    fun findOther() {
        if (Build.VERSION.SDK_INT >= 31) {
            vm.pairing.findOther()
            return
        }
        val cdm = getSystemService(CompanionDeviceManager::class.java) ?: return
        val filter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid(BleIds.SERVICE)).build())
            .build()
        val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build()
        cdm.associate(
            request,
            object : CompanionDeviceManager.Callback() {
                @Deprecated("API 30 path")
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    companionLauncher.launch(IntentSenderRequest.Builder(chooserLauncher).build())
                }

                override fun onFailure(error: CharSequence?) {
                    Toast.makeText(this@MainActivity, "Other phone not found: $error", Toast.LENGTH_LONG).show()
                }
            },
            null,
        )
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        if (pendingDisarm) {
            pendingDisarm = false
            onDisarm()
        }
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(OnboardingIntents.appDetails(this))
        }
    }

    private fun requestPermissions(vararg perms: String) = permissionLauncher.launch(arrayOf(*perms))

    fun onStep(step: OnboardingStep) {
        when (step) {
            OnboardingStep.INTRO -> vm.markOnboarding(CeStore.FLAG_INTRO)
            OnboardingStep.SECURE_LOCK -> launch(OnboardingIntents.security())
            OnboardingStep.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(Manifest.permission.POST_NOTIFICATIONS)
                else launch(OnboardingIntents.notificationSettings(this))
            OnboardingStep.CAMERA -> requestPermissions(Manifest.permission.CAMERA)
            OnboardingStep.FULL_SCREEN -> {
                Notifications.ensureChannels(this)
                launch(OnboardingIntents.fullScreenStep(this, vm.onboarding.value))
            }
            OnboardingStep.DND -> launch(OnboardingIntents.dnd())
            OnboardingStep.BATTERY -> launch(OnboardingIntents.ignoreBatteryOptimization(this))
            OnboardingStep.BUILT_IN -> {
                vm.markOnboarding(CeStore.FLAG_BUILT_IN)
                launch(OnboardingIntents.builtInProtections())
            }
            OnboardingStep.SELF_TEST -> runSelfTest()
            OnboardingStep.PHOTO_EMAIL -> openSettings?.invoke()
            OnboardingStep.OPEN_WIFI -> {
                ws.crandell.fafo.mail.OpenWifiFallback.requestApproval(this)
                lifecycleScope.launch { kotlinx.coroutines.delay(1_500); vm.refresh() }
            }
            OnboardingStep.FLASH -> testFlash()
        }
    }

    /** Settings opener set by the composition (Setup's photo email step pushes Settings). */
    private var openSettings: (() -> Unit)? = null

    /** Setup > Flash: three slow blinks so the owner sees the alarm light. */
    private fun testFlash() {
        val torch = ws.crandell.fafo.siren.AndroidTorch(this)
        if (!torch.available) { Toast.makeText(this, "No flash on this phone", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            repeat(3) { torch.set(true); kotlinx.coroutines.delay(300); torch.set(false); kotlinx.coroutines.delay(500) }
        }
    }

    /** F7 step 8: 1 s chirp + 1 s siren at alarm volume, then a test photo (not saved). */
    fun runSelfTest() {
        val dp = vm.dpSettings.value
        Siren(AndroidAudioBackend(this), lifecycleScope, { dp.sirenSound }, { dp.forceMaxVolume }).selfTest()
        vm.markOnboarding(CeStore.FLAG_SELF_TEST)
        if (!DeviceStatus.hasCamera(this)) {
            Toast.makeText(this, "Camera permission is off: no test photo", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val cap = CameraXCapturer(this@MainActivity, this@MainActivity)
            val photo = runCatching { cap.capture(Lens.FRONT) }.getOrNull()
            cap.release()
            val msg = if (photo != null) "Test photo OK (${photo.jpeg.size / 1024} KB, not saved)" else "Test photo failed"
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** User-initiated share through the Android share sheet (AC5.6). */
    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Share photo"))
    }

    private var armAfterPermissions = false

    /** F7 / section 8: the microphone is asked only at first intercom or push-to-talk use, never at arm time. */
    private var afterMic: (() -> Unit)? = null
    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterMic?.invoke() else Toast.makeText(this, "Intercom needs the microphone", Toast.LENGTH_LONG).show()
        afterMic = null
    }

    private fun withMic(block: () -> Unit) {
        if (DeviceStatus.hasPermission(this, Manifest.permission.RECORD_AUDIO)) block() else {
            afterMic = block
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val intercomActions = object : ws.crandell.fafo.ui.screens.IntercomActions {
        override fun setRoomMode(on: Boolean, circles: Set<String>) {
            if (!on) return ws.crandell.fafo.intercom.IntercomService.stop(this@MainActivity)
            if (!DeviceStatus.hasNotificationPermission(this@MainActivity) || !DeviceStatus.notificationsEnabled(this@MainActivity)) {
                Toast.makeText(this@MainActivity, "Allow notifications: intercom always shows one while on.", Toast.LENGTH_LONG).show()
                return
            }
            withMic { ws.crandell.fafo.intercom.IntercomService.start(this@MainActivity, circles) }
        }

        override fun listen(fingerprint: String, circleId: String) {
            lifecycleScope.launch {
                ws.crandell.fafo.intercom.Intercom.listen(this@MainActivity, fingerprint, circleId)?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                }
            }
        }

        override fun stopListening() = ws.crandell.fafo.intercom.Intercom.stopListening()

        override fun talkStart(targets: List<String>, circleId: String) {
            if (!DeviceStatus.hasPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)) {
                withMic {}
                return
            }
            talkJob = lifecycleScope.launch {
                ws.crandell.fafo.intercom.Intercom.startTalk(this@MainActivity, targets, circleId)?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun talkStop() {
            talkJob?.cancel()
            ws.crandell.fafo.intercom.Intercom.stopTalk()
        }
    }
    private var talkJob: kotlinx.coroutines.Job? = null

    /** AC1.3: ask for notifications and camera first; arming without camera is allowed ("Photos off"). */
    fun onArm() {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= 33 && !DeviceStatus.hasNotificationPermission(this@MainActivity)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!DeviceStatus.hasCamera(this@MainActivity)) add(Manifest.permission.CAMERA)
        }
        if (missing.isNotEmpty() && !armAfterPermissions) {
            armAfterPermissions = true
            requestPermissions(*missing.toTypedArray())
            return
        }
        armAfterPermissions = false
        if (!DeviceStatus.hasNotificationPermission(this) || !DeviceStatus.notificationsEnabled(this)) {
            Toast.makeText(this, "Allow notifications to arm: FAFO always shows one while armed.", Toast.LENGTH_LONG).show()
            return
        }
        if (!DeviceStatus.isPlugged(this) || !DeviceStatus.isDeviceSecure(this)) return
        // AC1.7: the screen lock or fingerprint, skipped inside the unlock grace window.
        vm.guarded(AuthAction.ARM) {
            ArmService.arm(this)
            // Spec F14: open the status screen, which asks Android to pin it once FAFO is arming.
            if (ws.crandell.fafo.arm.PinPolicy.requestOnArm(vm.dpSettings.value.pinWhileArmed)) startActivity(ws.crandell.fafo.arm.AlarmActivity.pinIntent(this))
        }
    }

    fun onDisarm() {
        vm.guardedDisarm { ArmService.disarm(this) }
    }

    /** ARMING: Cancel needs no confirmation (the result is "not armed"). */
    fun onCancelArming() = ArmService.cancelArming(this)

    /** Settings > Photo email; Gmail sign-in is added by the play flavor. */
    private val mailActions: ws.crandell.fafo.ui.screens.MailActions by lazy {
        object : ws.crandell.fafo.ui.screens.MailActions by vm.mailActions {
            override fun connectGmail() = connectGmailAccount()
        }
    }

    private fun connectGmailAccount() {
        ws.crandell.fafo.mail.flavor.FlavorMail.connectGmail(this) { account, error ->
            if (account != null) {
                vm.updateCe { it.copy(gmailAccount = account, mailProvider = ws.crandell.fafo.mail.MailProviderKind.GMAIL.name, mailTestOk = false) }
                Toast.makeText(this, "Connected $account", Toast.LENGTH_SHORT).show()
            } else if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** AC6.6: pairing ends with the device in the circle the owner started from (default Home). */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PairingCircleChooser() {
        val circles by vm.circles.collectAsStateWithLifecycle()
        val chosen by vm.pairingCircle.collectAsStateWithLifecycle()
        val current = chosen ?: circles.firstOrNull()?.id
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("The new device joins:", style = MaterialTheme.typography.titleMedium, color = FafoColors.White)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                circles.forEach { c -> FafoChip(selected = c.id == current, onClick = { vm.choosePairingCircle(c.id) }, label = c.name) }
            }
        }
    }

    private fun appVersion(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

    private fun openLink(link: HelpLink) = when (link) {
        HelpLink.FULL_SCREEN -> {
            Notifications.ensureChannels(this)
            launch(OnboardingIntents.fullScreenStep(this, vm.onboarding.value))
        }
        HelpLink.BATTERY -> launch(OnboardingIntents.ignoreBatteryOptimization(this))
        HelpLink.APP_DETAILS -> launch(OnboardingIntents.appDetails(this))
        HelpLink.SECURITY -> launch(OnboardingIntents.builtInProtections())
        HelpLink.DND -> launch(OnboardingIntents.dnd())
        HelpLink.NOTIFICATIONS -> launch(OnboardingIntents.notificationSettings(this))
    }

    companion object {
        const val ACTION_REQUEST_DISARM = "ws.crandell.fafo.action.REQUEST_DISARM"
        /** Unexported activity-alias of this activity (manifest), the only way in for [ACTION_REQUEST_DISARM]. */
        const val DISARM_ALIAS = "ws.crandell.fafo.ui.DisarmRequest"
    }

    @Composable
    private fun AppRoot() {
        var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
        var pushed by rememberSaveable { mutableStateOf<Pushed?>(null) }
        var helpTopic by rememberSaveable { mutableStateOf<String?>(null) }
        var pane by rememberSaveable { mutableStateOf(DevicesPane.CIRCLES) }
        var openIncident by rememberSaveable { mutableStateOf<String?>(null) }
        var openPhoto by rememberSaveable { mutableStateOf<String?>(null) }
        // Predictive back: a pushed screen goes back to its tab, a tab goes to Home, Home leaves the app.
        BackHandler(enabled = pushed != null || tab != Tab.HOME) {
            when {
                openPhoto != null -> openPhoto = null
                openIncident != null -> openIncident = null
                pushed != null -> pushed = null
                else -> tab = Tab.HOME
            }
        }

        val home by vm.home.collectAsStateWithLifecycle()
        val onboarding by vm.onboarding.collectAsStateWithLifecycle()
        val dp by vm.dpSettings.collectAsStateWithLifecycle()
        val ce by vm.ceSettings.collectAsStateWithLifecycle()
        val incidents by vm.incidents.collectAsStateWithLifecycle()
        val devices by vm.trustedDevices.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }

        openSettings = { pushed = Pushed.SETTINGS }
        fun open(screen: Screen) {
            when (screen) {
                Screen.HOME -> { pushed = null; tab = Tab.HOME }
                Screen.SETTINGS -> pushed = Pushed.SETTINGS
                Screen.INCIDENTS -> { openIncident = null; openPhoto = null; pushed = Pushed.INCIDENTS }
                Screen.TRUSTED -> { pane = DevicesPane.PAIRED; tab = Tab.DEVICES }
                Screen.CIRCLES -> { pane = DevicesPane.CIRCLES; tab = Tab.DEVICES }
                Screen.INTERCOM -> tab = Tab.INTERCOM
                Screen.ONBOARDING -> tab = Tab.SETUP
                Screen.HELP -> tab = Tab.HELP
            }
        }

        val incidentTitle = when {
            pushed != Pushed.INCIDENTS -> null
            openPhoto != null -> "Photo"
            openIncident != null -> "Incident"
            else -> null
        }
        AppShell(
            tab = tab, pushed = pushed, setupBadge = onboarding.missing.size,
            onTab = { tab = it; if (it != Tab.HELP) helpTopic = null },
            onBack = {
                when {
                    openPhoto != null -> openPhoto = null
                    openIncident != null -> openIncident = null
                    else -> pushed = null
                }
            },
            onSettings = { pushed = Pushed.SETTINGS },
            snackbar = snackbar,
            titleOverride = incidentTitle,
        ) { m ->
            when (pushed) {
                Pushed.SETTINGS -> SettingsScreen(
                    dp, ce, vm::updateDp, vm::updateCe, m,
                    onStayReachable = { on ->
                        if (on) ws.crandell.fafo.reach.ReachableService.start(this) else ws.crandell.fafo.reach.ReachableService.stop(this)
                    },
                    onGrace = { vm.changeGrace(it) },
                    version = appVersion(),
                    hasFlash = remember { ws.crandell.fafo.siren.AndroidTorch.hasFlash(this) },
                    mail = {
                        val c = ce
                        val test by vm.mailTest.collectAsStateWithLifecycle()
                        val status by vm.mailStatus.collectAsStateWithLifecycle()
                        if (c != null) ws.crandell.fafo.ui.screens.MailSettings(
                            c, ws.crandell.fafo.mail.flavor.FlavorMail.providers, test, status, mailActions,
                            showOpenWifi = Build.VERSION.SDK_INT >= 33,
                        )
                    },
                )
                Pushed.INCIDENTS -> {
                    LaunchedEffect(Unit) { vm.loadIncidents() }
                    IncidentsScreen(
                        incidents, openIncident, openPhoto, vm.photoStore::photoFile,
                        onOpenIncident = { openIncident = it }, onOpenPhoto = { openPhoto = it },
                        onDeletePhoto = vm::deletePhoto, onDeleteIncident = vm::deleteIncident,
                        onShare = ::share, modifier = m,
                    )
                }
                null -> when (tab) {
                    Tab.HOME -> {
                        LaunchedEffect(Unit) { vm.loadIncidents() }
                        HomeScreen(
                            home.copy(incidents = incidents.size, paired = devices.size, graceS = dp.unlockGraceS),
                            onArm = ::onArm,
                            onDisarm = ::onDisarm,
                            onOpen = ::open,
                            onStep = { tab = Tab.SETUP },
                            modifier = m,
                            onCancelArming = ::onCancelArming,
                            onOpenStatus = {
                                startActivity(android.content.Intent(this, ws.crandell.fafo.arm.AlarmActivity::class.java))
                            },
                        )
                    }
                    Tab.SETUP -> OnboardingScreen(
                        onboarding, ::onStep,
                        onLearnMore = { step -> helpTopic = HelpContent.topicFor(step); tab = Tab.HELP },
                        modifier = m,
                        onOpenAppSettings = { launch(OnboardingIntents.appDetails(this)) },
                    )
                    Tab.HELP -> HelpScreen(
                        helpTopic, { id -> helpTopic = if (helpTopic == id) null else id }, ::openLink, m,
                    )
                    Tab.DEVICES -> Column(m) {
                        DevicesSwitcher(pane, { pane = it })
                        val dm = Modifier.fillMaxSize()
                        when (pane) {
                            DevicesPane.PAIRED -> {
                                val pairing by vm.pairing.state.collectAsStateWithLifecycle()
                                val nearby = NearbyPermissions.missing(this@MainActivity).isEmpty()
                                // F7: Nearby devices is asked when the owner first opens Paired phones.
                                LaunchedEffect(Unit) { if (!nearby) requestNearby() }
                                TrustedScreen(
                                    devices, pairing, nearby,
                                    onRequestNearby = ::requestNearby,
                                    onShowThisPhone = vm.pairing::showThisPhone,
                                    onFindOther = ::findOther,
                                    onAnswerCode = vm.pairing::answerCode,
                                    onCancel = vm.pairing::cancel,
                                    onDismiss = vm.pairing::dismiss,
                                    onRemove = vm::removeDevice,
                                    modifier = dm,
                                    extra = { PairingCircleChooser() },
                                )
                            }
                            DevicesPane.CIRCLES -> {
                                val circles by vm.circles.collectAsStateWithLifecycle()
                                val reachable by vm.reachable.collectAsStateWithLifecycle()
                                CirclesScreen(
                                    circles, devices, reachable, ce?.deviceLabel ?: "",
                                    object : ws.crandell.fafo.ui.screens.CirclesActions by vm.circlesActions {
                                        override fun pairInto(circleId: String) {
                                            vm.circlesActions.pairInto(circleId)
                                            pane = DevicesPane.PAIRED
                                        }
                                    },
                                    dm,
                                )
                            }
                        }
                    }
                    Tab.INTERCOM -> {
                        val ui by ws.crandell.fafo.intercom.Intercom.ui.collectAsStateWithLifecycle()
                        val circles by vm.circles.collectAsStateWithLifecycle()
                        val reachable by vm.reachable.collectAsStateWithLifecycle()
                        ws.crandell.fafo.ui.screens.IntercomScreen(
                            ui, circles, reachable, home.alarming, intercomActions, m,
                            onOpenDevices = { open(Screen.CIRCLES) },
                        )
                    }
                }
            }
        }
        val invites by vm.invites.collectAsStateWithLifecycle()
        invites.firstOrNull()?.let { inv -> InviteDialog(inv) { vm.answerInvite(inv, it) } }
        val message by vm.message.collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                vm.messageShown()
                snackbar.showSnackbar(it)
            }
        }
    }
}
