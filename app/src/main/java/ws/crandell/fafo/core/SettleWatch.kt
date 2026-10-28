package ws.crandell.fafo.core

/**
 * Device feedback 2026-10-28 (Bill, A17): picked up, set down and left alone, the phone still went into the
 * full alarm. During a MOTION pre-alarm the accelerometer keeps running and every sample says whether the
 * phone is still (no linear motion, no tilt in progress, no bump or pickup). Once it has been still for
 * [settleMs] in a row, [onSample] returns true: the service sends [AlarmEvent.MotionSettled], which cancels
 * the pre-alarm only if the phone is still plugged in. It keeps returning true every [settleMs] while the
 * phone stays still, so a late plugged reading is retried. An unplug the machine saw during (or just before)
 * the pre-alarm latches ([AlarmState.PreAlarm.unplugged]): no settle cancels it after that. Pure, ms.
 */
class SettleWatch(val settleMs: Long = SETTLE_MS) {
    private var active = false
    private var stillSince: Long? = null

    val watching: Boolean get() = active

    /** Start watching (entering a MOTION pre-alarm). Stillness before this moment never counts. */
    fun begin() {
        active = true
        stillSince = null
    }

    fun end() {
        active = false
        stillSince = null
    }

    fun onSample(t: Long, still: Boolean): Boolean {
        if (!active) return false
        if (!still) {
            stillSince = null
            return false
        }
        val since = stillSince ?: t.also { stillSince = it }
        if (t - since < settleMs) return false
        stillSince = t
        return true
    }

    companion object {
        /** Still this long (and plugged in) cancels a motion pre-alarm. Shorter than the 5 s motion minimum. */
        const val SETTLE_MS = 1_500L
    }
}
