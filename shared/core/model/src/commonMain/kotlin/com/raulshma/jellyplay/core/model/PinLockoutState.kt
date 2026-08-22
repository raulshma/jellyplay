package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable

/**
 * Snapshot of the PIN-rate-limiting state surfaced to the UI after each
 * attempt. [lockoutUntilEpochMs] is `0L` when the user is not currently
 * locked out; otherwise it is the wall-clock time (in milliseconds since the
 * Unix epoch) at which the lockout expires and the next attempt will be
 * accepted again.
 */
@Immutable
data class PinLockoutState(
    val failedAttempts: Int,
    val lockoutUntilEpochMs: Long,
) {
    val isLockedOut: Boolean get() = lockoutUntilEpochMs > 0L

    companion object {
        val NOT_LOCKED = PinLockoutState(failedAttempts = 0, lockoutUntilEpochMs = 0L)
    }
}
