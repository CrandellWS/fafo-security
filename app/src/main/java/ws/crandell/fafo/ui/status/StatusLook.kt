package ws.crandell.fafo.ui.status

import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.IncidentCause
import ws.crandell.fafo.core.ThreatLevel

/** Shield stripe color on the status screen: brand red when disarmed, then the traffic light. */
enum class StripeColor { BRAND, GREEN, YELLOW, ORANGE, RED }

/** What the alarm light (camera torch) does: off, slow warning blink, fast strobe. */
enum class LightLevel { OFF, WARN, ALARM }

/**
 * What the traffic-light status screen shows for a state (Bill 2026-10-28). Pure, so every row of the
 * table is unit tested. GREEN armed and calm, YELLOW one bump (and ARMING), ORANGE repeated bumps or the
 * pre-alarm countdown, RED the siren (the whole background flashes). Eye white until armed, then red.
 */
data class StatusLook(
    val stripes: StripeColor,
    val eyeRed: Boolean,
    /** Big one-line word under the shield. */
    val label: String,
    /** Smaller line under it, or null. */
    val detail: String?,
    /** Seconds left (ARMING, PRE-ALARM), or null. */
    val countdown: Long?,
    /** RED: the whole background flashes red. */
    val flash: Boolean,
    /** ORANGE glow behind the shield (repeated bumps, pre-alarm). */
    val glow: Boolean,
    val light: LightLevel,
    /** Keep the screen on (pre-alarm and alarm only; calm green must not burn the screen). */
    val keepScreenOn: Boolean,
)

private fun secondsLeft(endsAt: Long, now: Long) = ((endsAt - now).coerceAtLeast(0) + 999) / 1000

fun statusLook(state: AlarmState, threat: ThreatLevel, now: Long): StatusLook = when (state) {
    AlarmState.Disarmed -> StatusLook(StripeColor.BRAND, false, "DISARMED", "Not protecting", null, false, false, LightLevel.OFF, false)
    is AlarmState.Arming -> StatusLook(
        StripeColor.YELLOW, false, "ARMING", "Leave it plugged in", secondsLeft(state.endsAt, now), false, false, LightLevel.OFF, false,
    )
    is AlarmState.Armed -> when {
        state.awaitingPower -> StatusLook(StripeColor.GREEN, true, "ARMED · PLUG IN", "Reset remotely. Plug in to protect again.", null, false, false, LightLevel.OFF, false)
        threat == ThreatLevel.YELLOW -> StatusLook(StripeColor.YELLOW, true, "BUMP DETECTED", "Leave it alone and it goes back to green", null, false, false, LightLevel.OFF, false)
        threat == ThreatLevel.ORANGE -> StatusLook(StripeColor.ORANGE, true, "HANDS OFF", "More handling sets off the alarm", null, false, true, LightLevel.WARN, false)
        else -> StatusLook(StripeColor.GREEN, true, "ARMED", "Unplugging while locked sets off the alarm", null, false, false, LightLevel.OFF, false)
    }
    is AlarmState.PreAlarm -> StatusLook(
        StripeColor.ORANGE, true, if (state.cause == IncidentCause.MOTION) "HANDS OFF" else "PLUG ME BACK IN",
        if (state.cause == IncidentCause.MOTION) "Unlock to stop the alarm" else "Plug the charger back in, or unlock",
        secondsLeft(state.endsAt, now), false, true, LightLevel.WARN, true,
    )
    is AlarmState.Triggered, is AlarmState.IncidentPersisted ->
        StatusLook(StripeColor.RED, true, "ALARM", "Only the owner can stop the alarm, by unlocking this phone.", null, true, false, LightLevel.ALARM, true)
}

/**
 * HANDS OFF (device feedback 2026-10-28): the screen that wakes over the lock screen when the armed phone is
 * bumped or picked up with the screen off. Traffic-light colors: YELLOW for a bump, ORANGE for a pickup or
 * repeated bumps, RED stripes (no flashing) for the pre-alarm countdown. The siren and the calm states look
 * the same as on the status screen.
 */
fun handsOffLook(state: AlarmState, threat: ThreatLevel, now: Long): StatusLook = when {
    state is AlarmState.Armed && !state.awaitingPower && threat == ThreatLevel.YELLOW ->
        StatusLook(StripeColor.YELLOW, true, "HANDS OFF", "This phone is protected. Put it down.", null, false, false, LightLevel.OFF, true)
    state is AlarmState.Armed && !state.awaitingPower && threat == ThreatLevel.ORANGE ->
        StatusLook(StripeColor.ORANGE, true, "HANDS OFF", "More handling sets off the alarm", null, false, true, LightLevel.WARN, true)
    state is AlarmState.PreAlarm -> StatusLook(
        StripeColor.RED, true, if (state.cause == IncidentCause.MOTION) "HANDS OFF" else "PLUG ME BACK IN",
        if (state.cause == IncidentCause.MOTION) "Put it down now or the alarm sounds" else "Plug the charger back in, or the alarm sounds",
        secondsLeft(state.endsAt, now), false, true, LightLevel.WARN, true,
    )
    else -> statusLook(state, threat, now)
}

/** How long the PHOTO TAKEN label and thumbnail stay up after each photo. */
const val PHOTO_BANNER_MS = 2_000L

/** The white shutter flash fades out over this long (none with reduced motion). */
const val PHOTO_FLASH_MS = 300L

/**
 * PHOTO TAKEN (device feedback 2026-10-28): what the alarm and hands-off screens show for the latest trigger
 * photo at [now]. [flashAlpha] is the white overlay (0 = none), [banner] the PHOTO TAKEN label with the
 * thumbnail, [countLabel] the running counter ("3 photos taken"), shown for the rest of the incident.
 */
data class PhotoTakenLook(val banner: Boolean, val flashAlpha: Float, val countLabel: String?)

fun photoTakenLook(shotAt: Long?, count: Int, now: Long, reducedMotion: Boolean): PhotoTakenLook {
    val countLabel = when {
        count <= 0 -> null
        count == 1 -> "1 photo taken"
        else -> "$count photos taken"
    }
    if (shotAt == null) return PhotoTakenLook(false, 0f, countLabel)
    val age = now - shotAt
    val banner = age in 0 until PHOTO_BANNER_MS
    val flash = if (reducedMotion || age < 0 || age >= PHOTO_FLASH_MS) 0f else 0.85f * (1f - age.toFloat() / PHOTO_FLASH_MS)
    return PhotoTakenLook(banner, flash, countLabel)
}
