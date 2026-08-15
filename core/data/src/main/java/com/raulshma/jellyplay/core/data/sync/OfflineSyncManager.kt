package com.raulshma.jellyplay.core.data.sync

import android.util.Log
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadArtifacts
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.ResyncBatchProgress
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
 * The single home of offline freshness state. Owns all three of:
 *  - **decision** — delegated to the pure [OfflineSyncComparator] (the internal
 *    seam; no I/O there),
 *  - **persistence** — read/written through [SyncBaselineDao], the freshness
 *    table split out of `offline_media`,
 *  - **projection** — [getOfflineSyncState] / [getUpdatesCount] /
 *    [getItemsWithUpdates] turn the persisted baseline + per-axis flags into a
 *    UI-facing [OfflineSyncState] / [OfflineSyncUpdate].
 *
 * Consolidating the three here kills the historical drift between the
 * DB-driven badge and the check/resync result: there is now one projection
 * (lossless, because each content axis has its own persisted flag) instead of a
 * lossy "5 axes → 1 flag" mapper reconstructed in two places. The 14-positional
 * argument DAO updater collapses to a single [SyncBaselineDao.upsert] call.
 *
 * **What it does not do**: re-download the media file. A server-side MediaSource
 * change is surfaced as [OfflineSyncState.mediaFileChanged] and left for the UI
 * to route through the existing delete + download path; this manager only ever
 * refreshes the lightweight artifacts (offline metadata row, poster, backdrop,
 * subtitles, trickplay, segments).
 *
 * **TTL gate.** [checkForUpdates] is a no-op network-wise when the persisted
 * `lastSyncedAt` is within [SYNC_TTL_MS] of now, so an offline-detail screen can
 * call it on every entry without spamming the server. The same gate applies to
 * [checkForUpdatesBatch] per item.
 *
 * **Baseline seeding.** Items downloaded before this feature shipped have no
 * baseline row. The first check treats a missing baseline as "first sync": it
 * records the fresh baseline and reports CURRENT (no spurious update flag on
 * first contact), so users aren't prompted to resync items they just opened.
 */
@Singleton
class OfflineSyncManager @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val writer: OfflineDownloadWriter,
    private val downloadRepository: DownloadRepository,
    private val offlineMediaDao: OfflineMediaDao,
    private val syncBaselineDao: SyncBaselineDao,
    private val comparator: OfflineSyncComparator,
    private val offlineModeManager: OfflineModeManager,
    private val playbackRepository: PlaybackRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _batchProgress = MutableStateFlow(ResyncBatchProgress())
    val batchProgress: StateFlow<ResyncBatchProgress> = _batchProgress.asStateFlow()

    init {
        // Clear any `syncChecking=1` markers left by a process death mid-check
        // so they don't render as a stuck "checking…" badge forever.
        ioScope.launch { runCatching { syncBaselineDao.clearAllCheckingFlags() } }
    }

    // ── Decision orchestration (check / resync) ───────────────────────────────

    /**
     * Checks [itemId] against the server, TTL-gated. Returns the resulting state.
     * Resolves from the persisted baseline (no network) when within the TTL or
     * when the device is offline; only fetches when the TTL has expired and the
     * device is online. Pass [force] to bypass the TTL gate (e.g. pull-to-refresh).
     */
    suspend fun checkForUpdates(itemId: String, force: Boolean = false): ResyncCheckResult {
        val baseline = syncBaselineDao.getBaseline(itemId)
            ?: return ResyncCheckResult(itemId, OfflineSyncState(SyncStatus.UNKNOWN))

        val now = System.currentTimeMillis()
        val lastSynced = baseline.lastSyncedAt
        if (!force && lastSynced != null && now - lastSynced < SYNC_TTL_MS) {
            return ResyncCheckResult(itemId, baseline.toOfflineSyncState())
        }
        if (offlineModeManager.isOffline) {
            // Can't fetch; surface the last known state rather than spinning.
            return ResyncCheckResult(itemId, baseline.toOfflineSyncState())
        }

        syncBaselineDao.setSyncChecking(itemId, 1)
        try {
            // Force read: bypass the TTL detail cache so we get a fresh fetch,
            // not a stale read.
            val fresh = mediaRepository.getMediaDetail(itemId, force = true).getOrNull()
            if (fresh == null) {
                recordError(itemId, baseline)
                return ResyncCheckResult(itemId, baseline.toOfflineSyncState().copy(status = SyncStatus.ERROR))
            }
            val result = comparator.diff(baseline.toSyncBaseline(), fresh, itemId)
            persistCheckResult(
                itemId = itemId,
                fresh = fresh,
                result = result,
                hadBaseline = baseline.hasStoredBaseline(),
                // Segments aren't part of MediaDetail and aren't fetched on the
                // proactive check, so retain the prior segments signature rather
                // than wipe it to empty (which would re-seed on next segments
                // resync and swallow a real change).
                retainedSegmentsSignature = baseline.syncedSegmentsSignature,
            )
            return result
        } catch (e: Exception) {
            Log.w(TAG, "Freshness check failed for $itemId", e)
            recordError(itemId, baseline)
            return ResyncCheckResult(itemId, baseline.toOfflineSyncState().copy(status = SyncStatus.ERROR))
        } finally {
            syncBaselineDao.setSyncChecking(itemId, 0)
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
     * Resyncs a single item's metadata and changed images/sidecars from the
     * server. Does NOT touch the media file. Updates the persisted baseline on
     * success and clears the update-available flag (a media-source change keeps
     * the media-changed flag set so the UI can still prompt for a full
     * re-download). Reports progress via [batchProgress] and returns the
     * step-by-step result.
     *
     * [options] selects which data categories to refresh. Skipped categories
     * retain their existing baseline, so a partial sync only clears the
     * update-available flag for the categories actually synced — the comparator
     * is re-run against an effective baseline that blends synced-fresh values
     * with the retained values. Defaults to [ResyncOptions.ALL].
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
            // Force read: fetch fresh detail, bypassing the TTL cache.
            val detail = mediaRepository.getMediaDetail(itemId, force = true).getOrNull()
            if (detail == null) {
                steps += ResyncStepResult(itemId, ResyncStep.FETCH_DETAIL, success = false, message = "Fetch failed")
                setProgress(itemId, ResyncPhase.ERROR, null)
                return@withContext ResyncResult(itemId, steps, mediaFileChanged)
            }
            steps += ResyncStepResult(itemId, ResyncStep.FETCH_DETAIL, success = true)

            val baselineRow = syncBaselineDao.getBaseline(itemId)
            val baseline = baselineRow?.toSyncBaseline()
            val freshSource = detail.mediaSources.firstOrNull()
            mediaFileChanged = baseline != null && comparator.isMediaSourceChanged(baseline, freshSource)

            // Resolve the on-disk download location once: image writes need the
            // parent dir, sidecar writes (subtitles/trickplay/segments) need the
            // video file path. Null when the download row is gone (e.g. deleted
            // mid-resync) — image/sidecar blocks skip themselves in that case.
            val downloadPath = downloadRepository.getDownloadByMediaItemId(itemId)?.downloadPath
            val parentDir = downloadPath?.let { File(it).parentFile }

            // Metadata: re-persist the offline detail row. Skipped when the user
            // only asked for images/sidecars — the existing metadata row is left as-is.
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

            // ── Sidecar artifacts: subtitles + trickplay ─────────────────────
            // Both inventories ride on the MediaDetail fetch above (free), so the
            // change check is a pure signature compare. Gated like the image
            // blocks: only re-fetched when the signature differs or on first
            // contact (baseline == null). Each writer returns its own success so
            // a best-effort failure is reported honestly and — see the baseline
            // masking below — not re-seeded as if it had landed on disk.
            var subtitlesOk = true
            var trickplayOk = true
            var segmentsOk = true
            if (downloadPath != null && freshSource != null) {
                if (options.subtitles) {
                    val subsChanged = baseline == null ||
                        comparator.isSubtitleChanged(baseline.subtitleSignature, detail)
                    if (subsChanged) {
                        setProgress(itemId, ResyncPhase.WORKING, ResyncStep.DOWNLOAD_SUBTITLES)
                        // The writer mirrors a genuine server-side removal (no
                        // deliverable streams) by clearing the dir, but leaves
                        // existing sidecars untouched when fetches fail and
                        // returns false so the baseline rolls back and retries.
                        subtitlesOk = writer.downloadExternalSubtitles(
                            itemId, freshSource.id, freshSource.mediaStreams, downloadPath,
                        )
                        steps += ResyncStepResult(itemId, ResyncStep.DOWNLOAD_SUBTITLES, success = subtitlesOk)
                    }
                }
                if (options.trickplay) {
                    val info = freshSource.trickplayInfo
                    if (info != null) {
                        val trickplayChanged = baseline == null ||
                            comparator.isTrickplayChanged(baseline.trickplaySignature, detail)
                        if (trickplayChanged) {
                            setProgress(itemId, ResyncPhase.WORKING, ResyncStep.DOWNLOAD_TRICKPLAY)
                            trickplayOk = writer.downloadTrickplayData(itemId, info, downloadPath)
                            steps += ResyncStepResult(itemId, ResyncStep.DOWNLOAD_TRICKPLAY, success = trickplayOk)
                        }
                    }
                }
            }

            // ── Sidecar artifacts: media segments ────────────────────────────
            // Segments aren't part of MediaDetail, so a separate fetch is
            // required. Bust the segments cache so the writer fetches fresh
            // server state, then re-read (cache hit, free) to compute the
            // baseline signature — one network call total. Always refreshed when
            // the option is on (no change-gate) because the detection fetch would
            // otherwise double the round-trips for a rarely-changing artifact.
            var freshSegments: List<com.raulshma.jellyplay.core.model.MediaSegment>? = null
            if (options.segments && downloadPath != null) {
                setProgress(itemId, ResyncPhase.WORKING, ResyncStep.DOWNLOAD_SEGMENTS)
                playbackRepository.invalidateSegmentsCache(itemId)
                segmentsOk = writer.downloadMediaSegments(itemId, downloadPath)
                freshSegments = playbackRepository.getMediaSegments(itemId).getOrDefault(emptyList())
                steps += ResyncStepResult(itemId, ResyncStep.DOWNLOAD_SEGMENTS, success = segmentsOk)
            }

            setProgress(itemId, ResyncPhase.WORKING, ResyncStep.UPDATE_BASELINE)
            val freshBaseline = comparator.baseline(detail, freshSegments)
            // A failed sidecar fetch must not re-seed its axis: roll each failed
            // axis back to the prior baseline value (empty on first contact) so
            // the next check still flags it as changed instead of reporting
            // CURRENT for artifacts the disk never received. Successful (or
            // un-attempted) axes keep the freshly computed signature.
            val seededBaseline = freshBaseline.copy(
                subtitleSignature = if (subtitlesOk) freshBaseline.subtitleSignature
                    else baseline?.subtitleSignature ?: "",
                trickplaySignature = if (trickplayOk) freshBaseline.trickplaySignature
                    else baseline?.trickplaySignature ?: "",
                segmentsSignature = if (segmentsOk) freshBaseline.segmentsSignature
                    else baseline?.segmentsSignature ?: "",
            )
            // Effective baseline blends synced-fresh values with the retained
            // (skipped-category) values from the prior baseline. Re-running the
            // comparator against it yields an accurate update-available flag:
            // a partial sync clears the flag only for the synced categories.
            val effectiveBaseline = baseline.mergePartial(seededBaseline, options, baseline == null)
            val recheck = comparator.diff(effectiveBaseline, detail, itemId, freshSegments)
            syncBaselineDao.upsert(
                baselineEntity(
                    itemId = itemId,
                    baseline = effectiveBaseline,
                    // Media-source id/size always come from the fresh detail.
                    mediaSourceId = freshBaseline.mediaSourceId,
                    mediaSizeBytes = freshBaseline.mediaSizeBytes,
                    state = recheck.state,
                    lastSyncedAt = System.currentTimeMillis(),
                    error = false,
                )
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
        retainedSegmentsSignature: String?,
    ) {
        val state = result.state
        // First-contact (no prior baseline): record the fresh baseline as
        // CURRENT instead of flagging an update on the very first check, so
        // opening a long-dormant download doesn't prompt an immediate resync.
        val firstContact = !hadBaseline
        val freshBaseline = comparator.baseline(fresh)
        syncBaselineDao.upsert(
            baselineEntity(
                itemId = itemId,
                baseline = freshBaseline,
                state = if (firstContact) state.copy(
                    metadataChanged = false,
                    imagesChanged = false,
                    subtitlesChanged = false,
                    trickplayChanged = false,
                    segmentsChanged = false,
                    mediaFileChanged = false,
                ) else state,
                // Check never fetches segments, so carry the prior signature
                // forward (empty when never recorded) instead of clobbering it.
                segmentsSignatureOverride = retainedSegmentsSignature,
                lastSyncedAt = state.lastCheckedAt,
                error = false,
            )
        )
    }

    /**
     * Records a check failure: sets the `syncError` flag (so the badge keeps
     * surfacing it past the TTL gate instead of flickering once) while
     * preserving the last-known baseline + flags. A later successful check
     * clears the flag via [persistCheckResult].
     */
    private suspend fun recordError(itemId: String, baseline: SyncBaselineEntity) {
        syncBaselineDao.upsert(baseline.copy(syncError = 1, syncChecking = 0))
    }

    /**
     * Builds the persisted [SyncBaselineEntity] from a comparator baseline + the
     * freshly computed [OfflineSyncState]. The denormalized `syncUpdateAvailable`
     * is the OR of the five resyncable per-axis flags, so the "items with
     * updates" query and badge count stay one predicate while projection stays
     * lossless. Each per-axis flag mirrors the corresponding state boolean.
     */
    private fun baselineEntity(
        itemId: String,
        baseline: SyncBaseline,
        state: OfflineSyncState,
        lastSyncedAt: Long?,
        error: Boolean,
        mediaSourceId: String? = baseline.mediaSourceId,
        mediaSizeBytes: Long? = baseline.mediaSizeBytes,
        segmentsSignatureOverride: String? = baseline.segmentsSignature.ifEmpty { null },
    ): SyncBaselineEntity {
        val metadataChanged = if (state.metadataChanged) 1 else 0
        val imagesChanged = if (state.imagesChanged) 1 else 0
        val subtitlesChanged = if (state.subtitlesChanged) 1 else 0
        val trickplayChanged = if (state.trickplayChanged) 1 else 0
        val segmentsChanged = if (state.segmentsChanged) 1 else 0
        val updateAvailable = if (state.needsResync) 1 else 0
        return SyncBaselineEntity(
            id = itemId,
            syncedPosterTag = baseline.posterTag,
            syncedBackdropTag = baseline.backdropTag,
            syncedMetadataSignature = baseline.metadataSignature,
            syncedSubtitleSignature = baseline.subtitleSignature,
            syncedTrickplaySignature = baseline.trickplaySignature,
            syncedSegmentsSignature = segmentsSignatureOverride,
            syncedMediaSourceId = mediaSourceId,
            syncedMediaSizeBytes = mediaSizeBytes,
            lastSyncedAt = lastSyncedAt,
            syncUpdateAvailable = updateAvailable,
            syncMediaChanged = if (state.mediaFileChanged) 1 else 0,
            syncChecking = 0,
            syncError = if (error) 1 else 0,
            syncMetadataChanged = metadataChanged,
            syncImagesChanged = imagesChanged,
            syncSubtitlesChanged = subtitlesChanged,
            syncTrickplayChanged = trickplayChanged,
            syncSegmentsChanged = segmentsChanged,
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

/** Adapts a persisted baseline row to the comparator's input type. */
private fun SyncBaselineEntity.toSyncBaseline(): SyncBaseline = SyncBaseline(
    posterTag = syncedPosterTag,
    backdropTag = syncedBackdropTag,
    metadataSignature = syncedMetadataSignature ?: "",
    subtitleSignature = syncedSubtitleSignature ?: "",
    trickplaySignature = syncedTrickplaySignature ?: "",
    segmentsSignature = syncedSegmentsSignature ?: "",
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
        subtitleSignature = if (options.subtitles) fresh.subtitleSignature else subtitleSignature,
        trickplaySignature = if (options.trickplay) fresh.trickplaySignature else trickplaySignature,
        segmentsSignature = if (options.segments) fresh.segmentsSignature else segmentsSignature,
        // Media-source id/size are always recomputed from the fresh detail
        // (not user-selectable); they never retain a stale value.
        mediaSourceId = fresh.mediaSourceId,
        mediaSizeBytes = fresh.mediaSizeBytes,
    )
}

/** True when at least the metadata signature was ever recorded (vs. pre-feature rows). */
private fun SyncBaselineEntity.hasStoredBaseline(): Boolean =
    syncedMetadataSignature != null || syncedPosterTag != null || syncedBackdropTag != null
