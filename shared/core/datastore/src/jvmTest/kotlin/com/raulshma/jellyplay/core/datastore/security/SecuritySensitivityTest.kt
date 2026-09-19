package com.raulshma.jellyplay.core.datastore.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the centralised [hasSecuritySensitive] predicate: for both
 * extension overloads ([SecuritySlice] and legacy [UserPreferences]) every one
 * of the four lock fields must flip the predicate on its own, and the bare
 * 4-argument overload must agree with them.
 */
class SecuritySensitivityTest {

    @Test
    fun `SecuritySlice with all defaults is not sensitive`() {
        assertFalse(SecuritySlice().hasSecuritySensitive())
    }

    @Test
    fun `each SecuritySlice field flips the predicate on its own`() {
        val singleFieldFlips = listOf(
            SecuritySlice(pinLockEnabled = true),
            SecuritySlice(biometricLockEnabled = true),
            SecuritySlice(pinHash = "hash"),
            SecuritySlice(usePinForPlayerLock = true),
        )

        singleFieldFlips.forEach { slice ->
            assertTrue(slice.hasSecuritySensitive(), "expected sensitive for $slice")
        }
    }

    @Test
    fun `SecuritySlice blank pinHash still counts as present`() {
        // Presence, not content — an empty hash means a PIN was (re)configured.
        assertTrue(SecuritySlice(pinHash = "").hasSecuritySensitive())
    }

    @Test
    fun `bare overload flips per argument`() {
        assertFalse(hasSecuritySensitive(pinLockEnabled = false, biometricLockEnabled = false, pinHash = null, usePinForPlayerLock = false))
        assertTrue(hasSecuritySensitive(pinLockEnabled = true, biometricLockEnabled = false, pinHash = null, usePinForPlayerLock = false))
        assertTrue(hasSecuritySensitive(pinLockEnabled = false, biometricLockEnabled = true, pinHash = null, usePinForPlayerLock = false))
        assertTrue(hasSecuritySensitive(pinLockEnabled = false, biometricLockEnabled = false, pinHash = "hash", usePinForPlayerLock = false))
        assertTrue(hasSecuritySensitive(pinLockEnabled = false, biometricLockEnabled = false, pinHash = null, usePinForPlayerLock = true))
    }

    @Test
    fun `both overloads agree on the same lock configuration`() {
        // The two extension overloads must not drift — feed the same lock state
        // through both and compare.
        val slices = listOf(
            SecuritySlice(),
            SecuritySlice(pinLockEnabled = true),
            SecuritySlice(biometricLockEnabled = true, pinHash = "h"),
            SecuritySlice(usePinForPlayerLock = true, autoLockTimerMs = 5L),
        )
        val expected = slices.map {
            hasSecuritySensitive(it.pinLockEnabled, it.biometricLockEnabled, it.pinHash, it.usePinForPlayerLock)
        }

        assertEquals(expected, slices.map { it.hasSecuritySensitive() })
    }
}
