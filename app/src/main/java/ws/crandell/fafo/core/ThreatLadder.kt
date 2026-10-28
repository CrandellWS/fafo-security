package ws.crandell.fafo.core

/** How worried the armed phone is about being handled (the status screen's stripe color while ARMED). */
enum class ThreatLevel { GREEN, YELLOW, ORANGE }

/** A motion reading from the accelerometer classifier. */
enum class Motion { BUMP, PICKUP }

sealed interface LadderEffect {
    /** [loud]: the louder triple chirp (repeated bumps); otherwise the short chirp. */
    data class Chirp(val loud: Boolean) : LadderEffect
    /** Continued handling or a pickup while locked: start the pre-alarm (AlarmEvent.MotionEscalated). */
    data object Escalate : LadderEffect
}

/**
 * Bump escalation (Bill 2026-10-28): one bump turns the status YELLOW with a chirp; repeated bumps close
 * together go ORANGE (louder); continued handling or a pickup starts the pre-alarm; left alone for the
 * "Bump reset time" setting (10/15/30/60 s, default 15 s) it cools back to GREEN. Pure: the caller passes
 * the clock and the reset time, so tests drive it.
 *
 * Escalation to the pre-alarm needs [mayAlarm] (phone locked and "Motion can alarm" on). Without it the
 * ladder still shows the colors and chirps but stops at ORANGE: the owner handling the unlocked phone, or
 * the v1 chirp-only behavior.
 */
class ThreatLadder(
    private val closeMs: Long = CLOSE_MS,
    private val coolMsSetting: () -> Long = { Defaults.BUMP_RESET_S * 1000L },
) {
    /** Untouched this long: back to GREEN. Read live, so a Settings change applies to the next bump. */
    val coolMs: Long get() = coolMsSetting()

    var level: ThreatLevel = ThreatLevel.GREEN
        private set
    private var lastBumpAt: Long? = null
    private var lastMotionAt: Long? = null

    fun onMotion(m: Motion, now: Long, mayAlarm: Boolean): List<LadderEffect> {
        val close = lastBumpAt?.let { now - it in 0..closeMs } == true
        lastMotionAt = now
        return when (m) {
            Motion.PICKUP -> if (mayAlarm) {
                level = ThreatLevel.ORANGE
                listOf(LadderEffect.Escalate)
            } else {
                level = ThreatLevel.ORANGE
                listOf(LadderEffect.Chirp(loud = true))
            }
            Motion.BUMP -> {
                lastBumpAt = now
                when (level) {
                    ThreatLevel.GREEN -> { level = ThreatLevel.YELLOW; listOf(LadderEffect.Chirp(loud = false)) }
                    ThreatLevel.YELLOW -> if (close) {
                        level = ThreatLevel.ORANGE
                        listOf(LadderEffect.Chirp(loud = true))
                    } else listOf(LadderEffect.Chirp(loud = false))
                    ThreatLevel.ORANGE -> if (close && mayAlarm) listOf(LadderEffect.Escalate) else listOf(LadderEffect.Chirp(loud = true))
                }
            }
        }
    }

    /** Cools back to GREEN once nothing moved for [coolMs]. True when the level changed. */
    fun tick(now: Long): Boolean {
        val last = lastMotionAt ?: return false
        if (level == ThreatLevel.GREEN || now - last < coolMs) return false
        reset()
        return true
    }

    /**
     * Milliseconds until [tick] would cool to GREEN with the current reset time, or null when already GREEN.
     * The service re-checks this after each wait, so a reset time changed mid cool-down (longer or shorter)
     * still ends in GREEN instead of leaving the status stuck.
     */
    fun msUntilCool(now: Long): Long? {
        val last = lastMotionAt ?: return null
        if (level == ThreatLevel.GREEN) return null
        return (last + coolMs - now).coerceAtLeast(0L)
    }

    /**
     * A motion pre-alarm settled back to ARMED (device feedback 2026-10-28): the status stays ORANGE, so
     * more handling escalates again, and cools to GREEN after the bump reset time like any bump.
     */
    fun settled(now: Long) {
        level = ThreatLevel.ORANGE
        lastBumpAt = null
        lastMotionAt = now
    }

    /** Back to GREEN (every change into or out of ARMED). */
    fun reset() {
        level = ThreatLevel.GREEN
        lastBumpAt = null
        lastMotionAt = null
    }

    companion object {
        /** Bumps this close together count as "repeated". */
        const val CLOSE_MS = 8_000L
    }
}
