package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.model.arr.ArrPreferences
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *
 * Server probes ride the shared [ConnectionProbe] board (single-flight
 * restart, cancellation-never-lands-as-Error, localized fallback texts — its
 * KDoc declares the machine policies; this adapter owns the concurrency cap).
 * Behavior changes vs the former hand-rolled sealed map: a probe failure with
 * no server message now localizes "Connection failed" at render time (was a
 * hardcoded literal), and an unexpected probe crash degrades to a declared
 * fallback Error instead of crashing the scope.
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
     * Caps concurrent network probes so a large server list doesn't fan out
     * into dozens of simultaneous connections. Probes are short-lived HTTP
     * calls; a small window keeps the UI responsive while bounding load.
     * Applied inside the board's [ConnectionProbe] action lambda below.
     */
    private val probePermit = Semaphore(4)

    /**
     * Per-server reachability status keyed by [ArrServerConfig.id], owned by
     * the shared [ConnectionProbe] board (the former hand-rolled sealed map).
     * Populated by [testServer] / [testAllServers]; reset via
     * [ConnectionProbe.retain] whenever the server set mutates
     * (add/remove/discovery toggle) so stale entries never linger.
     */
    private val probeBoard: ConnectionProbe<ArrServerConfig, String, Unit> = ConnectionProbe(
        scope = scope,
        keyOf = { server -> server.id },
        action = { server ->
            // The concurrency cap stays HERE, in the Arr adapter —
            // the shared probe deliberately owns no concurrency policy (see
            // its KDoc), and this is the one integration that batches.
            probePermit.withPermit {
                arrRepository.testServer(server).fold(
                    onSuccess = { ConnectionProbe.Outcome.Reachable(Unit) },
                    onFailure = { ConnectionProbe.unreachable(it.message) },
                )
            }
        },
    )
    val serverStatus: StateFlow<Map<String, ConnectionProbe.Status<Unit>>> = probeBoard.status

    init {
        refreshServers()
    }

    fun refreshServers() {
        launch {
            _isRefreshing.value = true
            // resolveServers degrades gracefully to an empty summary on failure,
            // so no try/catch needed here.
            val summary = arrRepository.resolveServers().getOrDefault(ArrServiceSummary())
            _servers.value = summary
            // Drop status (and cancel in-flight probes) for servers no longer
            // present, so a resolved set that lost an entry doesn't keep a
            // stale Error on screen.
            val liveIds = (summary.radarrServers + summary.sonarrServers).map { it.id }.toSet()
            probeBoard.retain(liveIds)
            _isRefreshing.value = false
            // Auto-probe so the user sees reachability on entry instead of having
            // to click Test. Skipped when empty (nothing to test).
            if (!summary.isEmpty) testAllServers(summary)
        }
    }

    /**
     * Probes a single resolved server. Single-flight is the board's RESTART
     * policy: a [testServer] while that id is already probing cancels
     * the in-flight probe (the screen disables the row button while Testing;
     * an in-flight batch supersedes cleanly per key).
     */
    fun testServer(server: ArrServerConfig) {
        probeBoard.probe(server)
    }

    /**
     * Probes every server in [summary] (or the current [_servers] value when
     * null). Unlike the former batch job, there is no batch-level
     * cancellation anymore — each probe rides the board's per-key RESTART
     * policy, so a new batch supersedes a still-running prior batch key by
     * key instead of cancelling it wholesale (and probes of servers that left
     * the set are reaped by [ConnectionProbe.retain] in [refreshServers]).
     */
    fun testAllServers(summary: ArrServiceSummary? = null) {
        val targets = (summary ?: _servers.value).let { it.radarrServers + it.sonarrServers }
        if (targets.isEmpty()) return
        targets.forEach { probeBoard.probe(it) }
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
