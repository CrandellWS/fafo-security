package ws.crandell.fafo.siren

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * AudioTrack on USAGE_ALARM / CONTENT_TYPE_SONIFICATION (AC2.3). Direct-boot safe: no storage access,
 * the tones are generated in memory.
 */
class AndroidAudioBackend(context: Context) : AudioBackend {
    private val app = context.applicationContext
    private val am = app.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var loopTrack: AudioTrack? = null
    private val oneShots = mutableListOf<AudioTrack>()
    private var focus: AudioFocusRequest? = null

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Vibrator::class.java)
        }

    private fun build(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(alarmAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Tones.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    override fun playLoop(samples: ShortArray, gain: Float) {
        stopLoop()
        try {
            val t = build(samples)
            t.setLoopPoints(0, samples.size, -1)
            t.setVolume(gain)
            t.play()
            loopTrack = t
        } catch (e: Exception) {
            Log.e(TAG, "siren loop failed", e)
        }
    }

    override fun playOnce(samples: ShortArray, gain: Float) {
        try {
            val t = build(samples)
            t.setVolume(gain)
            t.play()
            synchronized(oneShots) { oneShots += t }
            handler.postDelayed({
                synchronized(oneShots) { oneShots -= t }
                runCatching { t.stop() }
                t.release()
            }, Tones.durationMs(samples) + 100)
        } catch (e: Exception) {
            Log.e(TAG, "one-shot failed", e)
        }
    }

    private fun stopLoop() {
        loopTrack?.let { runCatching { it.stop() }; it.release() }
        loopTrack = null
    }

    override fun stop() {
        stopLoop()
        synchronized(oneShots) {
            oneShots.forEach { runCatching { it.stop() } }
        }
    }

    override fun requestFocus() {
        if (focus != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(alarmAttributes())
            .build()
        am?.requestAudioFocus(req)
        focus = req
    }

    override fun abandonFocus() {
        focus?.let { am?.abandonAudioFocusRequest(it) }
        focus = null
    }

    override fun setAlarmVolumeMax(): Boolean {
        val m = am ?: return false
        return try {
            m.setStreamVolume(AudioManager.STREAM_ALARM, m.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            true
        } catch (e: SecurityException) {
            // DND without notification policy access; surfaced in the onboarding checklist.
            false
        }
    }

    override fun isAlarmVolumeMax(): Boolean {
        val m = am ?: return true
        return m.getStreamVolume(AudioManager.STREAM_ALARM) >= m.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    }

    override fun vibrate(pattern: LongArray, repeat: Boolean) {
        val v = vibrator ?: return
        val effect = VibrationEffect.createWaveform(pattern, if (repeat) 0 else -1)
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, alarmAttributes())
        }
    }

    override fun cancelVibrate() {
        vibrator?.cancel()
    }

    override fun release() {
        stop()
        abandonFocus()
        cancelVibrate()
    }

    companion object {
        private const val TAG = "AudioBackend"

        fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
