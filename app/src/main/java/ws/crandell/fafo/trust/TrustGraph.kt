package ws.crandell.fafo.trust

import android.content.Context
import ws.crandell.fafo.pairing.IdentityKeys
import ws.crandell.fafo.pairing.KeystoreIdentityKeys
import ws.crandell.fafo.pairing.TrustedDeviceStore
import java.io.File

/**
 * Credential-protected trust state: keys, trusted devices. Never touched in direct boot or from the boot
 * path (AC2.6a); every caller is after the first unlock (UI, or services that check isUserUnlocked).
 */
object TrustGraph {
    @Volatile private var trusted: TrustedDeviceStore? = null

    /** Test hooks. */
    @Volatile var keysOverride: IdentityKeys? = null

    private val keystoreKeys: IdentityKeys by lazy { KeystoreIdentityKeys() }

    fun trusted(context: Context): TrustedDeviceStore = trusted ?: synchronized(this) {
        trusted ?: TrustedDeviceStore(File(context.applicationContext.filesDir, "trust/trusted.json")).also { trusted = it }
    }

    fun keys(): IdentityKeys = keysOverride ?: keystoreKeys

    /** Tests only. */
    fun reset() {
        trusted = null
        keysOverride = null
    }
}
