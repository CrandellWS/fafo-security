package ws.crandell.fafo.quick

import android.Manifest
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.core.isAlarming
import ws.crandell.fafo.intercom.Intercom
import ws.crandell.fafo.net.android.PeerNetwork
import ws.crandell.fafo.storage.DpStore
import ws.crandell.fafo.system.DeviceStatus
import ws.crandell.fafo.trust.TrustGraph
import ws.crandell.fafo.ui.MainActivity
import ws.crandell.fafo.ui.screens.TalkActions
import ws.crandell.fafo.ui.screens.TalkScreen
import ws.crandell.fafo.ui.theme.FafoTheme
import ws.crandell.fafo.ui.theme.ThemeMode
import ws.crandell.fafo.ui.theme.applySystemBars
import ws.crandell.fafo.ui.theme.isDark

/**
 * Talk (round 5, spec F13): push-to-talk from the armed notification's Talk action or the FAFO Talk tile.
 * showWhenLocked, so it opens over the lock screen; it offers push-to-talk only (AC10.4: the mic is open only
 * while the button is held in this visible screen). No Disarm, settings, room mode or listening here: "Open
 * FAFO" goes through Android's screen lock first. directBootAware: before the first unlock it only says so
 * and never touches credential-protected storage. It closes when the screen turns off.
 */
class TalkActivity : ComponentActivity() {
    private val ceLocked = mutableStateOf(true)
    private val micAllowed = mutableStateOf(false)
    private var networkHeld = false
    private var talkJob: Job? = null

    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        refresh()
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            val theme by remember { DpStore.get(this).settings }.collectAsStateWithLifecycle(null)
            val mode = ThemeMode.parse(theme?.theme)
            val dark = mode.isDark()
            LaunchedEffect(dark) { applySystemBars(this@TalkActivity, dark) }
            val locked = ceLocked.value
            // Circles live in credential-protected storage: never opened before the first unlock.
            val circlesFlow = remember(locked) { if (locked) MutableStateFlow(emptyList<Circle>()) else TrustGraph.circles(this).circles }
            val circles by circlesFlow.collectAsStateWithLifecycle()
            val ui by Intercom.ui.collectAsStateWithLifecycle()
            val reachable by PeerNetwork.reachable.collectAsStateWithLifecycle()
            val state by AlarmStatusBus.state.collectAsStateWithLifecycle()
            FafoTheme(mode) {
                TalkScreen(ui, circles, reachable, state.isAlarming, locked, micAllowed.value, actions)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refresh()
        // AC10.4: receivers must be reachable; while this screen is open this phone is too.
        if (!ceLocked.value && !networkHeld) networkHeld = PeerNetwork.acquire(this, PeerNetwork.OWNER_TALK) != null
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onStop() {
        stopTalking()
        if (networkHeld) PeerNetwork.release(PeerNetwork.OWNER_TALK)
        networkHeld = false
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    private fun refresh() {
        ceLocked.value = !DeviceStatus.isCeUnlocked(this)
        micAllowed.value = DeviceStatus.hasPermission(this, Manifest.permission.RECORD_AUDIO)
    }

    private fun stopTalking() {
        if (talkJob == null && !Intercom.ui.value.talking) return
        talkJob?.cancel()
        talkJob = null
        Intercom.stopTalk()
    }

    /** Runs [block] once the keyguard is gone: Android asks for the screen lock first when the phone is locked. */
    private fun afterUnlock(block: () -> Unit) {
        val km = getSystemService(KeyguardManager::class.java)
        if (km?.isKeyguardLocked == true) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = block()
            })
        } else {
            block()
        }
    }

    private val actions = object : TalkActions {
        override fun talkStart(targets: List<String>, circleId: String) {
            if (!micAllowed.value) return allowMic()
            talkJob = lifecycleScope.launch {
                Intercom.startTalk(this@TalkActivity, targets, circleId)?.let {
                    Toast.makeText(this@TalkActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun talkStop() = stopTalking()

        override fun allowMic() = afterUnlock { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

        override fun openApp() = afterUnlock {
            startActivity(Intent(this@TalkActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }

        override fun close() = finish()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, TalkActivity::class.java)

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 21,
            intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
