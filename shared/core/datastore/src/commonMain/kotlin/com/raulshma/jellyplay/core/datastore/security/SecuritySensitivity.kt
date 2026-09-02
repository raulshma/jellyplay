package com.raulshma.jellyplay.core.datastore.security

import com.raulshma.jellyplay.core.model.legacy.UserPreferences

/**
 * Centralised security-sensitive predicate — whether a backup would overwrite
 * PIN/biometric lock config. Used by both v2 ([SecuritySlice]) and
 * legacy ([UserPreferences]) import paths so the two branches cannot drift.
 */
fun SecuritySlice.hasSecuritySensitive(): Boolean =
    pinLockEnabled || biometricLockEnabled || pinHash != null || usePinForPlayerLock

fun UserPreferences.hasSecuritySensitive(): Boolean =
    pinLockEnabled || biometricLockEnabled || pinHash != null || usePinForPlayerLock

fun hasSecuritySensitive(
    pinLockEnabled: Boolean,
    biometricLockEnabled: Boolean,
    pinHash: String?,
    usePinForPlayerLock: Boolean,
): Boolean = pinLockEnabled || biometricLockEnabled || pinHash != null || usePinForPlayerLock
