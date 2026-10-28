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
