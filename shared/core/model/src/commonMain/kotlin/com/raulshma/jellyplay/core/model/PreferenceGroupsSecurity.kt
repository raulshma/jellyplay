package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** The security preference aggregate (PIN / biometric lock, remote control). */
@Immutable
@Serializable
data class SecurityPreferences(
    val pinLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val biometricLockEnabled: Boolean = false,
    val usePinForPlayerLock: Boolean = false,
    val autoLockTimerMs: Long = 30_000L,
    val incognitoModeEnabled: Boolean = false,
    val remoteControlEnabled: Boolean = true,
    /** Opt-in for remote "DisplayContent" (idle detail navigation). */
    val remoteDisplayContentEnabled: Boolean = false,
)
