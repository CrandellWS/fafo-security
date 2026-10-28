package ws.crandell.fafo.core

/** Spec section 4: every setting's choices and default. */
object Defaults {
    val SENSITIVITY_CHOICES_MS = listOf(100L, 250L, 500L, 1000L, 2000L, 5000L)
    const val SENSITIVITY_MS = 500L

    val SIREN_SOUNDS = listOf("Klaxon", "Wail", "Pulse")
    const val SIREN_SOUND = "Klaxon"

    const val FORCE_MAX_VOLUME = true

    val PRE_ALARM_CHOICES_S = listOf(0, 1, 5, 10, 30, 60)
    const val PRE_ALARM_S = 5

    const val DISARM_ON_UNLOCK = false

    /** ARMING countdown before ARMED (Bill 2026-10-28): a few seconds, cancellable. */
    val ARMING_CHOICES_S = listOf(3, 5, 10)
    const val ARMING_S = 5

    /** AC1.7: Arm / Disarm skip the prompt this long after the phone was unlocked (0 = always ask). */
    val UNLOCK_GRACE_CHOICES_S = listOf(0, 15, 30, 60, 120)
    const val UNLOCK_GRACE_S = 30

    const val MOTION = "MEDIUM" // OFF / LOW / MEDIUM / HIGH
    /** Round 3 (Bill's bump ladder): continued handling or a pickup while locked starts the pre-alarm. */
    const val MOTION_CAN_ALARM = true
    /** Bump reset time (Bill 2026-10-28): how long after the last bump the status goes back to green. Was a fixed 60 s. */
    val BUMP_RESET_CHOICES_S = listOf(10, 15, 30, 60)
    const val BUMP_RESET_S = 15
    /** Camera torch as the alarm light (Bill 2026-10-28, approved). */
    const val FLASH_ALARM = true

    const val PHOTO_ON_TRIGGER = true
    val PHOTO_INTERVAL_CHOICES_S = listOf(5, 10, 15, 20, 25, 30)
    const val PHOTO_INTERVAL_S = 10
    const val CAMERA = "FRONT" // FRONT / BACK / ALTERNATE
    val PHOTO_CAP_CHOICES_MB = listOf(25, 50, 100, 200)
    const val PHOTO_CAP_MB = 100
    const val LOW_STORAGE_FLOOR_BYTES = 500L * 1024 * 1024

    const val STAY_REACHABLE = false
    const val CHIME_ON_LISTEN = true
    const val INTERCOM_TRANSPORT = "AUTO"
    const val INTERCOM_CODEC = "AUTO"

    const val LABEL_MAX = 24

    /** App and status-screen theme: follow the phone (Bill 2026-10-28), or force LIGHT / DARK. */
    val THEME_CHOICES = listOf("SYSTEM", "LIGHT", "DARK")
    const val THEME = "SYSTEM"
}
