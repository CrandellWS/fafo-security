package ws.crandell.fafo.core

/**
 * AC2.4a: warning beeps whose interval shrinks from about 1 beep/s to about 5 beeps/s in the last
 * seconds. The interval depends only on the time remaining, so it is non-increasing.
 */
object PreAlarmSchedule {
    const val SLOW_INTERVAL_MS = 1000L
    const val FAST_INTERVAL_MS = 200L
    /** At or below this much time left, beep at the fast rate. */
    const val FAST_BELOW_MS = 2000L
    /** At or above this much time left, beep at the slow rate. */
    const val SLOW_ABOVE_MS = 10_000L

    fun intervalFor(remainingMs: Long): Long = when {
        remainingMs <= FAST_BELOW_MS -> FAST_INTERVAL_MS
        remainingMs >= SLOW_ABOVE_MS -> SLOW_INTERVAL_MS
        else -> {
            val f = (remainingMs - FAST_BELOW_MS).toDouble() / (SLOW_ABOVE_MS - FAST_BELOW_MS)
            (FAST_INTERVAL_MS + f * (SLOW_INTERVAL_MS - FAST_INTERVAL_MS)).toLong()
        }
    }

    /** Beep offsets in ms from the start of the pre-alarm, all strictly before [durationMs]. */
    fun beepOffsets(durationMs: Long): List<Long> {
        if (durationMs <= 0) return emptyList()
        val out = ArrayList<Long>()
        var t = 0L
        while (t < durationMs) {
            out += t
            t += intervalFor(durationMs - t)
        }
        return out
    }
}
