package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.raulshma.jellyplay.core.model.arr.ArrPreferences
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
 * — matching the [SeerrPreferencesStore] `.catch { emptyPreferences() }` pattern.
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

    val preferences: StateFlow<ArrPreferences> = dataStore.data
        .catch { _ -> emit(emptyPreferences()) }
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
            it[Keys.POLL_INTERVAL_SECONDS] = seconds.coerceAtLeast(15)
        }
    }

    fun setManualServers(servers: List<ArrServerConfig>) {
        secureCredentialsStore.setManualServers(servers)
        manualServersTick.value = secureCredentialsStore.getManualServers()
    }

    private data class SimpleArrPrefs(
        val useSeerrDiscovery: Boolean,
        val pollIntervalSeconds: Int,
    )
}
