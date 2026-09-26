package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.mapConcurrentCatching
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.ActiveDownloadCount
import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.download.SeriesEpisodeDownloads
import com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.model.maxBitrate
import com.raulshma.jellyplay.core.model.DownloadFileInventory
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File

// V3 downloads conveyor: moved verbatim from the legacy :core:data shim (same
// package/name) minus its Android-only surfaces. Ctor-level transforms only —
// method bodies are byte-identical except where a seam call replaces a direct
// platform call (documented inline):
//  - `@ApplicationContext context` dropped; its three uses became constructor
//    seams — WorkManager enqueue/cancel → [DownloadEnqueueCoordinator], the
//    notification group summary → [DownloadProgressNotifier], Coil preloading
//    → [OfflineImagePreloader] (the preloader rode with the offline-metadata
//    cluster to [OfflineDownloadWriterCore] at the D6 extraction).
//  - concrete `DownloadStorageLayout` → the [DownloadStorageLayoutContract]
//    interface (Android impl keeps its Context/StatFs logic verbatim) — the
//    contract is now a dependency of the writer core only.
//  - `mediaRepository: MediaRepository` → a deferred [MediaRepositoryAccess]
//    provider: every use sits on the series paths (downloadSeries + episode
//    series-seeding in the writer core); startDownload never touches it, so
//    desktop single-item downloads work with a throwing provider.
//  - `dagger.Lazy<DownloadDelegate>` → kotlin `Lazy<DownloadDelegate>` and
//    now (D6) a plain [DownloadDelegate]: the artifact-write half moved to
//    [OfflineDownloadWriterCore] — a standalone single over the same DAOs and
//    seams with NO back-reference to this repository — so the former
//    construction cycle `DownloadRepositoryImpl → DownloadDelegate →
//    OfflineDownloadWriter → DownloadRepositoryImpl` is gone and the `Lazy`
//    deferral with it. This class keeps the [DownloadRepository] surface
//    (which extends [OfflineDownloadWriter]) and forwards those members
//    one-to-one to the same writer-core single the delegate writes through.
//  - `android.util.Log` → the module's Log facade.
// Koin (dataJvmModule) owns construction; the legacy DataModule bridges the
// remaining Hilt injectors via koin().get().

class DownloadRepositoryImpl(
    private val downloadDao: DownloadDao,
    private val offlineMediaDao: OfflineMediaDao,
    private val playbackStateDao: PlaybackStateDao,
    private val syncBaselineDao: SyncBaselineDao,
    private val database: JellyPlayDatabase,
    private val mediaRepository: MediaRepositoryAccess,
    /**
     * The consolidated series seasons/episodes snapshot. [downloadSeries] uses
     * it in place of the former `mediaRepository.getSeasons` + per-season
     * `getEpisodes` fan-out — one load per series. Online-only path, so
     * `offline` defaults to `false`.
     */
    private val episodeCatalogue: EpisodeCatalogue,
    private val playbackRepository: PlaybackRepository,
    private val downloadsStore: DownloadsStore,
    private val storagePolicy: StoragePolicy,
    private val downloadEnqueuer: DownloadEnqueueCoordinator,
    private val progressNotifier: DownloadProgressNotifier,
    /**
     * The extracted artifact-write cluster ([OfflineDownloadWriterCore]): row
     * creation, offline metadata + baseline seeding, parent hierarchy seeding,
     * worker enqueue, and the sidecar/artifact surface — over its own DAO and
     * seam dependencies, with no reference back to this repository (D6). The
     * inherited [OfflineDownloadWriter] members (and the local manifest /
     * segments / inventory reads) forward to it one-to-one; [downloadSeries]
     * reuses its metadata + series-artwork helpers.
     */
    private val writer: OfflineDownloadWriterCore,
    /**
     * The per-item download recipe (prepare + execute + artifact bundle).
     * Resolved eagerly: the cycle it used to participate in is dissolved by
     * the writer-core extraction, so the former `Lazy` deferral is gone.
     */
    private val downloadDelegate: DownloadDelegate,
) : DownloadRepository,
    // The promoted feature-facing read seams (DownloadQueue,
    // TrackDownloadStatusWindow, ActiveDownloadCount, SeriesEpisodeDownloads)
    // — the DownloadIntake precedent: their surfaces cross commonMain
    // verbatim, so the engine implements them DIRECTLY instead of behind
    // per-read verbatim-forward adapters (the deleted JvmDownloadQueue /
    // JvmTrackDownloadStatusWindow / JvmActiveDownloadCount /
    // JvmSeriesEpisodeDownloads). The differently-named members forward to
    // this class's own repository methods one-to-one.
    DownloadQueue,
    TrackDownloadStatusWindow,
    ActiveDownloadCount,
    SeriesEpisodeDownloads {

    // Caps the number of episodes processed concurrently when queueing a series
    // download. Avoids launching 20+ parallel OkHttp calls + Coil decodes at once.
    private val downloadPermits = Semaphore(permits = 4)

    // The shared deletion choreography (see the class KDoc for the six-step
    // ordering spec) — the same collaborator OfflineRepositoryImpl's three
    // delete scopes run. This repo owns no artwork memo, so it passes no
    // evictArtworkMemo hook.
    private val deletionCore = OfflineDeletionCore(
        database = database,
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        playbackStateDao = playbackStateDao,
        syncBaselineDao = syncBaselineDao,
    )

    // Room re-runs download queries on every 2 s progress tick, and a full
    // structural `distinctUntilChanged` over up to 500 x ~25-field items is
    // always unequal while bytes move — so it never suppresses anything on
    // that path. Compare only the fields the rendered lists depend on
    // (id order, per-item downloadedBytes, status); emissions differing in
    // other fields alone (e.g. a speed update) no longer re-emit downstream.
    private fun List<DownloadItem>.rendersSameAs(other: List<DownloadItem>): Boolean {
        if (size != other.size) return false
        return zip(other).all { (o, n) ->
            o.id == n.id && o.downloadedBytes == n.downloadedBytes && o.status == n.status
        }
    }

    override fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloads().map { entities ->
            entities.map { it.toDownloadItem() }
        }.distinctUntilChanged { old, new -> old.rendersSameAs(new) }

    override fun getActiveDownloadProgress(): Flow<Map<String, DownloadProgress>> =
        downloadDao.getActiveDownloadProgress()
            .map { rows -> rows.associate { row -> row.id to row.toDownloadProgress() } }
            // The map is structurally compared, so invalidations that didn't
            // move an active row's bytes/speed (e.g. a COMPLETED flip on some
            // other row) collapse to nothing downstream.
            .distinctUntilChanged()

    override suspend fun getAllDownloadsSnapshot(): List<DownloadItem> =
        downloadDao.getAllDownloadsSnapshot().map { it.toDownloadItem() }

    override suspend fun getCompletedAudioDownloads(limit: Int, offset: Int): List<DownloadItem> =
        downloadDao.getCompletedAudioDownloads(limit, offset).map { it.toDownloadItem() }

    override fun getDownloadByMediaItemIdFlow(mediaItemId: String): Flow<DownloadItem?> =
        downloadDao.getDownloadByMediaItemIdFlow(mediaItemId).map { it?.toDownloadItem() }

    override fun getDownloadsByMediaItemIdsFlow(mediaItemIds: List<String>): Flow<List<DownloadItem>> =
        downloadDao.getDownloadsByMediaItemIdsFlow(mediaItemIds).map { entities ->
            entities.map { it.toDownloadItem() }
        }.distinctUntilChanged { old, new -> old.rendersSameAs(new) }

    override fun getActiveDownloadCount(): Flow<Int> =
        downloadDao.getActiveDownloadCount()

    // ── promoted read seams (see the supertype list above) ────────────────
    // One-to-one forwards onto the repository methods; dataJvmModule binds
    // each interface over this single. TrackDownloadStatusWindow.downloadsFor
    // deliberately IS the single getDownloadsByMediaItemIdsFlow IN-query —
    // not the N combined per-id flows the deleted JvmTrackDownloadStatusWindow
    // adapter re-expressed (reverted divergence: same rows, one narrow query).

    override val isSupported: Boolean = true

    override fun allDownloads(): Flow<List<DownloadItem>> = getAllDownloads()

    override fun activeDownloadProgress(): Flow<Map<String, DownloadProgress>> =
        getActiveDownloadProgress()

    override suspend fun allDownloadsSnapshot(): List<DownloadItem> = getAllDownloadsSnapshot()

    override suspend fun pause(id: String): Result<Unit> = pauseDownload(id)

    override suspend fun resume(id: String): Result<Unit> = resumeDownload(id)

    override fun enqueue(id: String) = enqueueDownload(id)

    override suspend fun cancel(id: String): Result<Unit> = cancelDownload(id)

    override suspend fun retry(id: String): Result<Unit> = retryDownload(id)

    override suspend fun delete(id: String): Result<Unit> = deleteDownload(id)

    override suspend fun setPriority(id: String, priority: Int): Result<Unit> =
        setDownloadPriority(id, priority)

    override fun downloadsFor(ids: List<String>): Flow<List<DownloadItem>> =
        getDownloadsByMediaItemIdsFlow(ids)

    override suspend fun remove(downloadId: String) {
        // Result ignored — the same fire-and-forget contract the deleted
        // JvmTrackDownloadStatusWindow adapter carried (the hosts' remove
        // paths have no error surface on a failed delete).
        deleteDownload(downloadId)
    }

    override fun activeDownloadCount(): Flow<Int> = getActiveDownloadCount()

    override suspend fun downloadedEpisodeIds(seriesId: String): Set<String> =
        getDownloadedEpisodeIdsForSeries(seriesId)

    override fun observeCompletedDownloadedIds(): Flow<Set<String>> =
        downloadDao.getCompletedDownloadedItemIds().map(List<String>::toSet).distinctUntilChanged()

    override suspend fun getDownloadByMediaItemId(mediaItemId: String): DownloadItem? =
        downloadDao.getDownloadByMediaItemId(mediaItemId)?.toDownloadItem()

    override suspend fun getDownloadName(id: String): String? =
        downloadDao.getDownloadById(id)?.name

    /**
     * Creates (or dedupes to) the PENDING downloads row for [request] — the
     * write body lives in [OfflineDownloadWriterCore.startDownload]; the
     * repository only carries the inherited [OfflineDownloadWriter] surface.
     */
    override suspend fun startDownload(request: DownloadStartRequest): Result<DownloadItem> =
        writer.startDownload(request)

    override suspend fun cancelDownload(id: String): Result<Unit> = runCatchingRethrowingCancellation {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatchingRethrowingCancellation
        // Cancel any in-flight background work first so the executing transfer
        // stops promptly and stops polling DB status. Without this, the
        // transfer keeps running until its next 2-second poll tick discovers
        // the row is gone.
        downloadEnqueuer.cancelWork(id)
        cleanupDownloadFiles(entity)
        refreshDownloadSummary()
    }

    override suspend fun pauseDownload(id: String): Result<Unit> = runCatchingRethrowingCancellation {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatchingRethrowingCancellation
        if (DownloadStates.isActive(entity.status)) {
            // Cancel the in-flight transfer first so the executing engine stops
            // promptly. Without this it keeps polling DB status until its next
            // tick discovers the row is PAUSED.
            downloadEnqueuer.cancelWork(id)
            // Status + user-initiated reason in one UPDATE — mark as
            // user-initiated so the reconnect auto-resume leaves it alone; only
            // NETWORK interruptions auto-resume.
            downloadDao.updateProgressWithPausedReason(
                id, entity.downloadedBytes, DownloadStatus.PAUSED.name, DownloadPauseReason.USER.persistedValue,
            )
        }
        refreshDownloadSummary()
    }

    override suspend fun resumeDownload(id: String): Result<Unit> = runCatchingRethrowingCancellation {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatchingRethrowingCancellation
        if (DownloadStates.isPausedOrFailed(entity.status)) {
            // Manual resume/retry clears both the pause reason and the
            // auto-retry budget — the user has taken ownership of this row.
            downloadDao.markPendingForManualResume(id, entity.downloadedBytes)
        }
        refreshDownloadSummary()
    }

    override suspend fun deleteDownload(id: String): Result<Unit> = runCatchingRethrowingCancellation {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatchingRethrowingCancellation
        downloadEnqueuer.cancelWork(id)
        cleanupDownloadFiles(entity)
        refreshDownloadSummary()
    }

    /**
     * Keeps the download summary surface in sync when this repository changes a
     * row's state (pause/cancel/resume/retry/delete) — e.g. in-app controls
     * that never cross a notification action receiver. Best-effort: a Room/
     * notification hiccup must not fail the state change. The platform surface
     * itself lives behind the [DownloadProgressNotifier] seam.
     */
    private suspend fun refreshDownloadSummary() {
        runCatchingRethrowingCancellation {
            progressNotifier.refreshSummary(downloadDao.getInFlightDownloadCount())
        }
    }

    override suspend fun retryDownload(id: String): Result<Unit> = runCatchingRethrowingCancellation {
        // A manual retry starts fresh — reset the bytes, clear the auto-retry
        // budget and reason, all in one UPDATE.
        downloadDao.markPendingForManualResume(id, 0L)
        refreshDownloadSummary()
    }

    override suspend fun resumeInterruptedDownloads() {
        val candidates = try {
            downloadDao.getInterruptedResumeRows(
                listOf(DownloadStatus.PAUSED.name, DownloadStatus.FAILED.name)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enumerate interrupted downloads for resume", e)
            return
        }
        // Eligible rows resume in two batched UPDATEs (one per byte-offset rule,
        // see [DownloadStates.keepsResumeBytes]) instead of a per-row loop.
        val keepBytesIds = mutableListOf<String>()
        val fromZeroIds = mutableListOf<String>()
        for (row in candidates) {
            // A user-paused download stays paused until the user resumes it.
            if (DownloadStates.isUserPaused(row.status, row.pausedReason)) continue
            // Exhausted the auto-retry budget — leave it FAILED for a manual
            // retry rather than spinning on every reconnect.
            if (DownloadStates.isExhausted(row.retryCount)) continue
            if (DownloadStates.keepsResumeBytes(row.status)) keepBytesIds += row.id else fromZeroIds += row.id
        }
        // Rows are only enqueued once their status flip succeeded — a failed
        // batch UPDATE must not leave FAILED/PAUSED rows enqueued. The two
        // batches are isolated: one failing must not abort the other (the old
        // per-row resume loop let every independent row proceed).
        suspend fun resumeBatch(
            ids: List<String>,
            label: String,
            update: suspend (List<String>) -> Unit,
        ): List<String> = try {
            update(ids)
            ids
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resume $label", e)
            emptyList()
        }

        // Paused rows keep their contiguous byte prefix (untouched here);
        // FAILED partials were deleted by cleanupStuckDownloads (multi-
        // connection scattered writes can't be appended to) and resume
        // from 0. Status + cleared pause reason in one UPDATE each; the
        // retry budget is deliberately preserved (the eligibility checks
        // above already dead-lettered exhausted rows).
        val resumedIds = buildList {
            if (keepBytesIds.isNotEmpty()) addAll(
                resumeBatch(keepBytesIds, "paused downloads") {
                    downloadDao.updateStatusWithPausedReasonForIds(it, DownloadStatus.PENDING.name, null)
                },
            )
            if (fromZeroIds.isNotEmpty()) addAll(
                resumeBatch(fromZeroIds, "failed downloads") {
                    downloadDao.markResumedFromZeroForIds(it, DownloadStatus.PENDING.name, null)
                },
            )
        }
        for (id in resumedIds) {
            try {
                enqueueDownload(id)
            } catch (e: Exception) {
                // One bad enqueue must not abort the whole batch — the other
                // interrupted downloads still resume this pass.
                Log.w(TAG, "Failed to resume interrupted download $id", e)
            }
        }
        refreshDownloadSummary()
    }

    override suspend fun getTotalDownloadedBytes(): Long =
        downloadDao.getTotalDownloadedBytes()

    /**
     * Persists the lightweight [MediaItem] form for an offline row (no chapters,
     * cast, or other [MediaDetail]-only fields). Item-only downloads therefore
     * remain chapter-less by design — callers that have a full [MediaDetail]
     * (download worker, resync) must use [saveOfflineMediaDetail] so
     * `chaptersJson` and other rich blobs are encoded. The write body lives in
     * [OfflineDownloadWriterCore.saveOfflineMediaItem].
     */
    override suspend fun saveOfflineMediaItem(item: MediaItem, imageUrl: String?, backdropUrl: String?, downloadPath: String?) =
        writer.saveOfflineMediaItem(item, imageUrl, backdropUrl, downloadPath)

    /**
     * Persist full metadata (overview, genres, ratings, cast, studios, …) for
     * a downloaded item from a [MediaDetail], seeding the parent series/season
     * rows for episodes — the write body lives in
     * [OfflineDownloadWriterCore.saveOfflineMediaDetail].
     */
    override suspend fun saveOfflineMediaDetail(detail: MediaDetail, imageUrl: String?, backdropUrl: String?) =
        writer.saveOfflineMediaDetail(detail, imageUrl, backdropUrl)

    override suspend fun getDownloadedEpisodeIdsForSeries(seriesId: String): Set<String> =
        // Room suspend functions already switch to the Room query executor, so
        // the wrapping `withContext(Dispatchers.IO)` was an unnecessary thread-
        // pool handoff. (The withContext(Dispatchers.IO) calls that wrap actual
        // File/FileOutputStream I/O elsewhere in this file are correct and stay.)
        downloadDao.getDownloadsForSeries(seriesId)
            .mapNotNull { it.mediaItemId }
            .toSet()

    override suspend fun getDownloadedEpisodeIdsBySeries(): Map<String, Set<String>> =
        // Single 2-column query over the whole table; grouped in memory into a
        // per-series index. Preferred over calling getDownloadedEpisodeIdsForSeries
        // per series from the periodic auto-download worker, which would issue N
        // full-row (23-col) queries when only mediaItemId is consumed.
        downloadDao.getDownloadedEpisodeIdsBySeries()
            .groupBy({ it.seriesId }, { it.mediaItemId })
            .mapValues { (_, ids) -> ids.toSet() }

    override suspend fun getDownloadedSeriesIds(): List<String> =
        downloadDao.getDownloadedSeriesIds()

    override fun observeDownloadedSeriesIds(): Flow<Set<String>> =
        downloadDao.observeDownloadedSeriesIds().map(List<String>::toSet).distinctUntilChanged()

    override fun observeDownloadedIdsIncludingSeries(): Flow<Set<String>> =
        combine(
            observeCompletedDownloadedIds(),
            observeDownloadedSeriesIds(),
        ) { itemIds, seriesIds -> itemIds + seriesIds }
            .distinctUntilChanged()

    override suspend fun downloadSeries(
        seriesId: String,
        episodeIds: Map<String, List<String>>?,
    ): Result<List<String>> = runCatchingRethrowingCancellation {
        withContext(Dispatchers.IO) {
            val prefs = downloadsStore.downloads.first()
            // The storage cap only needs to be evaluated once for the whole
            // enqueue batch: no bytes are actually downloaded here (the
            // DownloadWorker runs later), so every per-episode SUM(downloadedBytes)
            // would return an identical value. StoragePolicy.enforce reads the
            // current bytes once (via its injected provider) and compares
            // against both ceilings. The returned currentBytes is handed to
            // each per-episode start as a precomputed hint so the cap check
            // inside startDownload skips its own aggregate query.
            val batchCurrentBytes = storagePolicy.enforce()

            // Detail fetch and episode catalogue are independent round-trips —
            // start both before awaiting either so series download setup pays
            // max(detail, catalogue) instead of detail + catalogue.
            val detailDeferred = async { mediaRepository().getMediaDetail(seriesId).getOrThrow() }
            val snapshotDeferred = async { episodeCatalogue.loadSeriesEpisodes(seriesId) }

            val detail = detailDeferred.await()
            val imageUrl = playbackRepository.getImageUrl(seriesId, maxWidth = 300)
            val backdropUrl = playbackRepository.getBackdropUrl(seriesId, maxWidth = 1280)

            // Persist full series metadata (cast, studios, ratings, …) from the
            // fetched detail so the offline series screen is as rich as online.
            writer.saveOfflineMetadataForDetail(detail, imageUrl, backdropUrl)

            // One consolidated seasons + episodes load (single round-trip via
            // the catalogue) replaces the former getSeasons + per-season
            // getEpisodes fan-out. On failure, fall back to an empty snapshot so
            // the series metadata is still persisted and the run doesn't abort.
            val snapshot = snapshotDeferred.await()
                .getOrElse { EpisodeCatalogueSnapshot(seriesId, emptyList(), emptyMap(), emptySet(), emptyList(), 0L) }
            val seasons = snapshot.seasons
            val targetSeasons = if (episodeIds != null) {
                seasons.filter { it.id in episodeIds.keys }
            } else {
                seasons
            }

            // Per-episode artifact bundle (local poster/backdrop, trickplay,
            // external subtitles, intro/outro segments, rich offline metadata)
            // is delegated to DownloadDelegate — the same code path the single-
            // item intake uses (DownloadIntakeImpl.start). This is deliberate:
            // the series path must not re-implement the bundle recipe and risk
            // silently dropping an artifact (see DownloadIntake kdoc). Only the
            // series/season metadata + budget guard + concurrency permit live
            // here; everything else is DownloadDelegate.executeDownload.
            val delegate = downloadDelegate
            val qualityMaxBitrate = qualityToMaxBitrate(prefs.downloadQuality)
            val budgetHint = if (batchCurrentBytes >= 0) batchCurrentBytes else null
            val downloadIds = mutableListOf<String>()

            for (season in targetSeasons) {
                writer.saveOfflineMetadataForItem(season, null, null)

                val allEpisodes = snapshot.seasonEpisodes(season.id)
                val selectedEpisodeIds = episodeIds?.get(season.id)?.toSet()
                val episodes = if (selectedEpisodeIds != null) {
                    allEpisodes.filter { it.id in selectedEpisodeIds }
                } else {
                    allEpisodes
                }

                // The catching map drops the FAILED episodes (the transform
                // logs each one before yielding null); the trailing
                // filterNotNull narrows the nullable-completion type the
                // surviving list still carries.
                val episodeResults = downloadPermits.mapConcurrentCatching(episodes) { episode ->
                    try {
                        val episodeDetail = mediaRepository().getMediaDetail(episode.id).getOrNull()
                        // Single per-episode recipe shared with DownloadIntake.start
                        // via DownloadDelegate.startOne — no inline prepare/execute to
                        // drift out of sync. Series downloads bundle every external
                        // subtitle (null selection); per-item picker selection lives
                        // only on the single-item DownloadIntake.start path.
                        val result = episodeDetail?.let {
                            delegate.startOne(it, qualityMaxBitrate, null, budgetHint)
                        }
                        result?.downloadItem?.let { it.id to it.downloadPath }
                    } catch (ce: CancellationException) {
                        // Preserve structured concurrency: if the parent
                        // scope (e.g. user navigated away) is cancelled,
                        // the cancellation must propagate instead of
                        // being silently turned into a dropped result.
                        throw ce
                    } catch (e: Exception) {
                        // Surface the per-episode failure so the user
                        // has a clue why an episode is missing from the
                        // queue. Future: aggregate a failure count and
                        // expose it through the Result/uiState.
                        Log.w(TAG, "Failed to queue episode ${episode.id} (${episode.name})", e)
                        null
                    }
                }

                val enqueued = episodeResults.filterNotNull()
                enqueued.map { it.first }.forEach { downloadIds.add(it) }

                // The series row was seeded above with REMOTE poster/backdrop
                // URLs (so the per-episode saves don't each re-download the
                // series artwork). Persist the artwork as local files now, next
                // to the first enqueued episode, and re-upsert the series row
                // with those paths — otherwise the offline series screen's hero
                // and poster depend on Coil's cache and degrade to blurHash
                // whenever the preload raced or was evicted.
                val firstEpisodeDir = enqueued
                    .asSequence()
                    .mapNotNull { it.second?.takeIf { p -> p.isNotBlank() } }
                    .mapNotNull { File(it).parentFile }
                    .firstOrNull()
                if (firstEpisodeDir != null) {
                    val seriesArtwork = writer.downloadSeriesArtwork(seriesId, firstEpisodeDir)
                    if (seriesArtwork.posterPath != null || seriesArtwork.backdropPath != null) {
                        // Re-persist without re-preloading cast images: the
                        // preloads already ran for the seed above. This only
                        // swaps the artwork columns to the local files.
                        offlineMediaDao.upsert(
                            detail.toOfflineMediaEntity(
                                seriesArtwork.posterPath ?: imageUrl,
                                seriesArtwork.backdropPath ?: backdropUrl,
                            )
                        )
                    }
                }
            }

            downloadIds
        }
    }

    // ── Sidecar/artifact + write surface (forwards one-to-one) ─────────────
    // The inherited OfflineDownloadWriter members and the local reads forward
    // to [writer] — the extracted OfflineDownloadWriterCore single the
    // DownloadDelegate also writes through (bodies moved verbatim there;
    // the behavioral contracts are pinned by
    // DownloadRepositoryImplSubtitlesTest (androidHostTest),
    // OfflineDownloadWriterCoreTest and DownloadSidecarCoreTest (jvmTest)).

    override suspend fun downloadTrickplayData(
        itemId: String,
        trickplayInfo: TrickplayInfo,
        downloadPath: String,
    ): Boolean = writer.downloadTrickplayData(itemId, trickplayInfo, downloadPath)

    override suspend fun downloadExternalSubtitles(
        itemId: String,
        mediaSourceId: String,
        mediaStreams: List<MediaStream>,
        downloadPath: String,
    ): Boolean = writer.downloadExternalSubtitles(itemId, mediaSourceId, mediaStreams, downloadPath)

    override suspend fun markSubtitlesPending(itemId: String) {
        writer.markSubtitlesPending(itemId)
    }

    override suspend fun downloadMediaSegments(itemId: String, downloadPath: String): Boolean =
        writer.downloadMediaSegments(itemId, downloadPath)

    override suspend fun downloadOfflineImage(
        itemId: String,
        imageType: String,
        maxWidth: Int,
        parentDir: File,
        fileName: String,
    ): String? = writer.downloadOfflineImage(itemId, imageType, maxWidth, parentDir, fileName)

    override suspend fun loadLocalSubtitleManifest(
        downloadPath: String,
        itemId: String?,
    ): OfflineSubtitleManifest? = writer.loadLocalSubtitleManifest(downloadPath, itemId)

    override suspend fun loadLocalSegments(itemId: String): List<MediaSegment>? =
        writer.loadLocalSegments(itemId)

    override suspend fun getDownloadFileInventory(itemId: String): DownloadFileInventory =
        writer.getDownloadFileInventory(itemId)

    override fun enqueueDownload(downloadId: String) = writer.enqueueDownload(downloadId)

    override suspend fun setDownloadPriority(id: String, priority: Int): Result<Unit> = runCatchingRethrowingCancellation {
        downloadDao.updatePriority(id, priority)
    }

    // The entity ⟷ domain mappers this class used to carry as private members
    // (MediaItem/MediaDetail → OfflineMediaEntity, MediaItem → PlaybackStateEntity,
    // DownloadEntity → DownloadItem, DownloadProgressRow → DownloadProgress) live
    // in OfflineMediaMappers.kt — the single owner of the offline/download column
    // semantics, shared with OfflineRepositoryImpl's read side.

    /**
     * Declared delta vs the former inline body, adopted from the majority
     * choreography: the orphan prune now runs as the core's post-transaction
     * step (the old body pruned inside the same transaction — the difference
     * is observable only as transiently un-pruned orphan rows between the two
     * transactions), and the cast-image prune the old body skipped now runs —
     * its reference scan only deletes files no surviving offline row
     * references, so the former skip leaked cast images rather than
     * protecting them.
     */
    private suspend fun cleanupDownloadFiles(entity: DownloadEntity) {
        deletionCore.delete(
            downloads = listOf(entity),
            deleteMetadataRows = {
                offlineMediaDao.deleteById(entity.mediaItemId)
                playbackStateDao.deleteById(entity.mediaItemId)
                syncBaselineDao.deleteById(entity.mediaItemId)
            },
        )
    }

    /**
     * Maps a [DownloadQuality] preference to the max bitrate (bits/s) passed
     * to the stream-URL builder. `null` means "no cap" → original quality.
     * Values are aligned with [AdaptiveBitrateManager] streaming presets so
     * the downloaded file matches what the user would see streamed at the
     * equivalent quality.
     */
    private fun qualityToMaxBitrate(quality: DownloadQuality): Int? = quality.maxBitrate

    companion object {
        private const val TAG = "DownloadRepository"

        // Exponential backoff base delay applied to every DownloadWorker
        // request so a flaky server is not hammered by concurrent retries.
        // Mirrored by DownloadRecoveryInitializer so cold-start re-enqueues
        // back off identically. WorkManager caps each retry delay at 5h.
        const val DOWNLOAD_BACKOFF_DELAY_MS = 30_000L
    }
}
