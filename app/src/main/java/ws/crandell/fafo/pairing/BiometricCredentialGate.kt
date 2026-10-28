package ws.crandell.fafo.pairing

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.Signature
import kotlin.coroutines.resume

/** [CredentialGate] over `BiometricPrompt`. Needs a FragmentActivity on screen. */
class BiometricCredentialGate(private val activity: FragmentActivity) : CredentialGate {

    override fun hasSecureLock(): Boolean =
        BiometricManager.from(activity).canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(title: String, subtitle: String): GateResult =
        prompt(title, subtitle, DEVICE_CREDENTIAL, null)

    override suspend fun unlockCommandKey(title: String, subtitle: String, signer: Signature): GateResult =
        prompt(title, subtitle, DEVICE_CREDENTIAL or BIOMETRIC_STRONG, signer)

    private suspend fun prompt(title: String, subtitle: String, authenticators: Int, signer: Signature?): GateResult {
        val bm = BiometricManager.from(activity)
        if (bm.canAuthenticate(DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) return GateResult.NoSecureLock
        val allowed = if (bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) authenticators else DEVICE_CREDENTIAL
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowed)
            .build()
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(GateResult.Success(result.cryptoObject?.signature))
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(GateResult.Failed(errString.toString()))
                    }
                },
            )
            if (signer != null) prompt.authenticate(info, BiometricPrompt.CryptoObject(signer)) else prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}
