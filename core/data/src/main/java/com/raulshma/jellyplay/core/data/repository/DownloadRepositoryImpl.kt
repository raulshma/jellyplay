package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import android.os.Environment
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val offlineMediaDao: OfflineMediaDao,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val preferencesStore: com.raulshma.jellyplay.core.datastore.UserPreferencesStore,
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadItem>> =
        downloadDao.getAllDownloads().map { entities ->
            entities.map { it.toDownloadItem() }
        }

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
    ): Result<DownloadItem> = runCatching {
        val existing = downloadDao.getDownloadByMediaItemId(mediaItemId)
        if (existing != null && existing.status != DownloadStatus.FAILED.name && existing.status != DownloadStatus.CANCELLED.name) {
            return@runCatching existing.toDownloadItem()
        }

        val maxBytes = preferencesStore.preferences.first().maxCacheSizeMb.toLong() * 1024 * 1024
        val currentBytes = downloadDao.getTotalDownloadedBytes()
        if (currentBytes >= maxBytes) {
            throw IllegalStateException("Download limit reached (${preferencesStore.preferences.first().maxCacheSizeMb} MB). Delete some downloads first.")
        }

        val id = UUID.randomUUID().toString()
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        val safeName = name.replace(Regex("[^a-zA-Z0-9.\\-]"), "_")
        val extension = if (mediaType == MediaType.AUDIO.name) "mp3" else "mp4"
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
        )
        downloadDao.insertDownload(entity)
        entity.toDownloadItem()
    }

    override suspend fun cancelDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        downloadDao.updateProgress(id, entity.downloadedBytes, DownloadStatus.CANCELLED.name)
    }

    override suspend fun pauseDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        if (entity.status == DownloadStatus.DOWNLOADING.name || entity.status == DownloadStatus.PENDING.name) {
            downloadDao.updateProgress(id, entity.downloadedBytes, DownloadStatus.PAUSED.name)
        }
    }

    override suspend fun resumeDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        if (entity.status == DownloadStatus.PAUSED.name || entity.status == DownloadStatus.FAILED.name) {
            downloadDao.updateProgress(id, entity.downloadedBytes, DownloadStatus.PENDING.name)
        }
    }

    override suspend fun deleteDownload(id: String): Result<Unit> = runCatching {
        val entity = downloadDao.getDownloadById(id) ?: return@runCatching
        val file = File(entity.downloadPath)
        if (file.exists()) file.delete()
        downloadDao.deleteDownloadById(id)
        offlineMediaDao.deleteById(entity.mediaItemId)
    }

    override suspend fun retryDownload(id: String): Result<Unit> = runCatching {
        downloadDao.updateProgress(id, 0L, DownloadStatus.PENDING.name)
    }

    override suspend fun getTotalDownloadedBytes(): Long =
        downloadDao.getTotalDownloadedBytes()

    override suspend fun saveOfflineMediaItem(item: MediaItem, imageUrl: String?, backdropUrl: String?) {
        saveOfflineMetadataForItem(item, imageUrl, backdropUrl)

        if (item.mediaType == MediaType.EPISODE) {
            val seriesId = item.seriesId
            val seasonId = item.seasonId

            if (seriesId != null && offlineMediaDao.getById(seriesId) == null) {
                val seriesDetail = mediaRepository.getMediaDetail(seriesId).getOrNull()
                if (seriesDetail != null) {
                    val seriesImageUrl = playbackRepository.getImageUrl(seriesId, maxWidth = 300)
                    val seriesBackdropUrl = playbackRepository.getBackdropUrl(seriesId, maxWidth = 1280)
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

    override suspend fun downloadSeries(
        seriesId: String,
        seasonIds: List<String>?,
    ): Result<List<String>> = runCatching {
        withContext(Dispatchers.IO) {
            val detail = mediaRepository.getMediaDetail(seriesId).getOrThrow()
            val seriesItem = detail.item
            val imageUrl = playbackRepository.getImageUrl(seriesId, maxWidth = 300)
            val backdropUrl = playbackRepository.getBackdropUrl(seriesId, maxWidth = 1280)

            saveOfflineMetadataForItem(seriesItem, imageUrl, backdropUrl)

            val seasons = mediaRepository.getSeasons(seriesId).getOrElse { emptyList() }
            val targetSeasons = if (seasonIds != null) {
                seasons.filter { it.id in seasonIds }
            } else {
                seasons
            }

            val downloadIds = mutableListOf<String>()

            for (season in targetSeasons) {
                saveOfflineMetadataForItem(season, null, null)

                val episodes = mediaRepository.getEpisodes(seriesId, season.id).getOrElse { emptyList() }
                val offlineEntities = mutableListOf<OfflineMediaEntity>()

                for (episode in episodes) {
                    try {
                        val episodeDetail = mediaRepository.getMediaDetail(episode.id).getOrNull()
                        val source = episodeDetail?.mediaSources?.firstOrNull()
                        val streamUrl = if (source != null) {
                            playbackRepository.getStreamUrl(episode.id, source.id)
                        } else {
                            playbackRepository.getStreamUrl(episode.id, episode.id)
                        }

                        if (streamUrl.isNotBlank()) {
                            val epImageUrl = playbackRepository.getImageUrl(episode.id, maxWidth = 300)
                            offlineEntities.add(episode.toOfflineMediaEntity(epImageUrl, null))

                            val download = startDownload(
                                mediaItemId = episode.id,
                                name = episode.name,
                                mediaType = MediaType.EPISODE.name,
                                mediaSourceId = source?.id ?: episode.id,
                                downloadUrl = streamUrl,
                                imageUrl = epImageUrl,
                                imageBlurHash = episode.blurHashes.primary,
                                seriesId = seriesId,
                                seasonId = season.id,
                                seriesName = seriesItem.name,
                                seasonName = season.name,
                                episodeNumber = episode.episodeNumber,
                                seasonNumber = episode.seasonNumber,
                            ).getOrNull()

                            if (download != null) {
                                preloadImageToCache(epImageUrl)
                                enqueueDownloadWorker(download.id)
                                downloadIds.add(download.id)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                if (offlineEntities.isNotEmpty()) {
                    offlineMediaDao.upsertAll(offlineEntities)
                }
            }

            downloadIds
        }
    }

    private suspend fun saveOfflineMetadataForItem(item: MediaItem, imageUrl: String?, backdropUrl: String?) {
        val entity = item.toOfflineMediaEntity(imageUrl, backdropUrl)
        offlineMediaDao.upsert(entity)
        preloadImageToCache(imageUrl)
        preloadImageToCache(backdropUrl)
    }

    private fun enqueueDownloadWorker(downloadId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${DownloadWorker.UNIQUE_WORK_PREFIX}$downloadId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    private suspend fun preloadImageToCache(url: String?) {
        if (url.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            try {
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(512, 512)
                    .build()
                imageLoader.execute(request)
            } catch (_: Exception) { }
        }
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
        seriesName = seriesName,
        seasonName = seasonName,
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
    )
}
