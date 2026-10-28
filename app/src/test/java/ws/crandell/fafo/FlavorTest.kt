package ws.crandell.fafo

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.mail.MailProviderKind
import ws.crandell.fafo.mail.flavor.FlavorMail

/** Runs in both flavors: oss has no Google classes and no Gmail option; play has both. */
class FlavorTest {
    private fun hasClass(name: String) = runCatching { Class.forName(name) }.isSuccess

    @Test fun flavorMatchesItsLibraries() {
        val identity = "com.google.android.gms.auth.api.identity.Identity"
        when (FlavorMail.FLAVOR) {
            "oss" -> {
                assertThat(FlavorMail.providers).containsExactly(MailProviderKind.SMTP)
                assertThat(hasClass(identity)).isFalse()
                assertThat(hasClass("com.google.android.gms.common.api.GoogleApi")).isFalse()
            }
            "play" -> {
                assertThat(FlavorMail.providers).containsExactly(MailProviderKind.SMTP, MailProviderKind.GMAIL)
                assertThat(hasClass(identity)).isTrue()
            }
            else -> error("unknown flavor ${FlavorMail.FLAVOR}")
        }
    }
}
