package com.raulshma.jellyplay.core.datastore.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

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
 * **Storage-only scope:** PIN *hashing* (PBKDF2 derivation, constant-time
 * compare, legacy-format upgrade) lives in `PinHasher`, and PIN *rate-limit*
 * escalation state (failed-attempt counter / lockout deadline) lives in
 * `PinRateLimiter` — neither is duplicated here. This store persists only the
 * six user-tunable security keys. `setPinHash(null)` removes the stored hash so
 * callers that have just re-hashed via `PinHasher` can clear it atomically.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 */
@Singleton
class SecurityStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
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
}

/**
 * The security &amp; access-control preference slice. Plain data class.
 * Defaults mirror the projection defaults in [SecurityStore.read].
 */
data class SecuritySlice(
    val pinLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val biometricLockEnabled: Boolean = false,
    val usePinForPlayerLock: Boolean = false,
    val autoLockTimerMs: Long = 30_000L,
    val remoteControlEnabled: Boolean = true,
)
