package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.database.entity.PlaybackStateEntity
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.data.worker.DownloadNotificationHelper
import com.raulshma.jellyplay.core.data.worker.awaitResponse
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.maxBitrate
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.DownloadFileEntry
import com.raulshma.jellyplay.core.model.DownloadFileInventory
import com.raulshma.jellyplay.core.model.DownloadedFileCategory
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.isImageSubtitleCodec
import com.raulshma.jellyplay.core.model.isVobsubFamilyCodec
import com.raulshma.jellyplay.core.model.subtitleCompanionFileName
import com.raulshma.jellyplay.core.model.subtitleSidecarExtension
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

    @Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val offlineMediaDao: OfflineMediaDao,
    private val playbackStateDao: PlaybackStateDao,
    private val syncBaselineDao: SyncBaselineDao,
    private val database: JellyPlayDatabase,
    private val mediaRepository: MediaRepository,
    /**
     * The consolidated series seasons/episodes snapshot. [downloadSeries] uses
     * it in place of the former `mediaRepository.getSeasons` + per-season
     * `getEpisodes` fan-out — one load per series. Online-only path, so
     * `offline` defaults to `false`.
     */
    private val episodeCatalogue: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue,
    private val playbackRepository: PlaybackRepository,
    private val httpClient: OkHttpClient,
    private val downloadsStore: DownloadsStore,
    private val json: Json,
    /**
     * Lazy to break the Hilt construction cycle: [downloadSeries] (below)
     * delegates the per-episode artifact bundle to [DownloadDelegate], and
     * [DownloadDelegate] now depends on [OfflineDownloadWriter] — which this
     * class implements. That's still a cycle at graph-construction time
     * (`DownloadRepositoryImpl → DownloadDelegate → OfflineDownloadWriter →
     * DownloadRepositoryImpl`), so `dagger.Lazy` defers resolution until first
     * use.
     *
     * What changed vs the old NOTE: the delegate no longer depends on the full
     * 25-method [DownloadRepository] interface — it was narrowed to the
     * 8-method [OfflineDownloadWriter] write surface. The *coupling* disease
     * the old comment named is fixed; `Lazy` here is purely the structural
     * construction-cycle breaker it should always have been, not a paper-over
     * for a god-interface dependency.
     */
    private val downloadDelegate: dagger.Lazy<com.raulshma.jellyplay.core.data.util.DownloadDelegate>,
    private val storagePolicy: StoragePolicy,
    private val downloadEnqueuer: DownloadEnqueuer,
    private val storageLayout: DownloadStorageLayout,
    private val syncComparator: com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator,
) : DownloadRepository {

    // Caps the number of episodes processed concurrently when queueing a series
    // download. Avoids launching 20+ parallel OkHttp calls + Coil decodes at once.
    private val downloadPermits = Semaphore(permits = 4)

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

    override fun observeCompletedDownloadedIds(): Flow<Set<String>> =
        downloadDao.getCompletedDownloadedItemIds().map(List<String>::toSet).distinctUntilChanged()

    override suspend fun getDownloadByMediaItemId(mediaItemId: String): DownloadItem? =
        downloadDao.getDownloadByMediaItemId(mediaItemId)?.toDownloadItem()

    override suspend fun getDownloadName(id: String): String? =
        downloadDao.getDownloadById(id)?.name

    override suspend fun startDownload(
        mediaItemId: String,
        name: String,
        mediaType: String,
        mediaSourceId: String?,
        downloadUrl: String,
        imageUrl: String?,
        imageBlurHash: String?,
        seriesId: String?,
        seasonId: String?,
        seriesName: String?,
        seasonName: String?,
        episodeNumber: Int?,
        seasonNumber: Int?,
        container: String?,
        precomputedCurrentBytes: Long?,
    ): Result<DownloadItem> = startDownloadInternal(
        mediaItemId, name, mediaType, mediaSourceId, downloadUrl,
        imageUrl, imageBlurHash, seriesId, seasonId, seriesName, seasonName,
        episodeNumber, seasonNumber, container, precomputedCurrentBytes,
    )

    private suspend fun startDownloadInternal(
        mediaItemId: String,
        name: String,
        mediaType: String,
        mediaSourceId: String?,
        downloadUrl: String,
        imageUrl: String?,
        imageBlurHash: String?,
        seriesId: String?,
        seasonId: String?,
        seriesName: String?,
        seasonName: String?,
        episodeNumber: Int?,
        seasonNumber: Int?,
        container: String?,
        precomputedCurrentBytes: Long?,
    ): Result<DownloadItem> = runCatching {
        val existing = downloadDao.getDownloadByMediaItemId(mediaItemId)
        if (existing != null) {
            val isCompleted = existing.status == DownloadStatus.COMPLETED.name
            val fileExists = existing.downloadPath.isNotBlank() && java.io.File(existing.downloadPath).exists()
            if (isCompleted && fileExists) {
                return@runCatching existing.toDownloadItem()
            }
            if (existing.status != DownloadStatus.FAILED.name && existing.status != DownloadStatus.CANCELLED.name && !isCompleted) {
                return@runCatching existing.toDownloadItem()
            }
            if (existing.downloadPath.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    File(existing.downloadPath).let { f -> if (f.exists()) f.delete() }
                    DownloadArtifacts.cleanup(File(existing.downloadPath).parentFile, existing.mediaItemId)
                }
            }
            downloadDao.deleteDownloadById(existing.id)
        }

        val prefs = downloadsStore.downloads.first()
        // Storage cap (MB + GB): single owner is StoragePolicy. Previously
        // duplicated here and in downloadSeries; the two could drift.
        storagePolicy.enforce(precomputedCurrentBytes = precomputedCurrentBytes)

        // Path-layout policy (internal vs external dir, filename sanitize,
        // container extension, free-space floor) lives in DownloadStorageLayout
        // — previously inlined ~40 LOC in this method, unreachable from any
        // other call site and untestable without a full repo construction.
        val id = UUID.randomUUID().toString()
        val resolved = storageLayout.resolve(
            mediaType = mediaType,
            storageLocationPref = prefs.downloadStorageLocation,
            name = name,
            idHint = id.take(8),
            container = container,
        )
        val filePath = resolved.filePath

        val entity = DownloadEntity(
            id = id,
            mediaItemId = mediaItemId,
            name = name,
            mediaType = mediaType,
            downloadPath = filePath,
            downloadUrl = downloadUrl,
            totalSizeBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.PENDING.name,
            mediaSourceId = mediaSourceId,
            imageUrl = imageUrl,
            imageBlurHash = imageBlurHash,
            seriesId = seriesId,
            seasonId = seasonId,
            seriesName = seriesName,
            seasonName = seasonName,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        container = container,
    )
    downloadDao.insertDownload(entity)
    entity.toDownloadItem()
}

    override suspend fun cancelDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        // Cancel any in-flight WorkManager job first so the foreground service stops promptly
        // and the worker stops polling DB status. Without this, the worker keeps running until
        // its next 2-second poll tick discovers the row is gone.
        cancelWorkForDownload(id)
        cleanupDownloadFiles(entity)
        refreshDownloadSummary()
    }

    override suspend fun pauseDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        if (DownloadStates.isActive(entity.status)) {
            // Cancel the in-flight worker first so the foreground service stops
            // promptly. Without this the worker keeps polling DB status until its
            // next tick discovers the row is PAUSED.
            cancelWorkForDownload(id)
            // Status + user-initiated reason in one UPDATE — mark as
            // user-initiated so the reconnect auto-resume leaves it alone; only
            // NETWORK interruptions auto-resume.
            downloadDao.updateProgressWithPausedReason(
                id, entity.downloadedBytes, DownloadStatus.PAUSED.name, DownloadPauseReason.USER.persistedValue,
            )
        }
        refreshDownloadSummary()
    }

    override suspend fun resumeDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        if (DownloadStates.isPausedOrFailed(entity.status)) {
            // Manual resume/retry clears both the pause reason and the
            // auto-retry budget — the user has taken ownership of this row.
            downloadDao.markPendingForManualResume(id, entity.downloadedBytes)
        }
        refreshDownloadSummary()
    }

    override suspend fun deleteDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        cancelWorkForDownload(id)
        cleanupDownloadFiles(entity)
        refreshDownloadSummary()
    }

    /**
     * Cancels the unique WorkManager work associated with [downloadId], if any. Safe to call
     * even when no work is registered — WorkManager no-ops in that case.
     */
    private fun cancelWorkForDownload(downloadId: String) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.workName(downloadId))
        } catch (e: Exception) {
            // WorkManager may not be initialised in some instrumented-test or fresh-install
            // edge cases. Log and continue — file cleanup is still valuable on its own.
            Log.w(TAG, "Failed to cancel WorkManager work for download $downloadId", e)
        }
    }

    /**
     * Keeps the download notification group summary in sync when this repository
     * changes a row's state (pause/cancel/resume/retry/delete) — e.g. in-app
     * controls that never cross the notification action receiver. Best-effort:
     * a Room/notification hiccup must not fail the state change.
     */
    private suspend fun refreshDownloadSummary() {
        runCatching {
            DownloadNotificationHelper.refreshSummary(context, downloadDao.getInFlightDownloadCount())
        }
    }

    override suspend fun retryDownload(id: String): Result<Unit> = runCatching {
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
        for (row in candidates) {
            try {
                // A user-paused download stays paused until the user resumes it.
                if (DownloadStates.isUserPaused(row.status, row.pausedReason)) continue
                // Exhausted the auto-retry budget — leave it FAILED for a manual
                // retry rather than spinning on every reconnect.
                if (DownloadStates.isExhausted(row.retryCount)) continue
                // A FAILED partial was deleted by cleanupStuckDownloads (multi-
                // connection scattered writes can't be appended to), so resume
                // FAILED rows from 0. A NETWORK-paused single-connection row has
                // a contiguous prefix, so preserve its byte offset. The rule
                // lives in [DownloadStates.resumeByteOffset] — the single home
                // shared with the recovery initializer and the multi-connection
                // strategy.
                val startBytes = DownloadStates.resumeByteOffset(row.status, row.downloadedBytes)
                // Status + cleared pause reason in one UPDATE; the retry budget
                // is deliberately preserved (the eligibility check above already
                // dead-lettered exhausted rows).
                downloadDao.updateProgressWithPausedReason(
                    row.id, startBytes, DownloadStatus.PENDING.name, null,
                )
                enqueueDownload(row.id)
            } catch (e: Exception) {
                // One bad row/enqueue must not abort the whole batch — the other
                // interrupted downloads still resume this pass.
                Log.w(TAG, "Failed to resume interrupted download ${row.id}", e)
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
     * `chaptersJson` and other rich blobs are encoded.
     */
    override suspend fun saveOfflineMediaItem(item: MediaItem, imageUrl: String?, backdropUrl: String?, downloadPath: String?) {
        saveOfflineMetadataForItem(item, imageUrl, backdropUrl)
        seedEpisodeParents(item, artworkDir = downloadPath?.let { File(it).parentFile })
    }

    /**
     * Seeds the parent series/season rows for an episode download so a lone
     * episode still has its hierarchy. Deliberately does NOT touch the episode
     * row itself: callers that already persisted the rich [MediaDetail] entity
     * must not have it REPLACE-wiped by a bare-item re-upsert (which nulls
     * peopleJson/providerIdsJson/externalUrlsJson/chaptersJson).
     *
     * [artworkDir] is the directory series artwork is pre-downloaded into;
     * pass null when no media directory exists yet (artwork then falls back
     * to remote URLs).
     */
    private suspend fun seedEpisodeParents(item: MediaItem, artworkDir: File?) {
        if (item.mediaType != MediaType.EPISODE) return
        val seriesId = item.seriesId
        val seasonId = item.seasonId

        if (seriesId != null && offlineMediaDao.getById(seriesId) == null) {
            val seriesDetail = mediaRepository.getMediaDetail(seriesId).getOrNull()
            if (seriesDetail != null) {
                val localSeriesPoster = artworkDir?.let {
                    downloadImageToDisk(seriesId, "Primary", 300, it, "${seriesId}_poster.jpg")
                }
                val localSeriesBackdrop = artworkDir?.let {
                    downloadImageToDisk(seriesId, "Backdrop", 1280, it, "${seriesId}_backdrop.jpg")
                }
                val seriesImageUrl = localSeriesPoster
                    ?: playbackRepository.getImageUrl(seriesId, maxWidth = 300)
                val seriesBackdropUrl = localSeriesBackdrop
                    ?: playbackRepository.getBackdropUrl(seriesId, maxWidth = 1280)
                saveOfflineMetadataForItem(seriesDetail.item, seriesImageUrl, seriesBackdropUrl)
            } else {
                offlineMediaDao.upsert(
                    OfflineMediaEntity(
                        id = seriesId,
                        name = item.seriesName ?: "Unknown Series",
                        mediaType = MediaType.SERIES.name,
                    )
                )
            }
        }

        if (seasonId != null && offlineMediaDao.getById(seasonId) == null) {
            offlineMediaDao.upsert(
                OfflineMediaEntity(
                    id = seasonId,
                    name = item.seasonName ?: "Season ${item.seasonNumber}",
                    mediaType = MediaType.SEASON.name,
                    seriesId = seriesId,
                    seasonNumber = item.seasonNumber,
                )
            )
        }
    }

    override suspend fun saveOfflineMediaDetail(detail: MediaDetail, imageUrl: String?, backdropUrl: String?) {
        saveOfflineMetadataForDetail(detail, imageUrl, backdropUrl)

        // For episodes, seed the series/season rows so a lone episode download
        // still has its parent rows. Routes through seedEpisodeParents — NOT
        // saveOfflineMediaItem — so the just-persisted rich entity (cast,
        // providers, urls, chapters) is not wiped by a bare-item re-upsert.
        // No artworkDir: this call historically had none, so series artwork
        // keeps falling back to remote URLs here.
        seedEpisodeParents(detail.item, artworkDir = null)
    }

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
    ): Result<List<String>> = runCatching {
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
            val detailDeferred = async { mediaRepository.getMediaDetail(seriesId).getOrThrow() }
            val snapshotDeferred = async { episodeCatalogue.loadSeriesEpisodes(seriesId) }

            val detail = detailDeferred.await()
            val imageUrl = playbackRepository.getImageUrl(seriesId, maxWidth = 300)
            val backdropUrl = playbackRepository.getBackdropUrl(seriesId, maxWidth = 1280)

            // Persist full series metadata (cast, studios, ratings, …) from the
            // fetched detail so the offline series screen is as rich as online.
            saveOfflineMetadataForDetail(detail, imageUrl, backdropUrl)

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
            val delegate = downloadDelegate.get()
            val qualityMaxBitrate = qualityToMaxBitrate(prefs.downloadQuality)
            val budgetHint = if (batchCurrentBytes >= 0) batchCurrentBytes else null
            val downloadIds = mutableListOf<String>()

            for (season in targetSeasons) {
                saveOfflineMetadataForItem(season, null, null)

                val allEpisodes = snapshot.seasonEpisodes(season.id)
                val selectedEpisodeIds = episodeIds?.get(season.id)?.toSet()
                val episodes = if (selectedEpisodeIds != null) {
                    allEpisodes.filter { it.id in selectedEpisodeIds }
                } else {
                    allEpisodes
                }

                val episodeResults = coroutineScope {
                    episodes.map { episode ->
                        async {
                            downloadPermits.withPermit {
                                try {
                                    val episodeDetail = mediaRepository.getMediaDetail(episode.id).getOrNull()
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
                                    // being silently turned into a null result.
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
                        }
                    }.awaitAll()
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
                    val localSeriesPoster = downloadImageToDisk(
                        seriesId, "Primary", 300, firstEpisodeDir,
                        DownloadArtifacts.posterFile(seriesId),
                    )
                    val localSeriesBackdrop = downloadImageToDisk(
                        seriesId, "Backdrop", 1280, firstEpisodeDir,
                        DownloadArtifacts.backdropFile(seriesId),
                    )
                    if (localSeriesPoster != null || localSeriesBackdrop != null) {
                        // Re-persist without re-preloading cast images: the
                        // preloads already ran for the seed above. This only
                        // swaps the artwork columns to the local files.
                        offlineMediaDao.upsert(
                            detail.toOfflineMediaEntity(
                                localSeriesPoster ?: imageUrl,
                                localSeriesBackdrop ?: backdropUrl,
                            )
                        )
                    }
                }
            }

            downloadIds
        }
    }

    override suspend fun downloadTrickplayData(
        itemId: String,
        trickplayInfo: TrickplayInfo,
        downloadPath: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val parentDir = File(downloadPath).parentFile ?: return@withContext false
            val trickplayDir = File(parentDir, DownloadArtifacts.trickplayDir(itemId)).apply { mkdirs() }
            val thumbnailsPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            val totalSheets = (trickplayInfo.thumbnailCount + thumbnailsPerSheet - 1) / thumbnailsPerSheet

            for (sheetIndex in 0 until totalSheets) {
                val data = playbackRepository.getTrickplayTileImage(
                    itemId,
                    trickplayInfo.width,
                    sheetIndex,
                ) ?: continue
                File(trickplayDir, "trickplay_${sheetIndex}.jpg").writeBytes(data)
            }

            File(trickplayDir, "meta.json").writeText(buildString {
                appendLine("{\"width\":${trickplayInfo.width},")
                appendLine("\"height\":${trickplayInfo.height},")
                appendLine("\"tileWidth\":${trickplayInfo.tileWidth},")
                appendLine("\"tileHeight\":${trickplayInfo.tileHeight},")
                appendLine("\"thumbnailCount\":${trickplayInfo.thumbnailCount},")
                appendLine("\"interval\":${trickplayInfo.interval},")
                appendLine("\"bandwidth\":${trickplayInfo.bandwidth}}")
            })
            true
        } catch (e: Exception) {
            Log.d(TAG, "Failed to write trickplay meta.json", e)
            false
        }
    }

    override suspend fun downloadExternalSubtitles(
        itemId: String,
        mediaSourceId: String,
        mediaStreams: List<MediaStream>,
        downloadPath: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val parentDir = File(downloadPath).parentFile ?: return@withContext false
            // Drop streams the URL builders can never serve — an external
            // image-codec sub (PGS/VOBSUB) has no delivery endpoint, so
            // fetching it fails on every pass. Without this pre-filter an
            // image-only inventory would report failure forever and the resync
            // would retry a permanently-unfetchable list each sync.
            val subtitleStreams = mediaStreams
                .filter { it.isBundleableSubtitle }
                .filterNot { it.isExternal && it.deliveryUrl.isNullOrBlank() && isImageSubtitleCodec(it.codec) }
            val subtitlesDir = File(parentDir, DownloadArtifacts.subtitlesDir(itemId))

            // Nothing deliverable remains — either a genuine server-side
            // removal or an inventory of never-fetchable streams. Mirror that
            // to disk and report success (the baseline seeds as empty). Doing
            // this here — not on fetch failure — means a server change is
            // reflected without wiping sidecars on a transient error.
            if (subtitleStreams.isEmpty()) {
                if (subtitlesDir.exists()) subtitlesDir.deleteRecursively()
                return@withContext true
            }

            subtitlesDir.mkdirs()
            val entries = mutableListOf<OfflineSubtitleEntry>()
            // Any stream that fails to resolve a delivery URL or fetch marks
            // the pass incomplete; only a complete pass may prune (contract in
            // [pruneOrphanSidecarFiles]).
            var incompletePass = false

            for (stream in subtitleStreams) {
                try {
                    val subUrl = when {
                        !stream.deliveryUrl.isNullOrBlank() ->
                            playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                        stream.isExternal ->
                            playbackRepository.buildSubtitleDeliveryUrl(itemId, mediaSourceId, stream.index, stream.codec)
                        else -> continue
                    }
                    if (subUrl.isBlank()) {
                        incompletePass = true
                        continue
                    }

                    // VobSub renders only as an .idx+.sub pair; both halves are
                    // fetched and the manifest points at the .idx (the player's
                    // vobsub demuxer picks up the .sub sibling by base name).
                    val fileName = if (isVobsubFamilyCodec(stream.codec)) {
                        fetchVobsubPair(subUrl, subtitlesDir, stream.index)
                    } else {
                        fetchSingleSidecar(subUrl, subtitlesDir, stream.index, stream.codec)
                    }
                    if (fileName == null) {
                        incompletePass = true
                        continue
                    }

                    entries.add(
                        OfflineSubtitleEntry(
                            index = stream.index,
                            fileName = fileName,
                            language = stream.language,
                            codec = stream.codec,
                            title = stream.title,
                            displayTitle = stream.displayTitle,
                            isDefault = stream.isDefault,
                            isForced = stream.isForced,
                            isImage = isImageSubtitleCodec(stream.codec),
                        )
                    )
                } catch (e: Exception) {
                    incompletePass = true
                    Log.d(TAG, "Failed to download subtitle stream ${stream.index} for $itemId", e)
                }
            }

            if (entries.isNotEmpty()) {
                // Persist a manifest describing exactly what landed on disk.
                writeSubtitleManifest(subtitlesDir, entries, json)
                if (!incompletePass) {
                    // Pair halves count as live alongside the manifest's own
                    // entry — a pruned .idx or .sub breaks the whole pair.
                    val liveNames = entries.flatMap {
                        listOfNotNull(it.fileName, subtitleCompanionFileName(it.fileName))
                    }.toSet()
                    pruneOrphanSidecarFiles(subtitlesDir, liveNames)
                }
                true
            } else {
                // Deliverable streams existed but none fetched (transient
                // network/auth/delivery-URL failure). Leave the existing dir and
                // manifest untouched and report failure so the resync baseline
                // rolls its subtitle axis back and the next sync retries — instead
                // of destroying working sidecars and seeding the baseline as synced.
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to download external subtitles for $itemId", e)
            false
        }
    }

    override suspend fun markSubtitlesPending(itemId: String) {
        // Atomicity and the stub/raise pairing live on the @Transaction DAO
        // method — the canonical description of this flag's lifecycle.
        syncBaselineDao.markSubtitlesPending(itemId)
    }

    override suspend fun downloadMediaSegments(itemId: String, downloadPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val segments = playbackRepository.getMediaSegments(itemId).getOrDefault(emptyList())
                if (segments.isEmpty()) return@withContext true
                val parentDir = File(downloadPath).parentFile ?: return@withContext false
                File(parentDir, DownloadArtifacts.segmentsFile(itemId))
                    .writeText(json.encodeToString(segments))
                true
            } catch (e: Exception) {
                Log.d(TAG, "Failed to download media segments for $itemId", e)
                false
            }
        }

    override suspend fun downloadOfflineImage(
        itemId: String,
        imageType: String,
        maxWidth: Int,
        parentDir: File,
        fileName: String,
    ): String? = downloadImageToDisk(itemId, imageType, maxWidth, parentDir, fileName)

    override suspend fun loadLocalSubtitleManifest(
        downloadPath: String,
        itemId: String?,
    ): OfflineSubtitleManifest? = withContext(Dispatchers.IO) {
        val dir = File(downloadPath).parentFile ?: return@withContext null
        // Try item-scoped path first (new downloads).
        if (itemId != null) {
            val scopedFile = File(dir, "${DownloadArtifacts.subtitlesDir(itemId)}/${DownloadArtifacts.SUBTITLE_MANIFEST_FILE}")
            if (scopedFile.exists()) {
                return@withContext runCatching { json.decodeFromString<OfflineSubtitleManifest>(scopedFile.readText()) }
                    .onFailure { Log.w(TAG, "Failed to decode local subtitle manifest", it) }
                    .getOrNull()
            }
        }
        // Fall back to legacy un-scoped path (pre-fix downloads).
        val file = File(dir, "${DownloadArtifacts.LEGACY_SUBTITLES_DIR}/${DownloadArtifacts.SUBTITLE_MANIFEST_FILE}")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<OfflineSubtitleManifest>(file.readText()) }
            .onFailure { Log.w(TAG, "Failed to decode local subtitle manifest", it) }
            .getOrNull()
    }

    override suspend fun loadLocalSegments(itemId: String): List<MediaSegment>? = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadByMediaItemId(itemId) ?: return@withContext null
        val dir = File(download.downloadPath).parentFile ?: return@withContext null
        // Try item-scoped file first.
        val scopedFile = File(dir, DownloadArtifacts.segmentsFile(itemId))
        val file = if (scopedFile.exists()) scopedFile else {
            val legacy = File(dir, DownloadArtifacts.LEGACY_SEGMENTS_FILE)
            if (!legacy.exists()) return@withContext null
            legacy
        }
        runCatching { json.decodeFromString<List<MediaSegment>>(file.readText()) }
            .onFailure { Log.w(TAG, "Failed to decode local segments", it) }
            .getOrNull()
    }

    override suspend fun getDownloadFileInventory(itemId: String): DownloadFileInventory = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadByMediaItemId(itemId)
        val mediaPath = download?.downloadPath?.takeIf { it.isNotBlank() && File(it).isFile }
        if (mediaPath == null) return@withContext DownloadFileInventory.EMPTY
        val parentDir = File(mediaPath).parentFile ?: return@withContext DownloadFileInventory.EMPTY

        // Person ids (for cast-image enumeration) + series id (for series-keyed
        // artwork) are sourced from the offline_media row; the downloads row
        // alone doesn't carry cast. Both tables are keyed by the same item id.
        val offline = offlineMediaDao.getById(itemId)
        val seriesId = download.seriesId ?: offline?.seriesId
        val personIds = offline?.let { decodeCast(it.peopleJson).map { person -> person.id } } ?: emptyList()

        val entries = mutableListOf<DownloadFileEntry>()

        fun addFile(category: DownloadedFileCategory, file: File) {
            if (file.isFile) {
                entries += DownloadFileEntry(
                    category = category,
                    displayName = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                )
            }
        }

        // ── Media file ──
        addFile(DownloadedFileCategory.MEDIA, File(mediaPath))

        // ── Trickplay sprite sheets + meta (item-scoped dir, then legacy) ──
        listOf(DownloadArtifacts.trickplayDir(itemId), DownloadArtifacts.LEGACY_TRICKPLAY_DIR)
            .map { File(parentDir, it) }
            .filter { it.isDirectory }
            .forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    addFile(DownloadedFileCategory.TRICKPLAY, f)
                }
            }

        // ── Subtitle bundle (item-scoped dir, then legacy) ──
        listOf(DownloadArtifacts.subtitlesDir(itemId), DownloadArtifacts.LEGACY_SUBTITLES_DIR)
            .map { File(parentDir, it) }
            .filter { it.isDirectory }
            .forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    addFile(DownloadedFileCategory.SUBTITLE, f)
                }
            }

        // ── Segments (intro/outro/recap markers JSON) ──
        addFile(DownloadedFileCategory.SEGMENT, File(parentDir, DownloadArtifacts.segmentsFile(itemId)))
        addFile(DownloadedFileCategory.SEGMENT, File(parentDir, DownloadArtifacts.LEGACY_SEGMENTS_FILE))

        // ── Images: per-item poster/backdrop, series-keyed artwork, cast portraits ──
        addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.posterFile(itemId)))
        addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.backdropFile(itemId)))
        if (!seriesId.isNullOrBlank() && seriesId != itemId) {
            addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.posterFile(seriesId)))
            addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.backdropFile(seriesId)))
        }
        personIds.forEach { personId ->
            addFile(DownloadedFileCategory.IMAGE, File(parentDir, DownloadArtifacts.personImageFile(personId)))
        }

        DownloadFileInventory(
            entries = entries.sortedBy { it.category.ordinal },
            totalSizeBytes = entries.sumOf { it.sizeBytes },
        )
    }

    /**
     * Fetches one text/image sidecar as `{index}.{ext}`. Returns the manifest
     * file name, or null when the fetch failed (the caller marks the pass
     * incomplete; [downloadSubtitleFile]'s temp-file write leaves any previous
     * pass's sidecar untouched).
     */
    private suspend fun fetchSingleSidecar(
        subUrl: String,
        subtitlesDir: File,
        index: Int,
        codec: String?,
    ): String? {
        val fileName = "$index.${subtitleSidecarExtension(codec)}"
        return if (downloadSubtitleFile(subUrl, File(subtitlesDir, fileName))) fileName else null
    }

    /**
     * Fetches both halves of a VobSub pair ([index].idx palette + [index].sub
     * bitmap). The server's deliveryUrl for an external VobSub stream points
     * at whichever file the MediaStream advertises; [vobsubPairUrls] derives
     * the other half. Either half alone is unrenderable, so both fetches must
     * succeed. Returns the manifest file name (`{index}.idx`, which the
     * player's vobsub demuxer pairs with the `.sub` sibling), or null.
     *
     * On failure nothing is deleted: [downloadSubtitleFile] stages writes in
     * temp files, so a pair fetched by a previous successful pass stays
     * intact and keeps serving the still-valid manifest until the resync
     * retries.
     */
    private suspend fun fetchVobsubPair(subUrl: String, subtitlesDir: File, index: Int): String? {
        val (paletteUrl, bitmapUrl) = vobsubPairUrls(subUrl)
        val idxFile = File(subtitlesDir, "$index.idx")
        val subFile = File(subtitlesDir, "$index.sub")
        if (!downloadSubtitleFile(paletteUrl, idxFile)) return null
        if (!downloadSubtitleFile(bitmapUrl, subFile)) return null
        return idxFile.name
    }

    /**
     * Fetches one subtitle sidecar to [target]. Subtitle-specific policy lives
     * here on purpose: the auth-header fallback and the HTML/JSON rejection
     * below apply only to subtitle fetches (the video transfer in
     * DownloadTransferClient has its own), so this is deliberately not a
     * generic file-download helper.
     */
    private suspend fun downloadSubtitleFile(url: String, target: File): Boolean {
        // Staged write: the stream goes to a `.part` sibling and is moved into
        // place only when complete, so a mid-transfer failure never truncates
        // (or replaces) the sidecar a previous successful pass wrote — offline
        // playback keeps serving it until the resync retries.
        val staging = File(target.parentFile, target.name + ".part")
        return try {
            // Auth rides on the baked-in api_key query param, with the
            // X-Emby-Token header as a fallback for servers/reverse proxies
            // that reject or strip query-token auth (the same pairing
            // DownloadTransferClient uses for the video itself).
            val requestBuilder = Request.Builder().url(url)
            playbackRepository.getAccessToken()?.takeIf { it.isNotBlank() }?.let {
                requestBuilder.header("X-Emby-Token", it)
            }
            httpClient.newCall(requestBuilder.build()).awaitResponse().use { resp ->
                if (!resp.isSuccessful) return@use false
                // An auth/proxy failure can arrive as HTTP 200 with an HTML
                // login page (or a JSON error body); persisting either yields a
                // sidecar the player can't parse — indistinguishable offline
                // from "subtitle missing". No subtitle format is ever served
                // as HTML/JSON, so both are rejected outright.
                val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                if (REJECTED_SUBTITLE_CONTENT_TYPES.any { contentType.contains(it) }) {
                    Log.d(TAG, "Rejected subtitle response from $url: Content-Type $contentType")
                    return@use false
                }
                var moved = false
                resp.body?.byteStream()?.use { input ->
                    staging.outputStream().use { output -> input.copyTo(output) }
                    Files.move(
                        staging.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    moved = true
                }
                moved
            }
        } catch (e: CancellationException) {
            staging.delete()
            throw e
        } catch (e: Exception) {
            staging.delete()
            Log.d(TAG, "Failed to download file from $url", e)
            false
        }
    }

    /**
     * Downloads the given item's image to a local file so it is viewable fully
     * offline. Returns the absolute file path on success, or null if the item
     * has no such image or the download failed — callers then fall back to the
     * remote URL so an image fetch failure never blocks a download.
     *
     * @param parentDir directory that holds the downloaded media (the image is
     *   written as a sibling file there, matching the other offline artifacts).
     */
    private suspend fun downloadImageToDisk(
        itemId: String,
        imageType: String,
        maxWidth: Int,
        parentDir: File,
        fileName: String,
    ): String? = withContext(Dispatchers.IO) {
        val bytes = playbackRepository.getItemImageBytes(itemId, imageType, maxWidth)
            ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null
        val target = File(parentDir, fileName)
        try {
            target.writeBytes(bytes)
            target.absolutePath
        } catch (e: Exception) {
            Log.d(TAG, "Failed to write offline image $fileName for $itemId", e)
            null
        }
    }

    private suspend fun saveOfflineMetadataForItem(item: MediaItem, imageUrl: String?, backdropUrl: String?) {
        // Metadata + playback are split across two tables; seed both from the
        // fresh item in one transaction so a reader never sees a metadata row
        // without its playback snapshot. The freshness baseline is seeded only
        // on the detail path ([saveOfflineMetadataForDetail]) where a full
        // MediaDetail is available.
        database.withTransaction {
            offlineMediaDao.upsert(item.toOfflineMediaEntity(imageUrl, backdropUrl))
            playbackStateDao.upsert(item.toPlaybackState())
        }
        preloadImageToCache(imageUrl)
        preloadImageToCache(backdropUrl)
    }

    /**
     * Persist full metadata for a downloaded item from a [MediaDetail], including
     * cast, studios, critic rating, tagline, and original title. Cast images
     * are preloaded into the Coil cache so the offline detail screen can render
     * the cast row without network access.
     */
    private suspend fun saveOfflineMetadataForDetail(detail: MediaDetail, imageUrl: String?, backdropUrl: String?) {
        // Metadata, playback, and freshness baseline each live in their own
        // table now. A metadata re-persist (the resync PERSIST_METADATA step
        // re-uses this) can no longer clobber the baseline — it's in
        // `sync_baseline` — so the old "copy the sync columns forward" block is
        // gone.
        val existingMeta = offlineMediaDao.getById(detail.item.id)
        database.withTransaction {
            offlineMediaDao.upsert(detail.toOfflineMediaEntity(imageUrl, backdropUrl))
            playbackStateDao.upsert(detail.item.toPlaybackState())
        }
        // Seed the freshness baseline from the detail we just persisted so the
        // first auto-check has a reference to diff against. Without this, a fresh
        // download enters with no baseline row and the first check treats itself
        // as "first contact" — swallowing a real change that happened before that
        // first check (and always reporting CURRENT for new downloads). Only seed
        // when no baseline existed yet, so a re-download doesn't clobber a recent
        // check's flags; a genuine re-download is itself a fresh server snapshot.
        val existingBaseline = syncBaselineDao.getBaseline(detail.item.id)
        if (existingMeta == null || existingBaseline?.syncedMetadataSignature == null) {
            val baseline = syncComparator.baseline(detail)
            syncBaselineDao.upsert(
                SyncBaselineEntity(
                    id = detail.item.id,
                    syncedPosterTag = baseline.posterTag,
                    syncedBackdropTag = baseline.backdropTag,
                    syncedMetadataSignature = baseline.metadataSignature,
                    syncedSubtitleSignature = baseline.subtitleSignature,
                    syncedTrickplaySignature = baseline.trickplaySignature,
                    // Segments aren't part of MediaDetail; their signature is
                    // seeded on the first segments resync rather than at
                    // download time.
                    syncedSegmentsSignature = null,
                    syncedMediaSourceId = baseline.mediaSourceId,
                    syncedMediaSizeBytes = baseline.mediaSizeBytes,
                    lastSyncedAt = System.currentTimeMillis(),
                ),
            )
        }
        preloadImageToCache(imageUrl)
        preloadImageToCache(backdropUrl)
        // Preload up to 10 cast images so the offline cast row renders without
        // a network connection. Mirrors the poster/backdrop caching above.
        detail.people
            .filter { it.hasCastImage() }
            .take(10)
            .forEach { person ->
                preloadImageToCache(playbackRepository.getImageUrl(person.id, maxWidth = 200))
            }
    }

    override fun enqueueDownload(downloadId: String) {
        // Runtime enqueue honours the user's wifi-only + schedule-window
        // preferences (cold-start recovery in DownloadRecoveryInitializer calls
        // DownloadEnqueuer directly with honorScheduleAndNetwork = false).
        downloadEnqueuer.enqueue(downloadId)
    }

    override suspend fun setDownloadPriority(id: String, priority: Int): Result<Unit> = runCatching {
        downloadDao.updatePriority(id, priority)
    }

    private fun preloadImageToCache(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            val imageLoader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                // Match MediaImage's default decode size (384²). Decoding the
                // preload at a different size produces a separate memory-cache
                // key, so the display path would re-decode and the larger
                // bitmap would sit stranded until evicted.
                .size(384, 384)
                .build()
            imageLoader.enqueue(request)
        } catch (e: Exception) { Log.d(TAG, "Failed to preload image to cache", e) }
    }

    private fun MediaItem.toOfflineMediaEntity(imageUrl: String?, backdropUrl: String?) = OfflineMediaEntity(
        id = id,
        name = name,
        mediaType = mediaType.name,
        overview = overview,
        year = year,
        communityRating = communityRating,
        officialRating = officialRating,
        runTimeTicks = runTimeTicks,
        parentId = parentId,
        seriesId = seriesId,
        seasonId = seasonId,
        // Clear the series subtitle for top-level entities where it would just
        // duplicate the title; only episodes carry a meaningful
        // series name distinct from their own.
        seriesName = if (mediaType == MediaType.EPISODE || mediaType == MediaType.SEASON) seriesName else null,
        seasonName = if (mediaType == MediaType.EPISODE) seasonName else null,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        indexNumber = indexNumber,
        childCount = childCount,
        posterPath = imageUrl,
        backdropPath = backdropUrl,
        blurHashPrimary = blurHashes.primary,
        blurHashBackdrop = blurHashes.backdrop,
        premiereDate = premiereDate,
        genres = genres.joinToString(","),
    )

    /**
     * Server `UserData` snapshot seeded at download time (and re-seeded on a
     * metadata re-persist) into `playback_state`. Mirrors the playback fields
     * the metadata row used to carry, so a freshly downloaded item shows its
     * watched / resume state immediately.
     */
    private fun MediaItem.toPlaybackState(): PlaybackStateEntity = PlaybackStateEntity(
        id = id,
        playbackPositionTicks = playbackPositionTicks,
        playedPercentage = PlayedStateSync.computePlayedPercentage(playbackPositionTicks, runTimeTicks, isPlayed),
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        lastPlayedDate = null,
    )

    /**
     * Maps a [MediaDetail] (the rich server response) to an [OfflineMediaEntity],
     * additionally persisting original title, critic rating, studios, tagline,
     * the cast as a JSON blob, and the chapter list as a JSON blob (so chapter
     * markers and the chapter sheet work offline). Falls back to the item-level
     * values for the base fields so this stays consistent with
     * [MediaItem.toOfflineMediaEntity].
     */
    private fun MediaDetail.toOfflineMediaEntity(imageUrl: String?, backdropUrl: String?): OfflineMediaEntity {
        val base = item.toOfflineMediaEntity(imageUrl, backdropUrl)
        val cast = people
            .filter { it.type == "Actor" }
            .map { person ->
                OfflinePersonInfo(
                    id = person.id,
                    name = person.name,
                    role = person.role,
                    type = person.type,
                    imageTag = person.primaryImageTag,
                    blurHash = person.primaryBlurHash,
                )
            }
        return base.copy(
            originalTitle = item.originalTitle,
            criticRating = criticRating,
            studios = item.studios.joinToString(","),
            tagline = taglines.firstOrNull(),
            peopleJson = if (cast.isEmpty()) null else encodeCast(cast),
            providerIdsJson = if (providerIds.isEmpty()) null else encodeProviderIds(providerIds),
            externalUrlsJson = if (externalUrls.isEmpty()) null else encodeExternalUrls(externalUrls),
            chaptersJson = if (chapters.isEmpty()) null else encodeChapters(chapters),
        )
    }

    private suspend fun cleanupDownloadFiles(entity: DownloadEntity) {
        // File deletion + directory listing off the caller's (Main) dispatcher —
        // same pattern as the sibling helpers below.
        withContext(Dispatchers.IO) {
            if (entity.downloadPath.isNotBlank()) {
                val file = File(entity.downloadPath)
                if (file.exists()) file.delete()
                DownloadArtifacts.cleanup(file.parentFile, entity.mediaItemId)
            }
        }
        database.withTransaction {
            downloadDao.deleteDownloadById(entity.id)
            offlineMediaDao.deleteById(entity.mediaItemId)
            playbackStateDao.deleteById(entity.mediaItemId)
            syncBaselineDao.deleteById(entity.mediaItemId)
            offlineMediaDao.deleteOrphanedSeasons()
            offlineMediaDao.deleteOrphanedSeries()
            playbackStateDao.deleteUnreferenced()
            syncBaselineDao.deleteUnreferenced()
        }
    }

    private fun DownloadEntity.toDownloadItem() = DownloadItem(
        id = id,
        mediaItemId = mediaItemId,
        name = name,
        mediaType = try { MediaType.valueOf(mediaType) } catch (_: Exception) { MediaType.UNKNOWN },
        downloadPath = downloadPath,
        downloadUrl = downloadUrl,
        totalSizeBytes = totalSizeBytes,
        downloadedBytes = downloadedBytes,
        status = try { DownloadStatus.valueOf(status) } catch (_: Exception) { DownloadStatus.FAILED },
        speedBytesPerSec = speedBytesPerSec,
        mediaSourceId = mediaSourceId,
        imageUrl = imageUrl,
        imageBlurHash = imageBlurHash,
        seriesId = seriesId,
        seasonId = seasonId,
        seriesName = seriesName,
        seasonName = seasonName,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        errorMessage = errorMessage,
        priority = priority,
        container = container,
    )

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

        // Content types that can never be a subtitle file. An HTTP 200 body of
        // one of these is an auth/proxy error page, not sidecar content.
        private val REJECTED_SUBTITLE_CONTENT_TYPES =
            listOf("text/html", "application/json")
    }
}
