package ws.crandell.fafo.quick

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import ws.crandell.fafo.arm.ArmDeps
import ws.crandell.fafo.arm.Notifications
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.IncidentCause
import ws.crandell.fafo.siren.FakeTorch

/** Round 5, spec F13: lock-screen Flashlight / Talk actions and the FAFO Light / FAFO Talk tiles. */
@RunWith(AndroidJUnit4::class)
class ShortcutsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val torch = FakeTorch()

    @Before fun setUp() {
        ArmDeps.reset()
        ArmDeps.torch = { torch }
        Notifications.ensureChannels(app)
    }

    @After fun tearDown() = ArmDeps.reset()

    private fun armed() = Notifications.forState(app, AlarmState.Armed(), photosPaused = false, armedAfterBootNoCamera = false)

    @Test fun armedNotificationHasDisarmFlashlightAndTalk() {
        val actions = armed().actions.associateBy { it.title.toString() }
        assertThat(actions.keys).containsExactly("Disarm", "Flashlight", "Talk").inOrder()
        // Disarm stays behind the screen lock; the shortcuts work from the lock screen.
        assertThat(actions.getValue("Disarm").isAuthenticationRequired).isTrue()
        assertThat(actions.getValue("Flashlight").isAuthenticationRequired).isFalse()
        assertThat(actions.getValue("Talk").isAuthenticationRequired).isFalse()

        val light = shadowOf(actions.getValue("Flashlight").actionIntent)
        assertThat(light.isBroadcastIntent).isTrue()
        assertThat(light.savedIntent.component?.className).isEqualTo(TorchToggleReceiver::class.java.name)
        val talk = shadowOf(actions.getValue("Talk").actionIntent)
        assertThat(talk.isActivityIntent).isTrue()
        assertThat(talk.savedIntent.component?.className).isEqualTo(TalkActivity::class.java.name)
    }

    @Test fun noFlashlightActionWithoutAFlash() {
        torch.available = false
        assertThat(armed().actions.map { it.title.toString() }).containsExactly("Disarm", "Talk").inOrder()
    }

    @Test fun shortcutsOnlyWhileCalmlyArmed() {
        for (s in listOf(
            AlarmState.Arming(0, 5_000),
            AlarmState.PreAlarm("i", 0, 5_000, IncidentCause.UNPLUG),
            AlarmState.Triggered("i", 0),
            AlarmState.IncidentPersisted("i", 0),
        )) {
            val titles = Notifications.forState(app, s, false, false).actions.map { it.title.toString() }
            assertThat(titles).containsNoneOf("Flashlight", "Talk")
        }
    }

    @Test fun flashlightTogglesAndReportsFailures() {
        assertThat(Flashlight.toggle(app)).isTrue()
        assertThat(torch.on).isTrue()
        assertThat(Flashlight.on.value).isTrue()
        assertThat(Flashlight.toggle(app)).isFalse()
        assertThat(torch.on).isFalse()
        torch.busy = true
        assertThat(Flashlight.toggle(app)).isNull()
        assertThat(Flashlight.on.value).isFalse()
        torch.busy = false
        torch.available = false
        assertThat(Flashlight.toggle(app)).isNull()
    }

    @Test fun notificationActionTogglesTheTorch() {
        TorchToggleReceiver().onReceive(app, Intent(app, TorchToggleReceiver::class.java).setAction(TorchToggleReceiver.ACTION_TOGGLE))
        assertThat(torch.on).isTrue()
        // Anything else is ignored.
        TorchToggleReceiver().onReceive(app, Intent("other"))
        assertThat(torch.on).isTrue()
    }

    @Test fun lightTileClickToggles() {
        val tile = Robolectric.buildService(LightTileService::class.java).create().get()
        tile.onClick()
        assertThat(torch.on).isTrue()
        tile.onClick()
        assertThat(torch.on).isFalse()
    }

    @Test fun tileLooks() {
        assertThat(TileLooks.light(available = true, on = true)).isEqualTo(TileLook(Tile.STATE_ACTIVE, "On"))
        assertThat(TileLooks.light(available = true, on = false)).isEqualTo(TileLook(Tile.STATE_INACTIVE, "Off"))
        assertThat(TileLooks.light(available = false, on = true).state).isEqualTo(Tile.STATE_UNAVAILABLE)
        assertThat(TileLooks.talk.state).isEqualTo(Tile.STATE_INACTIVE)
    }

    @Test fun tilesAreBoundOnlyBySystemUi() {
        for (cls in listOf(LightTileService::class.java, TalkTileService::class.java)) {
            @Suppress("DEPRECATION")
            val si = app.packageManager.getServiceInfo(ComponentName(app, cls), PackageManager.GET_META_DATA)
            assertThat(si.exported).isTrue()
            assertThat(si.permission).isEqualTo("android.permission.BIND_QUICK_SETTINGS_TILE")
            assertThat(si.foregroundServiceType).isEqualTo(0)
            assertThat(si.directBootAware).isTrue()
            val found = app.packageManager.queryIntentServices(Intent(TileService.ACTION_QS_TILE).setPackage(app.packageName), 0)
            assertThat(found.map { it.serviceInfo.name }).contains(cls.name)
        }
        @Suppress("DEPRECATION")
        val light = app.packageManager.getServiceInfo(ComponentName(app, LightTileService::class.java), PackageManager.GET_META_DATA)
        assertThat(light.metaData.getBoolean(TileService.META_DATA_TOGGLEABLE_TILE)).isTrue()
    }

    @Test fun shortcutTargetsAreNotExported() {
        @Suppress("DEPRECATION")
        val talk = app.packageManager.getActivityInfo(ComponentName(app, TalkActivity::class.java), 0)
        assertThat(talk.exported).isFalse()
        assertThat(talk.directBootAware).isTrue()
        @Suppress("DEPRECATION")
        val receiver = app.packageManager.getReceiverInfo(ComponentName(app, TorchToggleReceiver::class.java), 0)
        assertThat(receiver.exported).isFalse()
        assertThat(receiver.directBootAware).isTrue()
    }
}
