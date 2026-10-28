package ws.crandell.fafo.siren

import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioAttributesTest {
    @Test fun sirenUsesAlarmUsageAndSonification() {
        val a = AndroidAudioBackend.alarmAttributes()
        assertThat(a.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
        assertThat(a.contentType).isEqualTo(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    }

    @Test fun backendPlaysWithoutCrashing() {
        val b = AndroidAudioBackend(ApplicationProvider.getApplicationContext())
        b.requestFocus()
        b.playLoop(Tones.siren("Klaxon"), 1f)
        b.playOnce(Tones.beep(), 0.4f)
        assertThat(b.setAlarmVolumeMax()).isTrue()
        assertThat(b.isAlarmVolumeMax()).isTrue()
        b.release()
    }
}
