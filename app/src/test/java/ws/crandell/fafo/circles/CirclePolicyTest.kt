package ws.crandell.fafo.circles

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ws.crandell.fafo.pairing.GateResult
import ws.crandell.fafo.testutil.FakeGate

class CirclePolicyTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun defaults() {
        val home = CirclePolicy.home()
        assertThat(home.name).isEqualTo("Home")
        assertThat(home.permissions).containsExactly(Permission.ALERTS, Permission.RESET, Permission.DISARM, Permission.INTERCOM)
        assertThat(home.members).isEmpty()
        assertThat(home.id).hasLength(32)
        assertThat(CirclePolicy.newCircle("Garage").permissions).containsExactly(Permission.INTERCOM)
        assertThat(CirclePolicy.newCircle("Work").permissions).containsExactly(Permission.INTERCOM, Permission.RESET)
    }

    @Test fun storeStartsWithEmptyHome() {
        val s = CircleStore(java.io.File(tmp.root, "c.json"))
        assertThat(s.all().map { it.name }).containsExactly("Home")
        val id = s.all()[0].id
        assertThat(CircleStore(java.io.File(tmp.root, "c.json")).all()[0].id).isEqualTo(id)
    }

    private val home = Circle("H", "Home", CirclePolicy.HOME_PERMISSIONS, listOf(Member("p", "Back room", CirclePolicy.HOME_PERMISSIONS)))
    private val work = Circle("W", "Work", CirclePolicy.WORK_PERMISSIONS, listOf(Member("p", "Register", CirclePolicy.WORK_PERMISSIONS), Member("q", "Till", setOf(Permission.INTERCOM))))
    private val all = listOf(home, work)

    @Test fun permissionsPerCircleId() {
        assertThat(CirclePolicy.allows(all, "H", "p", Permission.DISARM)).isTrue()
        assertThat(CirclePolicy.allows(all, "W", "p", Permission.DISARM)).isFalse()
        assertThat(CirclePolicy.allows(all, "W", "p", Permission.RESET)).isTrue()
        assertThat(CirclePolicy.allows(all, "W", "p", Permission.ALERTS)).isFalse()
        assertThat(CirclePolicy.allows(all, "H", "q", Permission.INTERCOM)).isFalse() // not a member of Home
        assertThat(CirclePolicy.allows(all, "W", "q", Permission.INTERCOM)).isTrue()
        assertThat(CirclePolicy.allows(all, "X", "p", Permission.ALERTS)).isFalse()
        assertThat(CirclePolicy.allows(all, "W", "stranger", Permission.INTERCOM)).isFalse()
    }

    @Test fun disarmImpliesResetButResetNeverDisarms() {
        val c = listOf(Circle("D", "D", emptySet(), listOf(Member("d", "x", setOf(Permission.DISARM)), Member("r", "y", setOf(Permission.RESET)))))
        assertThat(CirclePolicy.allows(c, "D", "d", Permission.RESET)).isTrue()
        assertThat(CirclePolicy.allows(c, "D", "r", Permission.DISARM)).isFalse()
    }

    @Test fun labelsUniqueAndSuggestionsSuffixed() {
        assertThat(CirclePolicy.labelFor(work, "new", "Register")).isEqualTo("Register 2")
        assertThat(CirclePolicy.labelFor(work, "new", "register")).isEqualTo("register 2")
        assertThat(CirclePolicy.labelFor(work, "new", "Kitchen")).isEqualTo("Kitchen")
        // A suggestion never replaces an existing local label.
        assertThat(CirclePolicy.labelFor(work, "q", "Register")).isEqualTo("Till")
        val crowded = work.copy(members = work.members + Member("z", "Register 2", emptySet()))
        assertThat(CirclePolicy.labelFor(crowded, "new", "Register")).isEqualTo("Register 3")
        assertThat(CirclePolicy.labelFor(work, "new", "A".repeat(30))).hasLength(24)
        assertThat(CirclePolicy.canRename(work, "q", "Register")).isFalse()
        assertThat(CirclePolicy.canRename(work, "q", "Back door")).isTrue()
        assertThat(CirclePolicy.canRename(work, "q", "")).isFalse()
        assertThat(CirclePolicy.canRename(work, "q", "x".repeat(25))).isFalse()
    }

    private fun manager(gate: FakeGate): Pair<CircleManager, CircleStore> {
        val s = CircleStore(java.io.File(tmp.root, "m${System.nanoTime()}.json"))
        return CircleManager(s, gate) to s
    }

    @Test fun credentialGates() = runBlocking<Unit> {
        val gate = FakeGate(GateResult.Failed("cancel"))
        val (m, s) = manager(gate)
        val homeId = s.all()[0].id
        assertThat(m.addMember(homeId, "p", "Back room")).isFalse()
        assertThat(s.get(homeId)!!.members).isEmpty()
        gate.result = GateResult.Success()
        assertThat(m.addMember(homeId, "p", "Back room")).isTrue()
        val work = m.create("Garage")!!
        assertThat(m.addMember(work.id, "p", "Back room")).isTrue()
        gate.result = GateResult.Failed("cancel")
        // Granting Reset needs the credential; granting Alerts does not; removing powers does not.
        assertThat(m.setCirclePermissions(work.id, setOf(Permission.INTERCOM, Permission.RESET))).isFalse()
        assertThat(s.get(work.id)!!.member("p")!!.permissions).containsExactly(Permission.INTERCOM)
        assertThat(m.setCirclePermissions(work.id, setOf(Permission.INTERCOM, Permission.ALERTS))).isTrue()
        assertThat(m.setCirclePermissions(work.id, emptySet())).isTrue()
        assertThat(m.setMemberPermissions(homeId, "p", setOf(Permission.ALERTS))).isTrue()
        assertThat(m.setMemberPermissions(homeId, "p", setOf(Permission.ALERTS, Permission.DISARM))).isFalse()
        // Rename needs nothing; remove needs the credential.
        assertThat(m.renameMember(homeId, "p", "Kitchen")).isTrue()
        assertThat(m.removeMember(homeId, "p")).isFalse()
        assertThat(m.removeCircle(work.id)).isFalse()
        gate.result = GateResult.Success()
        assertThat(m.removeMember(homeId, "p")).isTrue()
        assertThat(m.removeCircle(work.id)).isTrue()
        assertThat(s.get(work.id)).isNull()
    }

    @Test fun acceptInviteGatesAndToggles() = runBlocking<Unit> {
        val gate = FakeGate(GateResult.Failed("cancel"))
        val (m, s) = manager(gate)
        val offered = setOf(Permission.ALERTS, Permission.RESET, Permission.DISARM, Permission.INTERCOM)
        // Carrying Reset/Disarm without the credential: refused, nothing stored.
        assertThat(m.acceptInvite("X1", "Work", offered, offered, "inv", "Back room")).isFalse()
        assertThat(s.get("X1")).isNull()
        // Toggling the guarded powers off needs no credential and does not grant them.
        assertThat(m.acceptInvite("X1", "Work", offered, setOf(Permission.ALERTS, Permission.INTERCOM), "inv", "Back room")).isTrue()
        val c = s.get("X1")!!
        assertThat(c.member("inv")!!.permissions).containsExactly(Permission.ALERTS, Permission.INTERCOM)
        assertThat(CirclePolicy.allows(s.all(), "X1", "inv", Permission.RESET)).isFalse()
        // Never more than offered.
        gate.result = GateResult.Success()
        assertThat(m.acceptInvite("X2", "Shop", setOf(Permission.INTERCOM), offered, "inv", "Back room")).isTrue()
        assertThat(s.get("X2")!!.member("inv")!!.permissions).containsExactly(Permission.INTERCOM)
    }

    @Test fun acceptIntoFreshHomeAdoptsInviterId() = runBlocking<Unit> {
        val (m, s) = manager(FakeGate())
        assertThat(m.acceptInvite("SHARED", "Home", CirclePolicy.HOME_PERMISSIONS, CirclePolicy.HOME_PERMISSIONS, "inv", "Register")).isTrue()
        assertThat(s.all()).hasSize(1)
        assertThat(s.all()[0].id).isEqualTo("SHARED")
        assertThat(s.all()[0].builtIn).isTrue()
        // A later invite to another "Home" with a different id gets its own circle with a distinct name.
        assertThat(m.acceptInvite("OTHER", "Home", setOf(Permission.INTERCOM), setOf(Permission.INTERCOM), "inv2", "Office")).isTrue()
        assertThat(s.all().map { it.name }).containsExactly("Home", "Home 2")
    }
}
