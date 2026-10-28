package ws.crandell.fafo.pairing

import ws.crandell.fafo.crypto.Crypto
import java.security.Signature

/**
 * This install's keys (F6, AC9.8). Android Keystore in the app; a software fake on the JVM.
 * - identity key: ECDSA P-256 signing key, non-exportable; signs session handshakes.
 * - command key: ECDSA P-256, every signature needs a fresh device-credential or strong-biometric
 *   authentication; signs Reset / Disarm.
 */
interface IdentityKeys {
    fun identityPublic(): ByteArray
    fun commandPublic(): ByteArray
    fun signIdentity(data: ByteArray): ByteArray
    /** An initialized Signature over the command key; BiometricPrompt unlocks it via a CryptoObject. */
    fun commandSigner(): Signature
    fun fingerprint(): String = Crypto.fingerprint(identityPublic())
}
