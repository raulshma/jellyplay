package com.raulshma.jellyplay.feature.admin.mediacleanup

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.MediaItemStub
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.model.SelectionState
import com.raulshma.jellyplay.core.model.sortSizeBytes
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Sort vocabulary shared by the two media-cleanup screens (stale + watched).
 * Declared delta vs the pre-chassis code: the enum used to live in
 * `stalemedia/StaleMediaViewModel.kt` while `watchedremoval` imported it from
 * there — it moved to this chassis package so neither feature owns it. The
 * labels are NOT carried here: the scaffold maps each entry to its
 * `Res.string.admin_sort_*` resource (the old English literals were the
 * twins' copy drift).
 */
enum class MediaSortOption {
    DEFAULT,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
    TYPE,
    DATE,
}

/**
 * The one observable snapshot of a media-cleanup scan screen (the
 * `SeerrRequestStateHolder` / `InstantMixStateHolder` shape). Everything the
 * results/configuration/audit tabs render is here; the twins' former
 * per-feature state classes (`StaleMediaState` / `WatchedMediaState`) were
 * field-identical except for the config default — which is now the adapter's
 * [MediaCleanupScanStateHolder.initialConfig] constructor argument.
 */
@Immutable
data class MediaCleanupScanState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val config: MediaCleanupConfig = MediaCleanupConfig(),
    val scanId: String? = null,
    val scanProgress: ScanProgress = ScanProgress(),
    val rawScanResults: List<MediaItemStub> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    val canDeleteContent: Boolean = true,
    val auditEntries: List<AuditLogEntry> = emptyList(),
    val sortOption: MediaSortOption = MediaSortOption.DEFAULT,
) {
    /** The raw results re-sorted per [sortOption] — the results tab's read model. */
    val scanResults: List<MediaItemStub>
        get() = when (sortOption) {
            MediaSortOption.DEFAULT -> rawScanResults
            MediaSortOption.NAME_ASC -> rawScanResults.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            MediaSortOption.NAME_DESC -> rawScanResults.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            MediaSortOption.SIZE_DESC -> rawScanResults.sortedByDescending { it.sortSizeBytes }
            MediaSortOption.SIZE_ASC -> rawScanResults.sortedBy { it.sortSizeBytes }
            MediaSortOption.TYPE -> rawScanResults.sortedBy { it.type }
            MediaSortOption.DATE -> rawScanResults.sortedBy { it.dateText }
        }
}

/**
 * Deep module for the media-cleanup scan lifecycle the admin twins
 * (`stalemedia` / `watchedremoval`) used to carry as two ~200-line
 * field-identical ViewModels: the scan cascade (start → detect → progress
 * observe → COMPLETED → result-JSON decode), the selection machine, the
 * confirm/delete choreography, the delete-permission mirror, and the audit
 * history fold.
 *
 * The repository seam is constructor lambdas (the repository interfaces stay
 * untouched): each feature's ViewModel adapter supplies its own detect call
 * and bakes its `CleanupActionType` into the delete/audit lambdas — the
 * action-type divergence lives in the adapters, not the chassis.
 *
 * Declared deltas vs the two former hand-copies:
 *  - **Progress re-collect race fixed.** The twins launched a fresh progress
 *    collector per `startScan` without cancelling the previous one, so a late
 *    emission from an abandoned scan could overwrite the live scan's progress
 *    and results. The holder cancels the previous collector single-flight
 *    before observing the new scan (pinned in
 *    `MediaCleanupScanStateHolderTest`).
 *  - **Dead tab state dropped.** `StaleMediaState.selectedTabIndex` +
 *    `selectTab` had no reader — the screen drove its tab row from local
 *    `remember` state (unlike Logs/Plugins, whose VMs genuinely own tab
 *    state). The chassis holds no tab index.
 *  - **Selection algebra.** `toggleItemSelection` rides core/model's
 *    [SelectionState.toggled]; the results-tab select-all is an all↔none
 *    TOGGLE, which is deliberately NOT [SelectionState.selectAll] (an
 *    unconditional select) — the screen checkbox's second click clears, and
 *    that behaviour predates the chassis, so it stays chassis-side.
 */
class MediaCleanupScanStateHolder(
    private val scope: CoroutineScope,
    initialConfig: MediaCleanupConfig,
    private val detectMedia: suspend (MediaCleanupConfig) -> Result<String>,
    private val scanProgress: (scanId: String) -> Flow<ScanProgress>,
    private val scanResultJson: suspend (scanId: String) -> String?,
    private val removeMediaItems: suspend (
        itemIds: List<String>,
        itemNameMap: Map<String, String>,
        config: MediaCleanupConfig,
    ) -> Result<AuditLogEntry>,
    currentUser: Flow<UserInfo?>,
    auditHistory: Flow<List<AuditLogEntry>>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(MediaCleanupScanState(config = initialConfig))
    val state: StateFlow<MediaCleanupScanState> = _state.asStateFlow()

    /** The single live progress collector — replaced (cancelled) per new scan. */
    private var progressJob: Job? = null

    init {
        scope.launch {
            currentUser.collect { user ->
                _state.update { it.copy(canDeleteContent = user?.canDeleteContent ?: false) }
            }
        }
        scope.launch {
            auditHistory.collect { entries ->
                _state.update { it.copy(auditEntries = entries) }
            }
        }
    }

    fun updateConfig(config: MediaCleanupConfig) {
        _state.update { it.copy(config = config) }
    }

    fun updateSort(option: MediaSortOption) {
        _state.update { it.copy(sortOption = option) }
    }

    fun startScan() {
        scope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    rawScanResults = emptyList(),
                    selectedItems = emptySet(),
                )
            }
            detectMedia(_state.value.config)
                .onSuccess { scanId ->
                    _state.update { it.copy(scanId = scanId, isLoading = false) }
                    observeScanProgress(scanId)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private fun observeScanProgress(scanId: String) {
        progressJob?.cancel()
        progressJob = scope.launch {
            scanProgress(scanId).collect { progress ->
                _state.update { it.copy(scanProgress = progress) }
                if (progress.phase == ScanPhase.COMPLETED) {
                    val results = loadResults(scanId)
                    _state.update { it.copy(rawScanResults = results) }
                }
            }
        }
    }

    private suspend fun loadResults(scanId: String): List<MediaItemStub> {
        return try {
            val resultJson = scanResultJson(scanId)
            if (resultJson.isNullOrBlank()) emptyList()
            else json.decodeFromString<List<MediaItemStub>>(resultJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toggleItemSelection(itemId: String) {
        _state.update {
            it.copy(selectedItems = SelectionState(it.selectedItems).toggled(itemId).ids)
        }
    }

    fun selectAll() {
        val allIds = _state.value.scanResults.map { it.itemId }.toSet()
        _state.update {
            it.copy(
                selectedItems = if (it.selectedItems == allIds) emptySet() else allIds,
            )
        }
    }

    fun showDeleteConfirmation() {
        _state.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _state.update { it.copy(showDeleteConfirmation = false) }
    }

    fun deleteSelected() {
        scope.launch {
            _state.update { it.copy(isDeleting = true) }
            val selectedItems = _state.value.selectedItems.toList()
            val nameMap = _state.value.scanResults.associate { it.itemId to it.name }

            removeMediaItems(selectedItems, nameMap, _state.value.config)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            showDeleteConfirmation = false,
                            selectedItems = emptySet(),
                            rawScanResults = it.rawScanResults.filterNot { selectedItems.contains(it.itemId) },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            showDeleteConfirmation = false,
                            error = e.message,
                        )
                    }
                }
        }
    }
}
