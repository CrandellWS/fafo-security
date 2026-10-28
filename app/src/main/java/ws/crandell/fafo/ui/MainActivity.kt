package ws.crandell.fafo.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import ws.crandell.fafo.camera.CameraXCapturer
import ws.crandell.fafo.camera.Lens
import ws.crandell.fafo.ui.screens.IncidentsScreen
import ws.crandell.fafo.ui.screens.TrustedScreen
import ws.crandell.fafo.ui.screens.CirclesScreen
import ws.crandell.fafo.ui.screens.InviteDialog
import ws.crandell.fafo.net.android.PeerNetwork
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import ws.crandell.fafo.pairing.BiometricCredentialGate
import ws.crandell.fafo.ble.BleIds
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.ParcelUuid
import androidx.activity.result.IntentSenderRequest
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ws.crandell.fafo.arm.ArmService
import ws.crandell.fafo.siren.AndroidAudioBackend
import ws.crandell.fafo.siren.Siren
import ws.crandell.fafo.storage.CeStore
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.ui.screens.HomeScreen
import ws.crandell.fafo.ui.screens.OnboardingScreen
import ws.crandell.fafo.ui.screens.Screen
import ws.crandell.fafo.ui.screens.SettingsScreen
import ws.crandell.fafo.ui.theme.FafoTheme

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.gate.target = BiometricCredentialGate(this)
        PeerNetwork.gate.target = vm.gate.target
        setContent { FafoTheme { AppRoot() } }
    }

    override fun onStart() {
        super.onStart()
        // AC10.4 / F9: while the app is open this phone is reachable by its circles.
        PeerNetwork.acquire(this, PeerNetwork.OWNER_UI)
    }

    override fun onStop() {
        PeerNetwork.release(PeerNetwork.OWNER_UI)
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
            OnboardingStep.FULL_SCREEN -> launch(OnboardingIntents.fullScreen(this))
            OnboardingStep.DND -> launch(OnboardingIntents.dnd())
            OnboardingStep.BATTERY -> launch(OnboardingIntents.ignoreBatteryOptimization(this))
            OnboardingStep.BUILT_IN -> {
                vm.markOnboarding(CeStore.FLAG_BUILT_IN)
                launch(OnboardingIntents.builtInProtections())
            }
            OnboardingStep.SELF_TEST -> runSelfTest()
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
        ArmService.arm(this)
    }

    fun onDisarm() = ArmService.disarm(this)

    /** AC6.6: pairing ends with the device in the circle the owner started from (default Home). */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PairingCircleChooser() {
        val circles by vm.circles.collectAsStateWithLifecycle()
        val chosen by vm.pairingCircle.collectAsStateWithLifecycle()
        val current = chosen ?: circles.firstOrNull()?.id
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("The new device joins:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                circles.forEach { c ->
                    FilterChip(selected = c.id == current, onClick = { vm.choosePairingCircle(c.id) }, label = { Text(c.name) })
                }
            }
        }
    }

    @Composable
    private fun AppRoot() {
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        var samsungDialog by rememberSaveable { mutableStateOf(false) }
        var openIncident by rememberSaveable { mutableStateOf<String?>(null) }
        var openPhoto by rememberSaveable { mutableStateOf<String?>(null) }
        BackHandler(enabled = screen != Screen.HOME) {
            when {
                openPhoto != null -> openPhoto = null
                openIncident != null -> openIncident = null
                else -> screen = Screen.HOME
            }
        }

        val home by vm.home.collectAsStateWithLifecycle()
        val onboarding by vm.onboarding.collectAsStateWithLifecycle()
        val dp by vm.dpSettings.collectAsStateWithLifecycle()
        val ce by vm.ceSettings.collectAsStateWithLifecycle()

        Scaffold { padding ->
            val m = Modifier.fillMaxSize().padding(padding)
            when (screen) {
                Screen.HOME -> HomeScreen(
                    home,
                    onArm = ::onArm,
                    onDisarm = ::onDisarm,
                    onOpen = { screen = it },
                    onStep = { screen = Screen.ONBOARDING },
                    modifier = m,
                )
                Screen.SETTINGS -> SettingsScreen(dp, ce, vm::updateDp, vm::updateCe, m, onStayReachable = { on ->
                    if (on) ws.crandell.fafo.reach.ReachableService.start(this) else ws.crandell.fafo.reach.ReachableService.stop(this)
                })
                Screen.ONBOARDING -> OnboardingScreen(
                    onboarding, ::onStep, { screen = Screen.HOME }, m, onSamsungBattery = { samsungDialog = true },
                )
                Screen.INCIDENTS -> {
                    val incidents by vm.incidents.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { vm.loadIncidents() }
                    IncidentsScreen(
                        incidents, openIncident, openPhoto, vm.photoStore::photoFile,
                        onOpenIncident = { openIncident = it }, onOpenPhoto = { openPhoto = it },
                        onDeletePhoto = vm::deletePhoto, onDeleteIncident = vm::deleteIncident,
                        onShare = ::share, modifier = m,
                    )
                }
                Screen.TRUSTED -> {
                    val devices by vm.trustedDevices.collectAsStateWithLifecycle()
                    val pairing by vm.pairing.state.collectAsStateWithLifecycle()
                    val nearby = NearbyPermissions.missing(this).isEmpty()
                    // F7: Nearby devices is asked when the owner first opens Trusted devices.
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
                        modifier = m,
                        extra = { PairingCircleChooser() },
                    )
                }
                Screen.CIRCLES -> {
                    val circles by vm.circles.collectAsStateWithLifecycle()
                    val devices by vm.trustedDevices.collectAsStateWithLifecycle()
                    val reachable by vm.reachable.collectAsStateWithLifecycle()
                    CirclesScreen(
                        circles, devices, reachable, ce?.deviceLabel ?: "",
                        object : ws.crandell.fafo.ui.screens.CirclesActions by vm.circlesActions {
                            override fun pairInto(circleId: String) {
                                vm.circlesActions.pairInto(circleId)
                                screen = Screen.TRUSTED
                            }
                        },
                        m,
                    )
                }
                Screen.INTERCOM -> {
                    val ui by ws.crandell.fafo.intercom.Intercom.ui.collectAsStateWithLifecycle()
                    val circles by vm.circles.collectAsStateWithLifecycle()
                    val reachable by vm.reachable.collectAsStateWithLifecycle()
                    ws.crandell.fafo.ui.screens.IntercomScreen(ui, circles, reachable, home.alarming, intercomActions, m)
                }
            }
        }
        val invites by vm.invites.collectAsStateWithLifecycle()
        invites.firstOrNull()?.let { inv -> InviteDialog(inv) { vm.answerInvite(inv, it) } }
        val message by vm.message.collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                vm.messageShown()
            }
        }
        if (samsungDialog) {
            AlertDialog(
                onDismissRequest = { samsungDialog = false },
                title = { Text("Samsung battery steps") },
                text = { Text(OnboardingStep.BATTERY.detail) },
                confirmButton = {
                    TextButton(onClick = { samsungDialog = false; launch(OnboardingIntents.appDetails(this)) }) {
                        Text("Open app settings")
                    }
                },
                dismissButton = { TextButton(onClick = { samsungDialog = false }) { Text("Close") } },
            )
        }
    }
}
