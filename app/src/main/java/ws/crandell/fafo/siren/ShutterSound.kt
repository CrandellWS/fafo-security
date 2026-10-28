package ws.crandell.fafo.siren

import android.media.MediaActionSound

/**
 * PHOTO TAKEN (device feedback 2026-10-28): the system camera shutter click, so whoever holds the phone hears
 * that a photo was taken. Separate from the siren's audio track; the armed service calls it only while the
 * siren is not playing, so it can never interrupt or duck the siren.
 */
object ShutterSound {
    private val sound: MediaActionSound by lazy { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }

    fun play() {
        runCatching { sound.play(MediaActionSound.SHUTTER_CLICK) }
    }
}
