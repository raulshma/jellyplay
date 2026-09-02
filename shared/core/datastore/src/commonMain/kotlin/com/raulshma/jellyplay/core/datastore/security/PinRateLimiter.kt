package com.raulshma.jellyplay.core.datastore.security

import com.raulshma.jellyplay.core.model.wallNowMillis
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.raulshma.jellyplay.core.model.PinLockoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * PIN rate-limiting policy: after [MAX_PIN_ATTEMPTS] failed verifications,
 * apply an exponential backoff lockout (30s → 1m → 5m → 15m → 1h, capped).
 * The lockout state survives process death because it is persisted in
 * DataStore. Successful verification clears the counters (via [resetPinLockout]).
 *
 * Extracted from `UserPreferencesStore` so the escalation state machine lives
 * in one module — previously the policy was welded into the god store while
 * its single consumer (`MainActivity`) reached through three different store
 * methods to drive it.
 *
 * **Storage**: reuses the same `"user_prefs"` DataStore file as
 * `UserPreferencesStore` (same key strings). The DataStore singleton is keyed
 * by `(applicationContext, name)`, so both classes reach the same instance —
 * no migration, no second file.
 *
 * **Depth**: the interface is three functions; the implementation absorbs the
 * ATM-style "expired lockout → one-more-attempt grace" rule and the escalation
 * table, so callers don't re-derive either.
 */
class PinRateLimiter constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    // Eagerly-maintained snapshot of the persisted lockout keys, so the
    // synchronous [getPinLockoutState] can read the latest value without
    // suspending. Mirrors how `UserPreferencesStore.getPinLockoutState` read
    // `preferences.value` before the extraction.
    private val snapshot: StateFlow<Preferences> =
        dataStore.data
            .stateIn(externalScope, SharingStarted.Eagerly, emptyPreferences())

    /**
     * Reactive view of the persisted lockout deadline, for UI surfaces (e.g. the
     * lockscreen) that re-render as the lockout ticks down. Replaces the former
     * live read off the `UserPreferences.pinLockoutUntilEpochMs` aggregate field.
     */
    val pinLockoutUntilEpochMs: StateFlow<Long> =
        snapshot.map { it[Keys.PIN_LOCKOUT_UNTIL_MS] ?: 0L }
            .stateIn(externalScope, SharingStarted.Eagerly, 0L)

    fun getPinLockoutState(): PinLockoutState {
        val prefs = snapshot.value
        val until = prefs[Keys.PIN_LOCKOUT_UNTIL_MS] ?: 0L
        val now = wallNowMillis()
        // If the lockout has expired but the counter hasn't been reset yet,
        // expose the unlocked state and reset the failed-attempt count to the
        // threshold so the user gets exactly one attempt before the next
        // escalation (standard ATM-style behaviour).
        return if (until > 0L && now >= until) {
            PinLockoutState(
                failedAttempts = MAX_PIN_ATTEMPTS,
                lockoutUntilEpochMs = 0L,
            )
        } else {
            PinLockoutState(
                failedAttempts = prefs[Keys.PIN_FAILED_ATTEMPTS] ?: 0,
                lockoutUntilEpochMs = until,
            )
        }
    }

    /**
     * Records a failed PIN attempt and applies the rate-limit policy. Returns
     * the resulting [PinLockoutState] so the caller can render the lockout
     * message immediately.
     */
    suspend fun recordFailedPinAttempt(): PinLockoutState {
        var resultState: PinLockoutState = PinLockoutState.NOT_LOCKED
        dataStore.edit { prefs ->
            val now = wallNowMillis()
            val previousUntil = prefs[Keys.PIN_LOCKOUT_UNTIL_MS] ?: 0L
            val previousAttempts = if (previousUntil > 0L && now >= previousUntil) {
                // Previous lockout expired: give the user another attempt
                // before re-escalating.
                MAX_PIN_ATTEMPTS
            } else {
                prefs[Keys.PIN_FAILED_ATTEMPTS] ?: 0
            }
            val newAttempts = previousAttempts + 1
            prefs[Keys.PIN_FAILED_ATTEMPTS] = newAttempts

            val lockoutUntil = if (newAttempts >= MAX_PIN_ATTEMPTS) {
                val escalations = (newAttempts - MAX_PIN_ATTEMPTS)
                    .coerceAtMost(PIN_LOCKOUT_DURATIONS_MS.lastIndex)
                val durationMs = PIN_LOCKOUT_DURATIONS_MS[escalations]
                val until = now + durationMs
                prefs[Keys.PIN_LOCKOUT_UNTIL_MS] = until
                until
            } else {
                prefs[Keys.PIN_LOCKOUT_UNTIL_MS] = 0L
                0L
            }
            resultState = PinLockoutState(
                failedAttempts = newAttempts,
                lockoutUntilEpochMs = lockoutUntil,
            )
        }
        return resultState
    }

    /** Clears the failed-attempt counter and any active lockout. Call on successful unlock. */
    suspend fun resetPinLockout() {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_FAILED_ATTEMPTS] = 0
            prefs[Keys.PIN_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    internal object Keys {
        val PIN_FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val PIN_LOCKOUT_UNTIL_MS = longPreferencesKey("pin_lockout_until_ms")
    }

    companion object {
        const val MAX_PIN_ATTEMPTS = 5
        val PIN_LOCKOUT_DURATIONS_MS = longArrayOf(
            30_000L,
            60_000L,
            5 * 60_000L,
            15 * 60_000L,
            60 * 60_000L,
        )
    }
}

