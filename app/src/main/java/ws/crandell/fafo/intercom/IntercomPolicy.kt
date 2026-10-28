package ws.crandell.fafo.intercom

import ws.crandell.fafo.circles.Circle
import ws.crandell.fafo.circles.CirclePolicy
import ws.crandell.fafo.circles.Permission

/** Who asked to turn room mode on. Only the person at this phone, in the visible app, can (AC10.2). */
enum class RoomModeSource { LOCAL_UI, REMOTE_MESSAGE, BOOT, STICKY_RESTART }

enum class RoomDecision { STARTED, REFUSED_NOT_LOCAL, REFUSED_INCIDENT, REFUSED_NO_INTERCOM_CIRCLE, REFUSED_NOTIFICATIONS }

enum class ListenDecision { OK, ROOM_OFF, CIRCLE_NOT_CHOSEN, NOT_PERMITTED, INCIDENT }

/**
 * F10 rules, pure Kotlin. Room mode starts only from the local UI, never while an incident is open; listening
 * needs room mode on, a chosen circle that grants the listener Intercom; the mic is open exactly while room
 * mode has at least one verified listener (or the local person holds push-to-talk); an alarm ends room mode
 * and drops every listener (AC10.8).
 */
class IntercomPolicy(private val circles: () -> List<Circle>) {
    var roomCircles: Set<String>? = null
        private set
    private val _listeners = LinkedHashMap<String, String>() // peer -> circleId
    val listeners: Set<String> get() = _listeners.keys
    var incidentOpen = false
        private set
    var localTalking = false
        private set

    val roomMode: Boolean get() = roomCircles != null

    /** AC10.3: mic open iff room mode has a verified listener, or the local person holds Talk. */
    val micOpen: Boolean get() = (roomMode && _listeners.isNotEmpty()) || localTalking

    /** Circles that may be chosen for room mode: only those that grant Intercom (AC10.1). */
    fun intercomCircles(): List<Circle> = circles().filter { Permission.INTERCOM in it.permissions || it.members.any { m -> Permission.INTERCOM in m.permissions } }

    fun startRoomMode(chosen: Set<String>, source: RoomModeSource, notificationsAllowed: Boolean): RoomDecision {
        if (source != RoomModeSource.LOCAL_UI) return RoomDecision.REFUSED_NOT_LOCAL
        if (incidentOpen) return RoomDecision.REFUSED_INCIDENT
        if (!notificationsAllowed) return RoomDecision.REFUSED_NOTIFICATIONS
        val allowed = intercomCircles().map { it.id }.toSet()
        val ids = chosen intersect allowed
        if (ids.isEmpty()) return RoomDecision.REFUSED_NO_INTERCOM_CIRCLE
        roomCircles = ids
        return RoomDecision.STARTED
    }

    /** Returns the listeners that were dropped. */
    fun stopRoomMode(): Set<String> {
        val dropped = listeners.toSet()
        roomCircles = null
        _listeners.clear()
        return dropped
    }

    fun mayListen(peer: String, circleId: String): ListenDecision {
        if (incidentOpen) return ListenDecision.INCIDENT
        val chosen = roomCircles ?: return ListenDecision.ROOM_OFF
        if (circleId !in chosen) return ListenDecision.CIRCLE_NOT_CHOSEN
        if (!CirclePolicy.allows(circles(), circleId, peer, Permission.INTERCOM)) return ListenDecision.NOT_PERMITTED
        return ListenDecision.OK
    }

    /** Adds a verified listener; false when [mayListen] would refuse. */
    fun addListener(peer: String, circleId: String): Boolean {
        if (mayListen(peer, circleId) != ListenDecision.OK) return false
        _listeners[peer] = circleId
        return true
    }

    fun removeListener(peer: String) {
        _listeners.remove(peer)
    }

    /** AC10.4: push-to-talk to this phone needs Intercom in the sender's circle; refused during an alarm. */
    fun mayReceivePtt(peer: String, circleId: String): Boolean =
        !incidentOpen && CirclePolicy.allows(circles(), circleId, peer, Permission.INTERCOM)

    fun setLocalTalking(talking: Boolean) {
        localTalking = talking && !incidentOpen
    }

    /** AC10.8: alarm takes priority. Ends room mode, drops listeners, closes the mic. Returns dropped peers. */
    fun onIncident(open: Boolean): Set<String> {
        incidentOpen = open
        if (!open) return emptySet()
        localTalking = false
        return stopRoomMode()
    }
}

/**
 * Half duplex on the receiving phone (AC10.4): one talker at a time. A second talker, or anyone while the
 * local person holds Talk, gets PttBusy and is dropped.
 */
class FloorControl {
    var talker: Pair<String, Int>? = null
        private set

    fun onStart(peer: String, streamId: Int, localTalking: Boolean): Boolean {
        if (localTalking) return false
        val t = talker
        if (t != null && t.first != peer) return false
        talker = peer to streamId
        return true
    }

    fun onStop(peer: String, streamId: Int) {
        if (talker == peer to streamId) talker = null
    }

    fun onDisconnected(peer: String) {
        if (talker?.first == peer) talker = null
    }

    fun clear() {
        talker = null
    }
}
