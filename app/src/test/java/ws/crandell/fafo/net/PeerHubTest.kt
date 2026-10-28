package ws.crandell.fafo.net

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.CircleManager
import ws.crandell.fafo.circles.CircleStore
import ws.crandell.fafo.circles.Member
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.crypto.Crypto
import ws.crandell.fafo.pairing.PeerKeys
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.pairing.TrustedDeviceStore
import ws.crandell.fafo.testutil.FakeGate
import ws.crandell.fafo.testutil.FakeIdentityKeys
import ws.crandell.fafo.testutil.MemoryLink
import java.io.File

/** Two hubs wired over an in-memory link: alerts, challenge + signed command, permission checks, invites. */
class PeerHubTest {
    private val dir: File = java.nio.file.Files.createTempDirectory("hub").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    inner class Node(val name: String) {
        val keys = FakeIdentityKeys()
        val trusted = TrustedDeviceStore(File(dir, "$name-trusted.json"))
        val circles = CircleStore(File(dir, "$name-circles.json"))
        val beacon = Crypto.randomBytes(32)
        val alerts = mutableListOf<Message.Alert>()
        val invites = mutableListOf<Message.CircleInvite>()
        var incident: String? = null
        val applied = mutableListOf<Pair<Command, String>>()
        val hub = PeerHub(
            scope, keys, trusted, circles, CircleManager(circles, FakeGate()), { name }, { beacon },
            object : PeerHub.Events {
                override fun onAlert(peer: TrustedDevice, label: String, alert: Message.Alert) { synchronized(alerts) { alerts += alert } }
                override fun onInvite(peer: TrustedDevice, invite: Message.CircleInvite) { synchronized(invites) { invites += invite } }
            },
        ).also {
            it.commandTarget = object : CommandTarget {
                override fun currentIncident() = incident
                override suspend fun apply(cmd: Command, peerLabel: String, circleId: String): Boolean {
                    applied += cmd to peerLabel
                    return true
                }
            }
        }

        fun trust(other: Node) = trusted.put(TrustedDevice.from(PeerKeys(other.keys.identityPublic(), other.keys.commandPublic(), other.name), 0))
        val fp get() = keys.fingerprint()
    }

    private val a = Node("Register")
    private val b = Node("Back room")

    init {
        a.trust(b)
        b.trust(a)
        // B listens for A's dial.
        a.hub.dialer = { fp ->
            if (fp != b.fp) emptyList() else {
                val (la, lb) = MemoryLink.pair()
                scope.launchAccept(b.hub, lb)
                listOf(la)
            }
        }
    }

    private fun CoroutineScope.launchAccept(hub: PeerHub, link: MemoryLink) = launch(Dispatchers.Default) { hub.accept(link) }

    private fun grant(on: Node, to: Node, vararg p: Permission, id: String = "home") {
        on.circles.put(Circle(id, "Home", p.toSet(), listOf(Member(to.fp, to.name, p.toSet()))))
    }

    private suspend fun until(cond: () -> Boolean) = withTimeout(5_000) { while (!cond()) delay(10) }

    @Test fun helloDeliversBeaconKey() = runBlocking<Unit> {
        assertThat(a.hub.connect(b.fp)).isNotNull()
        until { b.trusted.get(a.fp)?.beaconKey != null && a.trusted.get(b.fp)?.beaconKey != null }
        assertThat(b.trusted.get(a.fp)!!.beaconKey).isEqualTo(a.beacon)
    }

    @Test fun alertsGoOnlyToCirclesWithAlertsAndCarryGrantedPowers() {
        grant(a, b, Permission.ALERTS, Permission.RESET)
        val alerts = a.hub.alertsFor(AlertInfo("inc", "TRIGGERED", 5), "Register")
        assertThat(alerts).hasSize(1)
        assertThat(Permission.fromBits(alerts[0].second.granted)).containsExactly(Permission.RESET)
        grant(a, b, Permission.INTERCOM)
        assertThat(a.hub.alertsFor(AlertInfo("inc", "TRIGGERED", 5), "Register")).isEmpty()
    }

    @Test fun alertSentAsSoonAsASessionOpensWhileAlarming() = runBlocking<Unit> {
        grant(a, b, Permission.ALERTS, Permission.DISARM)
        a.hub.startAlerts { AlertInfo("inc2", "TRIGGERED", 9) }
        until { synchronized(b.alerts) { b.alerts.isNotEmpty() } }
        assertThat(Permission.fromBits(b.alerts[0].granted)).containsExactly(Permission.RESET, Permission.DISARM)
        a.hub.stopAlerts()
    }

    @Test fun alertArrives() = runBlocking<Unit> {
        grant(a, b, Permission.ALERTS)
        a.hub.sendAlertsOnce(AlertInfo("inc1", "PRE_ALARM", 7))
        until { synchronized(b.alerts) { b.alerts.isNotEmpty() } }
        assertThat(b.alerts[0].incidentId).isEqualTo("inc1")
        assertThat(b.alerts[0].label).isEqualTo("Register")
    }

    @Test fun remoteResetVerifiedAndApplied() = runBlocking<Unit> {
        grant(b, a, Permission.RESET) // B lets A reset B
        b.incident = "inc7"
        val out = a.hub.sendCommand(b.fp, Command.RESET, "home", "inc7") { payload ->
            a.keys.commandSigner().run { update(payload); sign() }
        }
        assertThat(out).isEqualTo(CommandOutcome.Applied)
        assertThat(b.applied).containsExactly(Command.RESET to "Register")
    }

    @Test fun disarmRefusedWithoutPermissionAndNoChallengeIssued() = runBlocking<Unit> {
        grant(b, a, Permission.RESET)
        b.incident = "inc7"
        var asked = false
        val out = a.hub.sendCommand(b.fp, Command.DISARM, "home", "inc7") { asked = true; ByteArray(0) }
        assertThat(out).isEqualTo(CommandOutcome.Refused("NOT_PERMITTED"))
        assertThat(asked).isFalse() // the owner is never prompted for a command the target refuses
        assertThat(b.applied).isEmpty()
    }

    @Test fun badSignatureRefused() = runBlocking<Unit> {
        grant(b, a, Permission.DISARM)
        b.incident = "inc7"
        val out = a.hub.sendCommand(b.fp, Command.DISARM, "home", "inc7") { payload ->
            a.keys.signIdentity(payload) // identity key, not the auth-bound command key
        }
        assertThat(out).isEqualTo(CommandOutcome.Refused("BAD_SIGNATURE"))
        assertThat(b.applied).isEmpty()
    }

    @Test fun cancelledPromptSendsNothing() = runBlocking<Unit> {
        grant(b, a, Permission.RESET)
        b.incident = "inc7"
        assertThat(a.hub.sendCommand(b.fp, Command.RESET, "home", "inc7") { null }).isEqualTo(CommandOutcome.Cancelled)
        assertThat(b.applied).isEmpty()
    }

    @Test fun unknownPeerUnreachable() = runBlocking<Unit> {
        assertThat(a.hub.sendCommand("nobody", Command.RESET, "home", "x") { ByteArray(0) }).isEqualTo(CommandOutcome.Unreachable)
    }

    @Test fun inviteAndAcceptAddsMember() = runBlocking<Unit> {
        val work = Circle("work1", "Work", setOf(Permission.INTERCOM, Permission.RESET))
        a.circles.put(work)
        assertThat(a.hub.invite(b.fp, "work1")).isTrue()
        until { synchronized(b.invites) { b.invites.isNotEmpty() } }
        assertThat(Permission.fromBits(b.invites[0].permissions)).containsExactly(Permission.INTERCOM, Permission.RESET)
        // An invite never changes circles on the receiver by itself.
        assertThat(b.circles.get("work1")).isNull()
        b.hub.answerInvite(a.fp, "work1", true)
        until { a.circles.get("work1")!!.member(b.fp) != null }
        assertThat(a.circles.get("work1")!!.member(b.fp)!!.label).isEqualTo("Back room")
    }

    @Test fun acceptWithoutInviteIgnored() = runBlocking<Unit> {
        a.circles.put(Circle("work1", "Work", setOf(Permission.INTERCOM)))
        a.hub.connect(b.fp)
        until { b.hub.connected.value.contains(a.fp) }
        b.hub.answerInvite(a.fp, "work1", true)
        delay(300)
        assertThat(a.circles.get("work1")!!.members).isEmpty()
    }
}
