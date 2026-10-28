package ws.crandell.fafo.core

/** Spec section 2. Pure Kotlin, no Android types. */
sealed interface AlarmState {
    data object Disarmed : AlarmState

    /**
     * @param awaitingPower set after a remote Reset while unplugged: unplug detection is paused until
     *   power is reconnected (spec section 2, remote reset (a)).
     * @param unplugPendingSince time the power disconnect arrived; the sensitivity window is running.
     */
    data class Armed(
        val awaitingPower: Boolean = false,
        val unplugPendingSince: Long? = null,
    ) : AlarmState

    data class PreAlarm(val incidentId: String, val startedAt: Long, val endsAt: Long) : AlarmState

    data class Triggered(val incidentId: String, val startedAt: Long) : AlarmState

    data class IncidentPersisted(val incidentId: String, val startedAt: Long) : AlarmState
}

/** True for every state with an open incident (the alarm states). */
val AlarmState.incidentId: String?
    get() = when (this) {
        is AlarmState.PreAlarm -> incidentId
        is AlarmState.Triggered -> incidentId
        is AlarmState.IncidentPersisted -> incidentId
        else -> null
    }

val AlarmState.isAlarming: Boolean get() = incidentId != null

/** Wire/state names used in alerts and notifications. */
val AlarmState.wireName: String
    get() = when (this) {
        AlarmState.Disarmed -> "DISARMED"
        is AlarmState.Armed -> "ARMED"
        is AlarmState.PreAlarm -> "PRE_ALARM"
        is AlarmState.Triggered -> "TRIGGERED"
        is AlarmState.IncidentPersisted -> "INCIDENT_PERSISTED"
    }
