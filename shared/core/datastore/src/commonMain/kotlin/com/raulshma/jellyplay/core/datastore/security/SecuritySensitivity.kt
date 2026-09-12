package com.raulshma.jellyplay.core.datastore.security

/**
 * Centralised security-sensitive predicate — whether a backup would overwrite
 * PIN/biometric lock config. Used by the v2 ([SecuritySlice]) import path.
 */
fun SecuritySlice.hasSecuritySensitive(): Boolean =
    pinLockEnabled || biometricLockEnabled || pinHash != null || usePinForPlayerLock

fun hasSecuritySensitive(
    pinLockEnabled: Boolean,
    biometricLockEnabled: Boolean,
    pinHash: String?,
    usePinForPlayerLock: Boolean,
): Boolean = pinLockEnabled || biometricLockEnabled || pinHash != null || usePinForPlayerLock
