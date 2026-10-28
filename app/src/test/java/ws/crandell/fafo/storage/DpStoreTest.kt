package ws.crandell.fafo.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import ws.crandell.fafo.core.Defaults

@RunWith(AndroidJUnit4::class)
class DpStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After fun tearDown() = scope.cancel()

    @Test fun defaultsMatchSpec() = runBlocking<Unit> {
        val store = DpStore.create(context, scope)
        val s = store.readSettings()
        assertThat(s.sensitivityMs).isEqualTo(500L)
        assertThat(s.preAlarmS).isEqualTo(5)
        assertThat(s.forceMaxVolume).isTrue()
        assertThat(s.disarmOnUnlock).isFalse()
        assertThat(s.motion).isEqualTo("MEDIUM")
        assertThat(s.sirenSound).isEqualTo("Klaxon")
        assertThat(s.bumpResetS).isEqualTo(15)
        val st = store.readState()
        assertThat(st.armed).isFalse()
        assertThat(st.incidentActive).isFalse()
    }

    @Test fun readsBackFromDeviceProtectedContext() = runBlocking<Unit> {
        val store = DpStore.create(context, scope)
        store.setArmed(true, awaitingPower = false)
        store.setIncident(true, "abc", 42L)
        store.update { it.copy(sensitivityMs = 2000L, preAlarmS = 30) }

        val st = store.readState()
        assertThat(st.armed).isTrue()
        assertThat(st.incidentActive).isTrue()
        assertThat(st.incidentId).isEqualTo("abc")
        assertThat(st.incidentStartedAt).isEqualTo(42L)
        assertThat(store.readSettings().sensitivityMs).isEqualTo(2000L)
        store.update { it.copy(bumpResetS = 30) }
        assertThat(store.readSettings().bumpResetS).isEqualTo(30)

        val file = DpStore.file(context)
        assertThat(file.exists()).isTrue()
        val dpDir = context.createDeviceProtectedStorageContext().dataDir.canonicalPath
        assertThat(file.canonicalPath).startsWith(dpDir)

        store.setIncident(false, null, null)
        assertThat(store.readState().incidentId).isNull()
    }

    @Test fun ceDefaultsMatchSpec() = runBlocking<Unit> {
        val ce = CeStore.create(context, scope).read()
        assertThat(ce.photoOnTrigger).isTrue()
        assertThat(ce.photoIntervalS).isEqualTo(10)
        assertThat(ce.camera).isEqualTo("FRONT")
        assertThat(ce.photoCapMb).isEqualTo(100)
        assertThat(ce.stayReachable).isFalse()
        assertThat(ce.chimeOnListen).isTrue()
        assertThat(ce.deviceLabel.length).isAtMost(Defaults.LABEL_MAX)
    }
}
