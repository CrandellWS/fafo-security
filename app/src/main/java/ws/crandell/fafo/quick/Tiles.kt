package ws.crandell.fafo.quick

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** What a Quick Settings tile shows (pure, unit tested). */
data class TileLook(val state: Int, val subtitle: String)

object TileLooks {
    fun light(available: Boolean, on: Boolean): TileLook = when {
        !available -> TileLook(Tile.STATE_UNAVAILABLE, "No flash")
        on -> TileLook(Tile.STATE_ACTIVE, "On")
        else -> TileLook(Tile.STATE_INACTIVE, "Off")
    }

    val talk = TileLook(Tile.STATE_INACTIVE, "Push-to-talk")
}

private fun TileService.show(look: TileLook) {
    val t = qsTile ?: return
    t.state = look.state
    t.subtitle = look.subtitle
    t.updateTile()
}

/**
 * Quick Settings tile "FAFO Light" (round 5, spec F13): the plain flashlight. Works on the lock screen;
 * it never touches the alarm, so it needs no screen lock.
 */
class LightTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        Flashlight.watch(this)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        s.launch { Flashlight.on.collect { show(TileLooks.light(Flashlight.available(this@LightTileService), it)) } }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        Flashlight.toggle(this)
        show(TileLooks.light(Flashlight.available(this), Flashlight.on.value))
    }
}

/**
 * Quick Settings tile "FAFO Talk" (round 5, spec F13): opens the push-to-talk screen ([TalkActivity]),
 * which also shows over the lock screen. Push-to-talk only: nothing it opens can disarm or change settings.
 */
class TalkTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        show(TileLooks.talk)
    }

    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(TalkActivity.pendingIntent(this))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(TalkActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
