package ws.crandell.fafo.ui

import android.Manifest
import android.content.Context
import android.os.Build
import ws.crandell.fafo.system.DeviceStatus

/** The Nearby devices group (AC6.5, AC9.10): Bluetooth scan / advertise / connect, Wi-Fi nearby devices. */
object NearbyPermissions {
    fun all(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        // AC9.10: Android 16's opt-in local network protection gate, same Nearby devices group (one prompt).
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    fun missing(context: Context): List<String> = all().filterNot { DeviceStatus.hasPermission(context, it) }
}
