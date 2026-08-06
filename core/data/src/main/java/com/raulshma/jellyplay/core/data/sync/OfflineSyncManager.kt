package com.raulshma.jellyplay.core.data.sync

import android.util.Log
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadArtifacts
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineRow
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
import com.raulshma.jellyplay.core.model.offlineSyncStateOf
import com.raulshma.jellyplay.core.model.ResyncCheckResult
import com.raulshma.jellyplay.core.model.ResyncItemProgress
import com.raulshma.jellyplay.core.model.ResyncOptions
import com.raulshma.jellyplay.core.model.ResyncPhase
import com.raulshma.jellyplay.core.model.ResyncResult
import com.raulshma.jellyplay.core.model.ResyncStep
import com.raulshma.jellyplay.core.model.ResyncStepResult
import com.raulshma.jellyplay.core.model.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates offline download freshness checks and metadata/image resyncs.
 *
 * **What it does not do**: re-download the media file. A server-side MediaSource
 * change is surfaced as [OfflineSyncState.mediaFileChanged] and left for the UI
 * to route through the existing delete + download path; this manager only ever
 * refreshes the lightweight artifacts (offline metadata row, poster, backdrop).
 *
 * **TTL gate.** [checkForUpdates] is a no-op network-wise when the persisted
 * `lastSyncedAt` is within [SYNC_TTL_MS] of now, so an offline-detail screen can
 * call it on every entry without spamming the server — most calls resolve from
 * the DB. The same gate applies to [checkForUpdatesBatch] per item.
 *
 * **Baseline seeding.** Items downloaded before this feature shipped have no
 * baseline. The first check treats a missing baseline as "first sync": it
 * records the fresh baseline and reports CURRENT (no spurious update flag on
 * first contact), so users aren't prompted to resync items they just opened.
 *
 * **Decision purity.** All freshness logic (signature, tag/media-source diff)
 * lives in [OfflineSyncComparator]; this class only moves data between the
 * network, the DAO, and the disk image cache.
 */
@Singleton
class OfflineSyncManager @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val writer: OfflineDownloadWriter,
    private val downloadRepository: DownloadRepository,
    private val offlineMediaDao: OfflineMediaDao,
    private val comparator: OfflineSyncComparator,
    private val offlineModeManager: OfflineModeManager,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _batchProgress = MutableStateFlow(ResyncBatchProgress())
    val batchProgress: StateFlow<ResyncBatchProgress> = _batchProgress.asStateFlow()

    init {
        // Clear any `syncChecking=1` markers left by a process death mid-check
        // so they don't render as a stuck "checking…" badge forever.
        ioScope.launch { runCatching { offlineMediaDao.clearAllCheckingFlags() } }
    }

    /**
     * Checks [itemId] against the server, TTL-gated. Returns the resulting state.
     * Resolves from the persisted baseline (no network) when within the TTL or
     * when the device is offline; only fetches when the TTL has expired and the
     * device is online. Pass [force] to bypass the TTL gate (e.g. pull-to-refresh).
     */
    suspend fun checkForUpdates(itemId: String, force: Boolean = false): ResyncCheckResult {
        val baseline = offlineMediaDao.getSyncBaseline(itemId)
            ?: return ResyncCheckResult(itemId, OfflineSyncState(SyncStatus.UNKNOWN))

        val now = System.currentTimeMillis()
        val lastSynced = baseline.lastSyncedAt
        if (!force && lastSynced != null && now - lastSynced < SYNC_TTL_MS) {
            return ResyncCheckResult(itemId, baseline.toState())
        }
        if (offlineModeManager.isOffline) {
            // Can't fetch; surface the last known state rather than spinning.
            return ResyncCheckResult(itemId, baseline.toState())
        }

        offlineMediaDao.setSyncChecking(itemId, 1)
        try {
            // Bust the TTL detail cache so we get a fresh fetch, not a stale read.
            mediaRepository.invalidateDetailCache(itemId)
            val fresh = mediaRepository.getMediaDetail(itemId).getOrNull()
            if (fresh == null) {
                recordError(itemId, baseline)
                return ResyncCheckResult(itemId, baseline.toState().copy(status = SyncStatus.ERROR))
            }
            val result = comparator.diff(baseline.toSyncBaseline(), fresh, itemId)
            persistCheckResult(itemId, fresh, result, baseline.hasStoredBaseline())
            return result
        } catch (e: Exception) {
            Log.w(TAG, "Freshness check failed for $itemId", e)
            recordError(itemId, baseline)
            return ResyncCheckResult(itemId, baseline.toState().copy(status = SyncStatus.ERROR))
        } finally {
            offlineMediaDao.setSyncChecking(itemId, 0)
        }
    }

    /**
     * Batch freshness check over [itemIds]. Concurrency-limited to avoid
     * hammering the server with N parallel detail fetches. Each item is
     * individually TTL-gated, so a re-check shortly after a previous batch is
     * near-free. Returns each item's result in input order.
     */
    suspend fun checkForUpdatesBatch(
        itemIds: List<String>,
        force: Boolean = false,
    ): List<ResyncCheckResult> = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) return@withContext emptyList()
        itemIds.map { id ->
            async { checkPermits.withPermit { checkForUpdates(id, force) } }
        }.awaitAll()
    }

    /**
     * Resyncs a single item's metadata and changed images from the server. Does
     * NOT touch the media file. Updates the persisted baseline on success and
     * clears the update-available flag (a media-source change keeps the
     * media-changed flag set so the UI can still prompt for a full re-download).
     * Reports progress via [batchProgress] and returns the step-by-step result.
     *
     * [options] selects which data categories to refresh. Skipped categories
     * retain their existing baseline, so a partial sync only clears the
     * update-available flag for the categories actually synced — the comparator
     * is re-run against an effective baseline that blends synced-fresh values
     * with the retained values. This recheck governs only the metadata/image
     * axes (the selectable categories); the media-source axis is fixed
     * separately from [mediaFileChanged] and is not part of that re-check.
     * Defaults to [ResyncOptions.ALL] (the historical "resync everything"
     * behaviour).
     */
    suspend fun resyncItem(
        itemId: String,
        options: ResyncOptions = ResyncOptions.ALL,
    ): ResyncResult = withContext(Dispatchers.IO) {
        setProgress(itemId, ResyncPhase.WORKING, ResyncStep.FETCH_DETAIL)
        val steps = mutableListOf<ResyncStepResult>()
        var mediaFileChanged = false
        try {
            if (offlineModeManager.isOffline) {
                steps += ResyncStepResult(itemId, ResyncStep.FETCH_DETAIL, success = false, message = "Offline")
                setProgress(itemId, ResyncPhase.ERROR, null)
                return@withContext ResyncResult(itemId, steps, mediaFileChanged)
            }
            mediaRepository.invalidateDetailCache(itemId)
            val detail = mediaRepository.getMediaDetail(itemId).getOrNull()
            if (detail == null) {
                steps += ResyncStepResult(itemId, ResyncStep.FETCH_DETAIL, success = false, message = "Fetch failed")
                setProgress(itemId, ResyncPhase.ERROR, null)
                return@withContext ResyncResult(itemId, steps, mediaFileChanged)
            }
            steps += ResyncStepResult(itemId, ResyncStep.FETCH_DETAIL, success = true)

            val baselineRow = offlineMediaDao.getSyncBaseline(itemId)
            val baseline = baselineRow?.toSyncBaseline()
            val freshSource = detail.mediaSources.firstOrNull()
            mediaFileChanged = baseline != null && comparator.isMediaSourceChanged(baseline, freshSource)

            // Metadata: re-persist the offline detail row. Skipped when the user
            // only asked for images — the existing metadata row is left as-is.
            if (options.metadata) {
                setProgress(itemId, ResyncPhase.WORKING, ResyncStep.PERSIST_METADATA)
                // Preserve the existing local image paths so the re-persisted row
                // keeps pointing at the on-disk files; only changed image bytes are
                // overwritten below. Falls back to remote URLs if none persisted yet.
                val paths = offlineMediaDao.getLocalImagePaths(itemId)
                val posterPath = paths?.posterPath
                val backdropPath = paths?.backdropPath
                writer.saveOfflineMediaDetail(detail, posterPath, backdropPath)
                steps += ResyncStepResult(itemId, ResyncStep.PERSIST_METADATA, success = true)
            }

            val parentDir = downloadRepository.getDownloadByMediaItemId(itemId)?.downloadPath
                ?.let { File(it).parentFile }
            if (parentDir != null) {
                if (options.poster) {
                    val posterChanged = baseline == null || comparator.isImageChanged(baseline.posterTag, detail.posterImageTag)
                    if (posterChanged) {
                        setProgress(itemId, ResyncPhase.WORKING, ResyncStep.DOWNLOAD_POSTER)
                        val poster = writer.downloadOfflineImage(
                            itemId, "Primary", 300, parentDir, DownloadArtifacts.posterFile(itemId),
                        )
                        steps += ResyncStepResult(itemId, ResyncStep.DOWNLOAD_POSTER, success = poster != null)
                    }
                }
                if (options.backdrop) {
                    val backdropChanged = baseline == null || comparator.isImageChanged(baseline.backdropTag, detail.backdropImageTag)
                    if (backdropChanged) {
                        setProgress(itemId, ResyncPhase.WORKING, ResyncStep.DOWNLOAD_BACKDROP)
                        val backdrop = writer.downloadOfflineImage(
                            itemId, "Backdrop", 1280, parentDir, DownloadArtifacts.backdropFile(itemId),
                        )
                        steps += ResyncStepResult(itemId, ResyncStep.DOWNLOAD_BACKDROP, success = backdrop != null)
                    }
                }
            }

            setProgress(itemId, ResyncPhase.WORKING, ResyncStep.UPDATE_BASELINE)
            val freshBaseline = comparator.baseline(detail)
            // Effective baseline blends synced-fresh values with the retained
            // (skipped-category) values from the prior baseline. Re-running the
            // comparator against it yields an accurate update-available flag:
            // a partial sync clears the flag only for the synced categories.
            val effectiveBaseline = baseline.mergePartial(freshBaseline, options, baseline == null)
            val recheck = comparator.diff(effectiveBaseline, detail, itemId)
            offlineMediaDao.updateSyncBaseline(
                itemId = itemId,
                posterTag = effectiveBaseline.posterTag,
                backdropTag = effectiveBaseline.backdropTag,
                metadataSignature = effectiveBaseline.metadataSignature,
                mediaSourceId = freshBaseline.mediaSourceId,
                mediaSizeBytes = freshBaseline.mediaSizeBytes,
                lastSyncedAt = System.currentTimeMillis(),
                updateAvailable = if (recheck.state.needsResync) 1 else 0,
                mediaChanged = if (mediaFileChanged) 1 else 0,
                checking = 0,
                error = 0,
            )
            steps += ResyncStepResult(itemId, ResyncStep.UPDATE_BASELINE, success = true)
            setProgress(itemId, ResyncPhase.DONE, null)
        } catch (e: Exception) {
            Log.w(TAG, "Resync failed for $itemId", e)
            steps += ResyncStepResult(itemId, ResyncStep.UPDATE_BASELINE, success = false, message = e.message)
            setProgress(itemId, ResyncPhase.ERROR, null)
        }
        ResyncResult(itemId, steps, mediaFileChanged)
    }

    /**
     * Resyncs multiple items sequentially (concurrency = 1 to keep bandwidth
     * predictable and avoid competing with active downloads). Each item's
     * progress flows through [batchProgress]. Fire-and-forget on [appScope].
     * [options] is applied to every item in the batch.
     */
    fun resyncBatch(itemIds: List<String>, options: ResyncOptions = ResyncOptions.ALL) {
        if (itemIds.isEmpty()) return
        _batchProgress.value = ResyncBatchProgress(
            items = itemIds.associateWith { ResyncItemProgress(it, ResyncPhase.PENDING) },
            total = itemIds.size,
        )
        appScope.launch {
            for (id in itemIds) resyncItem(id, options)
        }
    }

    /** Resets batch progress once the UI no longer needs it (e.g. sheet dismissed). */
    fun clearBatchProgress() {
        _batchProgress.value = ResyncBatchProgress()
    }

    private fun setProgress(itemId: String, phase: ResyncPhase, step: ResyncStep?) {
        _batchProgress.value = _batchProgress.value.copy(
            items = _batchProgress.value.items + (itemId to ResyncItemProgress(itemId, phase, step)),
        )
    }

    private suspend fun persistCheckResult(
        itemId: String,
        fresh: MediaDetail,
        result: ResyncCheckResult,
        hadBaseline: Boolean,
    ) {
        val state = result.state
        // First-contact (no prior baseline): record the fresh baseline as
        // CURRENT instead of flagging an update on the very first check, so
        // opening a long-dormant download doesn't prompt an immediate resync.
        val freshBaseline = comparator.baseline(fresh)
        val (updateFlag, mediaFlag) = if (!hadBaseline) {
            0 to 0
        } else {
            (if (state.needsResync) 1 else 0) to (if (state.mediaFileChanged) 1 else 0)
        }
        offlineMediaDao.updateSyncBaseline(
            itemId = itemId,
            posterTag = freshBaseline.posterTag,
            backdropTag = freshBaseline.backdropTag,
            metadataSignature = freshBaseline.metadataSignature,
            mediaSourceId = freshBaseline.mediaSourceId,
            mediaSizeBytes = freshBaseline.mediaSizeBytes,
            lastSyncedAt = state.lastCheckedAt,
            updateAvailable = updateFlag,
            mediaChanged = mediaFlag,
            checking = 0,
            error = 0,
        )
    }

    /**
     * Records a check failure: sets the `syncError` flag (so the badge keeps
     * surfacing it past the TTL gate instead of flickering once) while
     * preserving the last-known baseline. A later successful check clears the
     * flag via [persistCheckResult].
     */
    private suspend fun recordError(itemId: String, baseline: SyncBaselineRow) {
        offlineMediaDao.updateSyncBaseline(
            itemId = itemId,
            posterTag = baseline.syncedPosterTag,
            backdropTag = baseline.syncedBackdropTag,
            metadataSignature = baseline.syncedMetadataSignature,
            mediaSourceId = baseline.syncedMediaSourceId,
            mediaSizeBytes = baseline.syncedMediaSizeBytes,
            lastSyncedAt = baseline.lastSyncedAt,
            updateAvailable = baseline.syncUpdateAvailable,
            mediaChanged = baseline.syncMediaChanged,
            checking = 0,
            error = 1,
        )
    }

    companion object {
        private const val TAG = "OfflineSyncManager"
        // Per-item freshness TTL: re-check no more than once an hour.
        const val SYNC_TTL_MS = 60L * 60 * 1000
        // Caps concurrent detail fetches during a batch check.
        private val checkPermits = Semaphore(permits = 4)
    }
}

/** Adapts a DAO baseline row to the comparator's input type. */
private fun SyncBaselineRow.toSyncBaseline(): SyncBaseline = SyncBaseline(
    posterTag = syncedPosterTag,
    backdropTag = syncedBackdropTag,
    metadataSignature = syncedMetadataSignature ?: "",
    mediaSourceId = syncedMediaSourceId,
    mediaSizeBytes = syncedMediaSizeBytes,
)

/**
 * Blends this (prior) baseline with a [fresh] one according to [options]:
 * synced categories take the fresh value, skipped categories retain the prior
 * value. When there was no prior baseline ([firstSync]), every category takes
 * the fresh value regardless of selection — otherwise a partial first sync
 * would seed an all-empty baseline and spuriously flag the skipped categories
 * as "changed" on the next check.
 */
private fun SyncBaseline?.mergePartial(
    fresh: SyncBaseline,
    options: ResyncOptions,
    firstSync: Boolean,
): SyncBaseline = if (this == null || firstSync) {
    fresh
} else {
    SyncBaseline(
        posterTag = if (options.poster) fresh.posterTag else posterTag,
        backdropTag = if (options.backdrop) fresh.backdropTag else backdropTag,
        metadataSignature = if (options.metadata) fresh.metadataSignature else metadataSignature,
        // Media-source id/size are always recomputed from the fresh detail
        // (not user-selectable); they never retain a stale value.
        mediaSourceId = fresh.mediaSourceId,
        mediaSizeBytes = fresh.mediaSizeBytes,
    )
}

/** True when at least the metadata signature was ever recorded (vs. pre-feature rows). */
private fun SyncBaselineRow.hasStoredBaseline(): Boolean =
    syncedMetadataSignature != null || syncedPosterTag != null || syncedBackdropTag != null

/** Maps the persisted columns to a UI-facing state via the shared mapper. */
private fun SyncBaselineRow.toState(): OfflineSyncState = offlineSyncStateOf(
    checking = syncChecking,
    error = syncError,
    mediaChanged = syncMediaChanged,
    updateAvailable = syncUpdateAvailable,
    lastSyncedAt = lastSyncedAt,
)
