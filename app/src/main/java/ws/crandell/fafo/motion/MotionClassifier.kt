package ws.crandell.fafo.motion

import kotlin.math.acos
import kotlin.math.sqrt

enum class MotionSensitivity(
    /** Peak linear acceleration (m/s^2) that counts as a bump. */
    val bumpThreshold: Double,
    /** RMS linear acceleration (m/s^2) that, held for [SUSTAIN_MS], counts as being carried. */
    val sustainThreshold: Double,
    /** Gravity-vector tilt (degrees) that, held for [SUSTAIN_MS], counts as a pick-up. */
    val tiltDegrees: Double,
) {
    LOW(4.0, 2.0, 35.0),
    MEDIUM(2.5, 1.3, 25.0),
    HIGH(1.2, 0.8, 15.0);

    companion object {
        /** Settings value OFF / LOW / MEDIUM / HIGH; null means off. */
        fun fromSetting(v: String): MotionSensitivity? = entries.firstOrNull { it.name == v }
    }
}

enum class MotionEvent { BUMP, PICKUP }

/**
 * AC4.1 classifier on accelerometer samples (m/s^2, timestamps in ms). Pure Kotlin.
 *
 * - A fast low-pass (tau 0.25 s) estimates gravity; raw minus gravity is the linear (high-passed) part.
 * - A slow reference gravity (tau 3 s) is updated only while the phone is at rest.
 * - Pick-up: tilt between fast gravity and the rest reference above the threshold for ~0.5 s, or
 *   linear RMS (0.3 s window) above the sustain threshold for ~0.5 s (carried / walking).
 * - Bump: a short linear peak above the bump threshold, confirmed 0.6 s later only if the phone is
 *   back at rest by then (so the start of a pick-up is not also reported as a bump).
 */
class MotionClassifier(private val sensitivity: MotionSensitivity) {
    private var g: DoubleArray? = null
    private var ref: DoubleArray? = null
    private var lastT = 0L

    private val window = ArrayDeque<Pair<Long, Double>>() // (t, |linear|^2)
    private var windowSum = 0.0

    private var tiltSince: Long? = null
    private var sustainSince: Long? = null
    private var spikeStart: Long? = null
    private var pendingBumpAt: Long? = null
    /** After a pick-up the phone is in hand: its settling jolts are not bumps. */
    private var noBumpUntil = Long.MIN_VALUE

    fun add(t: Long, x: Double, y: Double, z: Double): MotionEvent? {
        val raw = doubleArrayOf(x, y, z)
        val gPrev = g
        if (gPrev == null) {
            g = raw.copyOf(); ref = raw.copyOf(); lastT = t
            return null
        }
        val dt = ((t - lastT).coerceAtLeast(1)) / 1000.0
        lastT = t
        val aFast = dt / (FAST_TAU + dt)
        for (i in 0..2) gPrev[i] += (raw[i] - gPrev[i]) * aFast
        val lin = DoubleArray(3) { raw[it] - gPrev[it] }
        val linMag = norm(lin)

        window.addLast(t to linMag * linMag)
        windowSum += linMag * linMag
        while (window.isNotEmpty() && window.first().first < t - RMS_WINDOW_MS) windowSum -= window.removeFirst().second
        val rms = sqrt((windowSum / window.size).coerceAtLeast(0.0))

        val r = ref!!
        val atRest = rms < REST_RMS
        if (atRest) {
            val aSlow = dt / (SLOW_TAU + dt)
            for (i in 0..2) r[i] += (gPrev[i] - r[i]) * aSlow
        }
        val tilt = angleDeg(gPrev, r)

        // Pick-up by tilt.
        if (tilt > sensitivity.tiltDegrees) {
            if (tiltSince == null) tiltSince = t
        } else {
            tiltSince = null
        }
        // Pick-up / carried by sustained linear motion.
        if (rms > sensitivity.sustainThreshold) {
            if (sustainSince == null) sustainSince = t
        } else {
            sustainSince = null
        }
        val tiltHeld = tiltSince?.let { t - it >= SUSTAIN_MS } == true
        val motionHeld = sustainSince?.let { t - it >= SUSTAIN_MS } == true
        if (tiltHeld || motionHeld) {
            // Re-baseline so the next pick-up needs a fresh change.
            for (i in 0..2) r[i] = gPrev[i]
            tiltSince = null
            sustainSince = null
            pendingBumpAt = null
            spikeStart = null
            noBumpUntil = t + PICKUP_QUIET_MS
            return MotionEvent.PICKUP
        }

        // Bump: short spike above the threshold.
        if (linMag > sensitivity.bumpThreshold) {
            if (spikeStart == null) spikeStart = t
        } else {
            val s = spikeStart
            if (s != null) {
                if (t - s <= MAX_SPIKE_MS && t > noBumpUntil) pendingBumpAt = t + BUMP_CONFIRM_MS
                spikeStart = null
            }
        }
        val pb = pendingBumpAt
        if (pb != null && t >= pb) {
            pendingBumpAt = null
            if (rms < sensitivity.sustainThreshold && tilt <= sensitivity.tiltDegrees) return MotionEvent.BUMP
        }
        return null
    }

    private fun norm(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    private fun angleDeg(a: DoubleArray, b: DoubleArray): Double {
        val na = norm(a); val nb = norm(b)
        if (na < 1e-6 || nb < 1e-6) return 0.0
        val c = ((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(c))
    }

    companion object {
        const val FAST_TAU = 0.25
        const val SLOW_TAU = 3.0
        const val RMS_WINDOW_MS = 300L
        const val SUSTAIN_MS = 500L
        const val MAX_SPIKE_MS = 250L
        const val BUMP_CONFIRM_MS = 600L
        const val REST_RMS = 0.3
        const val PICKUP_QUIET_MS = 1500L
    }
}

/** AC4.3: at most one chirp per 1.5 s. */
class ChirpLimiter(private val minIntervalMs: Long = 1500L) {
    private var last: Long? = null
    fun allow(now: Long): Boolean {
        val l = last
        if (l != null && now - l < minIntervalMs) return false
        last = now
        return true
    }
}
