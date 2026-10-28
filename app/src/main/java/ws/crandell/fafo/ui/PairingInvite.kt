package ws.crandell.fafo.ui

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import ws.crandell.fafo.circles.CircleManager
import ws.crandell.fafo.circles.Permission
import ws.crandell.fafo.link.MessageLink
import ws.crandell.fafo.net.InviteInbox
import ws.crandell.fafo.net.Message
import ws.crandell.fafo.net.PeerHub
import ws.crandell.fafo.net.SecureChannel
import ws.crandell.fafo.net.SessionHandshake
import ws.crandell.fafo.net.TrustLookup
import ws.crandell.fafo.pairing.IdentityKeys
import ws.crandell.fafo.pairing.TrustedDevice
import ws.crandell.fafo.pairing.TrustedDeviceStore

/**
 * After both phones confirmed the pairing code (AC6.6, AC8.6): the first CircleInvite rides on the same
 * Bluetooth link, inside a normal encrypted session (the raw pairing link is not encrypted). The phone
 * that searched (initiator) invites the other into the circle its owner started from; the other phone's
 * owner sees the accept screen. Both also exchange Hello (label suggestion, beacon key, AC9.6).
 */
class PairingInvite(
    private val context: Context,
    private val keys: IdentityKeys,
    private val trusted: TrustedDeviceStore,
    private val circles: CircleManager,
    private val inbox: InviteInbox,
    private val myLabel: suspend () -> String,
    private val beaconKey: () -> ByteArray,
    private val status: (String) -> Unit,
) {
    private val trust = TrustLookup { fp -> trusted.get(fp)?.identityPublic }

    suspend fun run(initiator: Boolean, link: MessageLink, device: TrustedDevice, circleId: String) {
        val ch = if (initiator) {
            SecureChannel.initiate(link, SessionHandshake.Initiator(keys, trust, expectedPeer = device.fingerprint))
        } else {
            SecureChannel.respond(link, SessionHandshake.Responder(keys, trust))
        }
        if (ch.peer != device.fingerprint) return
        ch.send(Message.Hello(myLabel(), beaconKey()))
        if (initiator) invite(ch, device, circleId) else answer(ch, device)
    }

    private fun onHello(fp: String, m: Message.Hello) {
        m.beaconKey?.takeIf { it.size == 32 }?.let { trusted.setBeaconKey(fp, it) }
    }

    private suspend fun invite(ch: SecureChannel, device: TrustedDevice, circleId: String) {
        val circle = ws.crandell.fafo.trust.TrustGraph.circles(context).get(circleId) ?: return
        val expires = System.currentTimeMillis() + PeerHub.INVITE_TTL_MS
        ch.send(Message.CircleInvite(circle.id, circle.name, Permission.toBits(circle.permissions), myLabel(), expires))
        status("Waiting for ${device.name} to accept ${circle.name}")
        val accept = withTimeoutOrNull(PeerHub.INVITE_TTL_MS) {
            while (true) {
                when (val m = ch.receive()) {
                    is Message.Hello -> onHello(device.fingerprint, m)
                    is Message.CircleAccept -> if (m.circleId == circle.id) return@withTimeoutOrNull m
                    else -> Unit
                }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
        if (accept?.accepted == true) {
            // The owner passed the credential for this pairing a moment ago (AC8.5: adding a member).
            circles.addMember(circle.id, device.fingerprint, accept.label, alreadyAuthorized = true)
        }
    }

    private suspend fun answer(ch: SecureChannel, device: TrustedDevice) {
        val invite = withTimeoutOrNull(60_000) {
            while (true) {
                when (val m = ch.receive()) {
                    is Message.Hello -> onHello(device.fingerprint, m)
                    is Message.CircleInvite -> return@withTimeoutOrNull m
                    else -> Unit
                }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        } ?: return
        status("Answer the invite from ${device.name}")
        val (_, answer) = inbox.post(
            device.fingerprint, device.name, invite.circleId, invite.name, Permission.fromBits(invite.permissions), invite.expiresAt,
        )
        val accepted = withTimeoutOrNull((invite.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)) { answer.await() }
        ch.send(Message.CircleAccept(invite.circleId, accepted != null, myLabel()))
    }
}
