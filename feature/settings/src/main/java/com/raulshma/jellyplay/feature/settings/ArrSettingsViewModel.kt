package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.arr.ArrPreferences
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for [ArrSettingsScreen]. Surfaces three concerns:
 *
 * 1. The merged [ArrServiceSummary] (auto-discovered via Seerr + manual
 *    override) so the user can see every server JellyPlay will contact.
 * 2. The non-secret [ArrPreferences] (Seerr-discovery toggle) for editing.
 * 3. CRUD on manual server entries via [ArrPreferencesStore.setManualServers].
 *
 * Discovered servers are read-only here (they originate from Seerr's own
 * settings); only manual entries are editable. Removing a discovered server
 * requires editing it in Seerr.
 *
 * Manual-server mutations read-modify-write against the authoritative
 * [ArrSecureCredentialsStore] (not the [preferences] StateFlow) to avoid a
 * cold-start race: `preferences` seeds with an empty `ArrPreferences()` until
 * the first upstream emission, so reading `preferences.value.manualServers`
 * before that emission would overwrite the encrypted store with just the
 * single new entry and silently delete all prior manual servers.
 */
@HiltViewModel
class ArrSettingsViewModel @Inject constructor(
    private val arrRepository: ArrRepository,
    private val arrPreferencesStore: ArrPreferencesStore,
    private val secureCredentialsStore: ArrSecureCredentialsStore,
) : JellyPlayViewModel() {

    val preferences: StateFlow<ArrPreferences> = arrPreferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ArrPreferences())

    private val _servers = MutableStateFlow<ArrServiceSummary>(ArrServiceSummary())
    val servers: StateFlow<ArrServiceSummary> = _servers.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refreshServers()
    }

    fun refreshServers() {
        launch {
            _isRefreshing.value = true
            // resolveServers degrades gracefully to an empty summary on failure,
            // so no try/catch needed here.
            _servers.value = arrRepository.resolveServers().getOrDefault(ArrServiceSummary())
            _isRefreshing.value = false
        }
    }

    fun setUseSeerrDiscovery(enabled: Boolean) {
        launch {
            arrPreferencesStore.setUseSeerrDiscovery(enabled)
            // Server list depends on the discovery toggle; invalidate the cache
            // so resolveServers re-merges instead of returning the pre-toggle set.
            invalidateAndRefresh()
        }
    }

    fun addManualServer(name: String, baseUrl: String, apiKey: String, kind: ArrServiceKind) {
        if (name.isBlank() || baseUrl.isBlank() || apiKey.isBlank()) return
        launch {
            val latest = secureCredentialsStore.getManualServers()
            val newServer = ArrServerConfig(
                id = "manual-${kind.name.lowercase()}-${System.currentTimeMillis()}",
                baseUrl = baseUrl.trimEnd('/'),
                apiKey = apiKey.trim(),
                name = name.trim(),
                kind = kind,
                isManual = true,
            )
            arrPreferencesStore.setManualServers(latest + newServer)
            invalidateAndRefresh()
        }
    }

    fun removeManualServer(server: ArrServerConfig) {
        if (!server.isManual) return
        launch {
            val remaining = secureCredentialsStore.getManualServers().filterNot { it.id == server.id }
            arrPreferencesStore.setManualServers(remaining)
            invalidateAndRefresh()
        }
    }

    /**
     * Drops the resolved-servers cache then re-resolves. Without the
     * invalidation [resolveServers] would return the cached pre-mutation
     * summary for up to [ArrRepository.SERVER_CACHE_TTL_MS], so the newly
     * added/removed server wouldn't appear on the settings screen immediately.
     */
    private suspend fun invalidateAndRefresh() {
        arrRepository.invalidateServers()
        refreshServers()
    }
}
