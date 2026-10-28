package ws.crandell.fafo.intercom

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * AC10.6 microphone: `AudioRecord` on `VOICE_COMMUNICATION` (echo cancel / noise suppression where
 * available), 16 kHz mono, 20 ms frames to [onFrame]. Live only. The caller checks RECORD_AUDIO and opens
 * this only while a verified listener is connected or Talk is held (AC10.3, AC10.4).
 */
@SuppressLint("MissingPermission")
class MicCapture(private val onFrame: (ShortArray) -> Unit) {
    @Volatile private var running = false
    private var worker: Thread? = null

    val isOpen: Boolean get() = running

    fun open(): Boolean {
        if (running) return true
        val min = AudioRecord.getMinBufferSize(AudioFormat.SAMPLE_RATE, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, AudioFormat.SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, AudioFormat.FRAME_SAMPLES * 2 * 4),
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
        if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
        running = true
        worker = thread(name = "fafo-mic") {
            try {
                rec.startRecording()
                val buf = ShortArray(AudioFormat.FRAME_SAMPLES)
                while (running) {
                    var n = 0
                    while (n < buf.size && running) {
                        val r = rec.read(buf, n, buf.size - n)
                        if (r <= 0) break
                        n += r
                    }
                    if (n == buf.size) onFrame(buf.copyOf())
                }
            } catch (e: Exception) {
                Log.w(TAG, "capture", e)
            } finally {
                runCatching { rec.stop() }
                rec.release()
                running = false
            }
        }
        return true
    }

    /** Closes the mic; the capture thread ends within one frame (well under the 1 s of AC10.3). */
    fun close() {
        running = false
        worker = null
    }

    companion object {
        private const val TAG = "MicCapture"
    }
}

/** AC10.6 playback: `AudioTrack` with `USAGE_VOICE_COMMUNICATION`, fed from a jitter buffer every 20 ms. */
class Speaker(private val source: () -> ShortArray?) {
    @Volatile private var running = false
    @Volatile private var rate = AudioFormat.SAMPLE_RATE
    private var worker: Thread? = null
    private val extra = java.util.concurrent.ConcurrentLinkedQueue<ShortArray>()

    fun start(sampleRate: Int = AudioFormat.SAMPLE_RATE) {
        if (running && sampleRate == rate) return
        stop()
        rate = sampleRate
        running = true
        worker = thread(name = "fafo-speaker") {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(rate / 50 * 2 * 4)
                .build()
            try {
                track.play()
                val silence = ShortArray(rate / 50)
                while (running) {
                    val pcm = extra.poll() ?: source() ?: silence
                    track.write(pcm, 0, pcm.size)
                }
            } catch (e: Exception) {
                Log.w("Speaker", "playback", e)
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    /** Short tones (start / end beep, busy, chime) mixed in ahead of the stream. */
    fun tone(freq: Double, ms: Int, repeats: Int = 1) {
        val n = rate * ms / 1000
        repeat(repeats) {
            extra += ShortArray(n) { i -> (sin(2 * PI * freq * i / rate) * 8000 * (1 - i.toDouble() / n)).toInt().toShort() }
            extra += ShortArray(rate * 60 / 1000)
        }
    }

    fun stop() {
        running = false
        worker = null
        extra.clear()
    }
}
