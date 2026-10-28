package ws.crandell.fafo.ble

import android.app.Application
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.trust.TrustGraph
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** M11: the background-scan receiver ignores beacons whose tag matches no trusted peer. */
@RunWith(AndroidJUnit4::class)
class BleAlertReceiverTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @After fun tearDown() = TrustGraph.reset()

    private fun scanRecord(serviceData: ByteArray): ScanRecord {
        val uuid = BleIds.SERVICE
        val bb = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(uuid.leastSignificantBits).putLong(uuid.mostSignificantBits).array()
        val ad = byteArrayOf((1 + 16 + serviceData.size).toByte(), 0x21) + bb + serviceData
        val m = ScanRecord::class.java.getDeclaredMethod("parseFromBytes", ByteArray::class.java)
        return m.invoke(null, byteArrayOf(2, 1, 6) + ad) as ScanRecord
    }

    @Test fun unknownTagPostsNothing() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val device = app.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice("00:11:22:33:44:55")
        val record = scanRecord(BleBeacon.encode(true, Crypto.randomBytes(32), System.currentTimeMillis()))
        assertThat(record.getServiceData(android.os.ParcelUuid(BleIds.SERVICE))).hasLength(BleBeacon.SIZE)
        @Suppress("DEPRECATION")
        val result = ScanResult(device, record, -50, 0L)
        val intent = Intent(BleAlertScan.ACTION).putParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, arrayListOf(result))
        BleAlertReceiver().onReceive(app, intent)
        val nm = app.getSystemService(NotificationManager::class.java)
        assertThat(shadowOf(nm).allNotifications).isEmpty()
    }
}
