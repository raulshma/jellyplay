package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import android.os.Environment
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
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.data.worker.awaitResponse
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.TrickplayInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

    @Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val offlineMediaDao: OfflineMediaDao,
    private val database: JellyPlayDatabase,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val httpClient: OkHttpClient,
    private val preferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore,
    private val json: Json,
    /**
     * Lazy to break the Hilt cycle: [DownloadDelegate] injects
     * [DownloadRepository] (this impl, via its interface), and [downloadSeries]
     * delegates the per-episode artifact bundle to it. `dagger.Lazy` defers
     * resolution until first use, so the graph still constructs.
     */
    private val downloadDelegate: dagger.Lazy<com.raulshma.jellyplay.core.data.util.DownloadDelegate>,
) : DownloadRepository {

    // Caps the number of episodes processed concurrently when queueing a series
    // download. Avoids launching 20+ parallel OkHttp calls + Coil decodes at once.
    private val downloadPermits = Semaphore(permits = 4)

    override fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloads().map { entities ->
            entities.map { it.toDownloadItem() }
        }.distinctUntilChanged()

    override fun getDownloadByMediaItemIdFlow(mediaItemId: String): Flow<DownloadItem?> =
        downloadDao.getDownloadByMediaItemIdFlow(mediaItemId).map { it?.toDownloadItem() }

    override fun getActiveDownloadCount(): Flow<Int> =
        downloadDao.getActiveDownloadCount()

    override suspend fun getDownloadByMediaItemId(mediaItemId: String): DownloadItem? =
        downloadDao.getDownloadByMediaItemId(mediaItemId)?.toDownloadItem()

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
                File(existing.downloadPath).let { f -> if (f.exists()) f.delete() }
                DownloadArtifacts.cleanup(File(existing.downloadPath).parentFile, existing.mediaItemId)
            }
            downloadDao.deleteDownloadById(existing.id)
        }

        val prefs = preferencesStore.preferences.first()
        val maxBytes = prefs.maxCacheSizeMb.toLong() * 1024 * 1024
        // Enforce the user-facing download storage cap (in GB). 0 = unlimited.
        val maxBytesFromGb = prefs.maxDownloadStorageGb.toLong() * 1024L * 1024L * 1024L
        // Compute the current downloaded bytes once and compare against both
        // caps (was two identical SUM queries back-to-back when both caps set).
        if (maxBytes > 0 || maxBytesFromGb > 0) {
            val currentBytes = precomputedCurrentBytes ?: downloadDao.getTotalDownloadedBytes()
            if (maxBytes > 0 && currentBytes >= maxBytes) {
                throw IllegalStateException("Download limit reached (${prefs.maxCacheSizeMb} MB). Free up space in Settings › Storage or increase the limit.")
            }
            if (maxBytesFromGb > 0 && currentBytes >= maxBytesFromGb) {
                throw IllegalStateException("Download storage limit reached (${prefs.maxDownloadStorageGb} GB). Free up space in Settings › Storage or increase the limit.")
            }
        }

        val isAudioType = mediaType == MediaType.AUDIO.name || mediaType == MediaType.MUSIC.name
        val dirType = if (isAudioType) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        // downloadStorageLocation: "EXTERNAL" prefers the primary external storage
        // mount (still app-private externalFilesDir, scoped to MEDIA_ROOT).
        // "INTERNAL" falls back to filesDir which is never visible to other apps
        // and survives media scan indexing. Both remain app-private post-uninstall.
        val useInternalStorage = prefs.downloadStorageLocation.equals("INTERNAL", ignoreCase = true) &&
            prefs.downloadStorageLocation != "EXTERNAL"
        val baseDir = when {
            useInternalStorage && !isAudioType -> File(context.filesDir, "downloads")
            useInternalStorage && isAudioType -> File(context.filesDir, "downloads/music")
            else -> context.getExternalFilesDir(dirType)
                ?: File(context.filesDir, if (isAudioType) "downloads/music" else "downloads")
        }
        val downloadDir = baseDir
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        val statFs = android.os.StatFs(downloadDir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        if (availableBytes < 100L * 1024 * 1024) {
            throw IllegalStateException("Insufficient storage space. Less than 100 MB available on device.")
        }

        val id = UUID.randomUUID().toString()
        val dir = baseDir
        val safeName = name.replace(FILENAME_SANITIZE_REGEX, "_")
        // Prefer the original container reported by the Jellyfin MediaSource so
        // the on-disk extension reflects the real bytes — ExoPlayer selects its
        // extractor from the URI extension and hangs silently when the extension
        // lies (e.g. an MKV stream saved as `.mp4`). Sanitize and fall back to
        // the legacy hardcoded extension for audio/video when the container is
        // missing or unsafe (path-traversal / weird chars).
        val extension = container
            ?.takeIf { it.isNotBlank() && FILENAME_CONTAINER_REGEX.matches(it) }
            ?: if (isAudioType) "mp3" else "mp4"
        val filePath = File(dir, "${safeName}_${id.take(8)}.$extension").absolutePath

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
    }

    override suspend fun pauseDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        if (DownloadStates.isActive(entity.status)) {
            // Cancel the in-flight worker first so the foreground service stops
            // promptly. Without this the worker keeps polling DB status until its
            // next tick discovers the row is PAUSED.
            cancelWorkForDownload(id)
            downloadDao.updateProgress(id, entity.downloadedBytes, DownloadStatus.PAUSED.name)
            // Mark as user-initiated so the reconnect auto-resume leaves it
            // alone; only NETWORK interruptions auto-resume.
            downloadDao.updatePausedReason(id, DownloadPauseReason.USER.persistedValue)
        }
    }

    override suspend fun resumeDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        if (DownloadStates.isPausedOrFailed(entity.status)) {
            downloadDao.updateProgress(id, entity.downloadedBytes, DownloadStatus.PENDING.name)
            // Manual resume/retry clears both the pause reason and the
            // auto-retry budget — the user has taken ownership of this row.
            downloadDao.updatePausedReason(id, null)
            downloadDao.resetRetryCount(id)
        }
    }

    override suspend fun deleteDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        cancelWorkForDownload(id)
        cleanupDownloadFiles(entity)
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

    override suspend fun retryDownload(id: String): Result<Unit> = runCatching {
        downloadDao.updateProgress(id, 0L, DownloadStatus.PENDING.name)
        // A manual retry starts fresh — clear the auto-retry budget and reason.
        downloadDao.updatePausedReason(id, null)
        downloadDao.resetRetryCount(id)
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
                // a contiguous prefix, so preserve its byte offset.
                val startBytes = if (row.status == DownloadStatus.PAUSED.name) row.downloadedBytes else 0L
                downloadDao.updateProgress(row.id, startBytes, DownloadStatus.PENDING.name)
                downloadDao.updatePausedReason(row.id, null)
                enqueueDownload(row.id)
            } catch (e: Exception) {
                // One bad row/enqueue must not abort the whole batch — the other
                // interrupted downloads still resume this pass.
                Log.w(TAG, "Failed to resume interrupted download ${row.id}", e)
            }
        }
    }

    override suspend fun getTotalDownloadedBytes(): Long =
        downloadDao.getTotalDownloadedBytes()

    override suspend fun saveOfflineMediaItem(item: MediaItem, imageUrl: String?, backdropUrl: String?, downloadPath: String?) {
        saveOfflineMetadataForItem(item, imageUrl, backdropUrl)

        if (item.mediaType == MediaType.EPISODE) {
            val seriesId = item.seriesId
            val seasonId = item.seasonId
            val parentDir = downloadPath?.let { File(it).parentFile }

            if (seriesId != null && offlineMediaDao.getById(seriesId) == null) {
                val seriesDetail = mediaRepository.getMediaDetail(seriesId).getOrNull()
                if (seriesDetail != null) {
                    val localSeriesPoster = parentDir?.let {
                        downloadImageToDisk(seriesId, "Primary", 300, it, "${seriesId}_poster.jpg")
                    }
                    val localSeriesBackdrop = parentDir?.let {
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
    }

    override suspend fun saveOfflineMediaDetail(detail: MediaDetail, imageUrl: String?, backdropUrl: String?) {
        saveOfflineMetadataForDetail(detail, imageUrl, backdropUrl)

        // For episodes, fall back to the series/season seeding logic so a
        // lone episode download still has its parent rows.
        val item = detail.item
        if (item.mediaType == MediaType.EPISODE) {
            saveOfflineMediaItem(item, imageUrl, backdropUrl)
        }
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

    override suspend fun downloadSeries(
        seriesId: String,
        episodeIds: Map<String, List<String>>?,
    ): Result<List<String>> = runCatching {
        withContext(Dispatchers.IO) {
            val prefs = preferencesStore.preferences.first()
            // The storage cap only needs to be evaluated once for the whole
            // enqueue batch: no bytes are actually downloaded here (the
            // DownloadWorker runs later), so every per-episode SUM(downloadedBytes)
            // would return an identical value. Computing it up-front turns N
            // redundant aggregate queries into one and fails fast when over cap.
            val maxBytesBatch = prefs.maxCacheSizeMb.toLong() * 1024 * 1024
            val maxBytesFromGbBatch = prefs.maxDownloadStorageGb.toLong() * 1024L * 1024L * 1024L
            val batchCurrentBytes = if (maxBytesBatch > 0 || maxBytesFromGbBatch > 0) {
                downloadDao.getTotalDownloadedBytes()
            } else -1L
            if (batchCurrentBytes >= 0) {
                if (maxBytesBatch > 0 && batchCurrentBytes >= maxBytesBatch) {
                    throw IllegalStateException("Download limit reached (${prefs.maxCacheSizeMb} MB). Free up space in Settings › Storage or increase the limit.")
                }
                if (maxBytesFromGbBatch > 0 && batchCurrentBytes >= maxBytesFromGbBatch) {
                    throw IllegalStateException("Download storage limit reached (${prefs.maxDownloadStorageGb} GB). Free up space in Settings › Storage or increase the limit.")
                }
            }

            val detail = mediaRepository.getMediaDetail(seriesId).getOrThrow()
            val imageUrl = playbackRepository.getImageUrl(seriesId, maxWidth = 300)
            val backdropUrl = playbackRepository.getBackdropUrl(seriesId, maxWidth = 1280)

            // Persist full series metadata (cast, studios, ratings, …) from the
            // fetched detail so the offline series screen is as rich as online.
            saveOfflineMetadataForDetail(detail, imageUrl, backdropUrl)

            val seasons = mediaRepository.getSeasons(seriesId).getOrElse { emptyList() }
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

                val allEpisodes = mediaRepository.getEpisodes(seriesId, season.id).getOrElse { emptyList() }
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
                                    val request = episodeDetail?.let {
                                        delegate.prepareDownloadRequest(it, qualityMaxBitrate)
                                    }
                                    if (request == null) {
                                        null
                                    } else {
                                        val result = delegate.executeDownload(request, budgetHint)
                                        result.downloadItem?.id
                                    }
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

                episodeResults.filterNotNull().forEach { downloadIds.add(it) }
            }

            downloadIds
        }
    }

    override suspend fun downloadTrickplayData(
        itemId: String,
        trickplayInfo: TrickplayInfo,
        downloadPath: String,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val parentDir = File(downloadPath).parentFile ?: return@withContext
                val trickplayDir = File(parentDir, "trickplay").apply { mkdirs() }
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
            } catch (e: Exception) { Log.d(TAG, "Failed to write trickplay meta.json", e) }
        }
    }

    override suspend fun downloadExternalSubtitles(
        itemId: String,
        mediaSourceId: String,
        mediaStreams: List<MediaStream>,
        downloadPath: String,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val parentDir = File(downloadPath).parentFile ?: return@withContext
                val subtitleStreams = mediaStreams.filter {
                    it.type == StreamType.SUBTITLE && (it.isExternal || !it.deliveryUrl.isNullOrBlank())
                }
                if (subtitleStreams.isEmpty()) return@withContext

                val subtitlesDir = File(parentDir, DownloadArtifacts.SUBTITLES_DIR).apply { mkdirs() }
                val entries = mutableListOf<OfflineSubtitleEntry>()

                for (stream in subtitleStreams) {
                    try {
                        val subUrl = when {
                            !stream.deliveryUrl.isNullOrBlank() ->
                                playbackRepository.getSubtitleDeliveryUrl(stream.deliveryUrl!!)
                            stream.isExternal ->
                                playbackRepository.buildSubtitleDeliveryUrl(itemId, mediaSourceId, stream.index, stream.codec)
                            else -> continue
                        }
                        if (subUrl.isBlank()) continue

                        val fileName = "${stream.index}.${subtitleFileExtension(stream.codec)}"
                        val target = File(subtitlesDir, fileName)
                        if (!downloadToFile(subUrl, target)) continue

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
                            )
                        )
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to download subtitle stream ${stream.index} for $itemId", e)
                    }
                }

                // Only persist a manifest when at least one subtitle was saved.
                // Otherwise remove the dir so the player never reads a stale manifest.
                if (entries.isNotEmpty()) {
                    File(subtitlesDir, DownloadArtifacts.SUBTITLE_MANIFEST_FILE)
                        .writeText(json.encodeToString(OfflineSubtitleManifest(entries)))
                } else if (subtitlesDir.exists()) {
                    subtitlesDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to download external subtitles for $itemId", e)
            }
        }
    }

    override suspend fun downloadMediaSegments(itemId: String, downloadPath: String) {
        withContext(Dispatchers.IO) {
            try {
                val segments = playbackRepository.getMediaSegments(itemId).getOrDefault(emptyList())
                if (segments.isEmpty()) return@withContext
                val parentDir = File(downloadPath).parentFile ?: return@withContext
                File(parentDir, DownloadArtifacts.SEGMENTS_FILE)
                    .writeText(json.encodeToString(segments))
            } catch (e: Exception) {
                Log.d(TAG, "Failed to download media segments for $itemId", e)
            }
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
    ): OfflineSubtitleManifest? = withContext(Dispatchers.IO) {
        val dir = File(downloadPath).parentFile ?: return@withContext null
        val file = File(dir, "${DownloadArtifacts.SUBTITLES_DIR}/${DownloadArtifacts.SUBTITLE_MANIFEST_FILE}")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<OfflineSubtitleManifest>(file.readText()) }
            .onFailure { Log.w("DownloadRepository", "Failed to decode local subtitle manifest", it) }
            .getOrNull()
    }

    override suspend fun loadLocalSegments(itemId: String): List<MediaSegment>? = withContext(Dispatchers.IO) {
        val download = downloadDao.getDownloadByMediaItemId(itemId) ?: return@withContext null
        val dir = File(download.downloadPath).parentFile ?: return@withContext null
        val file = File(dir, DownloadArtifacts.SEGMENTS_FILE)
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<List<MediaSegment>>(file.readText()) }
            .onFailure { Log.w("DownloadRepository", "Failed to decode local segments", it) }
            .getOrNull()
    }

    private fun subtitleFileExtension(codec: String?): String = when (codec?.lowercase()) {
        "subrip", "srt" -> "srt"
        "ass" -> "ass"
        "ssa" -> "ass"
        "webvtt", "vtt" -> "vtt"
        "mov_text", "ttml" -> "ttml"
        "sub" -> "sub"
        else -> "srt"
    }

    private suspend fun downloadToFile(url: String, target: File): Boolean {
        return try {
            httpClient.newCall(Request.Builder().url(url).build()).awaitResponse().use { resp ->
                if (!resp.isSuccessful) return@use false
                resp.body?.byteStream()?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.exists() && target.length() > 0
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
        val entity = item.toOfflineMediaEntity(imageUrl, backdropUrl)
        offlineMediaDao.upsert(entity)
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
        val entity = detail.toOfflineMediaEntity(imageUrl, backdropUrl)
        offlineMediaDao.upsert(entity)
        preloadImageToCache(imageUrl)
        preloadImageToCache(backdropUrl)
        // Preload up to 10 cast images so the offline cast row renders without
        // a network connection. Mirrors the poster/backdrop caching above.
        detail.people
            .filter { (it.type == "Actor" || it.type == "Director") && !it.primaryImageTag.isNullOrBlank() }
            .take(10)
            .forEach { person ->
                preloadImageToCache(playbackRepository.getImageUrl(person.id, maxWidth = 200))
            }
    }

    override fun enqueueDownload(downloadId: String) {
        enqueueDownloadWorker(downloadId)
    }

    override suspend fun setDownloadPriority(id: String, priority: Int): Result<Unit> = runCatching {
        downloadDao.updatePriority(id, priority)
    }

    private fun enqueueDownloadWorker(downloadId: String) {
        val prefs = preferencesStore.preferences.value
        val wifiOnly = prefs.wifiOnlyDownloads
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraintsBuilder = Constraints.Builder()
            .setRequiredNetworkType(networkType)

        var initialDelayMs = 0L
        if (prefs.downloadScheduleEnabled) {
            val now = java.util.Calendar.getInstance()
            val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
            val window = prefs.downloadScheduleWindow
            val start = window.startHour
            val end = window.endHour
            val inWindow = if (start <= end) {
                currentHour in start until end
            } else {
                currentHour >= start || currentHour < end
            }
            if (!inWindow) {
                val target = now.clone() as java.util.Calendar
                target.set(java.util.Calendar.HOUR_OF_DAY, start)
                target.set(java.util.Calendar.MINUTE, 0)
                target.set(java.util.Calendar.SECOND, 0)
                target.set(java.util.Calendar.MILLISECOND, 0)
                if (target.before(now)) target.add(java.util.Calendar.DAY_OF_MONTH, 1)
                initialDelayMs = target.timeInMillis - now.timeInMillis
            }
            if (window.wifiOnly) {
                constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
            }
        }

        val workRequestBuilder = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraintsBuilder.build())
            // Explicit exponential backoff so a flaky server returning 503/429
            // is not hammered by all concurrent downloads retrying as fast as
            // WorkManager allows. 30s base multiplies load far less than the
            // implicit default while still recovering promptly.
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                DOWNLOAD_BACKOFF_DELAY_MS,
                java.util.concurrent.TimeUnit.MILLISECONDS,
            )
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                    .build()
            )
            .addTag(DownloadWorker.WORK_TAG)
        if (initialDelayMs > 0) {
            workRequestBuilder.setInitialDelay(initialDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
            WorkManager.getInstance(context).enqueueUniqueWork(
            DownloadWorker.workName(downloadId),
            ExistingWorkPolicy.KEEP,
            workRequestBuilder.build(),
        )
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
        // Seed playback progress from server UserData so the download shows its
        // watched/resume state immediately.
        playbackPositionTicks = playbackPositionTicks,
        playedPercentage = PlayedStateSync.computePlayedPercentage(playbackPositionTicks, runTimeTicks, isPlayed),
        isPlayed = isPlayed,
        lastPlayedDate = null,
    )

    /**
     * Maps a [MediaDetail] (the rich server response) to an [OfflineMediaEntity],
     * additionally persisting original title, critic rating, studios, tagline,
     * and the cast as a JSON blob. Falls back to the item-level values for the
     * base fields so this stays consistent with [MediaItem.toOfflineMediaEntity].
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
        )
    }

    private suspend fun cleanupDownloadFiles(entity: DownloadEntity) {
        if (entity.downloadPath.isNotBlank()) {
            val file = File(entity.downloadPath)
            if (file.exists()) file.delete()
            DownloadArtifacts.cleanup(file.parentFile, entity.mediaItemId)
        }
        database.withTransaction {
            downloadDao.deleteDownloadById(entity.id)
            offlineMediaDao.deleteById(entity.mediaItemId)
            offlineMediaDao.deleteOrphanedSeasons()
            offlineMediaDao.deleteOrphanedSeries()
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
    private fun qualityToMaxBitrate(quality: DownloadQuality): Int? = when (quality) {
        DownloadQuality.ORIGINAL -> null
        DownloadQuality.HIGH_1080P -> 8_000_000
        DownloadQuality.MEDIUM_720P -> 3_000_000
        DownloadQuality.LOW_480P -> 1_500_000
    }

    companion object {
        private const val TAG = "DownloadRepository"
        private val FILENAME_SANITIZE_REGEX = Regex("[^a-zA-Z0-9.\\-]")

        // Exponential backoff base delay applied to every DownloadWorker
        // request so a flaky server is not hammered by concurrent retries.
        // Mirrored by DownloadRecoveryInitializer so cold-start re-enqueues
        // back off identically. WorkManager caps each retry delay at 5h.
        const val DOWNLOAD_BACKOFF_DELAY_MS = 30_000L

        // Container strings from Jellyfin (mkv, mp4, ts, webm, flv, mov, ...).
        // Constrained to 2-8 alphanumerics so a malformed/missing value can
        // never leak into the on-disk filename; the caller falls back to the
        // legacy mp4/mp3 default otherwise.
        private val FILENAME_CONTAINER_REGEX = Regex("[A-Za-z0-9]{2,8}")
    }
}
