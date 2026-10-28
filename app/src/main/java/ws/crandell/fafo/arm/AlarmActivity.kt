package ws.crandell.fafo.arm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ws.crandell.fafo.core.AlarmStatusBus
import ws.crandell.fafo.core.stateLabel

/**
 * Full-screen alarm over the lock screen. directBootAware, showWhenLocked, turnScreenOn (manifest).
 * Opening it is a visible-app start, so it tells the service it may re-add the camera type (spec section 2).
 * It never stops the alarm itself: only the owner's unlock or Disarm after unlock does.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        tapService()
        setContent { AlarmContent() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tapService()
    }

    private fun tapService() {
        val i = Intent(this, ArmService::class.java).setAction(ArmService.ACTION_NOTIFICATION_TAP)
        try {
            ContextCompat.startForegroundService(this, i)
        } catch (_: IllegalStateException) {
            // Not allowed right now; the alarm keeps running regardless.
        }
    }

    companion object {
        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 10,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

@Composable
private fun AlarmContent() {
    val state by AlarmStatusBus.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().background(Color(0xFFB71C1C)).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stateLabel(state, System.currentTimeMillis()), color = Color.White, fontSize = 40.sp)
        Text("This phone is protected by FAFO Security.", color = Color.White, fontSize = 18.sp)
        Text("Only the owner can stop the alarm, by unlocking the phone.", color = Color.White, fontSize = 18.sp)
    }
}
