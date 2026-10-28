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
 * together go ORANGE (louder); continued handling or a pickup starts the pre-alarm; left alone for about a
 * minute it cools back to GREEN. Pure: the caller passes the clock, so tests drive it.
 *
 * Escalation to the pre-alarm needs [mayAlarm] (phone locked and "Motion can alarm" on). Without it the
 * ladder still shows the colors and chirps but stops at ORANGE: the owner handling the unlocked phone, or
 * the v1 chirp-only behavior.
 */
class ThreatLadder(private val closeMs: Long = CLOSE_MS, private val coolMs: Long = COOL_MS) {
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

    /** Back to GREEN (every change into or out of ARMED). */
    fun reset() {
        level = ThreatLevel.GREEN
        lastBumpAt = null
        lastMotionAt = null
    }

    companion object {
        /** Bumps this close together count as "repeated". */
        const val CLOSE_MS = 8_000L
        /** Untouched this long: back to GREEN. */
        const val COOL_MS = 60_000L
    }
}
