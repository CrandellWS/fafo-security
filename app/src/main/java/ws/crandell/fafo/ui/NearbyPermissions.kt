package ws.crandell.fafo.ui

import android.Manifest
import android.content.Context
import android.os.Build
import ws.crandell.fafo.system.DeviceStatus

/** The Nearby devices group (AC6.5): Bluetooth scan / advertise / connect on API 31+. */
object NearbyPermissions {
    fun all(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    fun missing(context: Context): List<String> = all().filterNot { DeviceStatus.hasPermission(context, it) }
}
