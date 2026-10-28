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
import ws.crandell.fafo.mail.MailConfig
import ws.crandell.fafo.mail.MailProviderKind
import ws.crandell.fafo.mail.SmtpSecurity

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
    // Photo email (round 3; opt-in, off by default).
    val mailEnabled: Boolean = false,
    val mailRecipients: List<String> = emptyList(),
    val mailProvider: String = "SMTP",
    val smtpHost: String = "",
    val smtpPort: Int = SmtpSecurity.SSL_TLS.defaultPort,
    val smtpSecurity: String = SmtpSecurity.SSL_TLS.name,
    val smtpUser: String = "",
    val smtpFrom: String = "",
    /** The app password, sealed by SecretBox (Keystore AES-GCM). Never the plain text. */
    val mailSecret: String = "",
    val gmailAccount: String = "",
    /** A test email went through with the current settings (Setup step). */
    val mailTestOk: Boolean = false,
    /** Item 7: propose open Wi-Fi when queued mail has no connection. */
    val mailOpenWifi: Boolean = false,
) {
    fun mailConfig() = MailConfig(
        enabled = mailEnabled,
        recipients = mailRecipients,
        provider = MailProviderKind.entries.firstOrNull { it.name == mailProvider } ?: MailProviderKind.SMTP,
        host = smtpHost.trim(),
        port = smtpPort,
        security = SmtpSecurity.entries.firstOrNull { it.name == smtpSecurity } ?: SmtpSecurity.SSL_TLS,
        user = smtpUser.trim(),
        from = smtpFrom.trim(),
        gmailAccount = gmailAccount,
    )

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
            it[MAIL_ENABLED] = n.mailEnabled
            it[MAIL_RECIPIENTS] = n.mailRecipients.joinToString(",")
            it[MAIL_PROVIDER] = n.mailProvider
            it[SMTP_HOST] = n.smtpHost
            it[SMTP_PORT] = n.smtpPort
            it[SMTP_SECURITY] = n.smtpSecurity
            it[SMTP_USER] = n.smtpUser
            it[SMTP_FROM] = n.smtpFrom
            it[MAIL_SECRET] = n.mailSecret
            it[GMAIL_ACCOUNT] = n.gmailAccount
            it[MAIL_TEST_OK] = n.mailTestOk
            it[MAIL_OPEN_WIFI] = n.mailOpenWifi
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
        mailEnabled = this[MAIL_ENABLED] ?: false,
        mailRecipients = this[MAIL_RECIPIENTS]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
        mailProvider = this[MAIL_PROVIDER] ?: "SMTP",
        smtpHost = this[SMTP_HOST] ?: "",
        smtpPort = this[SMTP_PORT] ?: SmtpSecurity.SSL_TLS.defaultPort,
        smtpSecurity = this[SMTP_SECURITY] ?: SmtpSecurity.SSL_TLS.name,
        smtpUser = this[SMTP_USER] ?: "",
        smtpFrom = this[SMTP_FROM] ?: "",
        mailSecret = this[MAIL_SECRET] ?: "",
        gmailAccount = this[GMAIL_ACCOUNT] ?: "",
        mailTestOk = this[MAIL_TEST_OK] ?: false,
        mailOpenWifi = this[MAIL_OPEN_WIFI] ?: false,
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
        private val MAIL_ENABLED = booleanPreferencesKey("mail_enabled")
        private val MAIL_RECIPIENTS = stringPreferencesKey("mail_recipients")
        private val MAIL_PROVIDER = stringPreferencesKey("mail_provider")
        private val SMTP_HOST = stringPreferencesKey("smtp_host")
        private val SMTP_PORT = intPreferencesKey("smtp_port")
        private val SMTP_SECURITY = stringPreferencesKey("smtp_security")
        private val SMTP_USER = stringPreferencesKey("smtp_user")
        private val SMTP_FROM = stringPreferencesKey("smtp_from")
        private val MAIL_SECRET = stringPreferencesKey("mail_secret")
        private val GMAIL_ACCOUNT = stringPreferencesKey("gmail_account")
        private val MAIL_TEST_OK = booleanPreferencesKey("mail_test_ok")
        private val MAIL_OPEN_WIFI = booleanPreferencesKey("mail_open_wifi")

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
