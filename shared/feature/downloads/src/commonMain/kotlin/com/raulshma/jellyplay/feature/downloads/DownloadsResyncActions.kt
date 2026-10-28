package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.download.OfflineResync
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.ResyncOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val mediaType: MediaType,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

/**
 * Owns the downloads resync/freshness family behind one constructed module
 * (the [com.raulshma.jellyplay.feature.details.MetadataAdminActions] seam
 * shape): the reactive update surface ([updatesAvailable] count,
 * [updateRows] rows, [resyncProgress] batch progress), the TTL-gated
 * check-for-updates sweep with its [checking] spinner flag, the batch resync
 * verbs, and the force-resync eligibility policy — the
 * [forceResyncEligibleStatuses] set is single-sited HERE, so the
 * candidate fold and any future consumer agree on which statuses a force
 * resync admits. Constructed from the [OfflineResync] + [OfflineRepository]
 * + [DownloadQueue] seams [DownloadsViewModel] already carries; its
 * WhileSubscribed state children ride the VM's [scope].
 *
 * A read/refresh family: the POST-action UI effects (snackbar/message folds,
 * selection clears) stay VM-side (the DownloadActions recorded rule —
 * effects-fold-stays-VM-side); this holder never emits
 * [DownloadsUserMessage]s.
 */
internal class DownloadsResyncActions(
    private val scope: CoroutineScope,
    private val offlineRepository: OfflineRepository,
    private val resync: OfflineResync,
    private val queue: DownloadQueue,
) {

    /** Live count of items flagged for a metadata/image resync — appbar badge. */
    val updatesAvailable: StateFlow<Int> =
        offlineRepository.getUpdatesCount().stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Items with updates, reactive — drives the resync sheet content. */
    val updateRows: StateFlow<List<OfflineSyncUpdate>> =
        offlineRepository.getItemsWithUpdates()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live batch resync progress (per-item phase + aggregate counts). */
    val resyncProgress: StateFlow<ResyncBatchProgress> = resync.batchProgress

    /** True while a batch freshness check is running. */
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /**
     * Checks every downloaded item for available updates (TTL-gated per item).
     * Safe to call repeatedly — items within their TTL are skipped. Sets
     * [checking] while the batch runs so the appbar icon can spin.
     */
    fun checkAllForUpdates() {
        if (_checking.value) return
        scope.launch {
            _checking.value = true
            try {
                val ids = offlineRepository.getDownloadedItemIds()
                resync.checkForUpdatesBatch(ids)
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
        resync.resyncBatch(listOf(itemId))
    }

    /**
     * Resyncs every item currently flagged for an update. Sequential to keep
     * bandwidth predictable and avoid competing with active downloads. Resolves
     * the flagged ids from the repository so it works even if the sheet hasn't
     * collected [updateRows] yet.
     */
    fun resyncAll() {
        scope.launch {
            val flagged = offlineRepository.getItemsWithUpdates().first()
            if (flagged.isNotEmpty()) resync.resyncBatch(flagged.map { it.id })
        }
    }

    /** Resyncs an explicit set of item ids (used by the sheet's per-item action). */
    fun resyncAll(itemIds: List<String>) {
        if (itemIds.isNotEmpty()) resync.resyncBatch(itemIds)
    }

    /** Clears batch progress once the resync sheet is dismissed. */
    fun clearResyncProgress() {
        resync.clearBatchProgress()
    }

    /**
     * Force-resyncs an explicit set of completed items, refreshing only the
     * data categories in [options]. Unlike [resyncAll], this is user-directed
     * (any completed item, not just flagged ones) and partial (skipped
     * categories retain their baseline). Progress flows through [resyncProgress].
     */
    fun forceResync(itemIds: List<String>, options: ResyncOptions) {
        if (itemIds.isEmpty() || options.isEmpty) return
        resync.resyncBatch(itemIds, options)
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
        queue.getAllDownloadsSnapshot()
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
}
