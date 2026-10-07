package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.worker.SettingsSyncScheduler
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlaySnapshot
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncAdminUser
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncDeviceStat
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryEntry
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKey
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKeys
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncNamespaceUsage
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncStatus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One merged "Devices" row: the plugin's device registry ([JellyPlayDevice])
 * enriched with the server-recorded last sync ([JellyPlaySyncDeviceStat]) by
 * [mergeDevices]. Registry-v7 fields ([model], [revoked]) are additive — an
 * empty model and a false revoked read exactly like the pre-registry rows.
 */
@Immutable
data class JellyPlaySyncDeviceRow(
    val deviceId: String,
    val name: String,
    val platform: String,
    val model: String?,
    val lastSyncAt: Long?,
    val lastOp: String,
    val revoked: Boolean,
    val isThisDevice: Boolean,
)

/**
 * One namespace row of the selective-sync + usage face: the server-recorded
 * usage ([keys]/[bytes], 0 when the server's status payload is unavailable)
 beside the device-local state — the pending push count and the per-namespace
 * selective-sync toggle. [toggleable] marks namespaces this engine actually
 * syncs through a registered adapter; server-only namespaces render usage
 * without a toggle.
 */
@Immutable
data class JellyPlaySyncNamespaceRow(
    val ns: String,
    val keys: Int,
    val bytes: Long,
    val pending: Int,
    val toggleable: Boolean,
    val enabled: Boolean,
)

/**
 * One history row's per-key diff detail ([JellyPlaySyncViewModel] fetches it
 * lazily on first expand, cached per seq).
 */
@Immutable
data class JellyPlaySyncHistoryKeyDetail(
    val isLoading: Boolean = false,
    /**
     * The changed keys, `ns`/`key` pairs. Empty = the server holds no diff
     * for the entry (reset rows). null = the server's plugin predates the
     * per-key history wave (404) — the quiet "—" degrade, never an error.
     */
    val keys: List<JellyPlaySyncHistoryKey>? = null,
)

/** The sync screen's loaded state. */
@Immutable
data class JellyPlaySyncUiState(
    val isLoading: Boolean = true,
    /**
     * The server's sync usage payload. null = the server's plugin predates the
     * sync-status wave (404) — the usage/history sections degrade quietly.
     */
    val status: JellyPlaySyncStatus? = null,
    val history: List<JellyPlaySyncHistoryEntry> = emptyList(),
    val devices: List<JellyPlaySyncDeviceRow> = emptyList(),
    /** The admin overview's per-user rows; null = not an admin (or old plugin) — the card hides. */
    val adminUsers: List<JellyPlaySyncAdminUser>? = null,
    /** This device's persisted sync identity — the "This device" badge's match key. */
    val thisDeviceId: String? = null,
    /** The expanded history row (its per-key diff below the row); null = none. */
    val expandedHistorySeq: Long? = null,
    /** Per-seq diff details, fetched lazily on first expand and cached. */
    val historyKeyDetails: Map<Long, JellyPlaySyncHistoryKeyDetail> = emptyMap(),
    /**
     * The server's rolling restore points, newest first. null = the server's
     * plugin predates the restore-points wave (or the read failed / the gate
     * closed) — the section hides quietly.
     */
    val snapshots: List<JellyPlaySnapshot>? = null,
    /** The selective-sync + usage rows (server usage ∪ engine namespaces); built by [refresh]. */
    val namespaceRows: List<JellyPlaySyncNamespaceRow> = emptyList(),
    /** One snapshot create/restore/export/import action in flight — the rows' re-entry guard. */
    val actionInFlight: Boolean = false,
    /** The last snapshot/export/import action failed — the banner's second face; cleared by the next action. */
    val actionError: Boolean = false,
)

/**
 * The companion-plugin settings-sync screen's model (ADR 0010): the sync
 * engine's toggle + manual cycles ([ProfileSyncRepository]) beside the
 * server-side usage / history / admin-overview reads, the conflict-resolution
 * intents, the selective-sync toggles, the device registry actions
 * (rename/revoke), the restore points, and the JSON export/import.
 *
 * The screen is reachable ONLY through the settings root's capability-gated
 * "Sync" entry (plugin probe AVAILABLE + the `settings-sync` meta feature key
 * — meta keys carry no user toggle, so the gate is probe-only), but the api
 * calls are STILL gate-checked here — the gate is re-checked at every call,
 * so the api client is never touched without it (ADR 0010's gating rule; the
 * capabilities contract can degrade mid-session). A degraded gate collapses
 * the loaded state to the quiet "unavailable" shape instead of erroring.
 */
class JellyPlaySyncViewModel(
    private val syncRepository: ProfileSyncRepository,
    private val pluginApiClient: JellyPlayPluginApiClient,
    private val statusStore: JellyPlayPluginStatusStore,
    /** The export/import file IO seam (Android SAF / desktop files) — the backup screen's seam, reused. */
    private val backupIo: SettingsBackupIo,
    /**
     * The background flush scheduler (ADR 0011) — the disable edge of the
     * sync toggle de-arms its 12h catch-up periodic here. Null in
     * direct-construction tests.
     */
    private val syncScheduler: SettingsSyncScheduler? = null,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow(JellyPlaySyncUiState())
    val uiState: StateFlow<JellyPlaySyncUiState> = _uiState.asStateFlow()

    /** The sync engine's outcome (opt-in toggle, last sync, errors, conflicts, pending). */
    val syncState: StateFlow<ProfileSyncRepository.SyncState> = syncRepository.state

    /** The resolved profile this device syncs under (the informational row). */
    val deviceProfile: String = syncRepository.resolvedProfile

    /** The namespaces this engine's adapters sync — the selective-sync toggle rows. */
    val activeNamespaces: List<String> = syncRepository.activeNamespaces

    /**
     * One pull of every server-side face (usage, history, devices, admin
     * overview, snapshots) plus the device-local selective-sync flags. Fired
     * by the screen on open and after each manual action. The usage/history
     * reads degrade to null/empty on the pre-wave 404; the admin read
     * degrades on 403 (non-admin) the same way.
     */
    fun refresh() {
        scope.launch {
            if (!syncGateOpen()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        status = null,
                        history = emptyList(),
                        adminUsers = null,
                        snapshots = null,
                        namespaceRows = emptyList(),
                    )
                }
                return@launch
            }
            val thisDeviceId = runCatchingRethrowingCancellation { syncRepository.currentDeviceId() }.getOrNull()
                ?: _uiState.value.thisDeviceId
            val status = pluginApiClient.getSyncStatus().getOrNull()
            val history = pluginApiClient.getSyncHistory(limit = HISTORY_LIMIT).getOrNull()
            val registryDevices = pluginApiClient.getDevices().getOrNull().orEmpty()
            val adminUsers = pluginApiClient.adminSyncOverview().getOrNull()?.users
            val snapshots = pluginApiClient.getSnapshots().getOrNull()
            val namespaceEnabled = buildMap {
                for (ns in activeNamespaces) {
                    put(ns, runCatchingRethrowingCancellation { syncRepository.namespaceEnabled(ns) }.getOrDefault(true))
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    thisDeviceId = thisDeviceId,
                    status = status,
                    history = history?.entries.orEmpty(),
                    devices = mergeDevices(registryDevices, status?.perDevice.orEmpty(), thisDeviceId),
                    adminUsers = adminUsers,
                    snapshots = snapshots,
                    namespaceRows = mergeNamespaces(
                        status?.namespaces.orEmpty(),
                        activeNamespaces,
                        namespaceEnabled,
                        syncRepository.state.value.pendingByNamespace,
                    ),
                )
            }
        }
    }

    /** The opt-in toggle — the same mechanism the old settings-root rows drove. */
    fun setSyncEnabled(enabled: Boolean) {
        syncRepository.setEnabled(enabled)
        if (enabled) {
            // First enable pulls + pushes immediately so the toggle has an effect.
            scope.launch {
                syncRepository.requestSync()
                refresh()
            }
        } else {
            // The periodic catch-up is armed only while sync is enabled — the
            // disable edge de-arms it (a canceled periodic re-arms on the next
            // start or re-enable).
            syncScheduler?.cancelPeriodic()
        }
    }

    /** The manual sync-now cycle; the server faces re-read once it lands. */
    fun syncNow() {
        scope.launch {
            if (!syncGateOpen()) return@launch
            syncRepository.requestSync()
            refresh()
        }
    }

    /** The pull-dominant recovery path: the server's view wins, nothing pushes. */
    fun forceRepull() {
        scope.launch {
            if (!syncGateOpen()) return@launch
            syncRepository.forceRepull()
            refresh()
        }
    }

    /**
     * Wipes the synced `prefs` namespace on the server; every device adopts
     * the tombstone batch on its next cycle and resets the synced prefs to
     * their defaults (the prefs adapter's inbound-tombstone rule). Confirmed
     * by the screen's destructive dialog before reaching here.
     */
    fun resetNamespace() {
        scope.launch {
            if (!syncGateOpen()) return@launch
            pluginApiClient.resetNamespace(SYNCED_NAMESPACE)
                .onSuccess { refresh() }
        }
    }

    /**
     * Flips the selective-sync toggle for one namespace (the engine persists
     * it device-locally and re-syncs on enable). The local row flips
     * optimistically — the persisted flag re-reads on the next refresh.
     */
    fun setNamespaceEnabled(ns: String, enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                namespaceRows = state.namespaceRows.map { row ->
                    if (row.ns == ns && row.toggleable) row.copy(enabled = enabled) else row
                },
            )
        }
        scope.launch {
            if (!syncGateOpen()) return@launch
            runCatchingRethrowingCancellation { syncRepository.setNamespaceEnabled(ns, enabled) }
        }
    }

    /** Conflict resolution, "keep mine": re-pushes the key with a fresh timestamp. */
    fun resolveConflictKeepMine(ns: String, key: String) {
        scope.launch {
            if (!syncGateOpen()) return@launch
            syncRepository.resolveConflictKeepMine(ns, key)
            refresh()
        }
    }

    /** Conflict resolution, "take theirs": adopts the server's current value for the key. */
    fun resolveConflictTakeTheirs(ns: String, key: String) {
        scope.launch {
            if (!syncGateOpen()) return@launch
            syncRepository.resolveConflictTakeTheirs(ns, key)
            refresh()
        }
    }

    /** Renames THIS device in the registry (a no-op when the identity is unknown). */
    fun renameThisDevice(name: String) {
        val deviceId = _uiState.value.thisDeviceId ?: return
        scope.launch {
            if (!syncGateOpen()) return@launch
            pluginApiClient.renameDevice(deviceId, name = name)
                .onSuccess { refresh() }
        }
    }

    /**
     * Revokes [deviceId] — the registry-v7 DELETE: revoke + tombstone wipe of
     * every row the device wrote. Confirmed by the screen's destructive
     * dialog before reaching here.
     */
    fun revokeDevice(deviceId: String) {
        scope.launch {
            if (!syncGateOpen()) return@launch
            pluginApiClient.revokeDevice(deviceId)
                .onSuccess { refresh() }
        }
    }

    /** Captures a manual restore point; the list re-reads once it lands. */
    fun createSnapshot() {
        scope.launch { runSnapshotAction { pluginApiClient.createSnapshot() } }
    }

    /** Restores [snapshotId] (server-orchestrated tombstone batch + re-apply). */
    fun restoreSnapshot(snapshotId: String) {
        scope.launch { runSnapshotAction { pluginApiClient.restoreSnapshot(snapshotId) } }
    }

    /**
     * Fetches the settings export bundle and hands the raw JSON to [onReady]
     * (the screen's share seam). A failed read raises [JellyPlaySyncUiState.actionError].
     */
    fun exportSettings(onReady: (String) -> Unit) {
        scope.launch {
            if (!syncGateOpen()) return@launch
            _uiState.update { it.copy(actionInFlight = true, actionError = false) }
            val bundle = runCatchingRethrowingCancellation { pluginApiClient.exportSettings().getOrNull() }.getOrNull()
            if (bundle == null) {
                _uiState.update { it.copy(actionInFlight = false, actionError = true) }
            } else {
                _uiState.update { it.copy(actionInFlight = false) }
                onReady(bundle)
            }
        }
    }

    /**
     * Reads the picked import file ([uri] via the platform IO seam) and hands
     * the bundle to the server. A failed read or import raises
     * [JellyPlaySyncUiState.actionError]; a success re-pulls everything.
     */
    fun importFromUri(uri: String) {
        scope.launch {
            if (!syncGateOpen()) return@launch
            _uiState.update { it.copy(actionInFlight = true, actionError = false) }
            val payload = runCatchingRethrowingCancellation { backupIo.readImportPayload(uri) }
            val bundle = payload.getOrNull()
            val result = bundle?.let { text ->
                runCatchingRethrowingCancellation {
                    pluginApiClient.importSettings(text, deviceId = _uiState.value.thisDeviceId)
                }.getOrNull()
            }
            _uiState.update {
                if (result?.getOrNull() != null) it.copy(actionInFlight = false)
                else it.copy(actionInFlight = false, actionError = true)
            }
            if (result?.getOrNull() != null) refresh()
        }
    }

    /** The shared guard for the snapshot create/restore pair: gate + in-flight + error face + refresh. */
    private suspend fun runSnapshotAction(action: suspend () -> Result<*>) {
        if (!syncGateOpen()) return
        _uiState.update { it.copy(actionInFlight = true, actionError = false) }
        val result = runCatchingRethrowingCancellation { action().getOrNull() }
        val ok = result.isSuccess && result.getOrNull() != null
        _uiState.update { it.copy(actionInFlight = false, actionError = !ok) }
        if (ok) refresh()
    }

    /**
     * Expands (or collapses) one history row's per-key diff. First expansion
     * of a seq fetches its detail once — the loading placeholder lands
     * SYNCHRONOUSLY with the expand (the state write happens before the
     * launch, not inside it), so a rapid expand/collapse/expand never
     * double-fetches: the second expand already sees the seq in
     * [JellyPlaySyncUiState.historyKeyDetails]. The cache holds it across
     * collapses and refreshes (seqs are server-monotonic). A degraded gate
     * expands the row without a placeholder and without touching the api; a
     * 404 (old plugin) or a failed read caches the quiet "—" degrade, so a
     * broken seq never retries behind the user's back.
     */
    fun toggleHistoryEntryExpanded(seq: Long) {
        if (_uiState.value.expandedHistorySeq == seq) {
            _uiState.update { it.copy(expandedHistorySeq = null) }
            return
        }
        val cached = _uiState.value.historyKeyDetails[seq]
        if (cached != null || !syncGateOpen()) {
            _uiState.update { it.copy(expandedHistorySeq = seq) }
            return
        }
        _uiState.update {
            it.copy(
                expandedHistorySeq = seq,
                historyKeyDetails = it.historyKeyDetails + (seq to JellyPlaySyncHistoryKeyDetail(isLoading = true)),
            )
        }
        scope.launch {
            val detail = pluginApiClient.getSyncHistoryKeys(seq, HISTORY_KEYS_LIMIT)
                .getOrNull()
                ?.let { keys -> JellyPlaySyncHistoryKeyDetail(keys = keys.keys) }
                ?: JellyPlaySyncHistoryKeyDetail(keys = null)
            _uiState.update { it.copy(historyKeyDetails = it.historyKeyDetails + (seq to detail)) }
        }
    }

    /**
     * The ONE gate seam for the meta-keyed sync surface: probe AVAILABLE AND
     * the `settings-sync` key in the registry. No toggle half — meta keys are
     * deliberately absent from [com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate.TOGGLEABLE_FEATURES].
     */
    private fun syncGateOpen(): Boolean =
        statusStore.status.value == JellyPlayPluginStatus.AVAILABLE &&
            statusStore.hasFeature(JellyPlayPluginFeatures.SettingsSync)

    private companion object {
        /** The one namespace the shipped adapter syncs (the data layer's wiring). */
        const val SYNCED_NAMESPACE = "prefs"

        /** The history window the screen shows (the contract's default page). */
        const val HISTORY_LIMIT = 50

        /** The per-key diff page one expanded history row fetches (the contract's default). */
        const val HISTORY_KEYS_LIMIT = 200

        /**
         * The registry joined with the server's per-device last-sync stats on
         * deviceId — registry rows keep their name/platform/model; when the
         * registry read came back EMPTY (the events face never registered this
         * device, or the read failed) the server's own per-device ledger stands
         * in, its rows surfacing under their raw device id. Ledger entries the
         * registry has never heard of are NOT merged beside registry rows —
         * the registry is the authoritative device list.
         * Pure so the merge stays pin-able without a VM.
         */
        fun mergeDevices(
            registry: List<com.raulshma.jellyplay.core.network.api.JellyPlayDevice>,
            perDevice: List<JellyPlaySyncDeviceStat>,
            thisDeviceId: String?,
        ): List<JellyPlaySyncDeviceRow> {
            val statsByDevice = perDevice.associateBy { it.deviceId }
            val rows = registry.map { device ->
                val stat = statsByDevice[device.deviceId]
                JellyPlaySyncDeviceRow(
                    deviceId = device.deviceId,
                    name = device.name.ifBlank { device.platform.ifBlank { device.deviceId } },
                    platform = device.platform,
                    model = device.model?.takeIf { it.isNotBlank() },
                    lastSyncAt = stat?.lastSyncAt?.takeIf { it > 0 },
                    lastOp = stat?.lastOp.orEmpty(),
                    revoked = device.revoked,
                    isThisDevice = device.deviceId == thisDeviceId,
                )
            }.ifEmpty {
                // No registry entries (the events face never registered this
                // device, or the read failed): fall back to the server's own
                // per-device ledger so the section still says something true.
                perDevice.map { stat ->
                    JellyPlaySyncDeviceRow(
                        deviceId = stat.deviceId,
                        name = stat.deviceId,
                        platform = "",
                        model = null,
                        lastSyncAt = stat.lastSyncAt.takeIf { it > 0 },
                        lastOp = stat.lastOp,
                        revoked = false,
                        isThisDevice = stat.deviceId == thisDeviceId,
                    )
                }
            }
            return rows.sortedWith(
                compareByDescending<JellyPlaySyncDeviceRow> { it.isThisDevice }
                    .thenBy { it.revoked }
                    .thenByDescending { it.lastSyncAt ?: Long.MIN_VALUE },
            )
        }

        /**
         * The selective-sync + usage face: one row per namespace this device
         * knows — the engine's adapter namespaces (toggleable, their
         * device-local flag from the persisted toggles) UNION the server
         * status's namespaces (usage numbers, toggle-less). Rows the server
         * doesn't report usage for show zeros. Pure so the merge stays
         * pin-able without a VM.
         */
        fun mergeNamespaces(
            serverNamespaces: List<JellyPlaySyncNamespaceUsage>,
            engineNamespaces: List<String>,
            engineEnabled: Map<String, Boolean>,
            pending: Map<String, Int>,
        ): List<JellyPlaySyncNamespaceRow> {
            val usageByNs = serverNamespaces.associateBy { it.ns }
            val names = (usageByNs.keys + engineNamespaces).sorted()
            return names.map { ns ->
                val usage = usageByNs[ns]
                val toggleable = ns in engineNamespaces
                JellyPlaySyncNamespaceRow(
                    ns = ns,
                    keys = usage?.keys ?: 0,
                    bytes = usage?.bytes ?: 0,
                    pending = pending[ns] ?: 0,
                    toggleable = toggleable,
                    enabled = if (toggleable) engineEnabled[ns] ?: true else true,
                )
            }
        }
    }
}
