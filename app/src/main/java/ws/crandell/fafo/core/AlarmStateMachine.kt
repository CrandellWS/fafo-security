package ws.crandell.fafo.core

import java.security.SecureRandom

fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}

/** Settings the machine needs, read fresh on every event so changes apply immediately. */
data class AlarmConfig(
    val sensitivityMs: Long = Defaults.SENSITIVITY_MS,
    val preAlarmMs: Long = Defaults.PRE_ALARM_S * 1000L,
    val disarmOnUnlock: Boolean = Defaults.DISARM_ON_UNLOCK,
)

object IncidentIds {
    private val random = SecureRandom()
    fun next(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Spec section 2 state machine. Pure Kotlin: it returns effects in the order they must run. The service
 * executes them sequentially, so [AlarmEffect.PersistIncident] always lands before any sound starts.
 */
class AlarmStateMachine(
    private val clock: Clock,
    private val config: () -> AlarmConfig = { AlarmConfig() },
    private val newIncidentId: () -> String = IncidentIds::next,
    initial: AlarmState = AlarmState.Disarmed,
) {
    var state: AlarmState = initial
        private set

    fun handle(event: AlarmEvent): List<AlarmEffect> {
        val (next, effects) = transition(state, event)
        state = next
        return effects
    }

    private fun transition(s: AlarmState, e: AlarmEvent): Pair<AlarmState, List<AlarmEffect>> = when (e) {
        is AlarmEvent.ArmRequested -> onArm(s, e)
        is AlarmEvent.PowerDisconnected -> onDisconnect(s, e)
        is AlarmEvent.PowerConnected -> onConnect(s)
        is AlarmEvent.WindowElapsed -> onWindowElapsed(s, e)
        AlarmEvent.PreAlarmElapsed -> onPreAlarmElapsed(s)
        AlarmEvent.UserPresent -> onUserPresent(s)
        AlarmEvent.DisarmTapped -> if (s == AlarmState.Disarmed) s to emptyList() else disarm(s, CloseReason.DISARMED, null)
        is AlarmEvent.BootCompleted -> onBoot(s, e)
        is AlarmEvent.NotificationTapped -> onNotificationTapped(s, e)
        is AlarmEvent.RemoteReset -> onRemoteReset(s, e)
        is AlarmEvent.RemoteDisarm -> onRemoteDisarm(s, e)
    }

    private fun onArm(s: AlarmState, e: AlarmEvent.ArmRequested): Pair<AlarmState, List<AlarmEffect>> {
        if (s != AlarmState.Disarmed) return s to emptyList()
        if (!e.plugged) return s to listOf(AlarmEffect.Refused(RefusalReason.NOT_PLUGGED))
        if (!e.secureLock) return s to listOf(AlarmEffect.Refused(RefusalReason.NO_SECURE_LOCK))
        return AlarmState.Armed() to listOf(AlarmEffect.PersistArmed(armed = true, awaitingPower = false))
    }

    private fun onDisconnect(s: AlarmState, e: AlarmEvent.PowerDisconnected): Pair<AlarmState, List<AlarmEffect>> {
        if (s !is AlarmState.Armed || s.awaitingPower || s.unplugPendingSince != null) return s to emptyList()
        return s.copy(unplugPendingSince = e.at) to listOf(AlarmEffect.StartWindowTimer(config().sensitivityMs))
    }

    private fun onConnect(s: AlarmState): Pair<AlarmState, List<AlarmEffect>> = when (s) {
        is AlarmState.Armed -> when {
            s.awaitingPower -> AlarmState.Armed() to listOf(AlarmEffect.PersistArmed(armed = true, awaitingPower = false))
            s.unplugPendingSince != null -> s.copy(unplugPendingSince = null) to listOf(AlarmEffect.CancelWindowTimer)
            else -> s to emptyList()
        }
        is AlarmState.PreAlarm -> AlarmState.Armed() to listOf(
            AlarmEffect.StopSound,
            AlarmEffect.StopPhotos,
            AlarmEffect.StopAlerts,
            AlarmEffect.CloseIncident(s.incidentId, CloseReason.CANCELLED),
            AlarmEffect.PersistIncident(active = false),
        )
        // Plugging back in during TRIGGERED / INCIDENT_PERSISTED does not stop the siren.
        else -> s to emptyList()
    }

    private fun onWindowElapsed(s: AlarmState, e: AlarmEvent.WindowElapsed): Pair<AlarmState, List<AlarmEffect>> {
        if (s !is AlarmState.Armed || s.unplugPendingSince == null || s.awaitingPower) return s to emptyList()
        val cleared = s.copy(unplugPendingSince = null)
        if (e.plugged) return cleared to emptyList()
        if (!e.locked) return cleared to listOf(AlarmEffect.StillArmedWarning)
        return startIncident()
    }

    private fun startIncident(): Pair<AlarmState, List<AlarmEffect>> {
        val now = clock.now()
        val id = newIncidentId()
        val pre = config().preAlarmMs
        val persist = AlarmEffect.PersistIncident(active = true, id = id, startedAt = now)
        return if (pre <= 0L) {
            AlarmState.Triggered(id, now) to listOf(
                persist,
                AlarmEffect.EndIntercom,
                AlarmEffect.StartSiren,
                AlarmEffect.StartPhotos,
                AlarmEffect.StartAlerts(id),
            )
        } else {
            AlarmState.PreAlarm(id, now, now + pre) to listOf(
                persist,
                AlarmEffect.EndIntercom,
                AlarmEffect.StartPreAlarm(pre, now + pre),
                AlarmEffect.StartPhotos,
                AlarmEffect.StartAlerts(id),
            )
        }
    }

    private fun onPreAlarmElapsed(s: AlarmState): Pair<AlarmState, List<AlarmEffect>> {
        if (s !is AlarmState.PreAlarm) return s to emptyList()
        if (clock.now() < s.endsAt) return s to emptyList()
        return AlarmState.Triggered(s.incidentId, s.startedAt) to listOf(AlarmEffect.StartSiren)
    }

    private fun onUserPresent(s: AlarmState): Pair<AlarmState, List<AlarmEffect>> = when (s) {
        is AlarmState.PreAlarm, is AlarmState.Triggered, is AlarmState.IncidentPersisted ->
            disarm(s, CloseReason.UNLOCKED, null)
        is AlarmState.Armed -> if (config().disarmOnUnlock) disarm(s, CloseReason.UNLOCKED, null) else s to emptyList()
        AlarmState.Disarmed -> s to emptyList()
    }

    private fun disarm(s: AlarmState, reason: CloseReason, by: String?): Pair<AlarmState, List<AlarmEffect>> {
        val effects = mutableListOf<AlarmEffect>()
        val id = s.incidentId
        if (s is AlarmState.Armed && s.unplugPendingSince != null) effects += AlarmEffect.CancelWindowTimer
        if (id != null) {
            effects += AlarmEffect.StopSound
            effects += AlarmEffect.StopPhotos
            effects += AlarmEffect.StopAlerts
            effects += AlarmEffect.CloseIncident(id, reason, by)
            effects += AlarmEffect.PersistIncident(active = false)
        }
        effects += AlarmEffect.PersistArmed(armed = false)
        if (by != null) effects += AlarmEffect.RemoteChirp
        effects += AlarmEffect.StopService
        return AlarmState.Disarmed to effects
    }

    private fun onBoot(s: AlarmState, e: AlarmEvent.BootCompleted): Pair<AlarmState, List<AlarmEffect>> {
        // Idempotent: a running machine ignores a second boot broadcast.
        if (s != AlarmState.Disarmed) return s to emptyList()
        if (e.incidentActive) {
            // A reboot during the countdown is treated as tampering: full siren, no second countdown.
            val id = e.incidentId ?: newIncidentId()
            val started = e.incidentStartedAt ?: clock.now()
            val effects = mutableListOf<AlarmEffect>()
            if (e.incidentId == null) effects += AlarmEffect.PersistIncident(true, id, started)
            effects += AlarmEffect.StartSiren
            return AlarmState.IncidentPersisted(id, started) to effects
        }
        if (!e.armed) return s to listOf(AlarmEffect.StopService)
        if (e.awaitingPower) {
            return if (e.plugged) {
                AlarmState.Armed() to listOf(AlarmEffect.PersistArmed(armed = true, awaitingPower = false))
            } else {
                AlarmState.Armed(awaitingPower = true) to emptyList()
            }
        }
        if (!e.plugged && e.locked) {
            // A thief who powers off, then unplugs: run the window, then pre-alarm as usual.
            val now = clock.now()
            return AlarmState.Armed(unplugPendingSince = now) to listOf(AlarmEffect.StartWindowTimer(config().sensitivityMs))
        }
        return AlarmState.Armed() to emptyList()
    }

    private fun onNotificationTapped(s: AlarmState, e: AlarmEvent.NotificationTapped): Pair<AlarmState, List<AlarmEffect>> {
        if (!e.ceUnlocked) return s to emptyList()
        return when (s) {
            is AlarmState.IncidentPersisted -> s to listOf(AlarmEffect.AddCameraType, AlarmEffect.StartPhotos)
            is AlarmState.Armed -> s to listOf(AlarmEffect.AddCameraType)
            else -> s to emptyList()
        }
    }

    private fun onRemoteReset(s: AlarmState, e: AlarmEvent.RemoteReset): Pair<AlarmState, List<AlarmEffect>> {
        val id = s.incidentId ?: return s to listOf(AlarmEffect.Refused(RefusalReason.NO_INCIDENT))
        if (s is AlarmState.IncidentPersisted && !e.ceUnlocked) {
            return s to listOf(AlarmEffect.Refused(RefusalReason.BEFORE_FIRST_UNLOCK))
        }
        val awaiting = !e.plugged
        return AlarmState.Armed(awaitingPower = awaiting) to listOf(
            AlarmEffect.StopSound,
            AlarmEffect.StopPhotos,
            AlarmEffect.StopAlerts,
            AlarmEffect.CloseIncident(id, CloseReason.RESET_REMOTE, e.peerLabel),
            AlarmEffect.PersistIncident(active = false),
            AlarmEffect.PersistArmed(armed = true, awaitingPower = awaiting),
            AlarmEffect.RemoteChirp,
        )
    }

    private fun onRemoteDisarm(s: AlarmState, e: AlarmEvent.RemoteDisarm): Pair<AlarmState, List<AlarmEffect>> {
        if (s == AlarmState.Disarmed) return s to listOf(AlarmEffect.Refused(RefusalReason.WRONG_STATE))
        if (s is AlarmState.IncidentPersisted && !e.ceUnlocked) {
            return s to listOf(AlarmEffect.Refused(RefusalReason.BEFORE_FIRST_UNLOCK))
        }
        return disarm(s, CloseReason.DISARMED_REMOTE, e.peerLabel)
    }
}
