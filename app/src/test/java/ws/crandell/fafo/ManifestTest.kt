package ws.crandell.fafo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
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

    @Test fun permissionsNotYetAdded() {
        // Added in later milestones: INTERNET (M10), connectedDevice (M11), microphone (M12).
        assertThat(permissions).doesNotContain("android.permission.INTERNET")
        assertThat(permissions).doesNotContain("android.permission.RECORD_AUDIO")
        assertThat(permissions).doesNotContain("android.permission.FOREGROUND_SERVICE_MICROPHONE")
        assertThat(permissions).doesNotContain("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE")
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

    @Test fun backupDisabled() {
        assertThat(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }
}
