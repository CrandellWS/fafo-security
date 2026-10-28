package ws.crandell.fafo.circles

import ws.crandell.fafo.core.Defaults
import ws.crandell.fafo.pairing.CredentialGate
import ws.crandell.fafo.pairing.GateResult

/**
 * Circle edits with the AC8.5 credential gates. Needs the credential: add a member, grant Reset or Disarm,
 * accept an invite that carries them, remove a member or a circle. Renaming, removing permissions and
 * granting Alerts / Intercom do not.
 */
class CircleManager(private val store: CircleStore, private val gate: CredentialGate) {

    private suspend fun confirm(title: String, subtitle: String): Boolean =
        gate.authenticate(title, subtitle) is GateResult.Success

    fun create(name: String): Circle? {
        val n = name.trim()
        if (n.isEmpty() || n.length > Defaults.LABEL_MAX || store.all().any { it.name.equals(n, true) }) return null
        return CirclePolicy.newCircle(n).also { store.put(it) }
    }

    fun rename(circleId: String, name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || n.length > Defaults.LABEL_MAX) return false
        val c = store.get(circleId) ?: return false
        store.put(c.copy(name = n))
        return true
    }

    /** Adds [peer] with the circle's permissions. [alreadyAuthorized]: the caller just passed the gate (pairing). */
    suspend fun addMember(circleId: String, peer: String, suggestedLabel: String, alreadyAuthorized: Boolean = false): Boolean {
        val c = store.get(circleId) ?: return false
        if (c.member(peer) != null) return true
        if (!alreadyAuthorized && !confirm("Add to ${c.name}", "Confirm with your screen lock")) return false
        val fresh = store.get(circleId) ?: return false
        val label = CirclePolicy.labelFor(fresh, peer, suggestedLabel)
        store.put(fresh.copy(members = fresh.members + Member(peer, label, fresh.permissions)))
        return true
    }

    /** Sets the circle's permissions and every member's copy. Granting Reset / Disarm needs the credential. */
    suspend fun setCirclePermissions(circleId: String, permissions: Set<Permission>): Boolean {
        val c = store.get(circleId) ?: return false
        val widening = CirclePolicy.grantsGuarded(c.permissions, permissions) ||
            c.members.any { CirclePolicy.grantsGuarded(it.permissions, permissions) }
        if (widening && !confirm("Allow ${guardedText(permissions)} for ${c.name}", "Confirm with your screen lock")) return false
        store.put(c.copy(permissions = permissions, members = c.members.map { it.copy(permissions = permissions) }))
        return true
    }

    suspend fun setMemberPermissions(circleId: String, peer: String, permissions: Set<Permission>): Boolean {
        val c = store.get(circleId) ?: return false
        val m = c.member(peer) ?: return false
        if (CirclePolicy.grantsGuarded(m.permissions, permissions) &&
            !confirm("Allow ${guardedText(permissions)} for ${m.label}", "Confirm with your screen lock")
        ) {
            return false
        }
        store.put(c.copy(members = c.members.map { if (it.fingerprint == peer) it.copy(permissions = permissions) else it }))
        return true
    }

    fun renameMember(circleId: String, peer: String, label: String): Boolean {
        val c = store.get(circleId) ?: return false
        if (c.member(peer) == null || !CirclePolicy.canRename(c, peer, label)) return false
        store.put(c.copy(members = c.members.map { if (it.fingerprint == peer) it.copy(label = label.trim()) else it }))
        return true
    }

    suspend fun removeMember(circleId: String, peer: String): Boolean {
        val c = store.get(circleId) ?: return false
        val m = c.member(peer) ?: return false
        if (!confirm("Remove ${m.label} from ${c.name}", "Confirm with your screen lock")) return false
        val fresh = store.get(circleId) ?: return false
        store.put(fresh.copy(members = fresh.members.filterNot { it.fingerprint == peer }))
        return true
    }

    suspend fun removeCircle(circleId: String): Boolean {
        val c = store.get(circleId) ?: return false
        if (!confirm("Remove circle ${c.name}", "Confirm with your screen lock")) return false
        store.update { list -> list.filterNot { it.id == circleId } }
        return true
    }

    /** "Remove device" deletes the trust entirely (AC8.8): drop it from every circle. The caller passed the gate. */
    fun forgetPeerEverywhere(peer: String) {
        store.update { list -> list.map { c -> c.copy(members = c.members.filterNot { it.fingerprint == peer }) } }
    }

    /**
     * AC8.6: accept an invite. [accepted] is what the invitee left switched on (never more than offered).
     * The invitee's copy of the circle uses the inviter's circle id. A fresh, empty built-in Home of the
     * same name takes the inviter's id instead of creating a second "Home".
     */
    suspend fun acceptInvite(
        circleId: String,
        name: String,
        offered: Set<Permission>,
        accepted: Set<Permission>,
        inviter: String,
        inviterLabel: String,
        alreadyAuthorized: Boolean = false,
    ): Boolean {
        val granted = accepted intersect offered
        val needsGate = granted.any { it in Permission.GUARDED }
        if (needsGate && !alreadyAuthorized &&
            !confirm("Accept $name from $inviterLabel", "Confirm with your screen lock")
        ) {
            return false
        }
        store.update { list ->
            val existing = list.firstOrNull { it.id == circleId }
            val adopt = existing ?: list.firstOrNull { it.builtIn && it.members.isEmpty() && it.name.equals(name, true) }
            val base = adopt?.copy(id = circleId) ?: Circle(circleId, uniqueName(list, name), granted)
            val label = CirclePolicy.labelFor(base, inviter, inviterLabel)
            val members = base.members.filterNot { it.fingerprint == inviter } + Member(inviter, label, granted)
            val updated = base.copy(members = members)
            if (adopt != null) list.map { if (it.id == adopt.id) updated else it } else list + updated
        }
        return true
    }

    private fun uniqueName(list: List<Circle>, name: String): String {
        val base = name.trim().ifEmpty { "Circle" }.take(Defaults.LABEL_MAX)
        if (list.none { it.name.equals(base, true) }) return base
        var n = 2
        while (list.any { it.name.equals("$base $n", true) }) n++
        return "$base $n"
    }

    private fun guardedText(p: Set<Permission>) =
        p.filter { it in Permission.GUARDED }.joinToString(" and ") { it.title }.ifEmpty { "this" }
}
