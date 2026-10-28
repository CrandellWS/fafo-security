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
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import ws.crandell.fafo.camera.CameraXCapturer
import ws.crandell.fafo.camera.Lens
import ws.crandell.fafo.ui.screens.IncidentsScreen
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
import ws.crandell.fafo.ui.screens.PlaceholderScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { FafoTheme { AppRoot() } }
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
                Screen.SETTINGS -> SettingsScreen(dp, ce, vm::updateDp, vm::updateCe, m)
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
                Screen.TRUSTED -> PlaceholderScreen("Trusted devices", "Pairing arrives in a later build.", m)
                Screen.CIRCLES -> PlaceholderScreen("Circles", "Circles arrive in a later build.", m)
                Screen.INTERCOM -> PlaceholderScreen("Intercom", "Intercom arrives in a later build.", m)
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
