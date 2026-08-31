package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncOptions
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.formatEta
import com.raulshma.jellyplay.core.model.formatSpeed
import com.raulshma.jellyplay.core.ui.feedback.UiText
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class DownloadsUiState(
    val downloads: List<DownloadItem> = emptyList(),
    val totalStorageBytes: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Stable ids currently in selection mode. */
    val selectedIds: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
)

/**
 * A downloaded item eligible for a force resync (completed, or stalled after
 * partial artifacts landed), with enough episode context (series name + SxxExx)
 * to render the same identification line the downloads list shows, so episodes
 * are distinguishable in the force-resync picker.
 */
@Immutable
data class ForceResyncCandidate(
    val id: String,
    val name: String,
    val mediaType: com.raulshma.jellyplay.core.model.MediaType,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val offlineRepository: OfflineRepository,
    private val syncManager: OfflineSyncManager,
    private val userMessageBus: UserMessageBus,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.flow

    /** Live count of items flagged for a metadata/image resync — appbar badge. */
    val updatesAvailable: StateFlow<Int> =
        offlineRepository.getUpdatesCount().stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Items with updates, reactive — drives the resync sheet content. */
    val updateRows: StateFlow<List<OfflineSyncUpdate>> =
        offlineRepository.getItemsWithUpdates()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live batch resync progress (per-item phase + aggregate counts). */
    val resyncProgress: StateFlow<ResyncBatchProgress> = syncManager.batchProgress

    /** True while a batch freshness check is running. */
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    init {
        launch {
            // Change-filtering already lives in the repository (id order +
            // per-item bytes/status), so only list-affecting changes land here.
            downloadRepository.getAllDownloads()
                .catch { e ->
                    _uiState.update {
                        it.copy(error = e.localizedMessage ?: "Failed to load downloads", isLoading = false)
                    }
                }
                .collectLatest { items ->
                    _uiState.update {
                        it.copy(
                            downloads = items,
                            error = null,
                            isLoading = false,
                            totalStorageBytes = items.sumOf { item -> item.downloadedBytes },
                        )
                    }
                }
        }
    }

    fun cancelDownload(item: DownloadItem) {
        launch {
            downloadRepository.cancelDownload(item.id)
        }
    }

    fun pauseDownload(item: DownloadItem) {
        launch {
            downloadRepository.pauseDownload(item.id)
        }
    }

    fun resumeDownload(item: DownloadItem) {
        launch {
            downloadRepository.resumeDownload(item.id)
            downloadRepository.enqueueDownload(item.id)
        }
    }

    fun deleteDownload(item: DownloadItem) {
        launch {
            downloadRepository.deleteDownload(item.id)
            userMessageBus.info(UiText.Resource(R.string.downloads_deleted_message))
        }
    }

    fun retryDownload(item: DownloadItem) {
        launch {
            downloadRepository.retryDownload(item.id)
            downloadRepository.enqueueDownload(item.id)
        }
    }

    fun moveToFront(item: DownloadItem) {
        launch {
            val maxPriority = _uiState.value.downloads.maxOfOrNull { it.priority } ?: 0
            downloadRepository.setDownloadPriority(item.id, maxPriority + 1)
        }
    }

    fun lowerPriority(item: DownloadItem) {
        launch {
            val minPriority = _uiState.value.downloads.minOfOrNull { it.priority } ?: 0
            downloadRepository.setDownloadPriority(item.id, minPriority - 1)
        }
    }

    // ── Selection ────────────────────────────────────────────────────────

    fun toggleSelection(item: DownloadItem) {
        _uiState.update {
            val next = if (item.id in it.selectedIds) it.selectedIds - item.id else it.selectedIds + item.id
            it.copy(selectedIds = next, selectionMode = next.isNotEmpty())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), selectionMode = false) }
    }

    fun selectAll() {
        _uiState.update {
            it.copy(selectedIds = it.downloads.map { item -> item.id }.toSet(), selectionMode = true)
        }
    }

    // ── Bulk actions ─────────────────────────────────────────────────────

    /** Bulk-delete every selected download. Frees disk for completed items. */
    fun deleteSelected() {
        val targets = _uiState.value.downloads.filter { it.id in _uiState.value.selectedIds }
        if (targets.isEmpty()) return
        launch {
            bulkMap(targets) { downloadRepository.deleteDownload(it.id) }
            clearSelection()
            userMessageBus.info(UiText.Resource(R.string.downloads_deleted_message))
        }
    }

    /** Pause every selected download that is currently downloading. */
    fun pauseSelected() {
        val targets = _uiState.value.downloads
            .filter { it.id in _uiState.value.selectedIds && it.status == DownloadStatus.DOWNLOADING }
        if (targets.isEmpty()) return
        launch {
            bulkMap(targets) { downloadRepository.pauseDownload(it.id) }
        }
    }

    /** Resume every selected download that is currently paused. */
    fun resumeSelected() {
        val targets = _uiState.value.downloads
            .filter { it.id in _uiState.value.selectedIds && it.status == DownloadStatus.PAUSED }
        if (targets.isEmpty()) return
        launch {
            bulkMap(targets) {
                downloadRepository.resumeDownload(it.id)
                downloadRepository.enqueueDownload(it.id)
            }
        }
    }

    /** Cancel every selected active/queued/paused download. */
    fun cancelSelected() {
        val targets = _uiState.value.downloads.filter { item ->
            item.id in _uiState.value.selectedIds && item.status in setOf(
                DownloadStatus.PENDING,
                DownloadStatus.QUEUED,
                DownloadStatus.DOWNLOADING,
                DownloadStatus.PAUSED,
            )
        }
        if (targets.isEmpty()) return
        launch {
            bulkMap(targets) { downloadRepository.cancelDownload(it.id) }
        }
    }

    // ── Global actions ──────────────────────────────────────────────────

    /**
     * Pause every download that is currently downloading. Mirrors [pauseSelected]
     * but over the full list, so the user can halt all active transfers without
     * entering selection mode.
     */
    fun pauseAll() {
        val targets = _uiState.value.downloads
            .filter { it.status == DownloadStatus.DOWNLOADING }
        if (targets.isEmpty()) return
        launch {
            bulkMap(targets) { downloadRepository.pauseDownload(it.id) }
        }
    }

    /**
     * Re-queue every download in a Failed state. Mirrors the per-item
     * [retryDownload] flow (reset + enqueue) applied to all Failed items, so a
     * transient batch failure (e.g. a dropped network) can be recovered in one
     * action without entering selection mode.
     */
    fun retryAllFailed() {
        val targets = _uiState.value.downloads
            .filter { it.status == DownloadStatus.FAILED }
        if (targets.isEmpty()) return
        launch {
            bulkMap(targets) {
                downloadRepository.retryDownload(it.id)
                downloadRepository.enqueueDownload(it.id)
            }
        }
    }

    /**
     * Runs [action] for every target concurrently instead of serially — each
     * repository call bundles WorkManager ops + file cleanup + a DB
     * transaction, so a 50-item selection paid 50 sequential round-trips.
     * Same shape as OfflineRepositoryImpl.deleteArtifactsParallel.
     */
    private suspend inline fun <T> bulkMap(
        targets: List<T>,
        crossinline action: suspend (T) -> Unit,
    ) {
        coroutineScope {
            targets.map { item -> async { action(item) } }.awaitAll()
        }
    }

    // ── Freshness check / resync ────────────────────────────────────────

    /**
     * Checks every downloaded item for available updates (TTL-gated per item).
     * Safe to call repeatedly — items within their TTL are skipped. Sets
     * [checking] while the batch runs so the appbar icon can spin.
     */
    fun checkAllForUpdates() {
        if (_checking.value) return
        launch {
            _checking.value = true
            try {
                val ids = offlineRepository.getDownloadedItemIds()
                syncManager.checkForUpdatesBatch(ids)
            } finally {
                _checking.value = false
            }
        }
    }

    /**
     * Resyncs a single item's metadata/images. Progress flows through
     * [resyncProgress]; the item's update flag clears once its baseline refreshes.
     */
    fun resyncOne(itemId: String) {
        syncManager.resyncBatch(listOf(itemId))
    }

    /**
     * Resyncs every item currently flagged for an update. Sequential to keep
     * bandwidth predictable and avoid competing with active downloads. Resolves
     * the flagged ids from the repository so it works even if the sheet hasn't
     * collected [updateRows] yet.
     */
    fun resyncAll() {
        launch {
            val flagged = offlineRepository.getItemsWithUpdates().first()
            if (flagged.isNotEmpty()) syncManager.resyncBatch(flagged.map { it.id })
        }
    }

    /** Resyncs an explicit set of item ids (used by the sheet's per-item action). */
    fun resyncAll(itemIds: List<String>) {
        if (itemIds.isNotEmpty()) syncManager.resyncBatch(itemIds)
    }

    /** Clears batch progress once the resync sheet is dismissed. */
    fun clearResyncProgress() {
        syncManager.clearBatchProgress()
    }

    /**
     * Force-resyncs an explicit set of completed items, refreshing only the
     * data categories in [options]. Unlike [resyncAll], this is user-directed
     * (any completed item, not just flagged ones) and partial (skipped
     * categories retain their baseline). Progress flows through [resyncProgress].
     */
    fun forceResync(itemIds: List<String>, options: ResyncOptions) {
        if (itemIds.isEmpty() || options.isEmpty) return
        syncManager.resyncBatch(itemIds, options)
    }

    /**
     * Download statuses eligible for a force resync. COMPLETED items carry the
     * full artifact set; FAILED and PAUSED items still have their offline
     * metadata row (seeded during intake) plus whatever sidecars landed before
     * the stall, so refreshing them from the server is valid. Actively
     * transferring states (PENDING/QUEUED/DOWNLOADING) are excluded — the
     * download worker itself lands fresh sidecars there, and a concurrent
     * resync would interleave with its writes. CANCELLED never lingers: cancel
     * deletes the row and its files.
     */
    private val forceResyncEligibleStatuses = setOf(
        DownloadStatus.COMPLETED,
        DownloadStatus.FAILED,
        DownloadStatus.PAUSED,
    )

    /**
     * Completed/stalled downloads available for a force resync, deduplicated by
     * media item id. Resolved from an uncapped one-shot DB read (not the UI
     * list's 500-row reactive window) so every downloaded media item is
     * offered, and so the picker is correct even when opened before the list
     * flow has emitted. Drives the item picker in the force-resync sheet.
     * Carries the episode context (series name + SxxExx) so episodes are
     * identifiable in the picker, mirroring the context line on the downloads
     * list.
     */
    suspend fun forceResyncCandidates(): List<ForceResyncCandidate> =
        downloadRepository.getAllDownloadsSnapshot()
            .filter { it.status in forceResyncEligibleStatuses }
            .distinctBy { it.mediaItemId }
            .map {
                ForceResyncCandidate(
                    id = it.mediaItemId,
                    name = it.name,
                    mediaType = it.mediaType,
                    seriesName = it.seriesName,
                    seasonNumber = it.seasonNumber,
                    episodeNumber = it.episodeNumber,
                )
            }

    fun formatBytes(bytes: Long): String = bytes.formatBytes()

    fun formatSpeed(speedBytesPerSec: Long): String = speedBytesPerSec.formatSpeed()

    fun formatEta(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): String =
        com.raulshma.jellyplay.core.model.formatEta(downloadedBytes, totalBytes, speedBytesPerSec)
}
