package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.raulshma.jellyplay.core.model.arr.ArrPreferences
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


/**
 * Non-secret *arr preferences, surfaced as [ArrPreferences].
 *
 * Mirrors [SeerrPreferencesStore]'s structure: Jetpack DataStore Preferences
 * for the non-secret toggles, [ArrSecureCredentialsStore] for the secrets.
 * The two are merged in [preferences] so callers see a single [ArrPreferences]
 * (manual server credentials ride along for read convenience).
 *
 * On any read/parse error, the flow degrades to defaults rather than throwing
 * — the module-wide `dataDegradingToDefaults` corrupt-read policy (see
 * `SliceStateFlow.kt`).
 *
 * Note on the manual-server bridge: [ArrSecureCredentialsStore] is backed by
 * EncryptedSharedPreferences which is not observable (no Flow API), so
 * [setManualServers] pokes [manualServersTick] to re-emit. The initial value
 * is seeded in the [MutableStateFlow] constructor so consumers always see the
 * current manual list on first collection.
 */
class ArrPreferencesStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val secureCredentialsStore: ArrSecureCredentialsStore,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private object Keys {
        val USE_SEERR_DISCOVERY = booleanPreferencesKey("arr_use_seerr_discovery")
        val POLL_INTERVAL_SECONDS = intPreferencesKey("arr_poll_interval_seconds")
    }

    /**
     * Hot trigger re-emitted whenever [setManualServers] mutates the encrypted
     * store. Seeded with the current manual list so the first collection is
     * correct without requiring callers to poke.
     */
    private val manualServersTick = MutableStateFlow(secureCredentialsStore.getManualServers())

    val preferences: StateFlow<ArrPreferences> = dataStore.dataDegradingToDefaults()
        .map { prefs ->
            SimpleArrPrefs(
                useSeerrDiscovery = prefs[Keys.USE_SEERR_DISCOVERY] ?: true,
                pollIntervalSeconds = prefs[Keys.POLL_INTERVAL_SECONDS]
                    ?: ArrPreferences.DEFAULT_POLL_INTERVAL_SECONDS,
            )
        }
        .combine(manualServersTick) { simple, manualServers ->
            ArrPreferences(
                useSeerrDiscovery = simple.useSeerrDiscovery,
                pollIntervalSeconds = simple.pollIntervalSeconds,
                manualServers = manualServers,
            )
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ArrPreferences())

    suspend fun setUseSeerrDiscovery(enabled: Boolean) {
        dataStore.edit { it[Keys.USE_SEERR_DISCOVERY] = enabled }
    }

    suspend fun setPollIntervalSeconds(seconds: Int) {
        dataStore.edit {
            it[Keys.POLL_INTERVAL_SECONDS] = seconds.coerceAtLeast(MIN_POLL_INTERVAL_SECONDS)
        }
    }

    fun setManualServers(servers: List<ArrServerConfig>) {
        secureCredentialsStore.setManualServers(servers)
        manualServersTick.value = secureCredentialsStore.getManualServers()
    }

    // ------------------------------------------------------------------
    // SYNC-ONLY SURFACE (jellyplay-plugin-jellyplay settings sync, the
    // `integrations` namespace): a minimal allowlisted read/write pair the
    // sync adapter translates into wire values. NOT a general editing API —
    // the setters above remain the only user-facing write path. The manual
    // *arr servers (API keys) live in [ArrSecureCredentialsStore] and are
    // deliberately absent from the allowlist — they can never sync.
    // ------------------------------------------------------------------

    /**
     * The sync allowlist: raw key name → its typed read/write entry (the
     * shared [SyncEntry] builders — see SyncAllowlist.kt). Key names are
     * private to this store (the adapter never hardcodes them); this map is
     * where renames land. The defaults mirror the read path's inline
     * fallbacks above.
     */
    private val SyncTypedKeys = SyncAllowlist(
        mapOf(
            "arr_use_seerr_discovery" to booleanEntry(Keys.USE_SEERR_DISCOVERY, default = true),
            "arr_poll_interval_seconds" to intEntry(
                Keys.POLL_INTERVAL_SECONDS,
                default = ArrPreferences.DEFAULT_POLL_INTERVAL_SECONDS,
                floor = MIN_POLL_INTERVAL_SECONDS,
            ),
        ),
    )

    /**
     * The raw key names [syncSnapshot]/[syncApply] may ever touch — the sync
     * allowlist (all non-secret *arr configuration; see [SyncTypedKeys]).
     */
    val SyncKeys: Set<String> get() = SyncTypedKeys.keys

    /**
     * The raw stored value per allowlisted key (`null` = the key is absent —
     * readers fall back to that key's default). The sync adapter's snapshot
     * face; a corrupt DataStore read degrades to all-absent per the module's
     * corrupt-read policy.
     */
    suspend fun syncSnapshot(): Map<String, String?> = SyncTypedKeys.snapshotFrom(dataStore)

    /**
     * Writes one allowlisted raw value (the sync adapter's adopt face), or
     * resets the key when [value] is `null` — the reset WRITES the entry's
     * default (never a removal: value-presence resets roam as value writes
     * through the value-only adapter, absence does not, so a removal would
     * leave the server's still-standing row to re-adopt on a later cycle and
     * undo the reset). The poll-interval floor holds on the sync path too.
     * Unallowlisted keys are ignored in BOTH directions; an uncoercible value
     * skips the write, leaving the local value (the prefs-adapter coercion
     * rule).
     */
    suspend fun syncApply(key: String, value: String?) =
        SyncTypedKeys.applyTo(dataStore, key, value)

    private data class SimpleArrPrefs(
        val useSeerrDiscovery: Boolean,
        val pollIntervalSeconds: Int,
    )

    private companion object {
        /** The poll-interval floor [setPollIntervalSeconds] and [syncApply] enforce. */
        const val MIN_POLL_INTERVAL_SECONDS = 15
    }
}
