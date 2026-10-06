package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingWrite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/**
 * One syncable namespace contributed by a local store. The engine is
 * adapter-agnostic: each adapter owns ONE plugin namespace (e.g. "prefs"),
 * knows how to read its local state, detect its own local changes, and apply
 * remote values.
 *
 * Change detection is mirror-based, never wall-clock-based: the adapter keeps
 * a last-synced snapshot; current != mirror == dirty. LWW arbitration happens
 * server-side across devices; the client stays clock-skew-immune.
 */
interface ProfileSyncAdapter {
    /** The plugin namespace this adapter syncs (one adapter : one namespace). */
    val namespace: String

    /** Current local values for every syncable key. */
    suspend fun snapshot(): Map<String, JsonElement>

    /**
     * The subset of [current] that changed locally since the last
     * [markSynced] — the engine's dirty set. These keys are PUSHED, never
     * silently overwritten by remote values.
     */
    suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement>

    /** Applies remote values locally (the engine passes only clean keys this device lost). */
    suspend fun applyRemote(entries: Map<String, JsonElement>)

    /** Marks [values] as the last-synced state so they stop reading as dirty. */
    suspend fun markSynced(values: Map<String, JsonElement>)
}

/**
 * The settings/profile sync engine for the jellyfin-plugin-jellyplay
 * companion plugin. Opt-in (no sync runs until [setEnabled(true)] — default
 * off), identity-scoped via [SessionCacheRegistry] invalidation, gated on the
 * plugin's capabilities probe, serialized by one mutex (concurrent cycles
 * would race the dirty sets).
 *
 * Cycle (plugin repo docs/CONTRACT.md "Sync protocol"), per adapter:
 *  1. pull `resolved/{deviceProfile}` — the merged base+profile+admin-defaults view;
 *  2. ADOPT: clean keys (not locally dirty) where remote differs are applied
 *     and marked synced — another device won, server authoritative;
 *  3. PUSH: dirty keys that differ from remote are sent; the server applies
 *     per-key LWW and reports applied/rejected; only APPLIED keys are marked
 *     synced, so `stale-write` rejects retry next cycle and then converge.
 *
 * Local change watching stays with the store owners: they call [requestSync]
 * on change (the engine serializes + gates internally). SSE live re-sync
 * rides [JellyPlayEventsRepository]'s settings stream into [requestSync].
 */
class ProfileSyncRepository(
    private val apiClient: com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient,
    private val statusStore: JellyPlayPluginStatusStore,
    private val sessionCacheRegistry: com.raulshma.jellyplay.core.data.session.SessionCacheRegistry,
    private val adapters: List<ProfileSyncAdapter>,
    private val deviceProfile: String,
    private val deviceIdProvider: suspend () -> String,
    private val nowMillis: () -> Long,
    /**
     * Opt-in persistence (device-scoped — survives identity changes; the
     * engine still hard-resets to the persisted value on sign-out/user switch
     * so a stale toggle never survives INTO another identity's session
     * silently). Null on both = in-memory only (tests).
     */
    private val persistenceScope: kotlinx.coroutines.CoroutineScope? = null,
    private val loadEnabled: (suspend () -> Boolean)? = null,
    private val saveEnabled: (suspend (Boolean) -> Unit)? = null,
) {

    /** Cumulative sync outcome for the settings screen. */
    data class SyncState(
        val enabled: Boolean = false,
        val lastSyncAt: Long? = null,
        val lastError: String? = null,
        val syncedKeys: Int = 0,
        val inFlight: Boolean = false,
    )

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var enabled = false

    init {
        // Identity change resets the engine — outcome and toggle re-arm from
        // the persisted device value (never silently carry `on` into another
        // identity's session; never lose the user's choice either).
        sessionCacheRegistry.registerAction(OWNER) {
            _state.value = _state.value.copy(enabled = false, inFlight = false)
            enabled = false
            reloadPersistedEnabled()
        }
        reloadPersistedEnabled()
    }

    private fun reloadPersistedEnabled() {
        val load = loadEnabled ?: return
        persistenceScope?.launch {
            try {
                if (load()) {
                    enabled = true
                    _state.value = _state.value.copy(enabled = true)
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
            }
        }
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        _state.value = _state.value.copy(enabled = value, lastError = null)
        val save = saveEnabled ?: return
        persistenceScope?.launch {
            try {
                save(value)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
            }
        }
    }

    /** Explicit sync request; no-op when disabled. Serialized; safe to call often. */
    suspend fun requestSync() {
        if (!enabled) return
        mutex.withLock { runCycle() }
    }

    private suspend fun runCycle() {
        if (statusStore.status.value != JellyPlayPluginStatus.AVAILABLE) {
            statusStore.refresh()
            if (statusStore.status.value != JellyPlayPluginStatus.AVAILABLE) {
                _state.value = _state.value.copy(lastError = "Plugin unavailable", inFlight = false)
                return
            }
        }

        _state.value = _state.value.copy(inFlight = true)
        try {
            val resolved = apiClient.resolveProfile(deviceProfile).getOrThrow()
            var adopted = 0
            var pushed = 0

            for (adapter in adapters) {
                val local = adapter.snapshot()
                val dirty = adapter.dirtyValues(local)
                val remote = resolved.settings
                    .filter { it.ns == adapter.namespace }
                    .associate { it.key to it.value }

                // ADOPT: clean keys lost to another device.
                val adopt = remote.filter { (key, value) -> key !in dirty && local[key] != value }
                if (adopt.isNotEmpty()) {
                    adapter.applyRemote(adopt)
                    adapter.markSynced(adopt)
                    adopted += adopt.size
                }

                // PUSH: local changes the server hasn't seen yet.
                val toPush = dirty.filter { (key, value) -> remote[key] != value }
                if (toPush.isEmpty()) continue

                val writes = toPush.map { (key, value) ->
                    JellyPlaySettingWrite(
                        ns = adapter.namespace,
                        key = key,
                        schemaVersion = 1,
                        updatedAt = nowMillis(),
                        value = value,
                    )
                }
                val result = apiClient.applySettings(deviceProfile, deviceIdProvider(), writes).getOrThrow()
                pushed += result.applied.size
                // Only applied writes are marked synced: a `stale-write` reject
                // keeps its key dirty so the next cycle retries and then adopts
                // the winner through the ADOPT phase.
                adapter.markSynced(toPush.filterKeys { key -> result.applied.any { it.key == key } })
            }

            _state.value = _state.value.copy(
                lastSyncAt = nowMillis(),
                syncedKeys = pushed + adopted,
                lastError = null,
                inFlight = false,
            )
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            _state.value = _state.value.copy(lastError = t.message ?: t.javaClass.simpleName, inFlight = false)
        }
    }

    private companion object {
        const val OWNER = "profile-sync"
    }
}
