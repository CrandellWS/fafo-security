package ws.crandell.fafo.ui.help

import ws.crandell.fafo.ui.OnboardingStep

/** System settings a help topic can open (the same deep links the Setup checklist uses). */
enum class HelpLink(val label: String) {
    FULL_SCREEN("Open full-screen setting"),
    BATTERY("Allow background use"),
    APP_DETAILS("Open FAFO's app settings"),
    SECURITY("Open security settings"),
    DND("Open Do Not Disturb access"),
    NOTIFICATIONS("Open notification settings"),
}

enum class HelpIcon { START, SHIELD, GRACE, SIREN, MOTION, CAMERA, POWER, PAIR, CIRCLES, REMOTE, MIC, WIFI, LOCK, SAMSUNG, LIMITS, PRIVACY }

/** A titled block inside a topic; [steps] render as a numbered list under [text]. */
data class HelpSection(val heading: String? = null, val text: String = "", val steps: List<String> = emptyList())

data class HelpTopic(
    val id: String,
    val title: String,
    val summary: String,
    val icon: HelpIcon,
    val sections: List<HelpSection>,
    val links: List<HelpLink> = emptyList(),
)

/** The Help guide: every feature, every permission and the Samsung-specific settings. Pure data, unit tested. */
object HelpContent {
    val topics: List<HelpTopic> = listOf(
        HelpTopic(
            "start", "Getting started", "Three things to do before the first night", HelpIcon.START,
            listOf(
                HelpSection(
                    text = "FAFO Security protects this phone while it charges. If someone unplugs it while it is locked, " +
                        "it counts down, then sounds a loud siren and takes photos. Only your screen lock stops it.",
                    steps = listOf(
                        "Open the Setup tab and finish every step marked To do. The badge on the tab shows how many are left.",
                        "Plug the phone in, lock it and tap ARM on Home.",
                        "Run the self-test once so you know how loud it is.",
                    ),
                ),
                HelpSection("Turn off Smart Lock", "Smart Lock and Samsung Extend Unlock keep the phone unlocked. A thief could then unplug it without setting off the alarm, and a swipe would stop the siren. See Samsung Galaxy settings."),
            ),
        ),
        HelpTopic(
            "arming", "Arming and disarming", "Plug in, lock, arm. Unlock to stop", HelpIcon.SHIELD,
            listOf(
                HelpSection(text = "ARM works only while the phone is charging and has a PIN, pattern or password. A notification shows the whole time FAFO is armed."),
                HelpSection(
                    "Confirming it's you",
                    "Arm and Disarm ask for your fingerprint or screen lock, so someone who picks up your unlocked phone can't turn FAFO off. " +
                        "If you unlocked the phone a moment ago, FAFO doesn't ask again (see Unlock grace window).",
                ),
                HelpSection("Disarm from the notification", "The Disarm button on the notification opens FAFO and asks the same way."),
                HelpSection("Stopping an alarm", "Unlock the phone with your PIN, pattern, password or fingerprint. That ends the alarm. Volume buttons and swiping the notification don't."),
            ),
        ),
        HelpTopic(
            "grace", "Unlock grace window", "When Arm and Disarm skip the fingerprint prompt", HelpIcon.GRACE,
            listOf(
                HelpSection(
                    text = "After you unlock the phone, Arm and Disarm work without asking again for the time you choose in Settings > Security: " +
                        "Off, 15 s, 30 s (default), 1 min or 2 min.",
                ),
                HelpSection("Off", "Every Arm and Disarm asks for your fingerprint or screen lock."),
                HelpSection(
                    "What counts as an unlock",
                    "Your PIN, pattern, password or a strong (Class 3) fingerprint. Smart Lock, Extend Unlock and most face unlock don't count, so FAFO asks.",
                ),
                HelpSection("Changing it", "Changing the window asks for your screen lock too. Cancel keeps the old value."),
            ),
        ),
        HelpTopic(
            "prealarm", "Pre-alarm and siren", "The countdown, the siren and the self-test", HelpIcon.SIREN,
            listOf(
                HelpSection(
                    text = "When the phone is unplugged while locked, FAFO waits for the unplug sensitivity (default 0.5 s) so a loose cable doesn't count. " +
                        "Then the red screen shows PLUG ME BACK IN with a countdown (default 5 s) and beeps faster and faster.",
                ),
                HelpSection("Cancel it", "Plug the charger back in, or unlock the phone."),
                HelpSection("The siren", "When the countdown ends the full siren plays at alarm volume, even on silent. Pick Klaxon, Wail or Pulse in Settings."),
                HelpSection("Self-test", "Setup > Self-test plays a 1 s chirp and a 1 s siren and takes a test photo that is not saved."),
            ),
        ),
        HelpTopic(
            "motion", "Motion chirps", "Short chirps if the armed phone is bumped or picked up", HelpIcon.MOTION,
            listOf(
                HelpSection(text = "While armed, a bump gives one short chirp and a pick-up gives three louder ones. Motion never starts the full siren; only unplugging does."),
                HelpSection("Sensitivity", "Settings > Motion chirps: Off, Low, Medium (default) or High."),
            ),
        ),
        HelpTopic(
            "photos", "Photos and incidents", "Who unplugged it, kept on this phone", HelpIcon.CAMERA,
            listOf(
                HelpSection(
                    text = "When the countdown starts, FAFO takes a photo, then one every 10 s (5 to 30 s in Settings) with the front, back or both cameras. " +
                        "Each alarm is saved as an incident under Home > Incidents.",
                ),
                HelpSection("Kept on this phone", "Photos are never sent anywhere. You can share one yourself from the photo viewer, or delete it."),
                HelpSection("Storage", "Photos stop at the storage cap (100 MB by default) and pause when less than 500 MB is free. The siren keeps going."),
            ),
        ),
        HelpTopic(
            "reboots", "Reboots and power-off", "The siren comes back after a restart", HelpIcon.POWER,
            listOf(
                HelpSection(
                    text = "A thief can still hold the power button to turn the phone off. If the alarm was going, the siren starts again as soon as the phone boots, before anyone unlocks it.",
                ),
                HelpSection("No photos after a reboot", "Android keeps the camera and FAFO's photo storage locked until your first unlock, so v1 takes no photos after a restart."),
                HelpSection("Theft protection", "Turn on Android's Theft Detection Lock, Offline Device Lock and Identity Check too. FAFO adds to them; it doesn't replace them."),
            ),
            links = listOf(HelpLink.SECURITY),
        ),
        HelpTopic(
            "pairing", "Pairing your phones", "Link your own phones over Bluetooth", HelpIcon.PAIR,
            listOf(
                HelpSection(
                    text = "Pair only phones you own or trust. Open Devices > Paired phones on both phones and hold them close together.",
                    steps = listOf(
                        "On one phone tap Show this phone; on the other tap Find the other phone.",
                        "Both phones show a 6-digit code. Tap Codes match only if they are the same.",
                        "Confirm with your screen lock. The phone joins the circle you picked (Home by default).",
                    ),
                ),
                HelpSection("Nearby devices", "Pairing needs the Nearby devices permission. FAFO never uses it to find your location."),
            ),
        ),
        HelpTopic(
            "circles", "Circles and permissions", "Who can do what to this phone", HelpIcon.CIRCLES,
            listOf(
                HelpSection(
                    text = "A circle is a group of your paired phones, like Home or Work. Each circle has powers its members get over this phone: " +
                        "Alerts (they hear about your alarm), Reset, Disarm and Intercom.",
                ),
                HelpSection("Defaults", "Home allows all four. New circles start with Intercom only; the Work suggestion adds Reset."),
                HelpSection("Guarded powers", "Granting Reset or Disarm asks for your screen lock. When someone invites you, you can turn off any power before you accept."),
            ),
        ),
        HelpTopic(
            "remote", "Remote reset and disarm", "Stop a trusted phone's alarm from yours", HelpIcon.REMOTE,
            listOf(
                HelpSection(text = "When a phone in your circle alarms, you get an alert with Reset and Disarm buttons, if that circle gave you those powers."),
                HelpSection("Reset", "Stops the alarm but keeps the phone armed. If it is still unplugged, it waits until it is plugged in again."),
                HelpSection("Disarm", "Ends the alarm and disarms the phone."),
                HelpSection("Safety", "Each command is signed with a key that only works after your fingerprint or screen lock, and the other phone checks it. A Reset never disarms."),
            ),
        ),
        HelpTopic(
            "intercom", "Intercom and push-to-talk", "Talk to your other phones on the same Wi-Fi or Bluetooth", HelpIcon.MIC,
            listOf(
                HelpSection("Push-to-talk", "Hold the big button on the Intercom tab to talk to one phone or to All in a circle. The mic is on only while you hold it."),
                HelpSection(
                    "Room mode",
                    "Turn on room mode on a phone to let the chosen circles listen to that room. It shows a notification naming who is listening, and Android shows its mic indicator. Nothing is recorded.",
                ),
                HelpSection("During an alarm", "An alarm turns room mode off and drops listeners until the incident ends."),
                HelpSection("Microphone permission", "Asked the first time you use the intercom, never when you arm."),
            ),
        ),
        HelpTopic(
            "reachable", "Stay reachable", "Alerts and push-to-talk in the background", HelpIcon.WIFI,
            listOf(
                HelpSection(
                    text = "While FAFO is open, your circles can reach this phone. Turn on Settings > Stay reachable to keep it reachable in the background. " +
                        "A notification \"Reachable by: ...\" shows the whole time.",
                ),
                HelpSection("Without it", "Bluetooth alarm alerts from nearby phones still arrive."),
                HelpSection("Local only", "FAFO talks only to your paired phones on the same Wi-Fi or over Bluetooth. Nothing goes over the internet."),
            ),
        ),
        HelpTopic(
            "permissions", "Permissions explained", "Every permission and why FAFO needs it", HelpIcon.LOCK,
            listOf(
                HelpSection("Screen lock", "Without a PIN, pattern or password a thief just swipes to unlock and stops the siren. FAFO won't arm without one."),
                HelpSection("Notifications", "FAFO always shows a notification while armed, alarming, reachable or in room mode. It can't arm without it."),
                HelpSection("Camera", "Photos of whoever unplugs the phone. Optional: FAFO arms without it and shows \"Photos off\"."),
                HelpSection("Full-screen notifications", "Lets the red alarm screen open by itself over the lock screen."),
                HelpSection("Do Not Disturb access", "Lets FAFO raise the alarm volume under Do Not Disturb."),
                HelpSection("Battery (unrestricted)", "Stops the phone from putting FAFO to sleep while it guards."),
                HelpSection("Nearby devices and Bluetooth", "Pairing and alerts between your own phones. Never used for location."),
                HelpSection("Microphone", "Intercom and push-to-talk only, asked at first use."),
            ),
            links = listOf(HelpLink.APP_DETAILS),
        ),
        HelpTopic(
            "samsung", "Samsung Galaxy settings", "Full-screen alarm, battery, Smart Lock and more", HelpIcon.SAMSUNG,
            listOf(
                HelpSection(text = "One UI 8 / Android 16 names; menus can differ by firmware. Firmware updates can reset these, so check after each update."),
                HelpSection(
                    "Full-screen alarm",
                    steps = listOf(
                        "Settings > Apps > FAFO Security > Notifications.",
                        "Turn on Allow full screen notifications.",
                        "Under Notification categories keep Alarm screen on and set to Alert.",
                    ),
                ),
                HelpSection(
                    "Battery",
                    steps = listOf(
                        "Settings > Apps > FAFO Security > Battery > Unrestricted.",
                        "Settings > Battery > Background usage limits: remove FAFO from Sleeping apps and Deep sleeping apps, and add it to Never sleeping apps.",
                        "Turn off Put unused apps to sleep.",
                    ),
                ),
                HelpSection(
                    "Smart Lock / Extend Unlock",
                    "Turn it off (Settings > Lock screen and AOD > Secure lock settings > Extend Unlock). While it keeps the phone unlocked, an unplug never counts and a swipe stops the siren. " +
                        "Unlocks by Extend Unlock or face recognition also don't count for the grace window, so FAFO asks for your fingerprint or PIN.",
                ),
                HelpSection("Do Not Disturb", "Allow FAFO in Do Not Disturb access. \"Total silence\" still mutes alarms, so avoid it while armed."),
                HelpSection("Theft protection", "Settings > Security and privacy > Lost device protection > Theft protection: turn on Theft Detection Lock, Offline Device Lock and Identity Check."),
            ),
            links = listOf(HelpLink.FULL_SCREEN, HelpLink.BATTERY, HelpLink.DND),
        ),
        HelpTopic(
            "limits", "What FAFO can't do", "Honest limits of v1", HelpIcon.LIMITS,
            listOf(
                HelpSection(
                    steps = listOf(
                        "It protects this phone only, while it is plugged in and locked.",
                        "Motion alone never starts the siren.",
                        "A thief can still power the phone off. The siren returns after the restart, but there are no photos after a reboot.",
                        "Do Not Disturb \"Total silence\" mutes alarms.",
                        "Your other phones hear about an alarm only if they are on the same Wi-Fi or in Bluetooth range. There is no internet relay.",
                        "It is not a tracker: no location, no remote listening during alarms.",
                    ),
                ),
            ),
        ),
        HelpTopic(
            "privacy", "Privacy", "Nothing leaves your devices", HelpIcon.PRIVACY,
            listOf(
                HelpSection(
                    text = "FAFO has no account, no server, no analytics and no ads. Photos, keys and settings stay on your phone. " +
                        "Messages between your paired phones are end-to-end encrypted and travel only over your Wi-Fi or Bluetooth.",
                ),
                HelpSection("Your own phones only", "FAFO has no hidden mode and always shows a notification while it runs. Use it only on phones you own."),
                HelpSection("Fonts", "The Barlow Condensed font is used under the SIL Open Font License 1.1."),
            ),
        ),
    )

    fun topic(id: String): HelpTopic? = topics.firstOrNull { it.id == id }

    /** The Help topic behind each Setup step's "Learn more". */
    fun topicFor(step: OnboardingStep): String = when (step) {
        OnboardingStep.INTRO -> "start"
        OnboardingStep.SECURE_LOCK, OnboardingStep.NOTIFICATIONS -> "permissions"
        OnboardingStep.CAMERA -> "photos"
        OnboardingStep.FULL_SCREEN, OnboardingStep.DND, OnboardingStep.BATTERY -> "samsung"
        OnboardingStep.BUILT_IN -> "reboots"
        OnboardingStep.SELF_TEST -> "prealarm"
    }
}
