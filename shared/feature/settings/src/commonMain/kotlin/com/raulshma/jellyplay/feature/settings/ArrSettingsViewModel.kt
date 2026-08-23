package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.arr.ArrPreferences
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
class ArrSettingsViewModel(
    private val arrRepository: ArrRepository,
    private val arrPreferencesStore: ArrPreferencesStore,
    private val secureCredentialsStore: ArrSecureCredentialsStore,
) : JellyPlayViewModel() {

    val preferences: StateFlow<ArrPreferences> = arrPreferencesStore.preferences

    private val _servers = MutableStateFlow<ArrServiceSummary>(ArrServiceSummary())
    val servers: StateFlow<ArrServiceSummary> = _servers.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Per-server reachability status keyed by [ArrServerConfig.id]. Populated
     * by [testServer] / [testAllServers]; reset to [ServerConnectionStatus.Idle]
     * whenever the server set mutates (add/remove/discovery toggle) so stale
     * entries never linger. Consumers read via [serverStatus].
     */
    private val _serverStatus = MutableStateFlow<Map<String, ServerConnectionStatus>>(emptyMap())
    val serverStatus: StateFlow<Map<String, ServerConnectionStatus>> = _serverStatus.asStateFlow()

    /**
     * Caps concurrent network probes so a large server list doesn't fan out
     * into dozens of simultaneous connections. Probes are short-lived HTTP
     * calls; a small window keeps the UI responsive while bounding load.
     */
    private val probePermit = Semaphore(4)

    /**
     * Tracks the in-flight [testAllServers] wave so a second invocation (e.g.
     * the user toggling discovery or adding a server while one wave is still
     * running) cancels the prior instead of stacking overlapping probes.
     */
    private var testAllJob: Job? = null

    init {
        refreshServers()
    }

    override fun onCleared() {
        super.onCleared()
        testAllJob?.cancel()
    }

    fun refreshServers() {
        launch {
            _isRefreshing.value = true
            // resolveServers degrades gracefully to an empty summary on failure,
            // so no try/catch needed here.
            val summary = arrRepository.resolveServers().getOrDefault(ArrServiceSummary())
            _servers.value = summary
            // Drop status for servers no longer present; seed the rest as Idle so
            // a resolved set that lost an entry doesn't keep a stale Error on screen.
            val liveIds = (summary.radarrServers + summary.sonarrServers).map { it.id }.toSet()
            _serverStatus.update { it.filterKeys { key -> key in liveIds } }
            _isRefreshing.value = false
            // Auto-probe so the user sees reachability on entry instead of having
            // to click Test. Skipped when empty (nothing to test).
            if (!summary.isEmpty) testAllServers(summary)
        }
    }

    /**
     * Probes a single resolved server and pushes the result into [serverStatus].
     * Concurrent-safe: each call writes only its own key via an atomic CAS
     * (`update`), so [testAllServers] can fan these out without a coordinating
     * lock even when many probes resolve near-simultaneously.
     */
    fun testServer(server: ArrServerConfig) {
        launch { probeServer(server) }
    }

    /**
     * Probes every server in [summary] (or the current [_servers] value when
     * null). Probes run with a bounded concurrency cap ([probePermit]) so a
     * large list doesn't open dozens of simultaneous connections. A new wave
     * cancels a still-running prior wave so rapid mutations don't stack.
     */
    fun testAllServers(summary: ArrServiceSummary? = null) {
        testAllJob?.cancel()
        val targets = (summary ?: _servers.value).let { it.radarrServers + it.sonarrServers }
        if (targets.isEmpty()) return
        testAllJob = launch {
            coroutineScope {
                targets.map { async { probeServer(it) } }.awaitAll()
            }
        }
    }

    /**
     * The actual probe: marks the server [ServerConnectionStatus.Testing],
     * waits its turn through [probePermit], then resolves and stores the
     * result. Factored out so both [testServer] (single) and [testAllServers]
     * (batched) share one code path and the concurrency cap applies uniformly.
     */
    private suspend fun probeServer(server: ArrServerConfig) {
        _serverStatus.update { it + (server.id to ServerConnectionStatus.Testing) }
        probePermit.withPermit {
            val result = arrRepository.testServer(server)
            _serverStatus.update {
                it + (server.id to result.fold(
                    onSuccess = { ServerConnectionStatus.Connected },
                    onFailure = { err -> ServerConnectionStatus.Error(err.message ?: "Connection failed") },
                ))
            }
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

    /**
     * Reachability state for a single resolved *arr server, surfaced in the
     * settings list so the user can tell at a glance whether the Coming Soon
     * calendar / queue features will be able to reach that instance. Mirrors
     * the [SeerrSettingsViewModel.ConnectionStatus] shape.
     */
    sealed class ServerConnectionStatus {
        /** Not yet probed (or the server set just changed). */
        data object Idle : ServerConnectionStatus()
        /** Probe in flight. */
        data object Testing : ServerConnectionStatus()
        /** `GET /api/v3/system/status` returned 2xx. */
        data object Connected : ServerConnectionStatus()
        /** Probe failed; [message] is the friendly ApiException text. */
        data class Error(val message: String) : ServerConnectionStatus()
    }
}
