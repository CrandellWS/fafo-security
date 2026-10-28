package ws.crandell.fafo.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import ws.crandell.fafo.core.Motion

/**
 * Accelerometer listener while ARMED only (AC4.1). It only classifies: the armed service feeds each bump or
 * pickup into the ThreatLadder, which picks the chirp (rate-limited, AC4.3), the status color and, while
 * locked, the escalation to the pre-alarm (AC4.2, round 3).
 */
class MotionMonitor(context: Context, private val onMotion: (Motion) -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private var classifier: MotionClassifier? = null
    private var current: MotionSensitivity? = null

    val running: Boolean get() = classifier != null

    /** Start, restart with a new sensitivity, or stop (null). */
    fun update(sensitivity: MotionSensitivity?) {
        if (sensitivity == current && (sensitivity == null) == (classifier == null)) return
        stop()
        if (sensitivity == null) return
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        classifier = MotionClassifier(sensitivity)
        current = sensitivity
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (classifier != null) sm?.unregisterListener(this)
        classifier = null
        current = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val c = classifier ?: return
        val t = event.timestamp / 1_000_000L
        when (c.add(t, event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble())) {
            MotionEvent.BUMP -> onMotion(Motion.BUMP)
            MotionEvent.PICKUP -> onMotion(Motion.PICKUP)
            null -> Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
