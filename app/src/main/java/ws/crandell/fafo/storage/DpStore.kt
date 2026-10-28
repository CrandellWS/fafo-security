package ws.crandell.fafo.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ws.crandell.fafo.auth.UnlockTrail
import ws.crandell.fafo.core.AlarmConfig
import ws.crandell.fafo.core.Defaults
import java.io.File

/** Settings that must be readable before first unlock (spec section 4, "DP"). */
data class DpSettings(
    val sensitivityMs: Long = Defaults.SENSITIVITY_MS,
    val sirenSound: String = Defaults.SIREN_SOUND,
    val forceMaxVolume: Boolean = Defaults.FORCE_MAX_VOLUME,
    val preAlarmS: Int = Defaults.PRE_ALARM_S,
    val disarmOnUnlock: Boolean = Defaults.DISARM_ON_UNLOCK,
    val motion: String = Defaults.MOTION,
    val unlockGraceS: Int = Defaults.UNLOCK_GRACE_S,
    val armingS: Int = Defaults.ARMING_S,
    /** SYSTEM / LIGHT / DARK. DP so the lock-screen status screen can read it before first unlock. */
    val theme: String = Defaults.THEME,
) {
    fun alarmConfig() = AlarmConfig(sensitivityMs, preAlarmS * 1000L, disarmOnUnlock, armingS * 1000L)
}

/** Runtime flags in DP (spec section 4, last paragraph). */
data class DpState(
    val armed: Boolean = false,
    val awaitingPower: Boolean = false,
    val incidentActive: Boolean = false,
    val incidentId: String? = null,
    val incidentStartedAt: Long? = null,
)

/**
 * Device-protected DataStore (`createDeviceProtectedStorageContext()`), readable in direct boot.
 * Holds only flags and DP settings, never photos, keys or the trusted-device list (spec section 5).
 */
class DpStore(private val store: DataStore<Preferences>) {

    val settings: Flow<DpSettings> = store.data.map { it.toSettings() }
    val state: Flow<DpState> = store.data.map { it.toState() }

    suspend fun readSettings(): DpSettings = settings.first()
    suspend fun readState(): DpState = state.first()

    /** The disarm unlock trail (auth/DisarmPolicy.kt). DP, so the armed service writes it before first unlock. */
    suspend fun readTrail(): UnlockTrail = store.data.first().let {
        UnlockTrail(it[ARMED_AT], it[LAST_SCREEN_OFF_AT], it[LAST_USER_PRESENT_AT], it[TRAIL_BOOT_ID] ?: 0L)
    }

    /** Entered ARMED: new arm time, and any earlier screen-off / unlock no longer counts. */
    suspend fun recordArmedAt(at: Long, bootId: Long) = store.edit {
        it[ARMED_AT] = at
        it[TRAIL_BOOT_ID] = bootId
        it.remove(LAST_SCREEN_OFF_AT)
        it.remove(LAST_USER_PRESENT_AT)
    }

    suspend fun recordScreenOff(at: Long, bootId: Long) = store.edit {
        if (it[TRAIL_BOOT_ID] == bootId) it[LAST_SCREEN_OFF_AT] = at
    }

    suspend fun recordUserPresent(at: Long, bootId: Long) = store.edit {
        if (it[TRAIL_BOOT_ID] == bootId) it[LAST_USER_PRESENT_AT] = at
    }

    suspend fun setArmed(armed: Boolean, awaitingPower: Boolean) = store.edit {
        it[ARMED] = armed
        it[AWAITING_POWER] = armed && awaitingPower
    }

    suspend fun setIncident(active: Boolean, id: String?, startedAt: Long?) = store.edit {
        it[INCIDENT_ACTIVE] = active
        if (active && id != null) it[INCIDENT_ID] = id else it.remove(INCIDENT_ID)
        if (active && startedAt != null) it[INCIDENT_STARTED_AT] = startedAt else it.remove(INCIDENT_STARTED_AT)
    }

    suspend fun update(transform: (DpSettings) -> DpSettings) = store.edit {
        val n = transform(it.toSettings())
        it[SENSITIVITY_MS] = n.sensitivityMs
        it[SIREN_SOUND] = n.sirenSound
        it[FORCE_MAX_VOLUME] = n.forceMaxVolume
        it[PRE_ALARM_S] = n.preAlarmS
        it[DISARM_ON_UNLOCK] = n.disarmOnUnlock
        it[MOTION] = n.motion
        it[UNLOCK_GRACE_S] = n.unlockGraceS
        it[ARMING_S] = n.armingS
        it[THEME] = n.theme
    }

    private fun Preferences.toSettings() = DpSettings(
        sensitivityMs = this[SENSITIVITY_MS] ?: Defaults.SENSITIVITY_MS,
        sirenSound = this[SIREN_SOUND] ?: Defaults.SIREN_SOUND,
        forceMaxVolume = this[FORCE_MAX_VOLUME] ?: Defaults.FORCE_MAX_VOLUME,
        preAlarmS = this[PRE_ALARM_S] ?: Defaults.PRE_ALARM_S,
        disarmOnUnlock = this[DISARM_ON_UNLOCK] ?: Defaults.DISARM_ON_UNLOCK,
        motion = this[MOTION] ?: Defaults.MOTION,
        unlockGraceS = this[UNLOCK_GRACE_S] ?: Defaults.UNLOCK_GRACE_S,
        armingS = this[ARMING_S] ?: Defaults.ARMING_S,
        theme = this[THEME] ?: Defaults.THEME,
    )

    private fun Preferences.toState() = DpState(
        armed = this[ARMED] ?: false,
        awaitingPower = this[AWAITING_POWER] ?: false,
        incidentActive = this[INCIDENT_ACTIVE] ?: false,
        incidentId = this[INCIDENT_ID],
        incidentStartedAt = this[INCIDENT_STARTED_AT],
    )

    companion object {
        const val FILE_NAME = "fafo_dp"

        private val ARMED = booleanPreferencesKey("armed")
        private val AWAITING_POWER = booleanPreferencesKey("awaiting_power")
        private val INCIDENT_ACTIVE = booleanPreferencesKey("incident_active")
        private val INCIDENT_ID = stringPreferencesKey("incident_id")
        private val INCIDENT_STARTED_AT = longPreferencesKey("incident_started_at")
        private val SENSITIVITY_MS = longPreferencesKey("sensitivity_ms")
        private val SIREN_SOUND = stringPreferencesKey("siren_sound")
        private val FORCE_MAX_VOLUME = booleanPreferencesKey("force_max_volume")
        private val PRE_ALARM_S = intPreferencesKey("pre_alarm_s")
        private val DISARM_ON_UNLOCK = booleanPreferencesKey("disarm_on_unlock")
        private val MOTION = stringPreferencesKey("motion")
        private val UNLOCK_GRACE_S = intPreferencesKey("unlock_grace_s")
        private val ARMING_S = intPreferencesKey("arming_s")
        private val THEME = stringPreferencesKey("theme")
        private val ARMED_AT = longPreferencesKey("armed_at")
        private val LAST_SCREEN_OFF_AT = longPreferencesKey("last_screen_off_at")
        private val LAST_USER_PRESENT_AT = longPreferencesKey("last_user_present_at")
        private val TRAIL_BOOT_ID = longPreferencesKey("trail_boot_id")

        @Volatile private var instance: DpStore? = null

        /**
         * The file inside device-protected storage. Not `preferencesDataStoreFile()`: that helper goes
         * through `applicationContext` and would silently land in credential-protected storage.
         */
        fun file(context: Context): File =
            File(context.applicationContext.createDeviceProtectedStorageContext().filesDir, "datastore/$FILE_NAME.preferences_pb")

        /** One DataStore per file per process (DataStore requirement). */
        fun get(context: Context): DpStore = instance ?: synchronized(this) {
            instance ?: create(context, CoroutineScope(Dispatchers.IO + SupervisorJob())).also { instance = it }
        }

        fun create(context: Context, scope: CoroutineScope): DpStore {
            val f = file(context)
            val ds = PreferenceDataStoreFactory.create(scope = scope) { f }
            return DpStore(ds)
        }
    }
}
