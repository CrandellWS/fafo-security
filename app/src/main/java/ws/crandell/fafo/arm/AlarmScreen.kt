package ws.crandell.fafo.arm

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.ThreatLevel
import ws.crandell.fafo.ui.components.ActionKind
import ws.crandell.fafo.ui.components.AutoFitText
import ws.crandell.fafo.ui.components.FafoWordmark
import ws.crandell.fafo.ui.components.PrimaryActionButton
import ws.crandell.fafo.ui.components.SHIELD_ART_HEIGHT_PER_HEIGHT
import ws.crandell.fafo.ui.components.SHIELD_ART_WIDTH_PER_HEIGHT
import ws.crandell.fafo.ui.components.SHIELD_MARK_ASPECT
import ws.crandell.fafo.ui.components.ShieldMark
import ws.crandell.fafo.ui.components.WordmarkVariant
import ws.crandell.fafo.ui.status.StatusLook
import ws.crandell.fafo.ui.status.StripeColor
import ws.crandell.fafo.ui.status.statusLook
import ws.crandell.fafo.ui.theme.FafoBrand
import ws.crandell.fafo.ui.theme.FafoColors
import ws.crandell.fafo.ui.theme.FafoType

/** Whole seconds left in a pre-alarm, rounded up (same math as `stateLabel`). */
fun secondsLeft(s: AlarmState.PreAlarm, now: Long): Long = ((s.endsAt - now).coerceAtLeast(0) + 999) / 1000

/**
 * Background of the alarm at [now]: Red and RedDeep alternate twice a second (a triangle wave, 250 ms each
 * way; Bill: "at red the whole background flashes"). 2 Hz stays under the 3 flashes/s photosensitivity
 * limit. Driven by the activity's clock, so it costs nothing with the screen off and tests stay idle.
 */
fun alarmPulse(now: Long): Color {
    val phase = (now % 500L).toFloat() / 500f
    val t = if (phase < 0.5f) phase * 2f else 2f - phase * 2f
    return lerp(FafoBrand.Red, FafoBrand.RedDeep, t)
}

fun StripeColor.color(): Color = when (this) {
    StripeColor.BRAND -> FafoBrand.Red
    StripeColor.GREEN -> FafoBrand.StatusGreen
    StripeColor.YELLOW -> FafoBrand.StatusYellow
    StripeColor.ORANGE -> FafoBrand.StatusOrange
    StripeColor.RED -> FafoBrand.StatusRed
}

@Composable
private fun StripeColor.textColor(): Color = when (this) {
    StripeColor.BRAND -> FafoColors.Steel
    StripeColor.GREEN -> FafoColors.Guard
    StripeColor.YELLOW -> FafoColors.Yellow
    StripeColor.ORANGE -> FafoColors.OrangeText
    StripeColor.RED -> FafoBrand.White
}

/** Round-2 entry point, kept for the alarm path and existing tests: the status screen with a calm ladder. */
@Composable
fun AlarmScreen(state: AlarmState, now: Long, photosOn: Boolean, animate: Boolean = true) =
    StatusScreen(state, ThreatLevel.GREEN, now, photosOn, animate)

/**
 * The traffic-light status screen (Bill 2026-10-28). A big shield in the middle, about 75% of the width in
 * portrait and 75% of the height in landscape, with the wordmark under it (beside it in landscape). The
 * stripes carry the status color, the beak is always red, the eye is white until armed. At RED the whole
 * background flashes. Pure (state in, callbacks out) so it is screenshot tested; [AlarmActivity] has the logic.
 */
@Composable
fun StatusScreen(
    state: AlarmState,
    threat: ThreatLevel,
    now: Long,
    photosOn: Boolean,
    animate: Boolean = true,
    onDisarm: (() -> Unit)? = null,
    onCancelArming: (() -> Unit)? = null,
) {
    val look = statusLook(state, threat, now)
    val bg = when {
        look.flash -> if (animate) alarmPulse(now) else FafoBrand.Red
        else -> FafoColors.Ink
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(Modifier.fillMaxSize().background(bg).safeDrawingPadding().padding(20.dp)) {
        if (landscape) {
            // The visible shield is 75% of the screen height (Bill 2026-10-28), within the padded box.
            val screenH = LocalConfiguration.current.screenHeightDp.dp
            val shieldH = minOf(screenH * 0.75f / SHIELD_ART_HEIGHT_PER_HEIGHT, maxHeight, maxWidth * 0.5f / SHIELD_MARK_ASPECT)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatusShield(look, shieldH)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusText(look, photosOn, onDisarm, onCancelArming, state)
                }
            }
        } else {
            // The visible shield is 75% of the screen width (Bill 2026-10-28), but leave room for the words
            // and buttons on short screens.
            val screenW = LocalConfiguration.current.screenWidthDp.dp
            val shieldH = minOf(screenW * 0.75f / SHIELD_ART_WIDTH_PER_HEIGHT, maxWidth / SHIELD_MARK_ASPECT, maxHeight * 0.48f)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                // Weighted, so the words and the Disarm button are measured first: with a big font or display
                // size the shield shrinks instead of pushing the button off the screen.
                BoxWithConstraints(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                    StatusShield(look, minOf(shieldH, maxHeight))
                }
                Spacer(Modifier.height(16.dp))
                StatusText(look, photosOn, onDisarm, onCancelArming, state)
            }
        }
    }
}

@Composable
private fun StatusShield(look: StatusLook, height: Dp) {
    Box(contentAlignment = Alignment.Center) {
        when {
            // On the red field: a dark halo so the white sticker edge and red stripes stay visible.
            look.flash -> Box(
                Modifier.size(height * 1.2f).background(
                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.2f), Color.Transparent)),
                ),
            )
            look.glow -> Box(
                Modifier.size(height * 1.25f).background(
                    Brush.radialGradient(listOf(FafoBrand.StatusOrange.copy(alpha = 0.45f), FafoBrand.StatusOrange.copy(alpha = 0.12f), Color.Transparent)),
                ),
            )
        }
        ShieldMark(
            height, Modifier.testTag("statusShield"), stripes = look.stripes.color(),
            eye = if (look.eyeRed) FafoBrand.Red else FafoBrand.White,
            contentDescription = "FAFO Security shield, ${look.stripes.name.lowercase()}",
        )
    }
}

@Composable
private fun ColumnScope.StatusText(
    look: StatusLook,
    photosOn: Boolean,
    onDisarm: (() -> Unit)?,
    onCancelArming: (() -> Unit)?,
    state: AlarmState,
) {
    val onRed = look.flash
    val text = if (onRed) FafoBrand.White else FafoColors.White
    FafoWordmark(height = 38.dp, variant = if (onRed) WordmarkVariant.MONO_WHITE else WordmarkVariant.AUTO)
    Spacer(Modifier.height(14.dp))
    AutoFitText(
        look.label, style = FafoType.stateDisplay, color = look.stripes.textColor(), minFontSize = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag(if (look.flash) "alarmWord" else "statusWord")
            .semantics { liveRegion = if (look.flash) LiveRegionMode.Assertive else LiveRegionMode.Polite },
    )
    look.countdown?.let { n ->
        AutoFitText(
            "$n", style = FafoType.countdown.copy(fontSize = 96.sp, lineHeight = 100.sp), color = text, sample = "88",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("countdown").semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    look.detail?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodyLarge, color = if (onRed) FafoBrand.White else FafoColors.TextMuted, textAlign = TextAlign.Center)
    }
    if (photosOn && (state is AlarmState.PreAlarm || look.flash)) {
        Spacer(Modifier.height(10.dp))
        Text("Photos are being taken.", style = MaterialTheme.typography.titleMedium, color = text, modifier = Modifier.testTag("photosLine"))
    }
    val button = when {
        state is AlarmState.Arming && onCancelArming != null -> Triple("Cancel arming", onCancelArming, "statusCancel")
        state != AlarmState.Disarmed && state !is AlarmState.Arming && onDisarm != null -> Triple("Disarm", onDisarm, "statusDisarm")
        else -> null
    }
    if (button != null) {
        Spacer(Modifier.height(20.dp))
        PrimaryActionButton(button.first, ActionKind.DISARM, button.second, Modifier.fillMaxWidth().widthIn(max = 320.dp).testTag(button.third))
    }
}
