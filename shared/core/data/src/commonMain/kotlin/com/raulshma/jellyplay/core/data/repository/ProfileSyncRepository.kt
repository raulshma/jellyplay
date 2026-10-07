package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingWrite
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsBatchResult
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsEntry
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * One syncable namespace contributed by a local store. The engine is
 * adapter-agnostic: each adapter owns ONE plugin namespace (e.g. "prefs"),
 * knows how to read its local state, detect its own local changes, and apply
 * remote values.
 *
 * Change detection is mirror-based, never wall-clock-based: the adapter keeps
 * a last-synced snapshot; current != mirror == dirty. LWW arbitration happens
 * server-side across devices; the client stays clock-skew-immune.
 *
 * TOMBSTONES (both directions): a server-side delete reaches the adapter
 * through [deleteRemote] (the delta pull's `deleted[]` rows) — the local value
 * AND its mirror entry must go, so the adopted delete never re-reads as dirty.
 * A LOCAL delete roams out through [deletedKeys] — keys the mirror still holds
 * but the store no longer does — which the engine pushes as null-value writes
 * (the wire's delete op) and confirms with another [deleteRemote] once the
 * server applies them.
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

    /**
     * The keys deleted locally since the last [markSynced]/[deleteRemote] —
     * the adapter's outbound tombstones (mirror holds them, [snapshot] no
     * longer does). The engine pushes each as a null-value write and confirms
     * the applied ones with [deleteRemote]; a rejected delete keeps its mirror
     * entry and retries next cycle. Default: none — value-only namespaces
     * (e.g. prefs, where a removed key means "reset to default", never
     * "deleted everywhere") keep the empty set rather than turning every
     * local key removal into a cross-device delete.
     */
    suspend fun deletedKeys(): Set<String> = emptySet()

    /** Applies remote values locally (the engine passes only clean keys this device lost). */
    suspend fun applyRemote(entries: Map<String, JsonElement>)

    /**
     * Applies remote tombstones: removes the local values for [keys] AND
     * their mirror entries, so an adopted delete neither resurrects the value
     * nor re-reads as a local edit. Idempotent (already-absent keys fine).
     * Default no-op — an adapter that never overrides it simply ignores
     * tombstones for its namespace.
     */
    suspend fun deleteRemote(keys: Set<String>) {}

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
 *  3. PUSH: dirty keys that differ from remote are sent, beside the adapter's
 *     [ProfileSyncAdapter.deletedKeys] as null-value (tombstone) writes; the
 *     server applies per-key LWW and reports applied/rejected; only APPLIED
 *     keys are marked synced, so `stale-write` rejects retry next cycle and
 *     then converge;
 *  4. DELTA: the paged `settings/changed?since=<cursor>` sweep — applies the
 *     change-log's tombstones through [ProfileSyncAdapter.deleteRemote] and
 *     adopts anything the resolved pull did not arbitrate, advancing the
 *     persisted cursor only after a fully drained sweep.
 *
 * Rejects that must NOT retry — `clock-skew` (device clock ahead of the
 * server's) and `device-revoked` (this device was revoked) — surface in
 * [state]'s `rejectedKeys`/`lastError` instead of silently retrying forever.
 *
 * Local change watching stays with the store owners: they call [requestSync]
 * on change (the engine serializes + gates internally). SSE live re-sync rides
 * [JellyPlayLiveResyncConnector] — the settings stream's
 * `settings.changed`/`settings.reset` frames fold into [requestSync].
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
    /**
     * The delta pull's resume cursor (the change-log seq the last fully
     * drained `settings/changed` sweep ended on), persisted PER USER by the
     * wiring — the seam closures key on the session identity, the engine stays
     * identity-agnostic. Null on both = an in-memory cursor (one full delta
     * sweep per process, then incremental). A load of `null` (no persisted
     * value — fresh device or identity) reads as 0.
     */
    private val loadDeltaCursor: (suspend () -> Long?)? = null,
    private val saveDeltaCursor: (suspend (Long) -> Unit)? = null,
    /**
     * SELECTIVE SYNC (per-namespace, device-local): the persisted
     * `jpsync.ns.enabled.<ns>` toggle's read/write seams. The loader answers
     * "is [ns] synced on THIS device" (a missing key = enabled — the default
     * is on), the saver persists the user's flip; both null = every namespace
     * enabled (tests, legacy constructions). The toggle is honored at BOTH
     * faces of a cycle — disabled namespaces are skipped whole: no dirty
     * collection, no push (values or tombstones), no adopt, no delta
     * application — so their pending local state simply parks until
     * re-enabled. Compile-time exclusions (the adapters' `jpsync.` reservation
     * and exclusion sets) are unchanged and still win.
     */
    private val loadNamespaceEnabled: (suspend (String) -> Boolean)? = null,
    private val saveNamespaceEnabled: (suspend (String, Boolean) -> Unit)? = null,
) {

    /** The latest cycle's sync outcome for the settings screen. */
    data class SyncState(
        val enabled: Boolean = false,
        val lastSyncAt: Long? = null,
        val lastError: String? = null,
        /** Keys pushed + adopted by the LAST cycle (not cumulative across cycles). */
        val syncedKeys: Int = 0,
        val inFlight: Boolean = false,
        /**
         * The LAST cycle's server rejects that must NOT silently retry —
         * `clock-skew` (this device's clock runs ahead of the server's) and
         * `device-revoked` (this device's registration was revoked
         * server-side): composite `"ns/key"` → reason. `stale-write` rejects
         * stay absent — they live in [conflicts] instead. Empty after any
         * cycle that drew no fatal rejects.
         */
        val rejectedKeys: Map<String, String> = emptyMap(),
        /**
         * Locally-pending (dirty or deleted-but-unpushed) key count per
         * namespace after the LAST cycle — the sync screen's "pending" face,
         * per namespace row and in total. Disabled (selectively-synced-off)
         * namespaces never contribute: they are opted out, not pending.
         * Empty before the first cycle.
         */
        val pendingByNamespace: Map<String, Int> = emptyMap(),
        /**
         * The LAST cycle's `stale-write` rejects with BOTH sides snapshotted
         * at reject time — [SyncConflict.ours] is the value (or pending
         * delete) this device tried to push, [SyncConflict.theirs] the server
         * row that beat it — so the conflict UI can preview both without a
         * re-read. The engine's ordinary retry loop keeps re-pushing these
         * with fresh stamps (they converge), so the list is a visibility
         * surface, not a stuck state; empty after any cycle without
         * stale-writes.
         */
        val conflicts: List<SyncConflict> = emptyList(),
    )

    /**
     * One LWW-lost write (a `stale-write` reject) with both sides captured at
     * reject time: [ours] is what this device tried to push (null = a pending
     * local delete), [theirs] the server's current row (null = the key is
     * tombstoned server-side), [theirsUpdatedAt] its LWW stamp (0 = unknown /
     * absent row). Addressed by the composite `"ns/key"` form.
     */
    data class SyncConflict(
        val ns: String,
        val key: String,
        val reason: String,
        val ours: JsonElement?,
        val theirs: JsonElement?,
        val theirsUpdatedAt: Long = 0,
    )

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * The keys (composite `"ns/key"` form, e.g. `prefs/pluginFeature.events.enabled`)
     * whose admin default is `forced` in the LATEST resolved pull — derived
     * from each cycle's `resolved/{profile}` payload's additive `modes` map
     * (absent = an older plugin: no mode in force anywhere, the flow reads
     * empty). Written only inside [runCycle] (the one place the resolved
     * payload is held, under the engine mutex) and cleared on the identity
     * reset; consumers (the feature-toggles section's forced locks) read it
     * reactively.
     */
    private val _forcedKeys = MutableStateFlow<Set<String>>(emptySet())
    val forcedKeys: StateFlow<Set<String>> = _forcedKeys.asStateFlow()

    /** The resolved device profile this engine syncs under (base/desktop/phone/tv). */
    val resolvedProfile: String get() = deviceProfile

    /** This device's persisted sync identity (the `jpsync.device.id` seam). */
    suspend fun currentDeviceId(): String = deviceIdProvider()

    /** The namespaces the registered adapters sync (the selective-sync toggle rows). */
    val activeNamespaces: List<String> get() = adapters.map { it.namespace }

    /**
     * The per-namespace selective-sync toggle's current state (missing
     * persisted key = enabled — the default is on). Read straight from the
     * seam, so a flip is visible immediately, cycle or not.
     */
    suspend fun namespaceEnabled(ns: String): Boolean = loadNamespaceEnabled?.invoke(ns) ?: true

    /**
     * Flips the per-namespace selective-sync toggle. Re-enabling runs one sync
     * cycle right away so the namespace converges immediately; disabling
     * parks its pending local state untouched (nothing is pushed, nothing is
     * adopted, nothing is lost). No-op when the engine is disabled — the
     * toggle only matters while sync runs.
     */
    suspend fun setNamespaceEnabled(ns: String, enabled: Boolean) {
        saveNamespaceEnabled?.invoke(ns, enabled) ?: return
        if (enabled) requestSync()
    }

    private val mutex = Mutex()
    private var enabled = false

    /**
     * The delta cursor when no [loadDeltaCursor]/[saveDeltaCursor] seam is
     * wired (tests, legacy constructions): one full sweep per process, then
     * incremental — the same graceful degrade the persisted seam gives, minus
     * the restart survival.
     */
    private var inMemoryDeltaCursor = 0L

    init {
        // Identity change resets the engine — outcome and toggle re-arm from
        // the persisted device value (never silently carry `on` into another
        // identity's session; never lose the user's choice either).
        sessionCacheRegistry.registerAction(OWNER) {
            _state.value = _state.value.copy(
                enabled = false,
                inFlight = false,
                pendingByNamespace = emptyMap(),
                conflicts = emptyList(),
            )
            enabled = false
            // The previous identity's admin defaults never leak into the next
            // session's toggle locks.
            _forcedKeys.value = emptySet()
            reloadPersistedEnabled()
        }
        reloadPersistedEnabled()
    }

    private fun reloadPersistedEnabled() {
        val load = loadEnabled ?: return
        persistenceScope?.launch {
            // The read is async; a setEnabled landing while it is in flight is
            // NEWER knowledge than the snapshot about to come back — a stale
            // `true` must never re-arm the engine over it.
            val token = ++loadGeneration
            try {
                val persisted = load()
                if (token == loadGeneration && persisted) {
                    enabled = true
                    _state.value = _state.value.copy(enabled = true)
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
            }
        }
    }

    /** Bumped by every [setEnabled]; stale persisted reloads discard themselves. */
    private var loadGeneration = 0

    fun setEnabled(value: Boolean) {
        loadGeneration++
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

    /**
     * The pull-dominant recovery path ("force re-pull"): one cycle where the
     * server's view wins for every key it holds — remote values are adopted
     * over locally-dirty state and nothing is pushed. Same mutex, same gates,
     * no-op when disabled; use when a device wants to re-converge onto the
     * server instead of fighting it with its own pending changes.
     */
    suspend fun forceRepull() {
        if (!enabled) return
        mutex.withLock { runCycle(forceRemoteWins = true) }
    }

    private suspend fun runCycle(forceRemoteWins: Boolean = false) {
        // The gate is the probe ladder AND the registry's `settings-sync` key —
        // the ONE gating mechanism (ADR 0010 §1/§6): a server whose handshake
        // omits the key (admin-disabled, or the plugin predates the sync wave)
        // ends the cycle the same quiet way a stock server does.
        if (!statusStore.ensureAvailable() || !statusStore.hasFeature(FEATURE_SETTINGS_SYNC)) {
            // ADR 0010 §1: probe failure = UNAVAILABLE, never an error
            // surface. lastError is the sync screen's failure row; a
            // pre-wave (or merely down) plugin ends the cycle quietly —
            // nothing synced, nothing claimed, lastSyncAt keeps its
            // previous value.
            _state.value = _state.value.copy(inFlight = false)
            return
        }

        _state.value = _state.value.copy(inFlight = true)
        try {
            val resolved = apiClient.resolveProfile(deviceProfile).getOrThrow()
            // The admin-defaults modes ride the same payload: derive the forced
            // set here (the payload's only holder) so the UI's locks track the
            // server's view cycle-by-cycle.
            _forcedKeys.value = resolved.modes
                ?.filterValues { mode -> mode == MODE_FORCED }
                ?.keys
                ?: emptySet()
            // SELECTIVE SYNC: the per-namespace toggles read once per cycle —
            // a mid-cycle flip lands next cycle (the mutex serializes cycles,
            // but the toggle's saver is not the engine's to serialize).
            val enabledNamespaces = mutableSetOf<String>()
            for (adapter in adapters) {
                val enabled = try {
                    loadNamespaceEnabled?.invoke(adapter.namespace) ?: true
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    true // a failed toggle read never silently unsyncs a namespace
                }
                if (enabled) enabledNamespaces += adapter.namespace
            }
            val cycleAdapters = adapters.filter { it.namespace in enabledNamespaces }
            // The cycle-start view, read ONCE per adapter and shared by the
            // push face and the delta sweep below: a snapshot can be the
            // store's whole content (the reader namespace's annotation
            // arrays), and re-reading it per sweep page multiplied that cost
            // by the page count for no semantic gain — the dirty set is this
            // cycle's own decision, and mid-cycle adoptions mark themselves
            // synced as they land.
            val cycleState = cycleAdapters.associate { adapter ->
                val local = adapter.snapshot()
                val dirty = adapter.dirtyValues(local)
                adapter.namespace to (local to dirty)
            }
            var adopted = 0
            var pushed = 0
            val fatalRejects = mutableMapOf<String, String>()
            val conflicts = mutableListOf<SyncConflict>()

            for (adapter in cycleAdapters) {
                val (local, dirty) = cycleState.getValue(adapter.namespace)
                // The locally-deleted keys this cycle should push as
                // tombstones. The recovery path pushes nothing — local deletes
                // included (they are abandoned; the server's view wins).
                val deletes = if (forceRemoteWins) emptySet() else adapter.deletedKeys()
                val remote = resolved.settings
                    .filter { it.ns == adapter.namespace }
                    .associateBy { it.key }

                // ADOPT: clean keys lost to another device — or, in the
                // force-remote-wins recovery path, EVERY differing key
                // (locally-dirty state included; see [forceRepull]). Keys this
                // cycle deletes locally are excluded: adopting the server's
                // still-standing row over a pending local delete would
                // resurrect the bookmark/pref the user just removed — the
                // tombstone push below has to land first.
                val adopt = if (forceRemoteWins) {
                    remote.filter { (key, entry) -> key !in deletes && local[key] != entry.value }
                } else {
                    remote.filter { (key, entry) -> key !in deletes && key !in dirty && local[key] != entry.value }
                }
                if (adopt.isNotEmpty()) {
                    adapter.applyRemote(adopt.mapValues { it.value.value })
                    adapter.markSynced(adopt.mapValues { it.value.value })
                    adopted += adopt.size
                }

                // PUSH: local changes the server hasn't seen yet — value edits
                // plus [deletes] as null-value writes (the wire's delete op).
                // The recovery path pushes nothing — it exists to take the
                // server's view.
                if (forceRemoteWins) continue
                val toPush = dirty.filter { (key, value) -> remote[key]?.value != value }
                if (toPush.isEmpty() && deletes.isEmpty()) continue

                val writes = toPush.map { (key, value) ->
                    JellyPlaySettingWrite(
                        ns = adapter.namespace,
                        key = key,
                        schemaVersion = 1,
                        updatedAt = nowMillis(),
                        value = value,
                    )
                } + deletes.map { key ->
                    JellyPlaySettingWrite(
                        ns = adapter.namespace,
                        key = key,
                        schemaVersion = 1,
                        updatedAt = nowMillis(),
                        value = JsonNull,
                        deleted = true, // the delete op — the plugin's tombstone write
                    )
                }
                val result = apiClient.applySettings(deviceProfile, deviceIdProvider(), writes).getOrThrow()
                val appliedKeys = result.applied.map { it.key }.toSet()
                pushed += result.applied.size
                // Only applied writes are marked synced: a `stale-write` reject
                // keeps its key dirty so the next cycle retries and then adopts
                // the winner through the ADOPT phase.
                adapter.markSynced(toPush.filterKeys { key -> key in appliedKeys })
                // Applied tombstones confirm through the same SPI the inbound
                // deletes use — clearing the mirror entries so the delete stops
                // reading as pending; a rejected delete keeps its entry and
                // retries next cycle, exactly like a stale-write value.
                val appliedDeletes = deletes.filter { it in appliedKeys }.toSet()
                if (appliedDeletes.isNotEmpty()) adapter.deleteRemote(appliedDeletes)
                collectFatalRejects(adapter.namespace, result, fatalRejects)
                collectConflicts(adapter.namespace, result, remote, toPush, deletes, conflicts)
            }

            // DELTA CATCH-UP: the change-log sweep the resolved view cannot
            // express — tombstones (`deleted[]`) and other devices' writes
            // since this device's cursor, paged through to the end. Advances
            // the cursor only on a fully drained sweep; a failed page leaves
            // the cursor (and the state's error face) to the catch below.
            adopted += pullDelta(resolved, forceRemoteWins, enabledNamespaces, cycleState)

            // The pending face, measured AFTER the cycle: what each synced
            // namespace still holds un-pushed (dirty or deleted-but-unconfirmed).
            val pendingByNamespace = cycleAdapters.associate { adapter ->
                val pending = adapter.dirtyValues(adapter.snapshot()).size + adapter.deletedKeys().size
                adapter.namespace to pending
            }.filterValues { it > 0 }

            _state.value = _state.value.copy(
                lastSyncAt = nowMillis(),
                syncedKeys = pushed + adopted,
                lastError = fatalRejects.errorSummary(),
                rejectedKeys = fatalRejects,
                pendingByNamespace = pendingByNamespace,
                conflicts = conflicts,
                inFlight = false,
            )
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            _state.value = _state.value.copy(lastError = t.message ?: t.javaClass.simpleName, inFlight = false)
        } finally {
            // A cancelled cycle rethrows from the catch above and skips both
            // setters — the screen must never park on "Syncing…".
            if (_state.value.inFlight) _state.value = _state.value.copy(inFlight = false)
        }
    }

    /**
     * The paged delta sweep (the tombstone face of the pull): walks
     * `settings/changed?since=<cursor>` through its `nextCursor` chain until a
     * page ends the sweep (null), applying per adapter — clean changed keys
     * adopt (the same clean-keys rule as the resolved ADOPT: locally-dirty
     * keys keep waiting for their own push to resolve), `deleted[]` keys go
     * through [ProfileSyncAdapter.deleteRemote] so the delete removes the
     * local value AND its mirror entry. Absent `deleted[]`/`nextCursor` (an
     * older plugin) decodes to the defaults and the sweep reads exactly like
     * the pre-tombstone delta: values only, single page. [enabledNamespaces]
     * gates the sweep per adapter (selective sync: a disabled namespace's
     * pending pages still advance the cursor — the rows are skipped, not
     * replayed forever). [cycleState] is the cycle-start per-adapter
     * (snapshot, dirty) pair the push face already read — reused here so the
     * sweep never re-reads a store per page.
     *
     * Returns the number of adopted keys. On success the cursor advances to
     * the last page's `head` (persisted through the seam when wired);
     * exceptions propagate — the cycle's catch surfaces them and the cursor
     * stays where it was, so the next cycle re-reads the un-drained range.
     */
    private suspend fun pullDelta(
        resolved: JellyPlaySettingsSnapshot,
        forceRemoteWins: Boolean,
        enabledNamespaces: Set<String>,
        cycleState: Map<String, Pair<Map<String, JsonElement>, Map<String, JsonElement>>>,
    ): Int {
        // The keys the resolved pull already arbitrated, grouped per namespace
        // once per sweep — the adopt filter below consults these sets instead
        // of re-scanning the resolved payload linearly per changed key.
        val arbitratedKeys = resolved.settings
            .groupBy({ it.ns }, { it.key })
            .mapValues { (_, keys) -> keys.toSet() }
        var since = loadDeltaCursor?.invoke() ?: inMemoryDeltaCursor
        var adopted = 0
        var cursor: Long? = null
        var head = since
        do {
            val page = apiClient.getChangedSettings(
                since = since,
                profile = deviceProfile,
                limit = DELTA_PAGE_LIMIT,
                cursor = cursor,
            ).getOrThrow()
            for (adapter in adapters) {
                if (adapter.namespace !in enabledNamespaces) continue
                val (local, dirty) = cycleState.getValue(adapter.namespace)
                val arbitrated = arbitratedKeys[adapter.namespace] ?: emptySet()

                // Changed keys: the resolved view this cycle pulled already
                // arbitrated the keys IT holds — re-adopting a differing delta
                // value for one of them would fight the profile merge, so only
                // keys the resolved payload did NOT arbitrate adopt here (the
                // freshness tail: writes that landed between the two reads ride
                // the next cycle's resolved pull).
                val adopt = page.settings
                    .filter { it.ns == adapter.namespace }
                    .associate { it.key to it.value }
                    .filter { (key, value) ->
                        key !in dirty &&
                            local[key] != value &&
                            (forceRemoteWins || key !in arbitrated)
                    }
                if (adopt.isNotEmpty()) {
                    adapter.applyRemote(adopt)
                    adapter.markSynced(adopt)
                    adopted += adopt.size
                }

                // Deleted keys: the server tombstoned them — the delete wins
                // over a local un-pushed edit (roaming deletes must not
                // resurrect), and over a clean synced value (the delete IS the
                // news).
                val deletes = page.deleted
                    .filter { it.ns == adapter.namespace }
                    .map { it.key }
                    .toSet()
                if (deletes.isNotEmpty()) adapter.deleteRemote(deletes)
            }
            cursor = page.nextCursor
            if (page.head > head) head = page.head
        } while (cursor != null)

        // Fully drained: the sweep's end is the last page's head. A head of 0
        // (an older plugin without the field) keeps the cursor at 0 — every
        // sweep re-reads the whole log, harmless (adopts are value-equal
        // no-ops) and self-correcting once the plugin upgrades. The in-memory
        // value advances even when a seam is wired: a `null` load (fresh
        // identity, failed read) falls back to it instead of re-sweeping
        // from zero.
        if (head > 0) {
            inMemoryDeltaCursor = head
            saveDeltaCursor?.invoke(head)
        }
        return adopted
    }

    /**
     * Folds a batch result's NON-retryable rejects into [fatalRejects]
     * (composite `"ns/key"` → reason): `clock-skew` — the device clock runs
     * ahead of the server's, so retrying with a fresh stamp fails identically
     * until the clock is fixed — and `device-revoked` — this device's
     * registration was revoked, so EVERY write fails until re-registered.
     * Both are failure states the sync screen must show, not traffic the
     * retry loop silently repeats; `stale-write` and the quota reasons keep
     * their key queued like any retryable reject and stay off the map.
     */
    private fun collectFatalRejects(
        namespace: String,
        result: JellyPlaySettingsBatchResult,
        fatalRejects: MutableMap<String, String>,
    ) {
        for (reject in result.rejected) {
            if (reject.reason == REJECT_CLOCK_SKEW || reject.reason == REJECT_DEVICE_REVOKED) {
                fatalRejects["$namespace/${reject.key}"] = reject.reason
            }
        }
    }

    /**
     * Snapshots the cycle's `stale-write` rejects into [conflicts] with BOTH
     * sides captured at reject time — [SyncConflict.ours] is the pushed value
     * (null for a pending tombstone write), [SyncConflict.theirs] the server
     * row that beat it straight out of the resolved pull this cycle already
     * holds ([theirRemote] — no second read), so the conflict UI can preview
     * ours/theirs without re-fetching. The retry loop keeps these keys dirty
     * and re-pushes with fresh stamps regardless; this is the visibility
     * face, not a gate.
     */
    private fun collectConflicts(
        namespace: String,
        result: JellyPlaySettingsBatchResult,
        theirRemote: Map<String, JellyPlaySettingsEntry>,
        pushedValues: Map<String, JsonElement>,
        pushedDeletes: Set<String>,
        conflicts: MutableList<SyncConflict>,
    ) {
        for (reject in result.rejected) {
            if (reject.reason != REJECT_STALE_WRITE) continue
            val theirs = theirRemote[reject.key]
            conflicts += SyncConflict(
                ns = namespace,
                key = reject.key,
                reason = reject.reason,
                ours = if (reject.key in pushedDeletes) null else pushedValues[reject.key],
                theirs = theirs?.value,
                theirsUpdatedAt = theirs?.updatedAt ?: 0,
            )
        }
    }

    /**
     * Conflict resolution, "keep mine": re-pushes ONE conflicted key with a
     * fresh timestamp so it wins LWW outright (a pending tombstone re-pushes
     * as the delete op), marks it synced on success and drops the conflict
     * from the state. Serialized under the cycle mutex; a failed push leaves
     * everything (dirty key, conflict) exactly as it was.
     */
    suspend fun resolveConflictKeepMine(ns: String, key: String) {
        if (!enabled) return
        mutex.withLock {
            val adapter = adapters.firstOrNull { it.namespace == ns } ?: return
            val value = adapter.snapshot()[key]
            val write = JellyPlaySettingWrite(
                ns = ns,
                key = key,
                schemaVersion = 1,
                updatedAt = nowMillis(),
                value = value ?: JsonNull,
                deleted = value == null, // no local value = the pending delete re-pushes as a tombstone
            )
            val result = apiClient.applySettings(deviceProfile, deviceIdProvider(), listOf(write)).getOrThrow()
            if (result.applied.isEmpty()) return // still losing LWW — the conflict stands
            if (value == null) adapter.deleteRemote(setOf(key)) else adapter.markSynced(mapOf(key to value))
            dropConflict(ns, key)
        }
    }

    /**
     * Conflict resolution, "take theirs": adopts the key's CURRENT server row
     * (a fresh read, not the reject-time snapshot — theirs may have moved),
     * marks it synced so the local edit stops reading dirty, and drops the
     * conflict. A key the server no longer holds (tombstoned in between)
     * resolves through [ProfileSyncAdapter.deleteRemote] — the delete wins.
     * The rejected local edit is abandoned, exactly like [forceRepull]'s
     * recovery semantics, just one key wide.
     */
    suspend fun resolveConflictTakeTheirs(ns: String, key: String) {
        if (!enabled) return
        mutex.withLock {
            val adapter = adapters.firstOrNull { it.namespace == ns } ?: return
            val theirs = apiClient.resolveProfile(deviceProfile).getOrThrow().settings
                .firstOrNull { it.ns == ns && it.key == key }
            if (theirs == null) {
                adapter.deleteRemote(setOf(key))
            } else {
                adapter.applyRemote(mapOf(key to theirs.value))
                adapter.markSynced(mapOf(key to theirs.value))
            }
            dropConflict(ns, key)
        }
    }

    /** Removes one resolved conflict from the live state (the UI's list shrinks in place). */
    private fun dropConflict(ns: String, key: String) {
        _state.value = _state.value.copy(
            conflicts = _state.value.conflicts.filter { it.ns != ns || it.key != key },
        )
    }

    /** The sync screen's error row for a cycle with fatal rejects; null when none. */
    private fun Map<String, String>.errorSummary(): String? {
        if (isEmpty()) return null
        val reasons = values.distinct().sorted().joinToString(", ")
        return "$size key(s) rejected by server: $reasons"
    }

    private companion object {
        const val OWNER = "profile-sync"

        /** The registry key this engine gates on (ADR 0010 §6). */
        const val FEATURE_SETTINGS_SYNC = com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures.SettingsSync

        /** The admin-defaults mode that locks a key's UI (`unset`/`suggested` stay writable). */
        const val MODE_FORCED = "forced"

        /** Delta-sweep page size — generous; the loop follows `nextCursor` regardless. */
        const val DELTA_PAGE_LIMIT = 200

        /** Non-retryable reject reasons — surfaced in sync state, never silently retried. */
        const val REJECT_CLOCK_SKEW = "clock-skew"
        const val REJECT_DEVICE_REVOKED = "device-revoked"

        /** The LWW-loss reject — retryable (and the conflict UI's subject). */
        const val REJECT_STALE_WRITE = "stale-write"
    }
}
