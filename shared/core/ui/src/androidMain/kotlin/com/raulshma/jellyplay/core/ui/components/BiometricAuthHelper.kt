package com.raulshma.jellyplay.core.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
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
     * Unlike the crypto-bound biometric prompt, this intentionally does **not** bind a
     * [BiometricPrompt.CryptoObject]: a CryptoObject cannot be combined with
     * [BiometricManager.Authenticators.DEVICE_CREDENTIAL] (the call would throw at
     * runtime). DEVICE_CREDENTIAL authentication is enforced by the framework — the user
     * must genuinely enter their screen-lock credential — so it is not a mere UI gate.
     * Additionally [BiometricPrompt.PromptInfo.Builder.setNegativeButtonText] must **not**
     * be called for DEVICE_CREDENTIAL; the system supplies its own Cancel button.
     *
     * Recoverable cancellations (user backed out, pressed the system Cancel button)
     * do **not** invoke [onError]: the user should be free to retry or pick another
     * method without a scary error message. Only genuine failures surface an error
     * string. (Wrong-credential retries for DEVICE_CREDENTIAL are handled inside
     * the system confirm-credentials dialog and never reach this callback — see
     * the note on [onAuthenticationError].)
     */
    fun authenticateDeviceCredential(
        activity: FragmentActivity,
        title: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        // User-driven cancellations: the user chose not to authenticate
                        // right now. Treat these as recoverable — let the caller keep the
                        // user on the lock screen so they can retry or pick another method,
                        // rather than surfacing an error string.
                        //
                        // Note on wrong credentials: the plan referenced
                        // ERROR_CREDENTIAL_NOT_MATCHED, but that constant exists only on
                        // the platform android.hardware.biometrics.BiometricPrompt
                        // (Android 11+), not on this androidx.biometric.BiometricPrompt.
                        // For DEVICE_CREDENTIAL the AndroidX library surfaces wrong-entry
                        // retries inside the system confirm-credentials dialog and never
                        // delivers them to this callback, so there is no additional error
                        // code to handle here.
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> {
                            Log.d(TAG, "Device credential prompt cancelled by user ($errorCode)")
                        }
                        else -> {
                            Log.w(TAG, "Device credential auth error: $errorCode ($errString)")
                            onError(errString.toString())
                        }
                    }
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            // NOTE: no setNegativeButtonText — it throws when DEVICE_CREDENTIAL is set;
            // the framework provides its own cancel button.
            .build()

        prompt.authenticate(promptInfo)
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        // Bind the prompt to a cryptographic operation so the biometric result is
        // actually used (and enforced by the framework). We create a key that
        // requires user authentication, wrap an ENCRYPT_MODE cipher in a
        // CryptoObject, and only invoke onSuccess after running doFinal on the
        // cipher returned by the system on success.
        //
        // Because the cipher is initialized up front and the key is created with
        // setUserAuthenticationRequired(true), Android guarantees doFinal can only
        // succeed after a real biometric authentication — it is not merely a UI
        // gate. Without consuming `result` the success path is only a UI gate,
        // which is flagged by java/android/insecure-local-authentication.
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // Use the authentication result for its cryptographic operation.
                    // The framework hands back the unlocked cipher; running doFinal
                    // completes the key-bound operation and proves the user actually
                    // authenticated.
                    val cipher = result.cryptoObject?.cipher
                    if (cipher == null) {
                        onError("Biometric authentication was not crypto-bound")
                        return
                    }
                    try {
                        cipher.doFinal()
                    } catch (e: Exception) {
                        Log.e(TAG, "Crypto operation after biometric auth failed", e)
                        onError(e.message ?: "Biometric crypto operation failed")
                        return
                    }
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }
            },
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .setNegativeButtonText(negativeButtonText)
            .build()

        try {
            val cipher = initBiometricCipher()
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            // KeyPermanentlyInvalidatedException (biometric enrollment changed) or
            // a provider failure: surface it so the caller can fall back to PIN.
            Log.e(TAG, "Could not initialize biometric crypto", e)
            onError(e.message ?: "Biometric authentication unavailable")
        }
    }

    /**
     * Loads (or creates on first use) an AES/GCM key from the Android Keystore that
     * is bound to biometric authentication, and returns a [Cipher] initialized for
     * encryption with it. The cipher is what gets wrapped in the [BiometricPrompt]'s
     * [BiometricPrompt.CryptoObject]; `setUserAuthenticationRequired(true)` makes the
     * key — and therefore the cipher — unusable until the user authenticates.
     */
    private fun initBiometricCipher(): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = (keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: createBiometricKey()
        return Cipher.getInstance(CRYPTO_TRANSFORMATION).apply {
            try {
                init(Cipher.ENCRYPT_MODE, secretKey)
            } catch (e: KeyPermanentlyInvalidatedException) {
                // Biometric enrollment changed since the key was created, so the
                // key is permanently dead. Drop the stale entry, mint a fresh key
                // bound to the current enrollment, and retry the init once —
                // otherwise biometric login stays broken until app data is cleared.
                Log.w(TAG, "Biometric key invalidated by enrollment change; recreating", e)
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
                init(Cipher.ENCRYPT_MODE, createBiometricKey())
            }
        }
    }

    private fun createBiometricKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // The key can only be used after the user authenticates with a
            // BIOMETRIC_STRONG credential, enforced by the framework.
            .setUserAuthenticationRequired(true)
        // Invalidate the key if a new biometric is enrolled, so a freshly-added
        // fingerprint cannot silently unlock the previously-bound operation.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
