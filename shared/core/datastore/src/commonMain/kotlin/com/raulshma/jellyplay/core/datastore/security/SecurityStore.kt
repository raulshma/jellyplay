package com.raulshma.jellyplay.core.datastore.security

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PinHasher
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **security &amp; access-control** preference domain:
 * the PIN-lock toggle and stored PIN hash, the biometric-lock fallback, the
 * PIN-protect-player-when-resuming toggle, the auto-lock timer, and the
 * remote-control (DIAL/cast) switch.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the nullable-pin-hash removal below), its read
 * projection, and its reset-key list end-to-end. Mirrors the `PlaybackStore` /
 * `AppearanceStore` shape.
 *
 * **Storage &amp; verification scope:** the low-level PBKDF2 derivation and
 * constant-time compare live in `PinHasher`, and PIN *rate-limit* escalation
 * state (failed-attempt counter / lockout deadline) lives in `PinRateLimiter` —
 * neither is duplicated here. This store owns the six user-tunable security
 * keys plus the composite PIN operations that combine storage with hashing
 * (`setPin`, `clearPin`, `verifyPinOffMainThread`, legacy-format upgrade).
 * `setPinHash(null)` removes the stored hash so callers that have just
 * re-hashed via `PinHasher` can clear it atomically.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class SecurityStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val PIN_LOCK_ENABLED = booleanPreferencesKey("pin_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val USE_PIN_FOR_PLAYER_LOCK = booleanPreferencesKey("use_pin_for_player_lock")
        val AUTO_LOCK_TIMER_MS = longPreferencesKey("auto_lock_timer_ms")
        val REMOTE_CONTROL_ENABLED = booleanPreferencesKey("remote_control_enabled")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> androidx.datastore.preferences.core.emptyPreferences() }

    val security: StateFlow<SecuritySlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SecuritySlice())

    internal fun read(prefs: Preferences): SecuritySlice = SecuritySlice(
        pinLockEnabled = PreferenceCodec.readBool(prefs, Keys.PIN_LOCK_ENABLED, "pin_lock_enabled", false),
        pinHash = prefs[Keys.PIN_HASH],
        biometricLockEnabled = PreferenceCodec.readBool(prefs, Keys.BIOMETRIC_LOCK_ENABLED, "biometric_lock_enabled", false),
        usePinForPlayerLock = PreferenceCodec.readBool(prefs, Keys.USE_PIN_FOR_PLAYER_LOCK, "use_pin_for_player_lock", false),
        autoLockTimerMs = PreferenceCodec.readLong(prefs, Keys.AUTO_LOCK_TIMER_MS, "auto_lock_timer_ms", 30_000L),
        remoteControlEnabled = PreferenceCodec.readBool(prefs, Keys.REMOTE_CONTROL_ENABLED, "remote_control_enabled", true),
    )

    // ------------------------------------------------------------------
    // Setters — storage only; hashing/verification stay in PinHasher.
    // ------------------------------------------------------------------

    suspend fun setPinLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.PIN_LOCK_ENABLED] = enabled }
    }

    /**
     * Writes [hash] when non-null, otherwise removes the stored PIN hash. Callers
     * derive [hash] via `PinHasher.hash` before calling; passing `null` clears
     * the slot (e.g. on PIN removal).
     */
    suspend fun setPinHash(hash: String?) {
        dataStore.edit {
            if (hash != null) it[Keys.PIN_HASH] = hash
            else it.remove(Keys.PIN_HASH)
        }
    }

    /**
     * Sets a new PIN atomically: hashes [pin] and writes both the hash and
     * `pinLockEnabled = true` in a single DataStore transaction so a concurrent
     * reader can never observe `pinLockEnabled = true` with the previous hash.
     */
    suspend fun setPin(pin: String) {
        if (pin.isBlank()) return
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
        dataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hash
            prefs[Keys.PIN_LOCK_ENABLED] = true
        }
    }

    /**
     * Clears the PIN atomically: disables the lock and removes the hash in a
     * single DataStore transaction.
     */
    suspend fun clearPin() {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_LOCK_ENABLED] = false
            prefs.remove(Keys.PIN_HASH)
        }
    }

    /**
     * Silently upgrades a legacy (unsalted SHA-256) PIN hash to the v2 PBKDF2
     * format after the user has successfully unlocked with [pin]. No-op when
     * the stored hash is already v2 or when no PIN is set. Safe to call after
     * every successful [verifyPinOffMainThread] — callers do not need to gate
     * on [pinHashNeedsMigration] first.
     *
     * @return `true` if the hash was upgraded, `false` otherwise.
     */
    suspend fun upgradePinHashIfLegacy(pin: String): Boolean {
        if (pin.isBlank()) return false
        val current = security.value.pinHash ?: return false
        if (!pinHashNeedsMigration(current)) return false
        // Re-verify against the legacy hash before persisting — protects
        // against an inadvertent upgrade with the wrong PIN if the caller
        // invokes this without first verifying.
        if (!PinHasher.verify(pin, current)) return false
        val upgraded = hashPin(pin)
        // Persist only if the user hasn't cleared the PIN concurrently.
        dataStore.edit { prefs ->
            if (prefs[Keys.PIN_HASH] == current) {
                prefs[Keys.PIN_HASH] = upgraded
            }
        }
        return true
    }

    /**
     * Verifies [pin] against the stored hash, running the PBKDF2 derivation on
     * [Dispatchers.Default] so callers never block the UI thread. Returns `false`
     * when no PIN is set. On success with a legacy hash, the hash is silently
     * upgraded to PBKDF2 (v2).
     */
    suspend fun verifyPinOffMainThread(pin: String): Boolean {
        val storedHash = security.value.pinHash ?: return false
        val valid = withContext(Dispatchers.Default) { PinHasher.verify(pin, storedHash) }
        if (valid && pinHashNeedsMigration(storedHash)) {
            upgradePinHashIfLegacy(pin)
        }
        return valid
    }

    /** Constant-time comparison of [input] against a (v2 or legacy) [storedHash]. */
    fun verifyPin(input: String, storedHash: String?): Boolean = PinHasher.verify(input, storedHash)

    /** PBKDF2 derivation of [pin]. */
    fun hashPin(pin: String): String = PinHasher.hash(pin)

    /**
     * Returns `true` when [storedHash] is in the legacy unsalted-SHA-256
     * format and should be upgraded to PBKDF2 (v2) on the next successful
     * unlock. Callers with write access should re-hash the user's PIN with
     * [hashPin] after a successful [verifyPin] when this returns true.
     */
    fun pinHashNeedsMigration(storedHash: String?): Boolean = PinHasher.needsMigration(storedHash)

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun setUsePinForPlayerLock(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_PIN_FOR_PLAYER_LOCK] = enabled }
    }

    suspend fun setAutoLockTimerMs(ms: Long) {
        dataStore.edit { it[Keys.AUTO_LOCK_TIMER_MS] = ms }
    }

    suspend fun setRemoteControlEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REMOTE_CONTROL_ENABLED] = enabled }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Aggregated by
     * the facade's reset-coverage guard. (PIN rate-limit counters live in
     * `PinRateLimiter` and are excluded from category reset.)
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.PIN_LOCK_ENABLED, Keys.PIN_HASH, Keys.BIOMETRIC_LOCK_ENABLED,
        Keys.USE_PIN_FOR_PLAYER_LOCK, Keys.AUTO_LOCK_TIMER_MS,
        Keys.REMOTE_CONTROL_ENABLED,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. Every key owned here descends under
     * `PreferenceResetCategory.SECURITY`. PIN rate-limit counters live in
     * `PinRateLimiter`, not here, and are excluded from category reset.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.SECURITY -> listOf(
            Keys.PIN_LOCK_ENABLED, Keys.PIN_HASH, Keys.BIOMETRIC_LOCK_ENABLED,
            Keys.USE_PIN_FOR_PLAYER_LOCK, Keys.AUTO_LOCK_TIMER_MS,
            Keys.REMOTE_CONTROL_ENABLED,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the non-security-sensitive key owned
     * by this store (the remote-control switch) from a decoded [UserPreferences].
     * The remote-control switch is independent of the lock config and restored
     * unconditionally.
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.REMOTE_CONTROL_ENABLED] = userPreferences.remoteControlEnabled
        }
    }

    /**
     * Restores the security-sensitive lock config (PIN lock/hash, biometric,
     * use-PIN-for-player-lock, auto-lock timer) from a decoded [UserPreferences].
     * Called separately by the facade so an imported backup can never silently
     * replace the device's lock config — only when the caller explicitly opts in.
     */
    internal suspend fun restoreSecuritySensitive(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_LOCK_ENABLED] = userPreferences.pinLockEnabled
            userPreferences.pinHash?.let { prefs[Keys.PIN_HASH] = it }
            prefs[Keys.BIOMETRIC_LOCK_ENABLED] = userPreferences.biometricLockEnabled
            prefs[Keys.USE_PIN_FOR_PLAYER_LOCK] = userPreferences.usePinForPlayerLock
            prefs[Keys.AUTO_LOCK_TIMER_MS] = userPreferences.autoLockTimerMs
        }
    }

    /**
     * Slice inverse of [read] for the non-security-sensitive key: writes
     * [Keys.REMOTE_CONTROL_ENABLED] from [slice], mirroring [restorePreferences]
     * (the remote-control switch is independent of the lock config and restored
     * unconditionally).
     */
    suspend fun restore(slice: SecuritySlice) {
        dataStore.edit { prefs ->
            prefs[Keys.REMOTE_CONTROL_ENABLED] = slice.remoteControlEnabled
        }
    }

    /**
     * Slice inverse of [read] for the security-sensitive lock config: writes the
     * five lock keys from [slice], mirroring [restoreSecuritySensitive]
     * (PIN lock/hash, biometric, use-PIN-for-player-lock, auto-lock timer).
     * Kept separate so an imported backup never silently replaces the device's
     * lock config — only when the caller explicitly opts in.
     */
    suspend fun restoreSecuritySensitive(slice: SecuritySlice) {
        dataStore.edit { prefs ->
            prefs[Keys.PIN_LOCK_ENABLED] = slice.pinLockEnabled
            slice.pinHash?.let { prefs[Keys.PIN_HASH] = it }
            prefs[Keys.BIOMETRIC_LOCK_ENABLED] = slice.biometricLockEnabled
            prefs[Keys.USE_PIN_FOR_PLAYER_LOCK] = slice.usePinForPlayerLock
            prefs[Keys.AUTO_LOCK_TIMER_MS] = slice.autoLockTimerMs
        }
    }
}

/**
 * The security &amp; access-control preference slice. Plain data class.
 * Defaults mirror the projection defaults in [SecurityStore.read].
 */
@Immutable
@Serializable
data class SecuritySlice(
    val pinLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val biometricLockEnabled: Boolean = false,
    val usePinForPlayerLock: Boolean = false,
    val autoLockTimerMs: Long = 30_000L,
    val remoteControlEnabled: Boolean = true,
)
