package com.raulshma.jellyplay.core.ui.components

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [BiometricAuthHelper.isCredentialPromptCancellation], the classification
 * behind `handleCredentialPromptError`: the user-driven cancellation codes (backed
 * out of the dialog, pressed the system Cancel / negative button, dialog cancelled)
 * are the recoverable ones that must NOT surface an error string on the
 * device-credential path, so the user stays free to retry or pick another unlock
 * method. Every genuine failure code must surface one.
 */
class BiometricAuthHelperCancellationTest {

    @Test
    fun `user-driven cancellations are recoverable`() {
        assertTrue(
            BiometricAuthHelper.isCredentialPromptCancellation(
                BiometricPrompt.ERROR_USER_CANCELED
            )
        )
        assertTrue(
            BiometricAuthHelper.isCredentialPromptCancellation(
                BiometricPrompt.ERROR_NEGATIVE_BUTTON
            )
        )
        assertTrue(
            BiometricAuthHelper.isCredentialPromptCancellation(
                BiometricPrompt.ERROR_CANCELED
            )
        )
    }

    @Test
    fun `genuine failures are not recoverable`() {
        assertFalse(
            BiometricAuthHelper.isCredentialPromptCancellation(
                BiometricPrompt.ERROR_HW_UNAVAILABLE
            )
        )
        assertFalse(
            BiometricAuthHelper.isCredentialPromptCancellation(
                BiometricPrompt.ERROR_TIMEOUT
            )
        )
        assertFalse(
            BiometricAuthHelper.isCredentialPromptCancellation(
                BiometricPrompt.ERROR_LOCKOUT_PERMANENT
            )
        )
        assertFalse(
            BiometricAuthHelper.isCredentialPromptCancellation(
                BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
            )
        )
        assertFalse(BiometricAuthHelper.isCredentialPromptCancellation(-1))
    }
}
