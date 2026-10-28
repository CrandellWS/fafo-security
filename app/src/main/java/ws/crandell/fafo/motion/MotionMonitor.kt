package ws.crandell.fafo.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import ws.crandell.fafo.arm.AlarmSound
import ws.crandell.fafo.arm.ChirpKind

/**
 * Accelerometer listener while ARMED only (AC4.1). Motion never starts the siren (AC4.2); it only chirps,
 * rate-limited to one chirp per 1.5 s (AC4.3).
 */
class MotionMonitor(context: Context, private val sound: AlarmSound, private val nowMs: () -> Long) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private var classifier: MotionClassifier? = null
    private var current: MotionSensitivity? = null
    private val limiter = ChirpLimiter()

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
            MotionEvent.BUMP -> if (limiter.allow(nowMs())) sound.chirp(ChirpKind.BUMP)
            MotionEvent.PICKUP -> if (limiter.allow(nowMs())) sound.chirp(ChirpKind.PICKUP)
            null -> Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
