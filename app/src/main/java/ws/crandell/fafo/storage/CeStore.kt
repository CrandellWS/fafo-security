package ws.crandell.fafo.storage

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ws.crandell.fafo.core.Defaults

/** Credential-protected settings (spec section 4, "CE"). */
data class CeSettings(
    val photoOnTrigger: Boolean = Defaults.PHOTO_ON_TRIGGER,
    val photoIntervalS: Int = Defaults.PHOTO_INTERVAL_S,
    val camera: String = Defaults.CAMERA,
    val photoCapMb: Int = Defaults.PHOTO_CAP_MB,
    val deviceLabel: String = defaultLabel(),
    val stayReachable: Boolean = Defaults.STAY_REACHABLE,
    val chimeOnListen: Boolean = Defaults.CHIME_ON_LISTEN,
    val intercomTransport: String = Defaults.INTERCOM_TRANSPORT,
    val intercomCodec: String = Defaults.INTERCOM_CODEC,
) {
    companion object {
        fun defaultLabel(): String = (Build.MODEL ?: "This phone").take(Defaults.LABEL_MAX).ifBlank { "This phone" }
    }
}

/**
 * Credential-protected (default) DataStore. Never opened from the boot path, the Application class or
 * any direct-boot component (spec AC2.6a).
 */
open class CeStore(private val store: DataStore<Preferences>?) {

    open val settings: Flow<CeSettings>
        get() = requireStore().data.map { it.toSettings() }

    open suspend fun read(): CeSettings = settings.first()

    open suspend fun update(transform: (CeSettings) -> CeSettings) {
        requireStore().edit {
            val n = transform(it.toSettings())
            it[PHOTO_ON_TRIGGER] = n.photoOnTrigger
            it[PHOTO_INTERVAL_S] = n.photoIntervalS
            it[CAMERA] = n.camera
            it[PHOTO_CAP_MB] = n.photoCapMb
            it[DEVICE_LABEL] = n.deviceLabel.take(Defaults.LABEL_MAX)
            it[STAY_REACHABLE] = n.stayReachable
            it[CHIME_ON_LISTEN] = n.chimeOnListen
            it[INTERCOM_TRANSPORT] = n.intercomTransport
            it[INTERCOM_CODEC] = n.intercomCodec
        }
    }

    /** Onboarding steps that cannot be read from the system (F7 1, 7, 8). */
    open val onboardingFlags: Flow<Set<String>>
        get() = requireStore().data.map { it[ONBOARDING_DONE] ?: emptySet() }

    open suspend fun markOnboarding(flag: String) {
        requireStore().edit { it[ONBOARDING_DONE] = (it[ONBOARDING_DONE] ?: emptySet()) + flag }
    }

    private fun requireStore(): DataStore<Preferences> = store ?: error("CeStore not available")

    private fun Preferences.toSettings() = CeSettings(
        photoOnTrigger = this[PHOTO_ON_TRIGGER] ?: Defaults.PHOTO_ON_TRIGGER,
        photoIntervalS = this[PHOTO_INTERVAL_S] ?: Defaults.PHOTO_INTERVAL_S,
        camera = this[CAMERA] ?: Defaults.CAMERA,
        photoCapMb = this[PHOTO_CAP_MB] ?: Defaults.PHOTO_CAP_MB,
        deviceLabel = this[DEVICE_LABEL] ?: CeSettings.defaultLabel(),
        stayReachable = this[STAY_REACHABLE] ?: Defaults.STAY_REACHABLE,
        chimeOnListen = this[CHIME_ON_LISTEN] ?: Defaults.CHIME_ON_LISTEN,
        intercomTransport = this[INTERCOM_TRANSPORT] ?: Defaults.INTERCOM_TRANSPORT,
        intercomCodec = this[INTERCOM_CODEC] ?: Defaults.INTERCOM_CODEC,
    )

    companion object {
        const val FILE_NAME = "fafo_ce"

        private val PHOTO_ON_TRIGGER = booleanPreferencesKey("photo_on_trigger")
        private val PHOTO_INTERVAL_S = intPreferencesKey("photo_interval_s")
        private val CAMERA = stringPreferencesKey("camera")
        private val PHOTO_CAP_MB = intPreferencesKey("photo_cap_mb")
        private val DEVICE_LABEL = stringPreferencesKey("device_label")
        private val STAY_REACHABLE = booleanPreferencesKey("stay_reachable")
        private val CHIME_ON_LISTEN = booleanPreferencesKey("chime_on_listen")
        private val INTERCOM_TRANSPORT = stringPreferencesKey("intercom_transport")
        private val INTERCOM_CODEC = stringPreferencesKey("intercom_codec")
        private val ONBOARDING_DONE = stringSetPreferencesKey("onboarding_done")

        const val FLAG_INTRO = "intro"
        const val FLAG_BUILT_IN = "built_in"
        const val FLAG_SELF_TEST = "self_test"

        @Volatile private var instance: CeStore? = null

        /**
         * Test hook: lets a test install a CeStore that throws if opened, to prove the boot path
         * never touches CE storage (BUILD-PLAN M6).
         */
        @Volatile var override: CeStore? = null

        fun get(context: Context): CeStore = override ?: instance ?: synchronized(this) {
            instance ?: create(context, CoroutineScope(Dispatchers.IO + SupervisorJob())).also { instance = it }
        }

        fun create(context: Context, scope: CoroutineScope): CeStore {
            val app = context.applicationContext
            val ds = PreferenceDataStoreFactory.create(scope = scope) { app.preferencesDataStoreFile(FILE_NAME) }
            return CeStore(ds)
        }
    }
}
