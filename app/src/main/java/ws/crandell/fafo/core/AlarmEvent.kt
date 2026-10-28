package ws.crandell.fafo.core

sealed interface AlarmEvent {
    /** User tap in the visible app. */
    data class ArmRequested(val plugged: Boolean, val secureLock: Boolean) : AlarmEvent

    /** The ARMING countdown ran out; the service re-read plugged state and the secure lock. */
    data class ArmingElapsed(val plugged: Boolean, val secureLock: Boolean) : AlarmEvent

    /** Cancel during ARMING (Home button or notification action). Needs no auth: the result is "not armed". */
    data object ArmCancelled : AlarmEvent

    data class PowerDisconnected(val at: Long) : AlarmEvent
    data class PowerConnected(val at: Long) : AlarmEvent

    /** Sensitivity window ran out; the service re-read plugged state and keyguard (AC2.2). */
    data class WindowElapsed(val plugged: Boolean, val locked: Boolean) : AlarmEvent

    data object PreAlarmElapsed : AlarmEvent

    /**
     * The bump ladder escalated (continued handling or a pickup) while ARMED. Starts a MOTION pre-alarm only
     * when [locked]; the owner handling the unlocked phone never sets it off.
     */
    data class MotionEscalated(val locked: Boolean) : AlarmEvent

    /** ACTION_USER_PRESENT: the owner unlocked with the real credential or biometric. */
    data object UserPresent : AlarmEvent

    /** Disarm in the app or the notification action (both need the device unlocked). */
    data object DisarmTapped : AlarmEvent

    /** Boot broadcast or a sticky restart, with the flags read from device-protected storage. */
    data class BootCompleted(
        val armed: Boolean,
        val incidentActive: Boolean,
        val incidentId: String?,
        val incidentStartedAt: Long?,
        val awaitingPower: Boolean,
        val plugged: Boolean,
        val locked: Boolean,
    ) : AlarmEvent

    /** Tap on the alarm notification or the full-screen alarm activity (a visible-app start). */
    data class NotificationTapped(val ceUnlocked: Boolean) : AlarmEvent

    /** Verified remote Reset (F9, AC9.8); permission and signature were checked before this event. */
    data class RemoteReset(
        val peerLabel: String,
        val circleId: String,
        val plugged: Boolean,
        val ceUnlocked: Boolean,
    ) : AlarmEvent

    /** Verified remote Disarm (F9, AC9.8). */
    data class RemoteDisarm(
        val peerLabel: String,
        val circleId: String,
        val ceUnlocked: Boolean,
    ) : AlarmEvent
}

enum class RefusalReason { NOT_PLUGGED, NO_SECURE_LOCK, NO_INCIDENT, BEFORE_FIRST_UNLOCK, WRONG_STATE }

enum class CloseReason { CANCELLED, UNLOCKED, DISARMED, RESET_REMOTE, DISARMED_REMOTE }

sealed interface AlarmEffect {
    data class PersistArmed(val armed: Boolean, val awaitingPower: Boolean = false) : AlarmEffect
    data class PersistIncident(val active: Boolean, val id: String? = null, val startedAt: Long? = null) : AlarmEffect
    /** ARMING: fire [AlarmEvent.ArmingElapsed] after [delayMs] (at [endsAt] by the machine's clock). */
    data class StartArmingTimer(val delayMs: Long, val endsAt: Long) : AlarmEffect
    data object CancelArmingTimer : AlarmEffect
    /**
     * Entered ARMED: store the arm time (elapsed realtime + boot id) and clear the unlock trail, so Disarm
     * skips the prompt only after a lock and unlock that happened after this moment (DisarmPolicy).
     */
    data object RecordArmedAt : AlarmEffect
    data class StartWindowTimer(val delayMs: Long) : AlarmEffect
    data object CancelWindowTimer : AlarmEffect
    /** Quieter warning beeps on the PreAlarmSchedule and the countdown timer. */
    data class StartPreAlarm(val durationMs: Long, val endsAt: Long) : AlarmEffect
    data object StartSiren : AlarmEffect
    data object StopSound : AlarmEffect
    /** Unplugged while unlocked: "Still armed: plug back in or disarm" chirp + notification. */
    data object StillArmedWarning : AlarmEffect
    /** First photo now, then every N s while the incident is open. */
    data object StartPhotos : AlarmEffect
    data object StopPhotos : AlarmEffect
    /** Re-add the camera FGS type (visible-app start only). */
    data object AddCameraType : AlarmEffect
    data class CloseIncident(val id: String, val reason: CloseReason, val by: String? = null) : AlarmEffect
    /** Send alerts to circle members with Alerts (F9). */
    data class StartAlerts(val incidentId: String) : AlarmEffect
    data object StopAlerts : AlarmEffect
    /** One chirp so the person at the phone knows a remote command was applied (AC9.8). */
    data object RemoteChirp : AlarmEffect
    /** Alarm takes priority: end intercom room mode and drop listeners (AC10.8). */
    data object EndIntercom : AlarmEffect
    data object StopService : AlarmEffect
    data class Refused(val reason: RefusalReason) : AlarmEffect
}
