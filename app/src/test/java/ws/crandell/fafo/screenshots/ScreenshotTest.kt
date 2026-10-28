package ws.crandell.fafo.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ws.crandell.fafo.arm.AlarmScreen
import ws.crandell.fafo.camera.IncidentSidecar
import ws.crandell.fafo.camera.PhotoMeta
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.Member
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.intercom.IntercomUi
import ws.crandell.fafo.net.Command
import ws.crandell.fafo.net.IncomingInvite
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.storage.CeSettings
import ws.crandell.fafo.storage.DpSettings
import ws.crandell.fafo.ui.AppShell
import ws.crandell.fafo.ui.OnboardingStatus
import ws.crandell.fafo.ui.PairingState
import ws.crandell.fafo.ui.Pushed
import ws.crandell.fafo.ui.RemoteCommandScreen
import ws.crandell.fafo.ui.Tab
import ws.crandell.fafo.ui.screens.CirclesActions
import ws.crandell.fafo.ui.screens.CirclesScreen
import ws.crandell.fafo.ui.screens.DevicesPane
import ws.crandell.fafo.ui.screens.DevicesSwitcher
import ws.crandell.fafo.ui.screens.HelpScreen
import ws.crandell.fafo.ui.screens.HomeScreen
import ws.crandell.fafo.ui.screens.HomeUiState
import ws.crandell.fafo.ui.screens.IncidentsScreen
import ws.crandell.fafo.ui.screens.IntercomActions
import ws.crandell.fafo.ui.screens.IntercomScreen
import ws.crandell.fafo.ui.screens.InviteDialog
import ws.crandell.fafo.ui.screens.OnboardingScreen
import ws.crandell.fafo.ui.screens.SettingsScreen
import ws.crandell.fafo.ui.screens.TrustedScreen
import ws.crandell.fafo.ui.theme.FafoTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ws.crandell.fafo.ui.components.ActionKind
import ws.crandell.fafo.ui.components.FafoTopBar
import ws.crandell.fafo.ui.components.PrimaryActionButton
import ws.crandell.fafo.ui.components.StateKind
import ws.crandell.fafo.ui.components.StatusHero
import ws.crandell.fafo.ui.OnboardingStep
import java.io.File

/** Galaxy A17: 1080 x 2340 px; 411 x 891 dp at 420 dpi renders 1079 x 2339. */
private const val PHONE = "w411dp-h891dp-port-420dpi"
/** Long screens (Settings, Setup, Help topics) rendered taller so the whole page shows. */
private const val TALL = "w411dp-h1600dp-port-420dpi"
/** Settings is the longest page. */
private const val TALLER = "w411dp-h2100dp-port-420dpi"
/** A narrow phone (Bill's DISARMED wrap case at a large font size). */
private const val NARROW = "w360dp-h780dp-port-420dpi"
/** Display size at its largest on the A17: about 320dp wide. */
private const val NARROWEST = "w320dp-h700dp-port-420dpi"

/**
 * Every screen in its key states at Galaxy A17 size (411 x 891 dp at 420 dpi = 1079 x 2339 px). Fixed fake
 * data, no ViewModel, no Keystore. In CI these only have to render; `./gradlew recordRoborazziDebug` writes
 * the PNGs to app/build/outputs/roborazzi/ for review (nothing is compared, so font hinting can't fail CI).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE)
@OptIn(ExperimentalRoborazziApi::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun path(name: String) = "build/outputs/roborazzi/$name.png"

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { FafoTheme { content() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(path(name))
    }

    /** Rendered at the user's font size [fontScale] (Settings > Display > Font size). */
    private fun shotFs(name: String, fontScale: Float, content: @Composable () -> Unit) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) { FafoTheme { content() } }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(path(name))
    }

    /** Dialogs live in their own window: capture the whole screen. */
    private fun screenShot(name: String, content: @Composable () -> Unit) {
        rule.setContent { FafoTheme { content() } }
        rule.waitForIdle()
        captureScreenRoboImage(path(name))
    }

    @Composable
    private fun Shell(
        tab: Tab,
        pushed: Pushed? = null,
        badge: Int = 0,
        message: String? = null,
        title: String? = null,
        content: @Composable (Modifier) -> Unit,
    ) {
        val snackbar = remember { SnackbarHostState() }
        if (message != null) LaunchedEffect(Unit) { snackbar.showSnackbar(message, duration = SnackbarDuration.Indefinite) }
        AppShell(tab, pushed, badge, {}, {}, {}, Modifier.fillMaxSize(), snackbar, title, content)
    }

    @Composable
    private fun Home(s: HomeUiState, message: String? = null) =
        Shell(Tab.HOME, badge = s.checklist.size, message = message) { m -> HomeScreen(s, {}, {}, {}, {}, m) }

    // ---- Home -------------------------------------------------------------------------------------

    @Test fun s01_home_disarmed_ready() = shot("01_home_disarmed_ready") { Home(ready.copy(checklist = threeLeft)) }
    @Test fun s02_home_disarmed_unplugged() = shot("02_home_disarmed_unplugged") { Home(ready.copy(plugged = false, checklist = threeLeft)) }
    @Test fun s03_home_no_lock() = shot("03_home_no_lock") { Home(ready.copy(secureLock = false, checklist = listOf(OnboardingStep.SECURE_LOCK) + threeLeft)) }
    @Test fun s04_home_setup_done() = shot("04_home_setup_done") { Home(ready) }
    @Test fun s05_home_arming_confirm() = shot("05_home_arming_confirm") { Home(ready.copy(confirming = true)) }
    @Test fun s06_home_arm_cancelled() = shot("06_home_arm_cancelled") { Home(ready, message = "Not armed. Confirmation cancelled.") }
    @Test fun s07_home_armed() = shot("07_home_armed") { Home(armed) }
    @Test fun s08_home_armed_waiting_power() = shot("08_home_armed_waiting_power") {
        Home(armed.copy(alarmState = AlarmState.Armed(awaitingPower = true), awaitingPower = true, plugged = false, photosOff = true, stateLabel = "Armed, waiting for power"))
    }
    @Test fun s09_home_prealarm() = shot("09_home_prealarm") {
        Home(armed.copy(alarmState = AlarmState.PreAlarm("i", 0, 5_000), armed = false, alarming = true, plugged = false, stateLabel = "Plug me back in (4 s)"))
    }
    @Test fun s10_home_alarm() = shot("10_home_alarm") {
        Home(armed.copy(alarmState = AlarmState.Triggered("i", 0), armed = false, alarming = true, plugged = false, stateLabel = "ALARM"))
    }

    @Test fun s44_home_arming_dark() = shot("44_home_arming_dark") {
        Home(ready.copy(alarmState = AlarmState.Arming(0, 5_000), arming = true, armingLeftS = 4, stateLabel = "Arming (4 s)"))
    }

    // ---- Large font and narrow width (AutoFitText) ------------------------------------------------

    @Test fun s61_home_disarmed_fs13() = shotFs("61_home_disarmed_fs13", 1.3f) { Home(ready.copy(checklist = threeLeft)) }
    @Test fun s62_home_disarmed_fs15() = shotFs("62_home_disarmed_fs15", 1.5f) { Home(ready.copy(checklist = threeLeft)) }
    @Config(qualifiers = NARROW)
    @Test fun s63_home_disarmed_360dp_fs15() = shotFs("63_home_disarmed_360dp_fs15", 1.5f) { Home(ready.copy(checklist = threeLeft)) }
    @Config(qualifiers = NARROWEST)
    @Test fun s63b_home_disarmed_320dp_fs15() = shotFs("63b_home_disarmed_320dp_fs15", 1.5f) { Home(ready.copy(checklist = threeLeft)) }
    @Config(qualifiers = NARROW)
    @Test fun s64_status_prealarm_360dp_fs15() = shotFs("64_status_prealarm_360dp_fs15", 1.5f) {
        AlarmScreen(AlarmState.PreAlarm("i", 0, 15_000), now = 0, photosOn = true)
    }
    @Config(qualifiers = NARROW)
    @Test fun s65_buttons_360dp_fs15() = shotFs("65_buttons_360dp_fs15", 1.5f) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryActionButton("Arm", ActionKind.ARM, {})
            PrimaryActionButton("Disarm", ActionKind.DISARM, {})
            PrimaryActionButton("Cancel arming", ActionKind.DISARM, {})
            PrimaryActionButton("Send test email", ActionKind.ARM, {})
            PrimaryActionButton("Arm", ActionKind.ARM, {}, busy = true)
            PrimaryActionButton("Set a screen lock first", ActionKind.ARM, {}, enabled = false)
        }
    }
    @Config(qualifiers = NARROW)
    @Test fun s66_topbar_titles_360dp_fs15() = shotFs("66_topbar_titles_360dp_fs15", 1.5f) {
        Column {
            FafoTopBar(null)
            FafoTopBar("Settings", onBack = {}, onSettings = {})
            FafoTopBar("Paired phones and circles", onBack = {}, onSettings = {})
            StatusHero(StateKind.DISARMED, "Disarmed")
            StatusHero(StateKind.PRE_ALARM, "Plug me back in")
        }
    }

    // ---- Alarm screen (AlarmActivity) -------------------------------------------------------------

    @Test fun s11_alarm_prealarm_5s() = shot("11_alarm_prealarm_5s") { AlarmScreen(AlarmState.PreAlarm("i", 0, 5_000), now = 0, photosOn = true) }
    @Test fun s12_alarm_prealarm_1s() = shot("12_alarm_prealarm_1s") { AlarmScreen(AlarmState.PreAlarm("i", 0, 5_000), now = 4_200, photosOn = true) }
    @Test fun s13_alarm_triggered() = shot("13_alarm_triggered") { AlarmScreen(AlarmState.Triggered("i", 0), now = 0, photosOn = true, animate = false) }
    @Test fun s14_alarm_after_reboot() = shot("14_alarm_after_reboot") { AlarmScreen(AlarmState.IncidentPersisted("i", 0), now = 0, photosOn = false, animate = false) }

    // ---- Settings ---------------------------------------------------------------------------------

    @Composable
    private fun Settings(dp: DpSettings = DpSettings(), ce: CeSettings? = CeSettings(deviceLabel = "Kitchen phone")) =
        Shell(Tab.HOME, Pushed.SETTINGS) { m -> SettingsScreen(dp, ce, {}, {}, m, version = "0.1.0") }

    @Test fun s15_settings_top() = shot("15_settings_top") { Settings() }
    @Config(qualifiers = TALLER)
    @Test fun s16_settings_full() = shot("16_settings_full") { Settings() }
    @Config(qualifiers = TALLER)
    @Test fun s17_settings_grace_off() = shot("17_settings_grace_off") { Settings(DpSettings(unlockGraceS = 0)) }
    @Config(qualifiers = TALLER)
    @Test fun s18_settings_before_unlock() = shot("18_settings_before_unlock") { Settings(ce = null) }

    // ---- Setup and Help ---------------------------------------------------------------------------

    @Composable
    private fun Setup(status: OnboardingStatus, samsung: Boolean = false) =
        Shell(Tab.SETUP, badge = status.missing.size) { m -> OnboardingScreen(status, {}, {}, m, samsungExpandedInitially = samsung) }

    @Config(qualifiers = TALL)
    @Test fun s19_setup_fresh() = shot("19_setup_fresh") { Setup(OnboardingStatus(alarmChannel = true)) }
    @Config(qualifiers = TALL)
    @Test fun s20_setup_partial() = shot("20_setup_partial") { Setup(partial, samsung = true) }
    @Test fun s21_setup_all_done() = shot("21_setup_all_done") { Setup(allDone) }
    @Test fun s22_help_index() = shot("22_help_index") { Shell(Tab.HELP) { m -> HelpScreen(null, {}, {}, m) } }
    @Config(qualifiers = TALL)
    @Test fun s23_help_samsung() = shot("23_help_samsung") { Shell(Tab.HELP) { m -> HelpScreen("samsung", {}, {}, m) } }
    @Test fun s24_help_grace() = shot("24_help_grace") { Shell(Tab.HELP) { m -> HelpScreen("grace", {}, {}, m) } }

    // ---- Devices ----------------------------------------------------------------------------------

    @Composable
    private fun Devices(pane: DevicesPane, content: @Composable (Modifier) -> Unit) = Shell(Tab.DEVICES) { m ->
        Column(m) {
            DevicesSwitcher(pane, {})
            content(Modifier.fillMaxSize())
        }
    }

    @Config(qualifiers = TALL)
    @Test fun s25_devices_circles() = shot("25_devices_circles") {
        Devices(DevicesPane.CIRCLES) { m -> CirclesScreen(circles, devices, setOf(PIXEL), "Kitchen phone", NoCircles, m) }
    }
    @Test fun s26_devices_paired() = shot("26_devices_paired") { Devices(DevicesPane.PAIRED) { m -> Trusted(PairingState.Idle, m) } }
    @Test fun s27_devices_nearby_needed() = shot("27_devices_nearby_needed") {
        Devices(DevicesPane.PAIRED) { m -> TrustedScreen(emptyList(), PairingState.Idle, false, {}, {}, {}, {}, {}, {}, {}, m) }
    }
    @Test fun s28_pairing_working() = screenShot("28_pairing_working") {
        Devices(DevicesPane.PAIRED) { m -> Trusted(PairingState.Working("Looking for your other phone. Keep both phones close."), m) }
    }
    @Test fun s29_pairing_code() = screenShot("29_pairing_code") { Devices(DevicesPane.PAIRED) { m -> Trusted(PairingState.ConfirmCode("482915", "Bill's Pixel"), m) } }
    @Test fun s30_pairing_done() = screenShot("30_pairing_done") {
        Devices(DevicesPane.PAIRED) { m -> Trusted(PairingState.Done("Bill's Pixel is now a trusted device in Home."), m) }
    }
    @Test fun s31_invite_dialog() = screenShot("31_invite_dialog") {
        Devices(DevicesPane.CIRCLES) { m -> CirclesScreen(circles, devices, setOf(PIXEL), "Kitchen phone", NoCircles, m) }
        InviteDialog(IncomingInvite(1, PIXEL, "Bill's Pixel", "home", "Home", setOf(Permission.ALERTS, Permission.RESET, Permission.INTERCOM), 0)) {}
    }

    @Composable
    private fun Trusted(state: PairingState, modifier: Modifier) =
        TrustedScreen(devices, state, true, {}, {}, {}, {}, {}, {}, {}, modifier)

    // ---- Intercom ---------------------------------------------------------------------------------

    @Composable
    private fun Intercom(ui: IntercomUi, alarming: Boolean = false) =
        Shell(Tab.INTERCOM) { m -> IntercomScreen(ui, circles, setOf(PIXEL), alarming, NoIntercom, m) }

    @Test fun s32_intercom_idle() = shot("32_intercom_idle") { Intercom(IntercomUi()) }
    @Test fun s33_intercom_talking() = shot("33_intercom_talking") { Intercom(IntercomUi(talking = true, listeners = listOf("Bill's Pixel"))) }
    @Config(qualifiers = TALL)
    @Test fun s34_intercom_room_on() = shot("34_intercom_room_on") { Intercom(IntercomUi(roomCircles = setOf("home"), listeners = listOf("Back room"))) }
    @Test fun s35_intercom_alarm() = shot("35_intercom_alarm") { Intercom(IntercomUi(), alarming = true) }

    // ---- Incidents and remote command -------------------------------------------------------------

    @Test fun s36_incidents_list() = shot("36_incidents_list") {
        Shell(Tab.HOME, Pushed.INCIDENTS) { m -> IncidentsScreen(incidents, null, null, { _, n -> File("build/no-photos/$n") }, {}, {}, { _, _ -> }, {}, {}, m) }
    }
    @Test fun s37_incidents_empty() = shot("37_incidents_empty") {
        Shell(Tab.HOME, Pushed.INCIDENTS) { m -> IncidentsScreen(emptyList(), null, null, { _, n -> File("build/no-photos/$n") }, {}, {}, { _, _ -> }, {}, {}, m) }
    }
    @Test fun s38_remote_command() = shot("38_remote_command") {
        RemoteCommandScreen(Command.RESET, "Back room", "Confirm with your screen lock", step = 2, done = false, ok = false) {}
    }

    private companion object {
        const val PIXEL = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        const val BACK = "0123456789abcdef0123456789abcdef"
        const val T0 = 1_792_000_000_000L // a fixed date in 2026

        val threeLeft = listOf(OnboardingStep.FULL_SCREEN, OnboardingStep.BATTERY, OnboardingStep.SELF_TEST)
        val ready = HomeUiState(plugged = true, secureLock = true, incidents = 2, paired = 2)
        val armed = ready.copy(alarmState = AlarmState.Armed(), armed = true, stateLabel = "Armed")

        val allDone = OnboardingStatus(true, true, true, true, true, true, true, true, true, true)
        val partial = allDone.copy(fullScreen = false, battery = false, selfTestDone = false)

        val devices = listOf(
            TrustedDevice(PIXEL, "", "", "Bill's Pixel", T0),
            TrustedDevice(BACK, "", "", "Back room", T0 + 86_400_000L * 3),
        )
        val circles = listOf(
            Circle(
                "home", "Home", Permission.entries.toSet(),
                listOf(
                    Member(PIXEL, "Bill's Pixel", setOf(Permission.ALERTS, Permission.RESET, Permission.DISARM, Permission.INTERCOM)),
                    Member(BACK, "Back room", setOf(Permission.INTERCOM)),
                ),
                builtIn = true,
            ),
            Circle("work", "Work", setOf(Permission.INTERCOM, Permission.RESET), listOf(Member(BACK, "Register", setOf(Permission.INTERCOM, Permission.RESET)))),
        )
        val incidents = listOf(
            IncidentSidecar("b", T0 + 3_600_000L, T0 + 3_700_000L, "RESET_REMOTE", "Bill's Pixel", List(5) { PhotoMeta("p$it.jpg", T0, "FRONT", 80, false, false, 1000) }),
            IncidentSidecar("a", T0, T0 + 60_000L, "UNLOCKED", null, List(2) { PhotoMeta("q$it.jpg", T0, "FRONT", 80, false, false, 1000) }),
        )

        val NoCircles = object : CirclesActions {
            override fun setDeviceLabel(label: String) {}
            override fun createCircle(name: String) {}
            override fun setPermissions(circleId: String, permissions: Set<Permission>) {}
            override fun renameMember(circleId: String, fingerprint: String, label: String) {}
            override fun removeMember(circleId: String, fingerprint: String) {}
            override fun removeCircle(circleId: String) {}
            override fun invite(circleId: String, fingerprint: String) {}
            override fun pairInto(circleId: String) {}
        }
        val NoIntercom = object : IntercomActions {
            override fun setRoomMode(on: Boolean, circles: Set<String>) {}
            override fun listen(fingerprint: String, circleId: String) {}
            override fun stopListening() {}
            override fun talkStart(targets: List<String>, circleId: String) {}
            override fun talkStop() {}
        }
    }
}
