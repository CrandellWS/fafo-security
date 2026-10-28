package ws.crandell.fafo.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ws.crandell.fafo.circles.Permission
import java.util.concurrent.atomic.AtomicLong

/** An invite waiting for this owner's answer (AC8.6). */
data class IncomingInvite(
    val id: Long,
    val peer: String,
    val peerLabel: String,
    val circleId: String,
    val circleName: String,
    val offered: Set<Permission>,
    val expiresAt: Long,
)

/**
 * Invites shown on the accept screen. [post] returns a deferred that completes with what the owner
 * accepted (after the circle was saved, credential included) or null for decline / expiry.
 */
class InviteInbox(private val clock: () -> Long = System::currentTimeMillis) {
    private val ids = AtomicLong()
    private val answers = HashMap<Long, CompletableDeferred<Set<Permission>?>>()
    private val _invites = MutableStateFlow<List<IncomingInvite>>(emptyList())
    val invites: StateFlow<List<IncomingInvite>> = _invites

    @Synchronized
    fun post(peer: String, label: String, circleId: String, name: String, offered: Set<Permission>, expiresAt: Long): Pair<IncomingInvite, CompletableDeferred<Set<Permission>?>> {
        val inv = IncomingInvite(ids.incrementAndGet(), peer, label, circleId, name, offered, expiresAt)
        val d = CompletableDeferred<Set<Permission>?>()
        answers[inv.id] = d
        _invites.value = _invites.value.filterNot { it.peer == peer && it.circleId == circleId } + inv
        return inv to d
    }

    @Synchronized
    fun answer(id: Long, accepted: Set<Permission>?) {
        answers.remove(id)?.complete(accepted)
        _invites.value = _invites.value.filterNot { it.id == id }
    }

    /** Drops expired invites (answered as declined). */
    @Synchronized
    fun prune() {
        val now = clock()
        _invites.value.filter { it.expiresAt <= now }.forEach { answer(it.id, null) }
    }
}
