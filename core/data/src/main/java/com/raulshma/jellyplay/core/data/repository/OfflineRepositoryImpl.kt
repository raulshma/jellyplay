package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRepositoryImpl @Inject constructor(
    private val offlineMediaDao: OfflineMediaDao,
    private val downloadDao: DownloadDao,
) : OfflineRepository {

    override fun getOfflineLibrary(): Flow<List<OfflineMediaItem>> =
        combine(
            offlineMediaDao.getTopLevelItems(),
            downloadDao.getAllDownloads(),
        ) { entities, downloads ->
            val downloadMap = downloads.associateBy { it.mediaItemId }
            entities.map { entity ->
                val download = downloadMap[entity.id]
                entity.toOfflineMediaItem().copy(
                    downloadPath = download?.downloadPath,
                    downloadStatus = try {
                        download?.status?.let { DownloadStatus.valueOf(it) }
                    } catch (_: Exception) {
                        null
                    },
                    downloadedBytes = download?.downloadedBytes ?: 0L,
                    totalSizeBytes = download?.totalSizeBytes ?: 0L,
                )
            }
        }

    override fun getSeasonsForSeries(seriesId: String): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getSeasonsForSeries(seriesId).map { entities ->
            entities.map { it.toOfflineMediaItem() }
        }

    override fun getEpisodesForSeason(seasonId: String): Flow<List<OfflineMediaItem>> =
        combine(
            offlineMediaDao.getEpisodesForSeason(seasonId),
            downloadDao.getAllDownloads(),
        ) { episodes, downloads ->
            val downloadMap = downloads.associateBy { it.mediaItemId }
            episodes.map { entity ->
                val download = downloadMap[entity.id]
                entity.toOfflineMediaItem().copy(
                    downloadPath = download?.downloadPath,
                    downloadStatus = try {
                        download?.status?.let { DownloadStatus.valueOf(it) }
                    } catch (_: Exception) {
                        null
                    },
                    downloadedBytes = download?.downloadedBytes ?: 0L,
                    totalSizeBytes = download?.totalSizeBytes ?: 0L,
                )
            }
        }

    override suspend fun getOfflineItem(id: String): OfflineMediaItem? =
        offlineMediaDao.getById(id)?.toOfflineMediaItem()

    override fun getOfflineItemCount(): Flow<Int> =
        offlineMediaDao.getOfflineItemCount()

    override suspend fun deleteOfflineItem(id: String) {
        downloadDao.getDownloadByMediaItemId(id)?.let { download ->
            if (download.downloadPath.isNotBlank()) {
                val file = java.io.File(download.downloadPath)
                if (file.exists()) file.delete()
            }
            downloadDao.deleteDownloadById(download.id)
        }
        offlineMediaDao.deleteById(id)
        cleanupOrphans()
    }

    override suspend fun deleteOfflineSeries(seriesId: String) {
        val downloads = downloadDao.getDownloadsForSeries(seriesId)
        for (download in downloads) {
            if (download.downloadPath.isNotBlank()) {
                val file = java.io.File(download.downloadPath)
                if (file.exists()) file.delete()
            }
            downloadDao.deleteDownloadById(download.id)
        }
        offlineMediaDao.deleteBySeriesId(seriesId)
        cleanupOrphans()
    }

    override suspend fun deleteOfflineSeason(seasonId: String) {
        val downloads = downloadDao.getDownloadsForSeason(seasonId)
        for (download in downloads) {
            if (download.downloadPath.isNotBlank()) {
                val file = java.io.File(download.downloadPath)
                if (file.exists()) file.delete()
            }
            downloadDao.deleteDownloadById(download.id)
        }
        offlineMediaDao.deleteBySeasonId(seasonId)
        cleanupOrphans()
    }

    override suspend fun cleanupOrphans() {
        offlineMediaDao.deleteOrphanedSeasons()
        offlineMediaDao.deleteOrphanedSeries()
    }

    private fun OfflineMediaEntity.toOfflineMediaItem() = OfflineMediaItem(
        id = id,
        name = name,
        mediaType = try { MediaType.valueOf(mediaType) } catch (_: Exception) { MediaType.UNKNOWN },
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
