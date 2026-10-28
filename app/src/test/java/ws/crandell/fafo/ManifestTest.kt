package ws.crandell.fafo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import ws.crandell.fafo.arm.ArmService

/** Manifest guard (spec section 6). The expected list grows milestone by milestone; M13 makes it exact. */
@RunWith(AndroidJUnit4::class)
class ManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm = context.packageManager

    @Suppress("DEPRECATION")
    private val info = pm.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_ACTIVITIES,
    )
    private val permissions = info.requestedPermissions?.toSet() ?: emptySet()

    private val notRequested = listOf(
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.USE_EXACT_ALARM",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_CONTACTS",
        "android.permission.CAPTURE_AUDIO_OUTPUT",
        "android.permission.BIND_DEVICE_ADMIN",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
    )

    @Test fun noForbiddenPermissions() {
        notRequested.forEach { assertThat(permissions).doesNotContain(it) }
    }

    @Test fun localNetworkPermissions() {
        // M10: sockets need INTERNET even on the LAN (spec F9); local-only use is enforced elsewhere.
        assertThat(permissions).containsAtLeast(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.NEARBY_WIFI_DEVICES",
        )
        val tag = Regex("<uses-permission[^>]*NEARBY_WIFI_DEVICES[^>]*>").find(sourceManifest())!!.value
        assertThat(tag).contains("neverForLocation")
        assertThat(sourceManifest()).contains("@xml/network_security_config")
    }

    /** M12: the microphone service type is declared only on IntercomService. */
    @Test fun microphoneOnlyOnIntercomService() {
        assertThat(permissions).containsAtLeast(
            "android.permission.RECORD_AUDIO", "android.permission.FOREGROUND_SERVICE_MICROPHONE", "android.permission.MODIFY_AUDIO_SETTINGS",
        )
        info.services.orEmpty().forEach { s ->
            val mic = s.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE != 0
            assertThat(mic).isEqualTo(s.name == "ws.crandell.fafo.intercom.IntercomService")
        }
    }

    @Test fun armServicePermissions() {
        assertThat(permissions).containsAtLeast(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "android.permission.FOREGROUND_SERVICE_CAMERA",
            "android.permission.CAMERA",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.WAKE_LOCK",
        )
    }

    @Test fun armServiceIsSpecialUseAndCameraAndDirectBootAware() {
        val si = pm.getServiceInfo(ComponentName(context, ArmService::class.java), PackageManager.GET_META_DATA)
        assertThat(si.foregroundServiceType).isEqualTo(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )
        assertThat(si.directBootAware).isTrue()
        assertThat(si.exported).isFalse()
    }

    @Test fun bluetoothPermissions() {
        assertThat(permissions).containsAtLeast(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
        )
    }

    /** AC6.5: neverForLocation on BLUETOOTH_SCAN, legacy Bluetooth capped at API 30 (read from the source manifest). */
    @Test fun bluetoothScanNeverForLocationAndLegacyCapped() {
        val xml = sourceManifest()
        val scan = Regex("<uses-permission[^>]*BLUETOOTH_SCAN[^>]*>").find(xml)!!.value
        assertThat(scan).contains("neverForLocation")
        for (legacy in listOf("android.permission.BLUETOOTH\"", "android.permission.BLUETOOTH_ADMIN\"")) {
            val tag = Regex("<uses-permission[^>]*${Regex.escape(legacy)}[^>]*>").find(xml)!!.value
            assertThat(tag).contains("maxSdkVersion=\"30\"")
        }
    }

    private fun sourceManifest(): String {
        val candidates = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map { File(it) }
        return candidates.first { it.isFile }.readText()
    }

    @Test fun reachableServiceIsConnectedDevice() {
        assertThat(permissions).contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE")
        val si = pm.getServiceInfo(ComponentName(context, ws.crandell.fafo.reach.ReachableService::class.java), PackageManager.GET_META_DATA)
        assertThat(si.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        assertThat(si.exported).isFalse()
    }

    @Test fun backupDisabled() {
        assertThat(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }
}
