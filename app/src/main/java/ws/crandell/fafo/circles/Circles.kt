package ws.crandell.fafo.circles

import kotlinx.serialization.Serializable
import ws.crandell.fafo.core.Defaults
import ws.crandell.fafo.crypto.Crypto

/** AC8.3. Bit values travel in CircleInvite / Alert. */
enum class Permission(val bit: Int, val title: String, val powerText: String) {
    ALERTS(1, "Alerts", "get your alarm alerts"),
    RESET(2, "Reset", "reset your alarm"),
    DISARM(4, "Disarm", "disarm you"),
    INTERCOM(8, "Intercom", "push-to-talk to you");

    companion object {
        fun toBits(set: Set<Permission>): Int = set.fold(0) { acc, p -> acc or p.bit }
        fun fromBits(bits: Int): Set<Permission> = entries.filter { bits and it.bit != 0 }.toSet()
        /** Reset and Disarm are the powers that need the owner's credential to grant (AC8.5). */
        val GUARDED = setOf(RESET, DISARM)
    }
}

@Serializable
data class Member(val fingerprint: String, val label: String, val permissions: Set<Permission>)

/** AC8.1. [permissions] is the circle's level, applied to members added later; each member holds its own copy. */
@Serializable
data class Circle(
    val id: String,
    val name: String,
    val permissions: Set<Permission>,
    val members: List<Member> = emptyList(),
    val builtIn: Boolean = false,
) {
    fun member(fingerprint: String): Member? = members.firstOrNull { it.fingerprint == fingerprint }
}

/** Pure F8 rules. No storage, no Android. */
object CirclePolicy {
    val HOME_PERMISSIONS = setOf(Permission.ALERTS, Permission.RESET, Permission.DISARM, Permission.INTERCOM)
    val NEW_CIRCLE_PERMISSIONS = setOf(Permission.INTERCOM)
    val WORK_PERMISSIONS = setOf(Permission.INTERCOM, Permission.RESET)

    fun newId(): String = Crypto.hex(Crypto.randomBytes(16))

    fun home(id: String = newId()) = Circle(id, "Home", HOME_PERMISSIONS, builtIn = true)

    fun newCircle(name: String, id: String = newId()) =
        Circle(id, name.trim().take(Defaults.LABEL_MAX), if (name.trim().equals("Work", true)) WORK_PERMISSIONS else NEW_CIRCLE_PERMISSIONS)

    /**
     * AC8.8 / AC8.3: [peer] may use [permission] on this device only under [circleId], only if it is a member
     * there and that member holds the permission. Disarm implies Reset; Reset never implies Disarm.
     */
    fun allows(circles: List<Circle>, circleId: String, peer: String, permission: Permission): Boolean {
        val m = circles.firstOrNull { it.id == circleId }?.member(peer) ?: return false
        return permission in m.permissions || (permission == Permission.RESET && Permission.DISARM in m.permissions)
    }

    /** Effective powers [peer] has under [circleId]. */
    fun powers(circles: List<Circle>, circleId: String, peer: String): Set<Permission> =
        Permission.entries.filter { allows(circles, circleId, peer, it) }.toSet()

    /** Circles in which [peer] holds [permission] (for "send my alert to ..."). */
    fun circlesGranting(circles: List<Circle>, peer: String, permission: Permission): List<Circle> =
        circles.filter { allows(circles, it.id, peer, permission) }

    fun validLabel(label: String): Boolean = label.trim().length in 1..Defaults.LABEL_MAX

    /**
     * AC8.2: a peer's suggested label never replaces a local one; on a collision with another member it gets
     * a numeric suffix ("Register 2"), so a peer cannot impersonate another device by renaming itself.
     */
    fun labelFor(circle: Circle, peer: String, suggested: String): String {
        circle.member(peer)?.let { return it.label }
        val base = suggested.trim().ifEmpty { "Device" }.take(Defaults.LABEL_MAX)
        val taken = circle.members.filter { it.fingerprint != peer }.map { it.label.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while (true) {
            val suffix = " $n"
            val candidate = base.take(Defaults.LABEL_MAX - suffix.length) + suffix
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }

    /** A local rename: must be valid and unique within the circle. */
    fun canRename(circle: Circle, peer: String, newLabel: String): Boolean =
        validLabel(newLabel) && circle.members.none { it.fingerprint != peer && it.label.equals(newLabel.trim(), true) }

    /** True when going from [old] to [new] grants Reset or Disarm, which needs the credential (AC8.5). */
    fun grantsGuarded(old: Set<Permission>, new: Set<Permission>): Boolean =
        (new - old).any { it in Permission.GUARDED }
}
