package ws.crandell.fafo.mail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** The fallback always cleans up: a crash leaves the saved list, the next cleanup removes it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OpenWifiFallbackTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun cleanupForgetsSavedSuggestions() {
        val f = File(ctx.filesDir, "open_wifi_suggestions.txt")
        f.writeText("Cafe\nLibrary")
        OpenWifiFallback.cleanup(ctx)
        assertThat(f.exists()).isFalse()
        assertThat(MailDispatcher.preferredNetwork).isNull()
    }

    @Test fun notApprovedUntilAndroidSaysSo() {
        assertThat(OpenWifiFallback.approved(ctx)).isFalse()
        OpenWifiFallback.requestApproval(ctx) // must not throw without a real Wi-Fi stack
    }
}
