package ws.crandell.fafo.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ws.crandell.fafo.storage.CeStore
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
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }

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

    /** Wired to the siren and camera in later milestones. */
    fun runSelfTest() {
        vm.markOnboarding(CeStore.FLAG_SELF_TEST)
    }

    fun onArm() {}
    fun onDisarm() {}

    @Composable
    private fun AppRoot() {
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        var samsungDialog by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

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
                Screen.INCIDENTS -> PlaceholderScreen("Incidents", "No incidents yet.", m)
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
