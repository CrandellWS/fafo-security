package ws.crandell.fafo.ui.help

import ws.crandell.fafo.ui.OnboardingStep
import ws.crandell.fafo.mail.MailProviderKind
import ws.crandell.fafo.mail.flavor.FlavorMail

/** System settings a help topic can open (the same deep links the Setup checklist uses). */
enum class HelpLink(val label: String) {
    FULL_SCREEN("Open full-screen setting"),
    BATTERY("Allow background use"),
    APP_DETAILS("Open FAFO's app settings"),
    SECURITY("Open security settings"),
    DND("Open Do Not Disturb access"),
    NOTIFICATIONS("Open notification settings"),
}

enum class HelpIcon { MAIL, FLASH, THEME, STATUS, START, SHIELD, GRACE, SIREN, MOTION, CAMERA, POWER, PAIR, CIRCLES, REMOTE, MIC, WIFI, LOCK, SAMSUNG, LIMITS, PRIVACY }

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
    val topics: List<HelpTopic> get() = all.filter { it.id != "gmail" || MailProviderKind.GMAIL in FlavorMail.providers }

    private val all: List<HelpTopic> = listOf(
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
                    "The ARMING countdown",
                    "After you confirm Arm, a yellow ARMING countdown runs (5 s by default; 3 or 10 s in Settings > Security), then FAFO is ARMED. " +
                        "Cancel arming needs no fingerprint, because cancelling leaves the phone unprotected. Unplugging during the countdown cancels it; nothing goes off.",
                ),
                HelpSection(
                    "Confirming it's you",
                    "Arm and Disarm ask for your fingerprint or screen lock, so someone who picks up your unlocked phone can't turn FAFO off. " +
                        "Arm skips the prompt if you unlocked the phone a moment ago. Disarm is stricter: it skips the prompt only if you locked " +
                        "the phone after arming, then unlocked it with your PIN, pattern, password or fingerprint, within the grace window. " +
                        "Otherwise Disarm always asks.",
                ),
                HelpSection("Disarm from the notification", "The Disarm button on the notification opens FAFO and asks the same way."),
                HelpSection("Stopping an alarm", "Unlock the phone with your PIN, pattern, password or fingerprint. That ends the alarm. Volume buttons and swiping the notification don't."),
            ),
        ),
        HelpTopic(
            "grace", "Unlock grace window", "When Arm and Disarm skip the fingerprint prompt", HelpIcon.GRACE,
            listOf(
                HelpSection(
                    text = "After you unlock the phone, Arm works without asking again for the time you choose in Settings > Security: " +
                        "Off, 15 s, 30 s (default), 1 min or 2 min.",
                ),
                HelpSection(
                    "Disarm needs an unlock after arming",
                    "Disarm skips the prompt only when the phone was locked after you armed it and then unlocked within the window. " +
                        "Tapping Disarm right after arming always asks: otherwise anyone holding the phone in those seconds could disarm it.",
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
                HelpSection(text = "While armed, a bump gives one short chirp and turns the status yellow; repeated bumps or a pick-up give three louder ones and turn it orange. While locked, continued handling can start the countdown (Settings > Motion can sound the alarm)."),
                HelpSection("Sensitivity", "Settings > Motion chirps: Off, Low, Medium (default) or High."),
                HelpSection("Bump reset time", "Settings > Bump reset time: how long after the last bump the status stays yellow or orange before going back to green. 10, 15 (default), 30 or 60 s."),
                HelpSection(
                    "Put it down and it calms down",
                    "If handling started the countdown, putting the phone down and leaving it still for about 2 seconds cancels it, as long as it is still plugged in. " +
                        "The status stays orange until the bump reset time runs out, so picking it up again starts a new countdown. " +
                        "Walking off with it, or unplugging it, still sets off the alarm. The countdown is your Pre-alarm setting (at least 5 s for handling).",
                ),
            ),
        ),
        HelpTopic(
            "handsoff", "HANDS OFF screen", "The warning that wakes up when someone touches the armed phone", HelpIcon.MOTION,
            listOf(
                HelpSection(
                    text = "While FAFO is armed and the screen is off or locked, a bump or a pick-up turns the screen on over the lock screen " +
                        "with a big HANDS OFF warning: yellow for a bump, orange for a pick-up, red with the countdown if the alarm is about to sound.",
                ),
                HelpSection("It goes away by itself", "Left alone, the warning closes after the bump reset time and the screen goes dark again. Unlocking the phone closes it too."),
                HelpSection("Nobody else can stop it", "The Disarm button on it asks for your PIN, pattern, password or fingerprint first, the same as everywhere else."),
                HelpSection(
                    "Full-screen permission",
                    "The warning uses the same full-screen permission as the alarm screen. Without it you get a HANDS OFF pop-up notification instead, and the screen does not turn on.",
                ),
            ),
            links = listOf(HelpLink.FULL_SCREEN),
        ),
        HelpTopic(
            "photos", "Photos and incidents", "Who unplugged it, kept on this phone", HelpIcon.CAMERA,
            listOf(
                HelpSection(
                    text = "When the countdown starts, FAFO takes a photo, then one every 10 s (5 to 30 s in Settings) with the front, back or both cameras. " +
                        "Each alarm is saved as an incident under Home > Incidents.",
                ),
                HelpSection(
                    "PHOTO TAKEN",
                    "Each photo is shown on the alarm and HANDS OFF screens: a white flash, PHOTO TAKEN with the photo for about 2 seconds, and a count (\"3 photos taken\"). " +
                        "Before the siren starts a camera shutter click plays too; once the siren is going it stays silent so nothing interrupts the siren. " +
                        "With Remove animations on (Android accessibility settings) there is no flash, just the label.",
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
                HelpSection("From the lock screen", "Talk on the armed notification and the FAFO Talk tile open push-to-talk without unlocking. See Lock-screen shortcuts."),
            ),
        ),
        HelpTopic(
            "shortcuts", "Lock-screen shortcuts", "Flashlight and Talk without unlocking", HelpIcon.FLASH,
            listOf(
                HelpSection(
                    text = "While FAFO is armed, its notification has Flashlight and Talk buttons that work from the lock screen. " +
                        "Disarm stays behind your screen lock, as always.",
                ),
                HelpSection("Flashlight", "Turns the camera flash on or off. Phones without a flash don't show it. During an alarm the flash belongs to the alarm light."),
                HelpSection(
                    "Talk",
                    "Opens a push-to-talk screen over the lock screen: pick a circle and a phone (or All) and hold the button. " +
                        "The mic is on only while you hold it, and nothing is recorded. It has no Disarm, settings, room mode or listening. " +
                        "Open FAFO on that screen asks for your screen lock first. Talk works after the first unlock since the phone started.",
                ),
                HelpSection(
                    "Quick Settings tiles",
                    "Add FAFO Light and FAFO Talk to Quick Settings. They work whether or not FAFO is armed.",
                    steps = listOf(
                        "Swipe down from the top of the screen twice to open Quick Settings.",
                        "Tap the pencil (Edit) button. On Samsung it can be in the menu at the top right.",
                        "Drag FAFO Light and FAFO Talk into your tiles, then tap Done.",
                    ),
                ),
                HelpSection(
                    "What a stranger can do with them",
                    "Anyone holding the phone can switch the flashlight and talk to your circles, and the Talk screen shows the names of your circles and phones. " +
                        "Nobody can disarm, reset, listen in or change anything from them.",
                ),
            ),
        ),
        HelpTopic(
            "pinning", "Pin app while armed", "Keep the status screen in front", HelpIcon.LOCK,
            listOf(
                HelpSection(
                    text = "Off by default. With Settings > Security > Pin app while armed on, FAFO opens its status screen when you arm and asks " +
                        "Android to pin it (Android's screen pinning). While pinned, Home, Recents and notifications are blocked, so whoever picks up " +
                        "the phone stays on FAFO's screen.",
                ),
                HelpSection(
                    "Turn on \"Ask for PIN before unpinning\"",
                    "Pinning is only as strong as this Android setting: with it, unpinning locks the phone. Menus differ by phone.",
                    steps = listOf(
                        "Samsung: Settings > Security and privacy > More security settings > Pin windows (or Pin app). Turn it on, then turn on Ask for PIN before unpinning.",
                        "Other phones: Settings > Security > App pinning.",
                    ),
                ),
                HelpSection(
                    "The way out",
                    steps = listOf(
                        "Disarm on the status screen ends the pin, then asks for your screen lock as usual.",
                        "Unpin under the Disarm button ends the pin and keeps FAFO armed.",
                        "Android's own unpin always works: hold Back and Recents together, or with gesture navigation swipe up and hold.",
                    ),
                ),
                HelpSection("Android asks first", "The first time, Android shows Pin app? Tap Got it or Pin. If you decline, FAFO stays armed without the pin."),
                HelpSection("While pinned", "The alarm screen still opens and the siren still sounds. Notification buttons (Flashlight, Talk) can't be reached until you unpin."),
            ),
            links = listOf(HelpLink.SECURITY),
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
            "status", "Status screen colors", "Green, yellow, orange, red and the eagle's eye", HelpIcon.STATUS,
            listOf(
                HelpSection(
                    text = "While armed, the full-screen status shows the FAFO shield. Its stripes are a traffic light; the state word under it always says the same thing in words.",
                    steps = listOf(
                        "Green: armed and calm.",
                        "Yellow: ARMING, or one bump (a short chirp).",
                        "Orange: repeated bumps close together (a louder chirp), or the countdown before the siren.",
                        "Red: the siren. The whole screen flashes red.",
                    ),
                ),
                HelpSection("The eagle's eye", "White while disarmed, red once armed. The beak is always red."),
                HelpSection(
                    "Bumps can set off the alarm",
                    "While the phone is locked, continued handling or picking it up starts the countdown (at least 5 s, so you can unlock). " +
                        "Left alone it goes back to green after the Bump reset time (Settings > Motion: 10, 15, 30 or 60 s; 15 s by default). Turn off \"Motion can sound the alarm\" in Settings for chirps only.",
                ),
            ),
        ),
        HelpTopic(
            "flash", "Flash alarm light", "Strobe at red, blink at orange", HelpIcon.FLASH,
            listOf(
                HelpSection(text = "The camera flash strobes about four times a second during the alarm and blinks slowly at orange. It goes dark for a moment while FAFO takes a photo."),
                HelpSection("Turn it off", "Settings > Alarm > Flash as alarm light. Phones without a flash skip it."),
                HelpSection("Flashing lights", "If flashing light can affect you or people nearby, turn it off. The screen flash during the alarm stays at 2 times a second."),
            ),
        ),
        HelpTopic(
            "email", "Photo email", "Send trigger photos to your inbox", HelpIcon.MAIL,
            listOf(
                HelpSection(text = "Off by default. When it's on, FAFO emails trigger photos to up to five addresses from your own mail account: the first photo at once, then a batch about every minute."),
                HelpSection(
                    "Set it up",
                    steps = listOf(
                        "Settings > Photo email: turn on Email photos on alarm and enter the addresses.",
                        "Enter your provider's mail server, choose SSL/TLS (465) or STARTTLS (587), and your username.",
                        "Create an app password with your provider and paste it. Gmail, Outlook and Yahoo require one when two-step verification is on.",
                        "Tap Send test email and check the inbox (and spam).",
                    ),
                ),
                HelpSection("Why an app password", "It works only for sending mail, you can revoke it any time, and your main password never goes into FAFO. It is stored encrypted on the phone."),
                HelpSection("No connection", "Photos wait in a queue and go out when the phone is online again while FAFO is armed or open. They are kept until sent."),
            ),
        ),
        HelpTopic(
            "open-wifi", "Open Wi-Fi fallback", "When queued photos have no connection", HelpIcon.WIFI,
            listOf(
                HelpSection(text = "Optional, Android 13 and later. If photos are waiting, there is no mobile data and no known Wi-Fi, FAFO suggests nearby open Wi-Fi networks to Android, and sends only once the network really reaches the internet."),
                HelpSection("Allow it in advance", "Android asks once whether FAFO may suggest Wi-Fi networks. Nobody can answer that during a theft, so Setup asks now."),
                HelpSection("Sign-in pages", "Hotel, airport and cafe networks with a sign-in page can't be passed automatically. FAFO skips them."),
                HelpSection("Not on every phone", "To find open networks FAFO needs Android's list of nearby Wi-Fi. Some Android versions give that list only to apps with location access, which FAFO never asks for. On those phones this fallback finds nothing, and the photos wait for mobile data or a known Wi-Fi."),
                HelpSection("Afterwards", "FAFO removes its suggestions once the photos are sent, after 30 minutes, or when you disarm. Mail is always encrypted, so an open network sees nothing readable."),
            ),
        ),
        HelpTopic(
            "theme", "Light and dark", "Follow the phone, or choose", HelpIcon.THEME,
            listOf(
                HelpSection(text = "FAFO follows your phone's dark mode. Settings > Display > Theme forces Light or Dark. The lock-screen status screen uses the same choice."),
            ),
        ),
        HelpTopic(
            "gmail", "Gmail sign-in", "Play version: send with your Google account", HelpIcon.MAIL,
            listOf(
                HelpSection(text = "In the Play version, Settings > Photo email > Send with: Google account, then Sign in with Google. FAFO asks only for permission to send email as you; it cannot read your mail."),
                HelpSection("Disconnect", "Remove FAFO in your Google Account > Security > Third-party access, or switch back to a mail server in Settings."),
            ),
        ),
        HelpTopic(
            "privacy", "Privacy", "No FAFO server, no analytics", HelpIcon.PRIVACY,
            listOf(
                HelpSection(
                    text = "FAFO has no account, no server, no analytics and no ads. Photos, keys and settings stay on your phone. " +
                        "Messages between your paired phones are end-to-end encrypted and travel only over your Wi-Fi or Bluetooth.",
                ),
                HelpSection(
                    "Photo email is the one exception, and it is off by default",
                    "If you turn on Photo email, trigger photos are sent from your own mail account (your mail server, or your Google account " +
                        "in the Play version) to the addresses you chose. FAFO never runs a server and never sees them.",
                ),
                HelpSection("Your own phones only", "FAFO has no hidden mode and always shows a notification while it runs. Use it only on phones you own."),
                HelpSection("Fonts", "The Barlow Condensed font is used under the SIL Open Font License 1.1."),
            ),
        ),
    )

    fun topic(id: String): HelpTopic? = topics.firstOrNull { it.id == id }

    /** Every topic, including flavor-specific ones (tests). */
    val allTopics: List<HelpTopic> get() = all

    /** The Help topic behind each Setup step's "Learn more". */
    fun topicFor(step: OnboardingStep): String = when (step) {
        OnboardingStep.INTRO -> "start"
        OnboardingStep.SECURE_LOCK, OnboardingStep.NOTIFICATIONS -> "permissions"
        OnboardingStep.CAMERA -> "photos"
        OnboardingStep.FULL_SCREEN, OnboardingStep.DND, OnboardingStep.BATTERY -> "samsung"
        OnboardingStep.BUILT_IN -> "reboots"
        OnboardingStep.SELF_TEST -> "prealarm"
        OnboardingStep.PHOTO_EMAIL -> "email"
        OnboardingStep.OPEN_WIFI -> "open-wifi"
        OnboardingStep.FLASH -> "flash"
    }
}
