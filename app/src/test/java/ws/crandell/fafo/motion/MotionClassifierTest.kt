package ws.crandell.fafo.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic accelerometer traces at 50 Hz (AC4.5). */
class MotionClassifierTest {
    private val G = 9.81
    private val dtMs = 20L

    private data class S(val t: Long, val x: Double, val y: Double, val z: Double)

    private fun run(sens: MotionSensitivity, trace: List<S>): List<MotionEvent> {
        val c = MotionClassifier(sens)
        return trace.mapNotNull { c.add(it.t, it.x, it.y, it.z) }
    }

    private fun trace(durationMs: Long, f: (Long) -> Triple<Double, Double, Double>): List<S> {
        val rnd = Random(7)
        return (0..durationMs step dtMs).map { t ->
            val (x, y, z) = f(t)
            S(t, x + rnd.nextDouble(-0.04, 0.04), y + rnd.nextDouble(-0.04, 0.04), z + rnd.nextDouble(-0.04, 0.04))
        }
    }

    private val rest = trace(5000) { Triple(0.0, 0.0, G) }

    /** Phone flat on a table; at 2 s a knock of [peak] m/s^2 for 80 ms along x. */
    private fun tableBump(peak: Double) = trace(5000) { t ->
        Triple(if (t in 2000L until 2080L) peak else 0.0, 0.0, G)
    }

    /** Lifted at 2 s: tilts 60 degrees over 0.6 s with an upward push, then held. */
    private val pickUp = trace(5000) { t ->
        val p = ((t - 2000).coerceIn(0, 600)) / 600.0
        val a = p * PI / 3
        val push = if (t in 2000L until 2400L) 2.0 else 0.0
        Triple(0.0, G * sin(a), G * cos(a) + push)
    }

    /** Carried while walking: 2 Hz vertical bounce of 4 m/s^2 with some sway. */
    private val walking = trace(5000) { t ->
        val s = t / 1000.0
        Triple(1.0 * sin(2 * PI * 1 * s), 2.0 + 0.5 * sin(2 * PI * 2 * s), G + 4.0 * sin(2 * PI * 2 * s))
    }

    /** On a vibrating surface (washer, phone buzzing next to it): 0.5 m/s^2 at 25 Hz. */
    private val vibrating = trace(5000) { t ->
        val s = t / 1000.0
        Triple(0.0, 0.0, G + 0.5 * sin(2 * PI * 25 * s + 0.3))
    }

    @Test fun restingIsQuietAtEverySensitivity() {
        MotionSensitivity.entries.forEach { assertThat(run(it, rest)).isEmpty() }
    }

    @Test fun hardTableBumpIsABumpAtEverySensitivity() {
        MotionSensitivity.entries.forEach { assertThat(run(it, tableBump(6.0))).containsExactly(MotionEvent.BUMP) }
    }

    @Test fun gentleBumpOnlyOnHigh() {
        assertThat(run(MotionSensitivity.HIGH, tableBump(2.0))).containsExactly(MotionEvent.BUMP)
        assertThat(run(MotionSensitivity.MEDIUM, tableBump(2.0))).isEmpty()
        assertThat(run(MotionSensitivity.LOW, tableBump(2.0))).isEmpty()
    }

    @Test fun pickUpIsAPickUpNotABump() {
        MotionSensitivity.entries.forEach {
            val ev = run(it, pickUp)
            assertThat(ev).contains(MotionEvent.PICKUP)
            assertThat(ev).doesNotContain(MotionEvent.BUMP)
        }
    }

    @Test fun walkingIsPickUpNotBumps() {
        MotionSensitivity.entries.forEach {
            val ev = run(it, walking)
            assertThat(ev).contains(MotionEvent.PICKUP)
            assertThat(ev).doesNotContain(MotionEvent.BUMP)
        }
    }

    @Test fun vibratingSurfaceIsIgnored() {
        MotionSensitivity.entries.forEach { assertThat(run(it, vibrating)).isEmpty() }
    }

    /**
     * Device feedback 2026-10-28, Bill's case: picked up at 2 s, held (hand shake) until 4 s, set back down flat
     * by 4.5 s, then left alone. The phone is not "still" while in hand, and the settle watch begun at the
     * first pickup fires within a few seconds of the set-down.
     */
    private val pickUpThenSetDown = trace(12_000) { t ->
        val up = ((t - 2000).coerceIn(0, 600)) / 600.0
        val down = ((t - 4000).coerceIn(0, 500)) / 500.0
        val a = (up - down).coerceAtLeast(0.0) * PI / 3
        val shake = if (t in 2000L until 4500L) 0.8 * sin(2 * PI * 6 * t / 1000.0) else 0.0
        Triple(shake, G * sin(a), G * cos(a))
    }

    private fun settleTime(sens: MotionSensitivity, tr: List<S>): Long? {
        val c = MotionClassifier(sens)
        val w = ws.crandell.fafo.core.SettleWatch()
        var settledAt: Long? = null
        for (s in tr) {
            val ev = c.add(s.t, s.x, s.y, s.z)
            if (ev == MotionEvent.PICKUP && !w.watching && settledAt == null) w.begin()
            if (s.t in 2200L until 4400L) assertThat(c.still).isFalse()
            if (w.onSample(s.t, c.still) && settledAt == null) settledAt = s.t
        }
        return settledAt
    }

    @Test fun pickUpSetDownAndLeftAloneSettles() {
        MotionSensitivity.entries.forEach {
            val at = settleTime(it, pickUpThenSetDown)
            assertThat(at).isNotNull()
            assertThat(at!!).isAtLeast(4500L + ws.crandell.fafo.core.SettleWatch.SETTLE_MS)
            // About 1 s for the gravity filter to catch up after the set-down, then the settle window.
            assertThat(at).isAtMost(7_000L)
        }
    }

    @Test fun carriedAwayNeverSettles() {
        val walkingLong = trace(12_000) { t ->
            val s = t / 1000.0
            Triple(1.0 * sin(2 * PI * 1 * s), 2.0 + 0.5 * sin(2 * PI * 2 * s), G + 4.0 * sin(2 * PI * 2 * s))
        }
        MotionSensitivity.entries.forEach {
            val c = MotionClassifier(it)
            val w = ws.crandell.fafo.core.SettleWatch()
            w.begin()
            assertThat(walkingLong.any { s -> c.add(s.t, s.x, s.y, s.z); w.onSample(s.t, c.still) }).isFalse()
        }
    }

    @Test fun restingIsStill() {
        val c = MotionClassifier(MotionSensitivity.MEDIUM)
        rest.forEach { c.add(it.t, it.x, it.y, it.z) }
        assertThat(c.still).isTrue()
    }

    @Test fun settingMapping() {
        assertThat(MotionSensitivity.fromSetting("OFF")).isNull()
        assertThat(MotionSensitivity.fromSetting("MEDIUM")).isEqualTo(MotionSensitivity.MEDIUM)
    }

    @Test fun chirpRateLimitedTo1500ms() {
        val l = ChirpLimiter()
        assertThat(l.allow(0)).isTrue()
        assertThat(l.allow(100)).isFalse()
        assertThat(l.allow(1499)).isFalse()
        assertThat(l.allow(1500)).isTrue()
        assertThat(l.allow(2000)).isFalse()
        assertThat(l.allow(3000)).isTrue()
    }
}
