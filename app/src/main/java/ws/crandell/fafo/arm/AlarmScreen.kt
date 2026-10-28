package ws.crandell.fafo.arm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.ui.components.AutoFitText
import ws.crandell.fafo.ui.components.FafoWordmark
import ws.crandell.fafo.ui.components.ShieldLogo
import ws.crandell.fafo.ui.theme.FafoBrand
import ws.crandell.fafo.ui.theme.FafoColors
import ws.crandell.fafo.ui.theme.FafoType

/** Whole seconds left in a pre-alarm, rounded up (same math as `stateLabel`). */
fun secondsLeft(s: AlarmState.PreAlarm, now: Long): Long = ((s.endsAt - now).coerceAtLeast(0) + 999) / 1000

/**
 * What [AlarmActivity] draws. Pure (state in, nothing out) so it can be screenshot-tested; the activity
 * keeps all the logic. Pre-alarm: dark field with an amber countdown ring. Alarm: the full red field with
 * a slow 1 Hz pulse (no strobe), the shield and "ALARM".
 */
@Composable
fun AlarmScreen(state: AlarmState, now: Long, photosOn: Boolean, animate: Boolean = true) {
    when (state) {
        is AlarmState.PreAlarm -> PreAlarm(state, now)
        is AlarmState.Triggered, is AlarmState.IncidentPersisted -> Triggered(photosOn, if (animate) now else null)
        // Shown only for a moment before the activity closes itself (or when opened with no alarm).
        else -> Box(Modifier.fillMaxSize().background(FafoColors.Ink), contentAlignment = Alignment.Center) {
            ShieldLogo(120.dp)
        }
    }
}

@Composable
private fun PreAlarm(s: AlarmState.PreAlarm, now: Long) {
    val left = secondsLeft(s, now)
    val total = (s.endsAt - s.startedAt).coerceAtLeast(1)
    val fraction = ((s.endsAt - now).coerceIn(0, total)).toFloat() / total
    Column(
        Modifier.fillMaxSize().background(FafoColors.Ink).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShieldLogo(112.dp)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            val track = FafoColors.Surface2
            val ring = FafoColors.Amber
            Canvas(Modifier.fillMaxSize()) {
                val w = 16.dp.toPx()
                val inset = w / 2
                val arc = Size(size.width - w, size.height - w)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
                drawArc(ring, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            AutoFitText(
                "$left", style = FafoType.countdown, color = FafoColors.White, sample = "88",
                modifier = Modifier.testTag("countdown").semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(28.dp))
        AutoFitText("PLUG ME BACK IN", style = MaterialTheme.typography.displaySmall, color = FafoColors.Amber, textAlign = TextAlign.Center, minFontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "The alarm sounds in $left ${if (left == 1L) "second" else "seconds"}.\nPlug the charger back in, or unlock.",
            style = MaterialTheme.typography.bodyLarge, color = FafoColors.White, textAlign = TextAlign.Center,
        )
    }
}

/**
 * Background of the alarm at [now]: a slow 1 Hz pulse between Red and RedDeep (a triangle wave, 500 ms each
 * way), far below the 3 flashes/s seizure threshold. Driven by the activity's clock rather than an infinite
 * animation, so it costs nothing when the screen is off and tests can idle the main looper.
 */
fun alarmPulse(now: Long): Color {
    val phase = (now % 1000L).toFloat() / 1000f
    val t = if (phase < 0.5f) phase * 2f else 2f - phase * 2f
    return lerp(FafoColors.Red, FafoColors.RedDeep, t)
}

@Composable
private fun Triggered(photosOn: Boolean, now: Long?) {
    val bg = if (now != null) alarmPulse(now) else FafoColors.Red
    Column(
        Modifier.fillMaxSize().background(bg).safeDrawingPadding().padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShieldLogo(240.dp, haloOnRed = true)
        Spacer(Modifier.height(20.dp))
        AutoFitText(
            "ALARM", style = FafoType.stateDisplay.copy(fontSize = 88.sp, lineHeight = 92.sp), color = FafoBrand.White,
            modifier = Modifier.testTag("alarmWord").semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Spacer(Modifier.height(12.dp))
        Text("This phone is protected by", style = MaterialTheme.typography.titleLarge, color = FafoBrand.White, textAlign = TextAlign.Center)
        FafoWordmark(height = 30.dp, onRed = true, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Only the owner can stop the alarm, by unlocking this phone.",
            style = MaterialTheme.typography.bodyLarge, color = FafoBrand.White.copy(alpha = 0.92f), textAlign = TextAlign.Center,
        )
        if (photosOn) {
            HorizontalDivider(Modifier.fillMaxWidth().padding(vertical = 18.dp), color = FafoBrand.White.copy(alpha = 0.35f))
            Text(
                "Photos are being taken.", style = MaterialTheme.typography.titleMedium, color = FafoBrand.White,
                modifier = Modifier.testTag("photosLine"),
            )
        }
    }
}
