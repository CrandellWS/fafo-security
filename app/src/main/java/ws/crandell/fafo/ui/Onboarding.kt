package ws.crandell.fafo.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import ws.crandell.fafo.system.DeviceStatus

/** Spec F7 onboarding steps, in order. Each is skippable and stays on the Home checklist until done. */
enum class OnboardingStep(val title: String, val detail: String) {
    INTRO(
        "What FAFO does",
        "Protects this phone only. A notification shows whenever it is armed. A thief can still power the " +
            "phone off; the siren comes back on the next boot. No photos after a reboot in v1. Turn off " +
            "Smart Lock / Samsung Extend Unlock: they keep the phone unlocked, so an unplug never counts and " +
            "a swipe stops the siren.",
    ),
    SECURE_LOCK("Screen lock", "A PIN, pattern or password is required. Without one a thief just swipes to unlock."),
    NOTIFICATIONS("Notifications", "Needed for the armed and alarm notifications."),
    CAMERA("Camera", "Takes photos of whoever unplugs the phone. Photos stay on this phone."),
    FULL_SCREEN(
        "Full-screen alarm",
        "Shows the red alarm screen over the lock screen by itself when the alarm goes off. Allow " +
            "\"Full screen notifications\" for FAFO, and keep the \"Alarm screen\" notification category on.",
    ),
    DND("Do Not Disturb access", "Lets FAFO raise the alarm volume under Do Not Disturb. \"Total silence\" still mutes alarms."),
    BATTERY(
        "Battery",
        "Allow FAFO to ignore battery optimization. On Samsung: Settings > Apps > FAFO Security > Battery > " +
            "Unrestricted; remove it from Sleeping / Deep sleeping apps or add it to Never sleeping apps; turn off " +
            "\"Put unused apps to sleep\". Firmware updates can revert these.",
    ),
    BUILT_IN(
        "Built-in protections",
        "Turn on Android's Theft Detection Lock, Offline Device Lock and Identity Check. FAFO is an add-on to " +
            "those, not a replacement.",
    ),
    SELF_TEST("Self-test", "Plays a 1 s chirp and a 1 s siren at alarm volume and takes a test photo."),
}

/** Snapshot of what is granted / done; pure data so the checklist can be tested. */
data class OnboardingStatus(
    val introSeen: Boolean = false,
    val secureLock: Boolean = false,
    val notifications: Boolean = false,
    val camera: Boolean = false,
    val fullScreen: Boolean = false,
    /** The full-screen alarm channel is on and at HIGH importance (see DeviceStatus.alarmChannelOk). */
    val alarmChannel: Boolean = true,
    val dnd: Boolean = false,
    val battery: Boolean = false,
    val builtInSeen: Boolean = false,
    val selfTestDone: Boolean = false,
) {
    fun isDone(step: OnboardingStep): Boolean = when (step) {
        OnboardingStep.INTRO -> introSeen
        OnboardingStep.SECURE_LOCK -> secureLock
        OnboardingStep.NOTIFICATIONS -> notifications
        OnboardingStep.CAMERA -> camera
        OnboardingStep.FULL_SCREEN -> fullScreen && alarmChannel
        OnboardingStep.DND -> dnd
        OnboardingStep.BATTERY -> battery
        OnboardingStep.BUILT_IN -> builtInSeen
        OnboardingStep.SELF_TEST -> selfTestDone
    }

    val missing: List<OnboardingStep> get() = OnboardingStep.entries.filterNot(::isDone)

    companion object {
        fun read(context: Context, introSeen: Boolean, builtInSeen: Boolean, selfTestDone: Boolean) = OnboardingStatus(
            introSeen = introSeen,
            secureLock = DeviceStatus.isDeviceSecure(context),
            notifications = DeviceStatus.hasNotificationPermission(context) && DeviceStatus.notificationsEnabled(context),
            camera = DeviceStatus.hasCamera(context),
            fullScreen = DeviceStatus.canUseFullScreenIntent(context),
            alarmChannel = DeviceStatus.alarmChannelOk(context),
            dnd = DeviceStatus.hasDndAccess(context),
            battery = DeviceStatus.ignoresBatteryOptimizations(context),
            builtInSeen = builtInSeen,
            selfTestDone = selfTestDone,
        )
    }
}

/** Deep links for each step (spec F7, BUILD-PLAN M3). */
object OnboardingIntents {
    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun security() = Intent(Settings.ACTION_SECURITY_SETTINGS)

    fun fullScreen(context: Context): Intent =
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", context.packageName, null))
        } else {
            appDetails(context)
        }

    /** The full-screen alarm notification channel's own settings page (to turn it back on / up). */
    fun alarmChannel(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ws.crandell.fafo.arm.Notifications.CH_ALARM_SCREEN)

    /** FULL_SCREEN step: the permission page first; once granted, the alarm channel if it was lowered. */
    fun fullScreenStep(context: Context, status: OnboardingStatus): Intent =
        if (!status.fullScreen) fullScreen(context) else alarmChannel(context)

    fun dnd() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    @android.annotation.SuppressLint("BatteryLife")
    fun ignoreBatteryOptimization(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))

    fun notificationSettings(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Theft protection lives under Security on Android 15+/One UI; this is the closest stable entry point. */
    fun builtInProtections() = Intent(Settings.ACTION_SECURITY_SETTINGS)
}
