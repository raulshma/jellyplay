package com.raulshma.jellyplay.core.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private const val TAG = "BiometricAuthHelper"

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val BIOMETRIC_KEY_ALIAS = "jellyplay_biometric_auth_v1"
private const val DEVICE_CREDENTIAL_KEY_ALIAS = "jellyplay_device_credential_auth_v1"
private const val CRYPTO_TRANSFORMATION = "AES/GCM/NoPadding"

object BiometricAuthHelper {

    enum class Availability {
        AVAILABLE, NO_HARDWARE, NO_ENROLLED, UNSUPPORTED
    }

    fun checkAvailability(context: Context): Availability {
        val manager = BiometricManager.from(context)
        // BIOMETRIC_STRONG only: the prompt is bound to a CryptoObject, and crypto
        // objects cannot be combined with DEVICE_CREDENTIAL authentication.
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        Log.d(TAG, "canAuthenticate result: $result")
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.w(TAG, "No biometric hardware detected")
                Availability.NO_HARDWARE
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.w(TAG, "Biometric hardware exists but no biometrics enrolled")
                Availability.NO_ENROLLED
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Log.w(TAG, "Security update required")
                Availability.UNSUPPORTED
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Log.w(TAG, "Biometric not supported on this device")
                Availability.UNSUPPORTED
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                Log.w(TAG, "Biometric status unknown")
                Availability.UNSUPPORTED
            }
            else -> {
                Log.w(TAG, "Unknown biometric status: $result")
                Availability.UNSUPPORTED
            }
        }
    }

    /**
     * Checks whether the device has a screen-lock credential (PIN/pattern/password)
     * available for [authenticateDeviceCredential]. This is the WhatsApp-style fallback
     * for when biometric authentication cannot be completed.
     */
    fun checkDeviceCredentialAvailability(context: Context): Availability {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NO_ENROLLED
            else -> Availability.UNSUPPORTED
        }
    }

    /**
     * Launches a system prompt authenticating against the device screen-lock credential
     * (PIN/pattern/password). This is the fallback path when biometric authentication is
     * unavailable or has been cancelled.
     *
     * On Android 11 (API 30)+ this is crypto-bound exactly like [authenticate]: the prompt
     * carries a [BiometricPrompt.CryptoObject] wrapping a Keystore key whose auth type is
     * [KeyProperties.AUTH_DEVICE_CREDENTIAL], and the success callback must run `doFinal` on
     * the cipher the framework hands back before access is granted — so the gate cannot be
     * bypassed by hooking the callback.
     *
     * Below API 30 a CryptoObject genuinely cannot be combined with device credential:
     * androidx rejects the combination in [BiometricPrompt.authenticate], and
     * [BiometricPrompt.PromptInfo.Builder] additionally rejects DEVICE_CREDENTIAL as a sole
     * authenticator prior to Android 11 (it is an unsupported combination — allowing
     * BIOMETRIC_WEAK alongside is what makes the legacy prompt buildable at all). The legacy
     * path therefore also accepts a Class 2 biometric where the framework requires it, and
     * its success callback consumes [BiometricPrompt.AuthenticationResult.authenticationType]
     * diagnostically instead of a cipher. Framework enforcement is unchanged in both cases:
     * the user must genuinely present the screen-lock credential (or a biometric, on the
     * legacy path) — this is not a mere UI gate.
     *
     * In both variants [BiometricPrompt.PromptInfo.Builder.setNegativeButtonText] must
     * **not** be called; the system supplies its own Cancel button.
     *
     * Recoverable cancellations (user backed out, pressed the system Cancel button)
     * do **not** invoke [onError]: the user should be free to retry or pick another
     * method without a scary error message. Only genuine failures surface an error
     * string. (Wrong-credential retries for DEVICE_CREDENTIAL are handled inside
     * the system confirm-credentials dialog and never reach this callback — see
     * the note on [handleCredentialPromptError].)
     */
    fun authenticateDeviceCredential(
        activity: FragmentActivity,
        title: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            authenticateDeviceCredentialWithCrypto(activity, title, description, onSuccess, onError)
        } else {
            authenticateDeviceCredentialLegacy(activity, title, description, onSuccess, onError)
        }
    }

    /**
     * Crypto-bound device credential authentication for Android 11 (API 30)+. Mirrors
     * [authenticate]: the key behind the [BiometricPrompt.CryptoObject] requires
     * device-credential authentication, so `doFinal` can only succeed after the user
     * genuinely entered their screen-lock credential. Thin builder over [launchPrompt];
     * see the engine's KDoc for the shared choreography.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun authenticateDeviceCredentialWithCrypto(
        activity: FragmentActivity,
        title: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        launchPrompt(
            activity,
            PromptSpec(
                allowedAuthenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                title = title,
                description = description,
                crypto = deviceCredentialCrypto,
                cancellationsRecoverable = true,
            ),
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    /**
     * Non-crypto device credential authentication for API 28-29, where androidx rejects
     * combining a CryptoObject with DEVICE_CREDENTIAL authentication. Thin builder over
     * [launchPrompt]; see the engine's KDoc for the shared choreography.
     */
    private fun authenticateDeviceCredentialLegacy(
        activity: FragmentActivity,
        title: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        launchPrompt(
            activity,
            PromptSpec(
                // DEVICE_CREDENTIAL alone is an unsupported combination below API 30 —
                // PromptInfo.build() throws. Allowing BIOMETRIC_WEAK as well is supported
                // on every API level and lets the framework substitute a Class 2 biometric
                // where the platform requires it.
                allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                title = title,
                description = description,
                cancellationsRecoverable = true,
            ),
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    /**
     * Shared error handling for both device-credential prompt variants.
     *
     * User-driven cancellations: the user chose not to authenticate right now. Treat these
     * as recoverable — let the caller keep the user on the lock screen so they can retry or
     * pick another method, rather than surfacing an error string.
     *
     * Note on wrong credentials: the plan referenced ERROR_CREDENTIAL_NOT_MATCHED, but that
     * constant exists only on the platform android.hardware.biometrics.BiometricPrompt
     * (Android 11+), not on this androidx.biometric.BiometricPrompt. For DEVICE_CREDENTIAL
     * the AndroidX library surfaces wrong-entry retries inside the system
     * confirm-credentials dialog and never delivers them to this callback, so there is no
     * additional error code to handle here.
     */
    private fun handleCredentialPromptError(
        errorCode: Int,
        errString: CharSequence,
        onError: (String) -> Unit,
    ) {
        if (isCredentialPromptCancellation(errorCode)) {
            Log.d(TAG, "Device credential prompt cancelled by user ($errorCode)")
        } else {
            Log.w(TAG, "Device credential auth error: $errorCode ($errString)")
            onError(errString.toString())
        }
    }

    /**
     * The [BiometricPrompt] error codes that mean "the user chose not to authenticate
     * right now" (backed out, pressed the system Cancel / negative button, or the dialog
     * was cancelled) rather than a genuine failure. Internal (not private) so module
     * tests can pin the classification.
     */
    internal fun isCredentialPromptCancellation(errorCode: Int): Boolean =
        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
            errorCode == BiometricPrompt.ERROR_CANCELED

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit,
    ) {
        // BIOMETRIC_STRONG + negative button + crypto-bound; see [launchPrompt] for why
        // the cipher must be consumed via doFinal before onSuccess fires.
        launchPrompt(
            activity,
            PromptSpec(
                allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG,
                title = title,
                subtitle = subtitle,
                negativeButtonText = negativeButtonText,
                crypto = biometricCrypto,
            ),
            onSuccess = onSuccess,
            onError = onError,
            onFailed = onFailed,
        )
    }

    /**
     * Describes the Keystore binding of a crypto-bound prompt variant: the key alias
     * behind the [BiometricPrompt.CryptoObject], whether the key is unlocked by the
     * device credential (API 30+) or by a BIOMETRIC_STRONG enrollment, and the exact
     * per-variant wording of the error/log strings (via [noun] and
     * [invalidationLogMessage]) so consolidating the choreography cannot silently
     * change what users and logs report.
     */
    private class CryptoBinding(
        val keyAlias: String,
        val bindToDeviceCredential: Boolean,
        val noun: String,
        val invalidationLogMessage: String,
    )

    private val biometricCrypto = CryptoBinding(
        keyAlias = BIOMETRIC_KEY_ALIAS,
        bindToDeviceCredential = false,
        noun = "Biometric",
        invalidationLogMessage = "Biometric key invalidated by enrollment change; recreating",
    )

    private val deviceCredentialCrypto = CryptoBinding(
        keyAlias = DEVICE_CREDENTIAL_KEY_ALIAS,
        bindToDeviceCredential = true,
        noun = "Device credential",
        invalidationLogMessage = "Device credential key invalidated; recreating",
    )

    /**
     * Everything [launchPrompt] needs to specialize the shared choreography for one of
     * the three public authenticate variants:
     *
     *  - [allowedAuthenticators]: BIOMETRIC_STRONG for [authenticate]; DEVICE_CREDENTIAL
     *    for the API 30+ device-credential variant; BIOMETRIC_WEAK or DEVICE_CREDENTIAL
     *    for the legacy one (DEVICE_CREDENTIAL alone makes PromptInfo.build() throw
     *    below API 30; allowing a Class 2 biometric alongside is what keeps the legacy
     *    prompt buildable where the framework requires it).
     *  - [subtitle] / [description]: [authenticate] shows a subtitle, the
     *    device-credential variants a description; null skips the setter.
     *  - [negativeButtonText]: only [authenticate] sets one. The device-credential
     *    variants must NOT — setNegativeButtonText throws when DEVICE_CREDENTIAL is
     *    set, and the system supplies its own Cancel button.
     *  - [crypto]: the Keystore binding for [authenticate] and the API 30+
     *    device-credential variant; null on the legacy path, where androidx rejects
     *    combining a CryptoObject with DEVICE_CREDENTIAL.
     *  - [cancellationsRecoverable]: the device-credential variants treat user-driven
     *    cancellations as recoverable (no [launchPrompt] `onError` — the user should be
     *    free to retry or pick another method), while [authenticate] surfaces every
     *    error string.
     */
    private class PromptSpec(
        val allowedAuthenticators: Int,
        val title: String,
        val subtitle: String? = null,
        val description: String? = null,
        val negativeButtonText: String? = null,
        val crypto: CryptoBinding? = null,
        val cancellationsRecoverable: Boolean = false,
    )

    /**
     * The single prompt choreography all three authenticate variants funnel through
     * ([authenticate], [authenticateDeviceCredentialWithCrypto],
     * [authenticateDeviceCredentialLegacy]). The handshake never differed between them
     * — executor acquisition, the [BiometricPrompt] skeleton, the success proof,
     * PromptInfo assembly, cipher initialization — only the policy knobs do, and
     * those are exactly what [spec] carries.
     *
     * Success proof (crypto variants): the cipher wrapped in the
     * [BiometricPrompt.CryptoObject] is initialized up front and its key was created
     * with `setUserAuthenticationRequired(true)`, so Android guarantees `doFinal` can
     * only succeed after a real authentication — it is not merely a UI gate. Without
     * consuming `result` the success path is only a UI gate, which is flagged by
     * java/android/insecure-local-authentication. The legacy variant has no cipher
     * available at all, so its success callback consumes
     * [BiometricPrompt.AuthenticationResult.authenticationType] diagnostically;
     * framework enforcement is unchanged — the user must genuinely present the
     * screen-lock credential (or a biometric, on the legacy path).
     */
    private fun launchPrompt(
        activity: FragmentActivity,
        spec: PromptSpec,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: (() -> Unit)? = null,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val binding = spec.crypto
                    if (binding == null) {
                        // No cipher is available on this path, so consume the result
                        // diagnostically: record which authenticator the framework
                        // actually accepted. AUTHENTICATION_RESULT_TYPE_UNKNOWN can be
                        // reported on these versions and does NOT imply a weaker method
                        // was used.
                        Log.d(
                            TAG,
                            "Device credential auth succeeded (type=${result.authenticationType})"
                        )
                        onSuccess()
                        return
                    }
                    // Use the authentication result for its cryptographic operation.
                    // The framework hands back the unlocked cipher; running doFinal
                    // completes the key-bound operation and proves the user actually
                    // authenticated.
                    val cipher = result.cryptoObject?.cipher
                    if (cipher == null) {
                        onError("${binding.noun} authentication was not crypto-bound")
                        return
                    }
                    try {
                        cipher.doFinal()
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Crypto operation after ${binding.noun.lowercase()} auth failed",
                            e
                        )
                        onError(e.message ?: "${binding.noun} crypto operation failed")
                        return
                    }
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (spec.cancellationsRecoverable) {
                        handleCredentialPromptError(errorCode, errString, onError)
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    onFailed?.invoke()
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(spec.title)
            .setAllowedAuthenticators(spec.allowedAuthenticators)
            .apply {
                spec.subtitle?.let { setSubtitle(it) }
                spec.description?.let { setDescription(it) }
                // NOTE: skipped when null — setNegativeButtonText throws when
                // DEVICE_CREDENTIAL is set; the framework provides its own cancel
                // button.
                spec.negativeButtonText?.let { setNegativeButtonText(it) }
            }
            .build()

        val binding = spec.crypto
        if (binding == null) {
            prompt.authenticate(promptInfo)
            return
        }
        try {
            val cipher = initCipher(binding)
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            // KeyPermanentlyInvalidatedException or a provider failure: surface it so
            // the caller can fall back to another unlock method.
            Log.e(TAG, "Could not initialize ${binding.noun.lowercase()} crypto", e)
            onError(e.message ?: "${binding.noun} authentication unavailable")
        }
    }

    /**
     * Loads (or creates on first use) an AES/GCM key from the Android Keystore under
     * [binding.keyAlias] that is bound to the binding's authenticator, and returns a
     * [Cipher] initialized for encryption with it. The cipher is what gets wrapped in
     * the [BiometricPrompt]'s [BiometricPrompt.CryptoObject]; the key's
     * `setUserAuthenticationRequired(true)` makes it — and therefore the cipher —
     * unusable until the user authenticates with that authenticator.
     *
     * KeyPermanentlyInvalidatedException recovery lives here, exactly once, for both
     * bindings: the key is permanently dead (the biometric enrollment changed, or the
     * secure lock screen was removed), so the stale entry is dropped, a fresh key
     * bound to the current authenticator state is minted, and the init is retried
     * once — otherwise that login method stays broken until app data is cleared.
     * Recreating is safe: the new key still requires the same authentication to be
     * of any use.
     */
    private fun initCipher(binding: CryptoBinding): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = (keyStore.getEntry(binding.keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: createAuthKey(binding)
        return Cipher.getInstance(CRYPTO_TRANSFORMATION).apply {
            try {
                init(Cipher.ENCRYPT_MODE, secretKey)
            } catch (e: KeyPermanentlyInvalidatedException) {
                Log.w(TAG, binding.invalidationLogMessage, e)
                keyStore.deleteEntry(binding.keyAlias)
                init(Cipher.ENCRYPT_MODE, createAuthKey(binding))
            }
        }
    }

    /**
     * Creates the AES/GCM key for [binding] in the Android Keystore. Both bindings
     * share the shape (PURPOSE_ENCRYPT, GCM/NoPadding, 256-bit,
     * `setUserAuthenticationRequired(true)`); they differ only in which authenticator
     * unlocks the key. The biometric binding additionally invalidates on new biometric
     * enrollment, so a freshly-added fingerprint cannot silently unlock the
     * previously-bound operation. The device-credential binding uses
     * `setUserAuthenticationParameters` — its timeout of 0 means per-use
     * authentication through the BiometricPrompt CryptoObject rather than a
     * time-bound grace period — and is only ever requested from the API 30+ path
     * (the version check keeps lint's NewApi analysis satisfied without changing
     * runtime behavior).
     */
    private fun createAuthKey(binding: CryptoBinding): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val builder = KeyGenParameterSpec.Builder(
            binding.keyAlias,
            KeyProperties.PURPOSE_ENCRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // The key can only be used after the user authenticates with the
            // binding's authenticator, enforced by the framework.
            .setUserAuthenticationRequired(true)
        if (binding.bindToDeviceCredential) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
}

@Composable
fun rememberBiometricAvailability(): BiometricAuthHelper.Availability {
    val context = LocalContext.current
    val availability = remember { mutableStateOf(BiometricAuthHelper.checkAvailability(context)) }
    LaunchedEffect(Unit) {
        availability.value = BiometricAuthHelper.checkAvailability(context)
    }
    return availability.value
}

@Composable
fun rememberDeviceCredentialAvailability(): BiometricAuthHelper.Availability {
    val context = LocalContext.current
    val availability = remember { mutableStateOf(BiometricAuthHelper.checkDeviceCredentialAvailability(context)) }
    LaunchedEffect(Unit) {
        availability.value = BiometricAuthHelper.checkDeviceCredentialAvailability(context)
    }
    return availability.value
}

tailrec fun Context.findFragmentActivity(): FragmentActivity? {
    if (this is FragmentActivity) return this
    if (this is ContextWrapper) return baseContext.findFragmentActivity()
    return null
}
