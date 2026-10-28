package ws.crandell.fafo.camera

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ws.crandell.fafo.arm.apply
import ws.crandell.fafo.core.AlarmConfig
import ws.crandell.fafo.core.AlarmEffect
import ws.crandell.fafo.core.AlarmEvent
import ws.crandell.fafo.core.AlarmState
import ws.crandell.fafo.core.AlarmStateMachine
import ws.crandell.fafo.core.Clock
import ws.crandell.fafo.core.Defaults
import ws.crandell.fafo.core.FakeClock
import ws.crandell.fafo.core.incidentId
import ws.crandell.fafo.siren.FakeAudioBackend
import ws.crandell.fafo.siren.Siren

class PrunePolicyTest {
    private fun inc(id: String, start: Long, closed: Boolean, n: Int, size: Long = 100, big: Set<Int> = emptySet()) =
        IncidentInfo(id, start, closed, (0 until n).map { PhotoInfo(id, "$id-$it", size, start + it * 10L, it in big) })

    @Test fun underCapDeletesNothing() {
        val p = PrunePolicy.plan(listOf(inc("a", 0, true, 5)), capBytes = 1000, incomingBytes = 100)
        assertThat(p.deletes).isEmpty()
        assertThat(p.fits).isTrue()
    }

    @Test fun neverDeletesProtectedFrames() {
        val a = inc("a", 0, true, 10, big = setOf(3, 6))
        val b = inc("b", 1000, false, 10, big = setOf(5))
        val p = PrunePolicy.plan(listOf(a, b), capBytes = 100, incomingBytes = 100)
        val deleted = p.deletes.map { it.name }.toSet()
        val protected = PrunePolicy.protectedNames(a) + PrunePolicy.protectedNames(b)
        assertThat(deleted.intersect(protected)).isEmpty()
        assertThat(protected).containsAtLeast("a-0", "a-9", "a-3", "a-6", "b-0", "b-9", "b-5")
        assertThat(p.fits).isFalse() // only protected frames are left
    }

    @Test fun respectsCap() {
        val a = inc("a", 0, true, 10)
        val p = PrunePolicy.plan(listOf(a), capBytes = 1000, incomingBytes = 300)
        val after = 1000 - p.deletes.sumOf { it.sizeBytes } + 300
        assertThat(after).isAtMost(1000)
        assertThat(p.fits).isTrue()
    }

    @Test fun prunesOldestClosedIncidentFirstOldestPhotosFirst() {
        val old = inc("old", 0, true, 5)
        val newer = inc("new", 500, true, 5)
        val cur = inc("cur", 1000, false, 5)
        val p = PrunePolicy.plan(listOf(cur, newer, old), capBytes = 1500, incomingBytes = 200)
        assertThat(p.deletes.map { it.name }).containsExactly("old-1", "old-2").inOrder()
    }

    @Test fun thinsCurrentIncidentEveryOtherFrame() {
        val cur = inc("cur", 0, false, 11)
        val p = PrunePolicy.plan(listOf(cur), capBytes = 1100, incomingBytes = 300)
        // Unprotected are cur-1..cur-9; the first round deletes every other one, oldest first.
        assertThat(p.deletes.map { it.name }).containsExactly("cur-1", "cur-3", "cur-5").inOrder()
    }

    @Test fun bigChangeCappedAt20PerIncident() {
        val i = inc("a", 0, false, 40, big = (1..30).toSet())
        assertThat(PrunePolicy.protectedNames(i).size).isEqualTo(2 + 20)
    }

    @Test fun bigChangeDiff() {
        val a = IntArray(1024) { 100 }
        assertThat(BigChange.isBigChange(a, IntArray(1024) { 110 })).isFalse()
        assertThat(BigChange.isBigChange(a, IntArray(1024) { 200 })).isTrue()
        assertThat(BigChange.isBigChange(null, a)).isFalse()
    }
}

class PhotoStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun meta(t: Long, size: Long = 10) = PhotoMeta("p$t.jpg", t, "FRONT", 80, false, false, size)

    @Test fun writesJpegAndSidecarPendingAndCloses() {
        val st = PhotoStore(tmp.newFolder("incidents"))
        assertThat(st.add("inc1", 5, ByteArray(10), meta(6), capBytes = 1000)).isTrue()
        val s = st.read("inc1")!!
        assertThat(s.photos.single().status).isEqualTo("pending")
        assertThat(st.photoFile("inc1", "p6.jpg").length()).isEqualTo(10)
        st.close("inc1", "CANCELLED", null, 50)
        assertThat(st.read("inc1")!!.closeReason).isEqualTo("CANCELLED")
        assertThat(st.read("inc1")!!.photos).hasSize(1) // photos kept and labelled
    }

    @Test fun capPrunesOnAdd() {
        val st = PhotoStore(tmp.newFolder("incidents"))
        st.open("old", 0)
        for (t in 1L..5L) st.add("old", 0, ByteArray(100), meta(t, 100), capBytes = 10_000)
        st.close("old", "UNLOCKED", null, 10)
        st.add("cur", 100, ByteArray(100), meta(101, 100), capBytes = 600)
        val total = st.totalBytes()
        assertThat(total).isAtMost(600)
        assertThat(st.read("old")!!.photos.map { it.file }).containsAtLeast("p1.jpg", "p5.jpg")
    }

    @Test fun deleteIncidentRemovesFiles() {
        val st = PhotoStore(tmp.newFolder("incidents"))
        st.add("x", 1, ByteArray(10), meta(2), 1000)
        st.deleteIncident("x")
        assertThat(st.list()).isEmpty()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoControllerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = TestScope()
    private val clock = Clock { scope.currentTime }

    private class FakeCapturer(var fail: Boolean = false) : Capturer {
        val lenses = mutableListOf<Lens>()
        override suspend fun capture(lens: Lens): CapturedPhoto? {
            lenses += lens
            return if (fail) null else CapturedPhoto(ByteArray(1000), IntArray(1024) { lenses.size * 40 % 255 })
        }
    }

    private fun controller(cap: Capturer, free: () -> Long, settings: PhotoSettings = PhotoSettings(), store: PhotoStore, paused: (Boolean) -> Unit = {}) =
        PhotoController(scope, cap, { store }, { settings }, free, { DeviceSnapshot(50, false) }, clock, paused)

    @Test fun firstPhotoImmediatelyThenEveryInterval() {
        val cap = FakeCapturer()
        val store = PhotoStore(tmp.newFolder("i"))
        val pc = controller(cap, { Long.MAX_VALUE }, store = store)
        pc.start("inc", 0)
        scope.runCurrent()
        assertThat(pc.shots).isEqualTo(1) // within 2 s of the trigger
        scope.advanceTimeBy(Defaults.PHOTO_INTERVAL_S * 1000L * 3)
        scope.runCurrent()
        assertThat(pc.shots).isEqualTo(4)
        pc.stop()
        scope.advanceTimeBy(60_000)
        scope.runCurrent()
        assertThat(pc.shots).isEqualTo(4)
        assertThat(store.read("inc")!!.photos).hasSize(4)
    }

    @Test fun alternateUsesBothCameras() {
        val cap = FakeCapturer()
        val pc = controller(cap, { Long.MAX_VALUE }, PhotoSettings(camera = "ALTERNATE", intervalS = 5), PhotoStore(tmp.newFolder("i")))
        pc.start("inc", 0)
        scope.advanceTimeBy(10_001)
        scope.runCurrent()
        assertThat(cap.lenses).containsExactly(Lens.FRONT, Lens.BACK, Lens.FRONT).inOrder()
        pc.stop()
    }

    @Test fun cameraFailureIsLoggedAndRetried() {
        val cap = FakeCapturer(fail = true)
        val store = PhotoStore(tmp.newFolder("i"))
        val pc = controller(cap, { Long.MAX_VALUE }, store = store)
        pc.start("inc", 0)
        scope.advanceTimeBy(10_001)
        scope.runCurrent()
        assertThat(cap.lenses).hasSize(2)
        assertThat(store.read("inc")!!.log.filter { "capture failed" in it }).hasSize(2)
        pc.stop()
    }

    @Test fun lowStorageStopsPhotosButNotTheSiren() {
        val cap = FakeCapturer()
        val store = PhotoStore(tmp.newFolder("i"))
        var paused = false
        val pc = controller(cap, { 100L * 1024 * 1024 }, store = store, paused = { paused = it })
        val backend = FakeAudioBackend()
        val siren = Siren(backend, scope)
        val fc = FakeClock()
        val m = AlarmStateMachine(fc, { AlarmConfig(preAlarmMs = 0) })
        fun send(e: AlarmEvent) = m.handle(e).forEach { fx ->
            if (!siren.apply(fx) && fx == AlarmEffect.StartPhotos) {
                val s = m.state as AlarmState.Triggered
                pc.start(s.incidentId, s.startedAt)
            }
        }
        send(AlarmEvent.ArmRequested(true, true))
        send(AlarmEvent.PowerDisconnected(fc.now()))
        send(AlarmEvent.WindowElapsed(plugged = false, locked = true))
        scope.advanceTimeBy(30_000)
        scope.runCurrent()
        assertThat(m.state.incidentId).isNotNull()
        assertThat(cap.lenses).isEmpty()
        assertThat(pc.shots).isEqualTo(0)
        assertThat(paused).isTrue()
        assertThat(backend.looping).isTrue()
        assertThat(siren.mode).isEqualTo(Siren.Mode.SIREN)
        pc.stop()
        siren.stop()
    }

    @Test fun photoOffSettingTakesNothing() {
        val cap = FakeCapturer()
        val pc = controller(cap, { Long.MAX_VALUE }, PhotoSettings(enabled = false), PhotoStore(tmp.newFolder("i")))
        pc.start("inc", 0)
        scope.advanceTimeBy(30_000)
        scope.runCurrent()
        assertThat(cap.lenses).isEmpty()
    }
}
