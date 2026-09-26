package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.model.DownloadFileInventory
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID

/**
 * The offline-artifact-write cluster behind [OfflineDownloadWriter]'s seam —
 * start the transfer row, enqueue the worker, and persist every sibling
 * artifact (offline metadata row, local images, trickplay, subtitles,
 * intro/outro segments) that makes a download usable offline. Extracted
 * verbatim from [DownloadRepositoryImpl] (D6) so the former construction
 * cycle `DownloadRepositoryImpl → DownloadDelegate → OfflineDownloadWriter →
 * DownloadRepositoryImpl` — broken by a `Lazy<DownloadDelegate>` deferral —
 * dissolves into a straight line: this core takes its DAOs and seams
 * directly, [DownloadDelegate] writes through this core, and the repository
 * (which must still carry the [OfflineDownloadWriter] surface because
 * [DownloadRepository] extends it) forwards those members one-to-one to the
 * same core instance. No back-reference to the repository remains.
 *
 * Ownership split with the other download collaborators (the
 * [OfflineDeletionCore] / [DownloadSidecarCore] precedent):
 *  - this core owns the WRITE choreography (row creation, offline metadata +
 *    baseline seeding, parent hierarchy seeding, worker enqueue);
 *  - [DownloadSidecarCore] (constructed from this class's own constructor
 *    dependencies, like the repo constructed it before) owns ARTIFACT BYTES —
 *    the per-item directory grammar of [DownloadArtifacts] and the local
 *    reads; the sidecar members of the writer surface forward one-to-one;
 *  - [DownloadDelegate] owns the per-item recipe and drives this core
 *    through the narrow [OfflineDownloadWriter] port;
 *  - [DownloadRepositoryImpl] keeps queue lifecycle, series orchestration,
 *    and cleanup.
 *
 * Stateless beyond its DAOs/stores — every member is a DB or FS step — so the
 * single instance shared by the delegate and the repository is behaviorally
 * identical to the writer methods this class extracted.
 */
public class OfflineDownloadWriterCore(
    private val downloadDao: DownloadDao,
    private val offlineMediaDao: OfflineMediaDao,
    private val playbackStateDao: PlaybackStateDao,
    private val syncBaselineDao: SyncBaselineDao,
    private val database: JellyPlayDatabase,
    private val downloadsStore: DownloadsStore,
    private val storagePolicy: StoragePolicy,
    private val storageLayout: DownloadStorageLayoutContract,
    private val syncComparator: OfflineSyncComparator,
    private val downloadEnqueuer: DownloadEnqueueCoordinator,
    private val imagePreloader: OfflineImagePreloader,
    private val playbackRepository: PlaybackRepository,
    private val playbackIdentity: PlaybackIdentity,
    private val httpClient: OkHttpClient,
    private val json: Json,
    /** Clock seam for the baseline-seeding `lastSyncedAt` stamp. */
    private val timeSource: TimeSource,
    private val mediaRepository: MediaRepositoryAccess,
) : OfflineDownloadWriter {

    // The sidecar/artifact half of a download — trickplay/subtitle/segment/
    // image writes and the local manifest/segments/inventory reads (see
    // [DownloadSidecarCore]'s KDoc for the ownership split). Constructed from
    // this class's own constructor deps (the OfflineDeletionCore precedent).
    private val sidecarCore = DownloadSidecarCore(
        playbackRepository = playbackRepository,
        playbackIdentity = playbackIdentity,
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        syncBaselineDao = syncBaselineDao,
        httpClient = httpClient,
        json = json,
    )

    /**
     * Creates (or dedupes to) the PENDING downloads row for [request]. The
     * former 15-positional-parameter override + internal forwarding twin
     * collapsed into the [DownloadStartRequest] value object — the entity
     * construction below is unchanged semantically.
     */
    override suspend fun startDownload(request: DownloadStartRequest): Result<DownloadItem> =
        runCatchingRethrowingCancellation {
        val mediaItemId = request.mediaItemId
        val existing = downloadDao.getDownloadByMediaItemId(mediaItemId)
        if (existing != null) {
            val isCompleted = existing.status == DownloadStatus.COMPLETED.name
            val fileExists = existing.downloadPath.isNotBlank() && java.io.File(existing.downloadPath).exists()
            if (isCompleted && fileExists) {
                return@runCatchingRethrowingCancellation existing.toDownloadItem()
            }
            if (existing.status != DownloadStatus.FAILED.name && existing.status != DownloadStatus.CANCELLED.name && !isCompleted) {
                return@runCatchingRethrowingCancellation existing.toDownloadItem()
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
        storagePolicy.enforce(precomputedCurrentBytes = request.precomputedCurrentBytes)

        // Path-layout policy (internal vs external dir, filename sanitize,
        // container extension, free-space floor) lives in DownloadStorageLayout
        // — previously inlined ~40 LOC in this method, unreachable from any
        // other call site and untestable without a full repo construction.
        val id = UUID.randomUUID().toString()
        val resolved = storageLayout.resolve(
            mediaType = request.mediaType,
            storageLocationPref = prefs.downloadStorageLocation,
            name = request.name,
            idHint = id.take(8),
            container = request.container,
        )
        val filePath = resolved.filePath

        val entity = DownloadEntity(
            id = id,
            mediaItemId = mediaItemId,
            name = request.name,
            mediaType = request.mediaType,
            downloadPath = filePath,
            downloadUrl = request.downloadUrl,
            totalSizeBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.PENDING.name,
            mediaSourceId = request.mediaSourceId,
            imageUrl = request.imageUrl,
            imageBlurHash = request.imageBlurHash,
            seriesId = request.seriesId,
            seasonId = request.seasonId,
            seriesName = request.seriesName,
            seasonName = request.seasonName,
            episodeNumber = request.episodeNumber,
            seasonNumber = request.seasonNumber,
            container = request.container,
        )
        downloadDao.insertDownload(entity)
        entity.toDownloadItem()
    }

    override suspend fun saveOfflineMediaItem(
        item: MediaItem,
        imageUrl: String?,
        backdropUrl: String?,
        downloadPath: String?,
    ) {
        saveOfflineMetadataForItem(item, imageUrl, backdropUrl)
        seedEpisodeParents(item, artworkDir = downloadPath?.let { File(it).parentFile })
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

    override fun enqueueDownload(downloadId: String) {
        // Runtime enqueue honours the user's wifi-only + schedule-window
        // preferences (cold-start recovery in DownloadRecoveryInitializer calls
        // DownloadEnqueuer directly with honorScheduleAndNetwork = false).
        downloadEnqueuer.enqueue(downloadId)
    }

    // ── Sidecar/artifact surface (delegates one-to-one to the core) ────────
    // The byte-level half of the writer surface lives in DownloadSidecarCore;
    // the behavioral contracts are pinned by DownloadRepositoryImplSubtitlesTest
    // (androidHostTest) and DownloadSidecarCoreTest (jvmTest).

    override suspend fun downloadTrickplayData(
        itemId: String,
        trickplayInfo: TrickplayInfo,
        downloadPath: String,
    ): Boolean = sidecarCore.downloadTrickplayData(itemId, trickplayInfo, downloadPath)

    override suspend fun downloadExternalSubtitles(
        itemId: String,
        mediaSourceId: String,
        mediaStreams: List<MediaStream>,
        downloadPath: String,
    ): Boolean = sidecarCore.downloadExternalSubtitles(itemId, mediaSourceId, mediaStreams, downloadPath)

    override suspend fun markSubtitlesPending(itemId: String) {
        sidecarCore.markSubtitlesPending(itemId)
    }

    override suspend fun downloadMediaSegments(itemId: String, downloadPath: String): Boolean =
        sidecarCore.downloadMediaSegments(itemId, downloadPath)

    override suspend fun downloadOfflineImage(
        itemId: String,
        imageType: String,
        maxWidth: Int,
        parentDir: File,
        fileName: String,
    ): String? = sidecarCore.downloadImageToDisk(itemId, imageType, maxWidth, parentDir, fileName)

    /** Local sidecar reads the [DownloadRepository] surface exposes. */
    suspend fun loadLocalSubtitleManifest(
        downloadPath: String,
        itemId: String?,
    ): OfflineSubtitleManifest? = sidecarCore.loadLocalSubtitleManifest(downloadPath, itemId)

    /** Local sidecar reads the [DownloadRepository] surface exposes. */
    suspend fun loadLocalSegments(itemId: String): List<MediaSegment>? =
        sidecarCore.loadLocalSegments(itemId)

    /** Local sidecar reads the [DownloadRepository] surface exposes. */
    suspend fun getDownloadFileInventory(itemId: String): DownloadFileInventory =
        sidecarCore.getDownloadFileInventory(itemId)

    /**
     * Fetches a series' poster (Primary @300) and backdrop (Backdrop @1280)
     * into [artworkDir] as [DownloadArtifacts]-named sibling files, preferring
     * the local copy; a `null` path (no dir, no such image, or a failed fetch)
     * makes the caller fall back to the remote URL. The one home for the
     * series-artwork grammar the two series paths previously hand-copied —
     * the seedEpisodeParents copy even built the filenames from raw
     * `"${seriesId}_poster.jpg"` literals, leaking the grammar out of
     * [DownloadArtifacts] (identical strings, so this is a pure fold).
     */
    suspend fun downloadSeriesArtwork(seriesId: String, artworkDir: File?): SeriesArtwork {
        val posterPath = artworkDir?.let {
            sidecarCore.downloadImageToDisk(seriesId, "Primary", 300, it, DownloadArtifacts.posterFile(seriesId))
        }
        val backdropPath = artworkDir?.let {
            sidecarCore.downloadImageToDisk(seriesId, "Backdrop", 1280, it, DownloadArtifacts.backdropFile(seriesId))
        }
        return SeriesArtwork(posterPath, backdropPath)
    }

    data class SeriesArtwork(val posterPath: String?, val backdropPath: String?)

    /**
     * The item-level metadata write, exposed for the repository's
     * [DownloadRepositoryImpl.downloadSeries] (season seeding) — the same body
     * [saveOfflineMediaItem] runs before its parent-hierarchy seeding.
     */
    suspend fun saveOfflineMetadataForItem(item: MediaItem, imageUrl: String?, backdropUrl: String?) {
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
     * are preloaded into the platform image cache so the offline detail screen
     * can render the cast row without network access.
     */
    /**
     * The detail-level metadata write (offline row + playback snapshot +
     * freshness baseline + image preloads), exposed for the repository's
     * [DownloadRepositoryImpl.downloadSeries] (series seeding) — the same body
     * [saveOfflineMediaDetail] runs before its parent-hierarchy seeding.
     */
    suspend fun saveOfflineMetadataForDetail(detail: MediaDetail, imageUrl: String?, backdropUrl: String?) {
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
                    lastSyncedAt = timeSource.nowEpochMillis(),
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
            // The lazy accessor itself may throw (desktop: no MediaRepository
            // definition until ). Degrade to the minimal-row fallback
            // below — the same shape as a failed detail fetch on Android —
            // so episode downloads still seed their parent series/season
            // rows instead of aborting the whole metadata block.
            val seriesDetail = runCatchingRethrowingCancellation { mediaRepository().getMediaDetail(seriesId) }
                .getOrNull()
                ?.getOrNull()
            if (seriesDetail != null) {
                val seriesArtwork = downloadSeriesArtwork(seriesId, artworkDir)
                val seriesImageUrl = seriesArtwork.posterPath
                    ?: playbackRepository.getImageUrl(seriesId, maxWidth = 300)
                val seriesBackdropUrl = seriesArtwork.backdropPath
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

    private fun preloadImageToCache(url: String?) {
        if (url.isNullOrBlank()) return
        imagePreloader.preload(url)
    }
}
