package com.raulshma.jellyplay.core.data.repository

import androidx.room.withTransaction
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OfflineRepositoryImpl @Inject constructor(
    private val offlineMediaDao: OfflineMediaDao,
    private val downloadDao: DownloadDao,
    private val database: JellyPlayDatabase,
) : OfflineRepository {

    override fun getOfflineLibrary(): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getTopLevelItems().flatMapLatest { entities ->
            if (entities.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                downloadDao.getDownloadsByMediaItemIdsFlow(entities.map { it.id })
                    .map { downloads ->
                        val downloadMap = downloads.associateBy { it.mediaItemId }
                        entities.map { entity ->
                            val download = downloadMap[entity.id]
                            entity.toOfflineMediaItem().copy(
                                downloadPath = download?.downloadPath,
                                downloadStatus = download?.status?.let { safeDownloadStatusOf(it) },
                                downloadedBytes = download?.downloadedBytes ?: 0L,
                                totalSizeBytes = download?.totalSizeBytes ?: 0L,
                            )
                        }
                    }
            }
        }.distinctUntilChanged()

    override fun getSeasonsForSeries(seriesId: String): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getSeasonsForSeries(seriesId).map { entities ->
            entities.map { it.toOfflineMediaItem() }
        }

    override fun getEpisodesForSeason(seasonId: String): Flow<List<OfflineMediaItem>> =
        combine(
            offlineMediaDao.getEpisodesForSeason(seasonId),
            downloadDao.getAllDownloads().conflate(),
        ) { episodes, downloads ->
            val downloadMap = downloads.associateBy { it.mediaItemId }
            episodes.map { entity ->
                val download = downloadMap[entity.id]
                entity.toOfflineMediaItem().copy(
                    downloadPath = download?.downloadPath,
                    downloadStatus = download?.status?.let { safeDownloadStatusOf(it) },
                    downloadedBytes = download?.downloadedBytes ?: 0L,
                    totalSizeBytes = download?.totalSizeBytes ?: 0L,
                )
            }
        }.distinctUntilChanged()

    override suspend fun getOfflineItem(id: String): OfflineMediaItem? =
        offlineMediaDao.getById(id)?.toOfflineMediaItem()

    override fun getOfflineItemCount(): Flow<Int> =
        offlineMediaDao.getOfflineItemCount()

    override suspend fun deleteOfflineItem(id: String) {
        val download = downloadDao.getDownloadByMediaItemId(id)
        download?.let {
            if (it.downloadPath.isNotBlank()) {
                val file = java.io.File(it.downloadPath)
                if (file.exists()) file.delete()
                file.parentFile?.let { parent ->
                    val trickplayDir = java.io.File(parent, "trickplay")
                    if (trickplayDir.exists()) trickplayDir.deleteRecursively()
                }
            }
        }
        database.withTransaction {
            download?.let { downloadDao.deleteDownloadById(it.id) }
            offlineMediaDao.deleteById(id)
        }
        cleanupOrphans()
    }

    override suspend fun deleteOfflineSeries(seriesId: String) {
        val downloads = downloadDao.getDownloadsForSeries(seriesId)
        for (download in downloads) {
            if (download.downloadPath.isNotBlank()) {
                val file = java.io.File(download.downloadPath)
                if (file.exists()) file.delete()
                file.parentFile?.let { parent ->
                    val trickplayDir = java.io.File(parent, "trickplay")
                    if (trickplayDir.exists()) trickplayDir.deleteRecursively()
                }
            }
        }
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            offlineMediaDao.deleteBySeriesId(seriesId)
        }
        cleanupOrphans()
    }

    override suspend fun deleteOfflineSeason(seasonId: String) {
        val downloads = downloadDao.getDownloadsForSeason(seasonId)
        for (download in downloads) {
            if (download.downloadPath.isNotBlank()) {
                val file = java.io.File(download.downloadPath)
                if (file.exists()) file.delete()
                file.parentFile?.let { parent ->
                    val trickplayDir = java.io.File(parent, "trickplay")
                    if (trickplayDir.exists()) trickplayDir.deleteRecursively()
                }
            }
        }
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            offlineMediaDao.deleteBySeasonId(seasonId)
        }
        cleanupOrphans()
    }

    override suspend fun cleanupOrphans() {
        offlineMediaDao.cleanupOrphans()
    }

    private fun safeMediaTypeOf(name: String): MediaType =
        MediaType.entries.find { it.name == name } ?: MediaType.UNKNOWN

    private fun safeDownloadStatusOf(name: String): DownloadStatus? =
        DownloadStatus.entries.find { it.name == name }

    private fun OfflineMediaEntity.toOfflineMediaItem() = OfflineMediaItem(
        id = id,
        name = name,
        mediaType = safeMediaTypeOf(mediaType),
        overview = overview,
        year = year,
        communityRating = communityRating,
        officialRating = officialRating,
        runTimeTicks = runTimeTicks,
        seriesId = seriesId,
        seasonId = seasonId,
        seriesName = seriesName,
        seasonName = seasonName,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        posterPath = posterPath,
        backdropPath = backdropPath,
        blurHashPrimary = blurHashPrimary,
        blurHashBackdrop = blurHashBackdrop,
        genres = genres?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() } ?: emptyList(),
        childCount = childCount ?: 0,
    )
}
