package ws.crandell.fafo.siren

import kotlin.math.PI
import kotlin.math.sin

/**
 * Original tones generated in code (no third-party audio). 16-bit mono PCM at [SAMPLE_RATE].
 * Square-ish (soft-clipped sine) waves carry better through a phone speaker than pure sines.
 */
object Tones {
    const val SAMPLE_RATE = 22_050

    private fun tone(freqHz: (Double) -> Double, durationMs: Int, amp: Double = 0.95, clip: Double = 2.5): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            phase += 2 * PI * freqHz(t) / SAMPLE_RATE
            val v = (sin(phase) * clip).coerceIn(-1.0, 1.0) * amp
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        // 3 ms fade in/out against clicks.
        val fade = minOf(n / 2, SAMPLE_RATE * 3 / 1000)
        for (i in 0 until fade) {
            val g = i.toDouble() / fade
            out[i] = (out[i] * g).toInt().toShort()
            out[n - 1 - i] = (out[n - 1 - i] * g).toInt().toShort()
        }
        return out
    }

    private fun silence(ms: Int) = ShortArray(SAMPLE_RATE * ms / 1000)

    private fun concat(vararg parts: ShortArray): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var o = 0
        for (p in parts) { p.copyInto(out, o); o += p.size }
        return out
    }

    /** One loop period of the selected siren sound (spec section 4: 2-3 bundled sounds, default Klaxon). */
    fun siren(name: String): ShortArray = when (name) {
        "Wail" -> tone({ t -> 900 + 500 * sin(2 * PI * t / 1.5) }, 1500)
        "Pulse" -> concat(tone({ 1050.0 }, 250), silence(120))
        else -> concat(tone({ 660.0 }, 450), tone({ 880.0 }, 450)) // Klaxon
    }

    /** Pre-alarm warning beep. */
    fun beep(): ShortArray = tone({ 1500.0 }, 80)

    /** Bump: one short chirp. */
    fun chirpShort(): ShortArray = tone({ 2000.0 }, 70)

    /** Pick-up: louder, longer chirp in 3 pulses (AC4.1). */
    fun chirpTriple(): ShortArray = concat(
        tone({ 2400.0 }, 130), silence(80), tone({ 2400.0 }, 130), silence(80), tone({ 2400.0 }, 130),
    )

    fun durationMs(samples: ShortArray): Long = samples.size * 1000L / SAMPLE_RATE
}
