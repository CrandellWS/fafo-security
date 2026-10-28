package ws.crandell.fafo.pairing

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.Signature
import javax.crypto.Mac
import kotlin.coroutines.resume

/** [CredentialGate] over `BiometricPrompt`. Needs a FragmentActivity on screen. */
class BiometricCredentialGate(private val activity: FragmentActivity) : CredentialGate {

    override fun hasSecureLock(): Boolean =
        BiometricManager.from(activity).canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(title: String, subtitle: String): GateResult =
        prompt(title, subtitle, DEVICE_CREDENTIAL, null)

    override suspend fun unlockCommandKey(title: String, subtitle: String, signer: Signature): GateResult =
        prompt(title, subtitle, DEVICE_CREDENTIAL or BIOMETRIC_STRONG, BiometricPrompt.CryptoObject(signer))

    override suspend fun confirmUser(title: String, subtitle: String, mac: Mac?): GateResult =
        prompt(title, subtitle, DEVICE_CREDENTIAL or BIOMETRIC_STRONG, mac?.let { BiometricPrompt.CryptoObject(it) })

    private suspend fun prompt(title: String, subtitle: String, authenticators: Int, crypto: BiometricPrompt.CryptoObject?): GateResult {
        val bm = BiometricManager.from(activity)
        if (bm.canAuthenticate(DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) return GateResult.NoSecureLock
        val allowed = if (bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) authenticators else DEVICE_CREDENTIAL
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowed)
            .build()
        return suspendCancellableCoroutine { cont ->
            lateinit var onDestroy: LifecycleEventObserver
            fun done() = activity.lifecycle.removeObserver(onDestroy)
            val prompt = BiometricPrompt(
                activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        done()
                        if (cont.isActive) cont.resume(GateResult.Success(result.cryptoObject?.signature, result.cryptoObject?.mac))
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        done()
                        if (cont.isActive) cont.resume(failure(errorCode, errString))
                    }
                },
            )
            // The callback belongs to this activity. If it is destroyed (rotation, theme change, finish) the
            // prompt can never answer: count it as cancelled so Arm / Disarm don't stay "Confirming" forever.
            onDestroy = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY && cont.isActive) {
                    prompt.cancelAuthentication()
                    cont.resume(GateResult.Failed("Confirmation interrupted", cancelled = true))
                }
            }
            activity.lifecycle.addObserver(onDestroy)
            if (crypto != null) prompt.authenticate(info, crypto) else prompt.authenticate(info)
            cont.invokeOnCancellation {
                prompt.cancelAuthentication()
                ContextCompat.getMainExecutor(activity).execute { done() }
            }
        }
    }

    companion object {
        /** Maps a BiometricPrompt error to a short, owner-facing result. */
        fun failure(errorCode: Int, errString: CharSequence): GateResult.Failed = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED ->
                GateResult.Failed(errString.toString(), cancelled = true)
            BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                GateResult.Failed("Too many attempts. Try again later.")
            else -> GateResult.Failed(errString.toString())
        }
    }
}
