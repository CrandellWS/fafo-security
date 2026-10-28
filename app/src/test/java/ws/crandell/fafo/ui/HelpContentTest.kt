package ws.crandell.fafo.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ws.crandell.fafo.ui.help.HelpContent
import ws.crandell.fafo.ui.help.HelpLink

class HelpContentTest {
    @Test fun everySetupStepHasAHelpTopic() {
        OnboardingStep.entries.forEach { step -> assertThat(HelpContent.topic(HelpContent.topicFor(step))).isNotNull() }
    }

    @Test fun coversEveryFeatureAndTheSamsungSettings() {
        assertThat(HelpContent.topics.map { it.id }).containsAtLeast(
            "start", "arming", "grace", "prealarm", "motion", "photos", "reboots", "pairing", "circles", "remote",
            "intercom", "reachable", "permissions", "samsung", "limits", "privacy",
        )
        assertThat(HelpContent.topics.map { it.id }.toSet()).hasSize(HelpContent.topics.size)
        val samsung = HelpContent.topic("samsung")!!
        assertThat(samsung.sections.mapNotNull { it.heading }).containsAtLeast("Full-screen alarm", "Battery", "Smart Lock / Extend Unlock")
        assertThat(samsung.links).containsAtLeast(HelpLink.FULL_SCREEN, HelpLink.BATTERY)
    }

    @Test fun roundThreeTopics() {
        assertThat(HelpContent.topics.map { it.id }).containsAtLeast("email", "flash", "open-wifi", "theme", "status")
        assertThat(HelpContent.allTopics.map { it.id }).contains("gmail")
        val gmailShown = "gmail" in HelpContent.topics.map { it.id }
        assertThat(gmailShown).isEqualTo(ws.crandell.fafo.mail.flavor.FlavorMail.FLAVOR == "play")
        val wifi = HelpContent.topic("open-wifi")!!.sections.joinToString { it.heading + it.text }
        assertThat(wifi).contains("sign-in page")
        assertThat(wifi).contains("Android 13")
        val arming = HelpContent.topic("arming")!!.sections.joinToString { it.text }
        assertThat(arming).contains("ARMING countdown")
        assertThat(arming).contains("locked the phone after arming")
    }

    @Test fun deviceFeedbackTopics() {
        val hands = HelpContent.topic("handsoff")!!
        assertThat(hands.links).contains(HelpLink.FULL_SCREEN)
        val handsText = hands.sections.joinToString { it.heading + it.text }
        assertThat(handsText).contains("HANDS OFF")
        assertThat(handsText).contains("bump reset time")
        assertThat(HelpContent.topic("motion")!!.sections.joinToString { it.text }).contains("plugged in")
        assertThat(HelpContent.topic("photos")!!.sections.mapNotNull { it.heading }).contains("PHOTO TAKEN")
    }

    @Test fun optionalStepsNeverBlockTheBadge() {
        val s = OnboardingStatus(true, true, true, true, true, true, true, true, true, true, mailReady = false, openWifiWanted = true)
        assertThat(s.missing).isEmpty()
        assertThat(s.recommended).containsExactly(OnboardingStep.PHOTO_EMAIL, OnboardingStep.OPEN_WIFI)
        assertThat(OnboardingStatus(openWifiWanted = false).shown).doesNotContain(OnboardingStep.OPEN_WIFI)
    }

    @Test fun neverCalledABabyMonitor() {
        val all = HelpContent.topics.joinToString { t -> t.title + t.summary + t.sections.joinToString { it.text + it.steps } }
        assertThat(all.lowercase()).doesNotContain("baby monitor")
    }
}
