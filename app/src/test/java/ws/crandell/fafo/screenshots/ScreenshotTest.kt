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
import ws.crandell.fafo.arm.StatusScreen
import ws.crandell.fafo.core.ThreatLevel
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
import ws.crandell.fafo.ui.theme.ThemeMode
import ws.crandell.fafo.ui.screens.MailActions
import ws.crandell.fafo.ui.screens.MailSettings
import ws.crandell.fafo.ui.screens.MailTestUi
import ws.crandell.fafo.mail.MailProviderKind
import ws.crandell.fafo.mail.MailStatus
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import ws.crandell.fafo.ui.theme.DarkTokens
import ws.crandell.fafo.ui.theme.LightTokens
import ws.crandell.fafo.ui.theme.FafoBrand
import ws.crandell.fafo.ui.components.FafoWordmark
import ws.crandell.fafo.ui.components.FafoLockup
import ws.crandell.fafo.ui.components.WordmarkVariant
import ws.crandell.fafo.ui.components.ShieldMark
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asImageBitmap
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
        rule.setContent { FafoTheme(ThemeMode.DARK) { content() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(path(name))
    }

    /** The light theme (Settings > Display > Light, or the phone in light mode). */
    private fun shotLight(name: String, content: @Composable () -> Unit) {
        rule.setContent { FafoTheme(ThemeMode.LIGHT) { content() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(path(name))
    }

    /** Rendered at the user's font size [fontScale] (Settings > Display > Font size). */
    private fun shotFs(name: String, fontScale: Float, content: @Composable () -> Unit) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) { FafoTheme(ThemeMode.DARK) { content() } }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(path(name))
    }

    /** Dialogs live in their own window: capture the whole screen. */
    private fun screenShot(name: String, content: @Composable () -> Unit) {
        rule.setContent { FafoTheme(ThemeMode.DARK) { content() } }
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

    // ---- Light theme -------------------------------------------------------------------------------

    @Test fun s39_home_light_disarmed() = shotLight("39_home_light_disarmed") { Home(ready.copy(checklist = threeLeft)) }
    @Test fun s40_home_light_armed() = shotLight("40_home_light_armed") { Home(armed) }
    @Config(qualifiers = TALLER)
    @Test fun s41_settings_light() = shotLight("41_settings_light") { Settings(DpSettings(theme = "LIGHT")) }
    @Test fun s42_help_light() = shotLight("42_help_light") { Shell(Tab.HELP) { m -> HelpScreen(null, {}, {}, m) } }
    @Config(qualifiers = TALL)
    @Test fun s43_setup_light() = shotLight("43_setup_light") { Setup(partial) }
    @Test fun s45_home_arming_light() = shotLight("45_home_arming_light") {
        Home(ready.copy(alarmState = AlarmState.Arming(0, 5_000), arming = true, armingLeftS = 4, stateLabel = "Arming (4 s)"))
    }

    // ---- Status screen (traffic light), dark then light -------------------------------------------

    private val statusCases = listOf<Triple<String, AlarmState, ThreatLevel>>(
        Triple("46_status_disarmed", AlarmState.Disarmed, ThreatLevel.GREEN),
        Triple("47_status_arming", AlarmState.Arming(0, 5_000), ThreatLevel.GREEN),
        Triple("48_status_green", AlarmState.Armed(), ThreatLevel.GREEN),
        Triple("49_status_yellow", AlarmState.Armed(), ThreatLevel.YELLOW),
        Triple("50_status_orange_bumps", AlarmState.Armed(), ThreatLevel.ORANGE),
        Triple("51_status_prealarm_orange", AlarmState.PreAlarm("i", 0, 5_000), ThreatLevel.GREEN),
        Triple("52_status_red", AlarmState.Triggered("i", 0), ThreatLevel.GREEN),
    )

    private fun status(i: Int, light: Boolean) {
        val (name, st, threat) = statusCases[i]
        val content: @Composable () -> Unit = {
            StatusScreen(st, threat, now = 1_200, photosOn = true, animate = false, onDisarm = {}, onCancelArming = {})
        }
        if (light) shotLight(name.replaceFirst(Regex("^\\d+"), (53 + i).toString()) + "_light", content) else shot(name, content)
    }

    @Test fun s46() = status(0, false)
    @Test fun s47() = status(1, false)
    @Test fun s48() = status(2, false)
    @Test fun s49() = status(3, false)
    @Test fun s50() = status(4, false)
    @Test fun s51() = status(5, false)
    @Test fun s52() = status(6, false)
    @Test fun s53() = status(0, true)
    @Test fun s54() = status(1, true)
    @Test fun s55() = status(2, true)
    @Test fun s56() = status(3, true)
    @Test fun s57() = status(4, true)
    @Test fun s58() = status(5, true)
    @Test fun s59() = status(6, true)
    @Test fun s60_status_red_flash_low() = shot("60_status_red_flash_low") {
        StatusScreen(AlarmState.Triggered("i", 0), ThreatLevel.GREEN, now = 250, photosOn = true, animate = true)
    }
    @Config(qualifiers = "w891dp-h411dp-land-420dpi")
    @Test fun s60b_status_green_landscape() = shot("60b_status_green_landscape") {
        StatusScreen(AlarmState.Armed(), ThreatLevel.GREEN, now = 0, photosOn = false, animate = false, onDisarm = {})
    }

    // ---- Photo email, flash, theme, Setup and Help (round 3) ---------------------------------------

    private val mailCe = CeSettings(
        deviceLabel = "Kitchen phone", mailEnabled = true, mailRecipients = listOf("bill@example.org", "rob@example.org"),
        smtpHost = "smtp.example.org", smtpUser = "bill@example.org", mailSecret = "sealed",
    )
    private object NoMail : MailActions {
        override fun update(t: (CeSettings) -> CeSettings) {}
        override fun savePassword(password: String) {}
        override fun removePassword() {}
        override fun sendTest() {}
    }

    @Composable
    private fun MailShell(ce: CeSettings, providers: List<MailProviderKind>, test: MailTestUi, status: MailStatus = MailStatus()) =
        Shell(Tab.HOME, Pushed.SETTINGS) { m ->
            Column(m.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                MailSettings(ce, providers, test, status, NoMail, showOpenWifi = true)
            }
        }

    @Config(qualifiers = TALL)
    @Test fun s67_settings_email_smtp() = shot("67_settings_email_smtp") { MailShell(mailCe, listOf(MailProviderKind.SMTP), MailTestUi.Ok) }
    @Config(qualifiers = TALL)
    @Test fun s68_settings_email_play_gmail() = shot("68_settings_email_play_gmail") {
        MailShell(mailCe.copy(mailProvider = "GMAIL", gmailAccount = "bill@gmail.example"), listOf(MailProviderKind.SMTP, MailProviderKind.GMAIL), MailTestUi.Idle)
    }
    @Config(qualifiers = TALL)
    @Test fun s69_settings_email_test_error() = shot("69_settings_email_test_error") {
        MailShell(
            mailCe.copy(mailSecret = ""), listOf(MailProviderKind.SMTP),
            MailTestUi.Error("Server refused the username or app password (535). Gmail, Outlook and Yahoo need an app password."),
            MailStatus(pending = 3, lastError = "No connection to smtp.example.org:465"),
        )
    }
    @Test fun s70_settings_flash_theme() = shot("70_settings_flash_theme") { Settings(DpSettings(theme = "DARK")) }
    @Config(qualifiers = TALL)
    @Test fun s71_setup_email_wifi_steps() = shot("71_setup_email_wifi_steps") { Setup(allDone.copy(openWifiWanted = true)) }
    @Config(qualifiers = TALL)
    @Test fun s72_help_open_wifi() = shot("72_help_open_wifi") { Shell(Tab.HELP) { m -> HelpScreen("open-wifi", {}, {}, m) } }
    @Config(qualifiers = TALL)
    @Test fun s73_help_status() = shot("73_help_status") { Shell(Tab.HELP) { m -> HelpScreen("status", {}, {}, m) } }

    // ---- Brand ------------------------------------------------------------------------------------

    @Config(qualifiers = TALL)
    @Test fun s74_wordmark_variants() = shot("74_wordmark_variants") {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(DarkTokens.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FafoWordmark(height = 48.dp, variant = WordmarkVariant.DARK_WHITE)
                FafoLockup(wordmarkHeight = 32.dp, variant = WordmarkVariant.DARK_WHITE)
            }
            Column(Modifier.fillMaxWidth().background(LightTokens.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FafoWordmark(height = 48.dp, variant = WordmarkVariant.LIGHT)
                FafoLockup(wordmarkHeight = 32.dp, variant = WordmarkVariant.LIGHT)
            }
            Row(Modifier.fillMaxWidth().background(FafoBrand.Red).padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                FafoLockup(stacked = true, wordmarkHeight = 30.dp, variant = WordmarkVariant.MONO_WHITE)
            }
            Row(Modifier.fillMaxWidth().background(DarkTokens.bg).padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                FafoLockup(stacked = true, wordmarkHeight = 30.dp, variant = WordmarkVariant.DARK_WHITE)
            }
        }
    }

    @Test fun s75_shield_layers() = shot("75_shield_layers") {
        val stripes = listOf(FafoBrand.StatusGreen, FafoBrand.StatusYellow, FafoBrand.StatusOrange, FafoBrand.StatusRed)
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (eye in listOf(FafoBrand.White, FafoBrand.Red)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { stripes.forEach { ShieldMark(100.dp, stripes = it, eye = eye) } }
                Row(Modifier.background(LightTokens.bg), horizontalArrangement = Arrangement.spacedBy(8.dp)) { stripes.forEach { ShieldMark(100.dp, stripes = it, eye = eye) } }
            }
        }
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
        StatusScreen(AlarmState.PreAlarm("i", 0, 15_000), ThreatLevel.GREEN, now = 0, photosOn = true, onDisarm = {})
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

    // ---- HANDS OFF and PHOTO TAKEN (device feedback 2026-10-28) ------------------------------------

    /** A stand-in "photo" for the thumbnail: a dim room with a face-ish blob. */
    private fun fakeThumb(): androidx.compose.ui.graphics.ImageBitmap {
        val bmp = android.graphics.Bitmap.createBitmap(240, 320, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(58, 52, 48))
        val p = android.graphics.Paint().apply { color = android.graphics.Color.rgb(196, 160, 132); isAntiAlias = true }
        c.drawCircle(120f, 140f, 62f, p)
        p.color = android.graphics.Color.rgb(40, 36, 60)
        c.drawRect(40f, 230f, 200f, 320f, p)
        return bmp.asImageBitmap()
    }

    @Test fun s76_handsoff_yellow_bump() = shot("76_handsoff_yellow_bump") {
        StatusScreen(AlarmState.Armed(), ThreatLevel.YELLOW, now = 0, photosOn = false, animate = false, onDisarm = {}, handsOff = true)
    }
    @Test fun s77_handsoff_orange_pickup() = shot("77_handsoff_orange_pickup") {
        StatusScreen(AlarmState.Armed(), ThreatLevel.ORANGE, now = 0, photosOn = false, animate = false, onDisarm = {}, handsOff = true)
    }
    @Test fun s78_handsoff_prealarm_red_countdown() = shot("78_handsoff_prealarm_red_countdown") {
        StatusScreen(
            AlarmState.PreAlarm("i", 0, 10_000, ws.crandell.fafo.core.IncidentCause.MOTION), ThreatLevel.GREEN, now = 2_500,
            photosOn = true, animate = false, onDisarm = {}, handsOff = true,
            photo = ws.crandell.fafo.ui.status.photoTakenLook(0, 1, 2_500, reducedMotion = false),
        )
    }
    @Test fun s79_handsoff_photo_taken() = shot("79_handsoff_photo_taken") {
        StatusScreen(
            AlarmState.PreAlarm("i", 0, 10_000, ws.crandell.fafo.core.IncidentCause.MOTION), ThreatLevel.GREEN, now = 1_300,
            photosOn = true, animate = false, onDisarm = {}, handsOff = true,
            photo = ws.crandell.fafo.ui.status.photoTakenLook(800, 1, 1_300, reducedMotion = false), thumbnail = fakeThumb(),
        )
    }
    @Test fun s80_alarm_photo_taken_flash() = shot("80_alarm_photo_taken_flash") {
        StatusScreen(
            AlarmState.Triggered("i", 0), ThreatLevel.GREEN, now = 20_100, photosOn = true, animate = false, onDisarm = {},
            photo = ws.crandell.fafo.ui.status.photoTakenLook(20_000, 3, 20_100, reducedMotion = false), thumbnail = fakeThumb(),
        )
    }
    @Test fun s81_alarm_photo_taken_reduced_motion() = shot("81_alarm_photo_taken_reduced_motion") {
        StatusScreen(
            AlarmState.Triggered("i", 0), ThreatLevel.GREEN, now = 20_100, photosOn = true, animate = false, onDisarm = {},
            photo = ws.crandell.fafo.ui.status.photoTakenLook(20_000, 3, 20_100, reducedMotion = true), thumbnail = fakeThumb(),
        )
    }
    @Test fun s82_handsoff_orange_light() = shotLight("82_handsoff_orange_light") {
        StatusScreen(AlarmState.Armed(), ThreatLevel.ORANGE, now = 0, photosOn = false, animate = false, onDisarm = {}, handsOff = true)
    }
    @Config(qualifiers = NARROW)
    @Test fun s83_handsoff_photo_360dp_fs15() = shotFs("83_handsoff_photo_360dp_fs15", 1.5f) {
        StatusScreen(
            AlarmState.PreAlarm("i", 0, 10_000, ws.crandell.fafo.core.IncidentCause.MOTION), ThreatLevel.GREEN, now = 1_000,
            photosOn = true, animate = false, onDisarm = {}, handsOff = true,
            photo = ws.crandell.fafo.ui.status.photoTakenLook(800, 2, 1_000, reducedMotion = true), thumbnail = fakeThumb(),
        )
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
    @Test fun s18b_settings_bump_reset_60() = shot("18b_settings_bump_reset_60") { Settings(DpSettings(bumpResetS = 60)) }
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
