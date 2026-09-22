package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Single auditable write seam over the 19 domain stores +
 * [AppRuntimeStateStore]. Every preference mutation from a ViewModel flows
 * through here so cross-cutting write concerns (logging, validation, batching)
 * have one place to live.
 *
 * The surface is deliberately minimal:
 *  - [edit] is the general-purpose path: its receiver is a
 *    [PreferencesEditScope] exposing each owning store, so a call looks like
 *    `editor.edit { appearance.setThemeMode(mode) }`. Every store setter is
 *    reached this way — there are no per-field convenience forwarders on the
 *    editor (the former ~50 one-line named setters had test-only callers and
 *    were deleted; writes that require a post-write side effect on a scheduler
 *    belong to the layer that owns those scheduler dependencies — they call
 *    [edit] for the store write and then trigger the scheduler themselves).
 *  - [hashPin] / [verifyPin] delegate to `SecurityStore`, which owns hashing
 *    (PinHasher) and rate-limit escalation (PinRateLimiter) as collaborators.
 *  - [resetCategory] / [clearAllPreferences] are the reset machinery, which
 *    still lives on the [UserPreferencesStore] facade.
 */
class PreferencesEditor constructor(
    private val scope: CoroutineScope,
    private val editScope: PreferencesEditScope,
    // Retained for the reset / clear machinery that still lives on the facade.
    private val store: UserPreferencesStore,
) {
    /** Fire-and-forget launch over the application scope. */
    private fun run(block: suspend () -> Unit) = scope.launch { block() }

    private val securityStore: SecurityStore get() = editScope.security

    /**
     * Runs [block] against the [PreferencesEditScope] on the application scope.
     * Use this for any store mutation that does not need a post-write side
     * effect. Reach the owning store directly: `appearance.setThemeMode(mode)`.
     */
    fun edit(block: suspend PreferencesEditScope.() -> Unit) = run { editScope.block() }

    // ----- Security / PIN: composite ops live in SecurityStore, which owns
    // hashing + verification (PinHasher) and rate-limit escalation
    // (PinRateLimiter) as collaborators.
    fun hashPin(pin: String): String = securityStore.hashPin(pin)
    suspend fun verifyPin(pin: String): Boolean = securityStore.verifyPinOffMainThread(pin)

    /** Resets all preferences in a specific category to their default values. */
    fun resetCategory(category: PreferenceResetCategory) = run { store.resetCategory(category) }

    /**
     * Clears the preferences DataStore only (preferences reset to defaults).
     * Does **not** sign out the user or delete downloaded media, cache, or DB.
     */
    fun clearAllPreferences() = run { store.clearAllPreferencesOnly() }
}
