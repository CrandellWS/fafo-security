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

    @Test fun neverCalledABabyMonitor() {
        val all = HelpContent.topics.joinToString { t -> t.title + t.summary + t.sections.joinToString { it.text + it.steps } }
        assertThat(all.lowercase()).doesNotContain("baby monitor")
    }
}
