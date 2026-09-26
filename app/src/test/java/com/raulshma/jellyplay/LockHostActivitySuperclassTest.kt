package com.raulshma.jellyplay

import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the superclass of the two shell activities to [FragmentActivity].
 *
 * androidx.biometric.BiometricPrompt hosts its dialog in a support fragment, so
 * it can only be constructed with a FragmentActivity — the lock screen
 * (`AuthChallengeScreen`) resolves that host via `findFragmentActivity()` from
 * the ambient context. The v0.11.0 performance refactor migrated both hosts to
 * ComponentActivity, which made that lookup return null: fingerprint-only
 * users (no app PIN) then hit an AuthChallengeScreen whose every branch was
 * gated off and rendered as an empty black screen — a hard lockout only
 * recoverable by clearing app data (issue #162). These pins fail the build if
 * either host ever loses FragmentActivity again.
 */
class LockHostActivitySuperclassTest {

    @Test
    fun `MainActivity is a FragmentActivity so the biometric prompt can resolve its host`() {
        assertTrue(
            "MainActivity must extend FragmentActivity: androidx.biometric.BiometricPrompt " +
                "needs a FragmentActivity host, and a non-fragment host leaves fingerprint-only " +
                "users on an empty lock screen (issue #162)",
            FragmentActivity::class.java.isAssignableFrom(MainActivity::class.java),
        )
    }

    @Test
    fun `PlayerActivity is a FragmentActivity for lock-host parity`() {
        assertTrue(
            "PlayerActivity must extend FragmentActivity to keep lock-host parity with " +
                "MainActivity (issue #162)",
            FragmentActivity::class.java.isAssignableFrom(PlayerActivity::class.java),
        )
    }
}
