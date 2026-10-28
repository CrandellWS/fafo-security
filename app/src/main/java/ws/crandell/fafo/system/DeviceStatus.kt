package ws.crandell.fafo.system

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import androidx.core.content.ContextCompat

/** Thin reads of system state. Every call here is safe in direct boot (no CE storage, no Keystore). */
object DeviceStatus {

    /** AC1.1: sticky ACTION_BATTERY_CHANGED, EXTRA_PLUGGED != 0. */
    fun isPlugged(context: Context): Boolean {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return pluggedFrom(sticky)
    }

    fun pluggedFrom(batteryChanged: Intent?): Boolean =
        (batteryChanged?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    fun batteryPercent(context: Context): Int {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: false

    fun isDeviceLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked ?: true

    /** True once credential-protected storage is unlocked (after the first unlock since boot). */
    fun isCeUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: false

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) hasPermission(context, Manifest.permission.POST_NOTIFICATIONS) else true

    fun notificationsEnabled(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false

    fun hasCamera(context: Context): Boolean = hasPermission(context, Manifest.permission.CAMERA)

    fun canUseFullScreenIntent(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
        } else {
            true
        }

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted ?: false

    fun ignoresBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    fun freeBytes(context: Context): Long = context.filesDir.usableSpace
}
