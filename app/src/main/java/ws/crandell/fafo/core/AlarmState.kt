package ws.crandell.fafo.core

/** Spec section 2. Pure Kotlin, no Android types. */
sealed interface AlarmState {
    data object Disarmed : AlarmState

    /**
     * The ARMING countdown (Bill 2026-10-28): a few seconds, cancellable, before ARMED. Never persisted: a
     * reboot or process death during ARMING ends DISARMED. Unplugging cancels it; nothing alarms.
     */
    data class Arming(val startedAt: Long, val endsAt: Long) : AlarmState

    /**
     * @param awaitingPower set after a remote Reset while unplugged: unplug detection is paused until
     *   power is reconnected (spec section 2, remote reset (a)).
     * @param unplugPendingSince time the power disconnect arrived; the sensitivity window is running.
     */
    data class Armed(
        val awaitingPower: Boolean = false,
        val unplugPendingSince: Long? = null,
    ) : AlarmState

    /** [cause]: unplugged (replug cancels) or continued handling / pickup while locked (replug does not). */
    data class PreAlarm(
        val incidentId: String,
        val startedAt: Long,
        val endsAt: Long,
        val cause: IncidentCause = IncidentCause.UNPLUG,
        /**
         * MOTION only: the cable came out during (or just before) this pre-alarm. Latched: a settle can no longer
         * cancel it, even if the plugged reading lags the unplug or the cable goes back in.
         */
        val unplugged: Boolean = false,
    ) : AlarmState

    data class Triggered(val incidentId: String, val startedAt: Long) : AlarmState

    data class IncidentPersisted(val incidentId: String, val startedAt: Long) : AlarmState
}

enum class IncidentCause { UNPLUG, MOTION }

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
        is AlarmState.Arming -> "ARMING"
        is AlarmState.Armed -> "ARMED"
        is AlarmState.PreAlarm -> "PRE_ALARM"
        is AlarmState.Triggered -> "TRIGGERED"
        is AlarmState.IncidentPersisted -> "INCIDENT_PERSISTED"
    }
