package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncAdminUser
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncDeviceStat
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryEntry
import com.raulshma.jellyplay.core.network.api.JellyPlaySyncHistoryKey
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
 * [mergeDevices].
 */
@Immutable
data class JellyPlaySyncDeviceRow(
    val deviceId: String,
    val name: String,
    val platform: String,
    val lastSyncAt: Long?,
    val lastOp: String,
    val isThisDevice: Boolean,
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
)

/**
 * The companion-plugin settings-sync screen's model (ADR 0010): the sync
 * engine's toggle + manual cycles ([ProfileSyncRepository]) beside the
 * server-side usage / history / admin-overview reads and the namespace reset.
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
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow(JellyPlaySyncUiState())
    val uiState: StateFlow<JellyPlaySyncUiState> = _uiState.asStateFlow()

    /** The sync engine's outcome (opt-in toggle, last sync, errors) — the toggle's state home. */
    val syncState: StateFlow<ProfileSyncRepository.SyncState> = syncRepository.state

    /** The resolved profile this device syncs under (the informational row). */
    val deviceProfile: String = syncRepository.resolvedProfile

    /**
     * One pull of every server-side face (usage, history, devices, admin
     * overview). Fired by the screen on open and after each manual action.
     * The usage/history reads degrade to null/empty on the pre-wave 404; the
     * admin read degrades on 403 (non-admin) the same way.
     */
    fun refresh() {
        scope.launch {
            if (!syncGateOpen()) {
                _uiState.update {
                    it.copy(isLoading = false, status = null, history = emptyList(), adminUsers = null)
                }
                return@launch
            }
            val thisDeviceId = runCatching { syncRepository.currentDeviceId() }.getOrNull()
                ?: _uiState.value.thisDeviceId
            val status = pluginApiClient.getSyncStatus().getOrNull()
            val history = pluginApiClient.getSyncHistory(limit = HISTORY_LIMIT).getOrNull()
            val registryDevices = pluginApiClient.getDevices().getOrNull().orEmpty()
            val adminUsers = pluginApiClient.adminSyncOverview().getOrNull()?.users

            _uiState.update {
                it.copy(
                    isLoading = false,
                    thisDeviceId = thisDeviceId,
                    status = status,
                    history = history?.entries.orEmpty(),
                    devices = mergeDevices(registryDevices, status?.perDevice.orEmpty(), thisDeviceId),
                    adminUsers = adminUsers,
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
     * Wipes the synced `prefs` namespace on the server; every device falls
     * back to its local state on the next cycle. Confirmed by the screen's
     * destructive dialog before reaching here.
     */
    fun resetNamespace() {
        scope.launch {
            if (!syncGateOpen()) return@launch
            pluginApiClient.resetNamespace(SYNCED_NAMESPACE)
                .onSuccess { refresh() }
        }
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
         * deviceId — registry rows keep their name/platform; when the registry
         * read came back EMPTY (the events face never registered this device,
         * or the read failed) the server's own per-device ledger stands in,
         * its rows surfacing under their raw device id. Ledger entries the
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
                    lastSyncAt = stat?.lastSyncAt?.takeIf { it > 0 },
                    lastOp = stat?.lastOp.orEmpty(),
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
                        lastSyncAt = stat.lastSyncAt.takeIf { it > 0 },
                        lastOp = stat.lastOp,
                        isThisDevice = stat.deviceId == thisDeviceId,
                    )
                }
            }
            return rows.sortedWith(
                compareByDescending<JellyPlaySyncDeviceRow> { it.isThisDevice }
                    .thenByDescending { it.lastSyncAt ?: Long.MIN_VALUE },
            )
        }
    }
}
