package com.raulshma.jellyplay.core.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimum query length (in characters) before offline search is performed.
 * Anything shorter would match too many unrelated items.
 */
const val MIN_OFFLINE_SEARCH_LENGTH: Int = 2

private val MEDIA_TYPE_BY_NAME: Map<String, MediaType> = MediaType.entries.associateBy { it.name }
private val DOWNLOAD_STATUS_BY_NAME: Map<String, DownloadStatus> = DownloadStatus.entries.associateBy { it.name }

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OfflineRepositoryImpl @Inject constructor(
    private val offlineMediaDao: OfflineMediaDao,
    private val downloadDao: DownloadDao,
    private val database: JellyPlayDatabase,
) : OfflineRepository {

    override fun getOfflineDetail(id: String): Flow<OfflineMediaItem?> =
        offlineMediaDao.getByIdFlow(id).flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                downloadDao.getDownloadByMediaItemIdFlow(id).map { download ->
                    entity.toOfflineMediaItem().copy(
                        downloadPath = download?.downloadPath,
                        downloadStatus = download?.status?.let { safeDownloadStatusOf(it) },
                        downloadedBytes = download?.downloadedBytes ?: 0L,
                        totalSizeBytes = download?.totalSizeBytes ?: 0L,
                    )
                }
            }
        }.distinctUntilChanged()

    override fun getChildren(parentId: String): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getChildrenByParent(parentId).flatMapLatest { children ->
            val ids = children.map { it.id }
            if (ids.isEmpty()) {
                flowOf(emptyList())
            } else {
                downloadDao.getDownloadsByMediaItemIdsFlow(ids).map { downloads ->
                    val downloadMap = downloads.associateBy { it.mediaItemId }
                    children.map { entity ->
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
        }.distinctUntilChanged()

    override fun getEpisodesForSeason(seasonId: String): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getEpisodesForSeason(seasonId).flatMapLatest { episodes ->
            val ids = episodes.map { it.id }
            if (ids.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
            else downloadDao.getDownloadsByMediaItemIdsFlow(ids).map { downloads ->
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
                DownloadArtifacts.cleanup(file.parentFile)
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
        deleteArtifactsParallel(downloads.map { it.downloadPath })
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            offlineMediaDao.deleteBySeriesId(seriesId)
        }
        cleanupOrphans()
    }

    override suspend fun deleteOfflineSeason(seasonId: String) {
        val downloads = downloadDao.getDownloadsForSeason(seasonId)
        deleteArtifactsParallel(downloads.map { it.downloadPath })
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            offlineMediaDao.deleteBySeasonId(seasonId)
        }
        cleanupOrphans()
    }

    /**
     * Deletes downloaded artifact files concurrently (was a serial per-episode
     * `File.delete()` + `cleanup()` loop — for a 100-episode series that was
     * 100+ serial FS syscalls). Runs on Dispatchers.IO; the subsequent DB
     * transaction does not depend on the file deletion result.
     */
    private suspend fun deleteArtifactsParallel(downloadPaths: List<String>) {
        val paths = downloadPaths.filter { it.isNotBlank() }
        if (paths.isEmpty()) return
        coroutineScope {
            paths.map { path ->
                async(Dispatchers.IO) {
                    val file = java.io.File(path)
                    if (file.exists()) file.delete()
                    DownloadArtifacts.cleanup(file.parentFile)
                }
            }.awaitAll()
        }
    }

    override suspend fun cleanupOrphans() {
        offlineMediaDao.cleanupOrphans()
    }

    override suspend fun updatePlaybackProgress(
        itemId: String,
        positionTicks: Long?,
        percentage: Double,
        isPlayed: Boolean,
    ) {
        // The UPDATE's `WHERE id = :itemId` already no-ops for a server-only
        // item (no offline row), so the previous `getById` guard was a 28-column
        // SELECT * on every playback-progress tick just to null-check existence.
        offlineMediaDao.updatePlaybackProgress(
            itemId = itemId,
            positionTicks = positionTicks,
            percentage = percentage.coerceIn(0.0, 100.0),
            isPlayed = isPlayed,
            lastPlayedDate = java.time.OffsetDateTime.now().toString(),
        )
    }

    override suspend fun searchOffline(query: String, limit: Int): List<OfflineMediaItem> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_OFFLINE_SEARCH_LENGTH || limit <= 0) return emptyList()
        // Escape LIKE wildcards in the user-supplied query so characters like
        // `%` and `_` are treated as literals. SQLite LIKE uses `\` as the
        // default escape character when the ESCAPE clause is supplied.
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        val pattern = "%$escaped%"
        val prefixPattern = "$escaped%"
        return offlineMediaDao
            .search(pattern = pattern, prefixPattern = prefixPattern, limit = limit)
            .map { it.toOfflineMediaItem() }
    }

    private fun safeMediaTypeOf(name: String): MediaType =
        MEDIA_TYPE_BY_NAME[name] ?: MediaType.UNKNOWN

    private fun safeDownloadStatusOf(name: String): DownloadStatus? =
        DOWNLOAD_STATUS_BY_NAME[name]

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
        playbackPositionTicks = playbackPositionTicks,
        playedPercentage = playedPercentage,
        isPlayed = isPlayed,
        lastPlayedDate = lastPlayedDate,
        originalTitle = originalTitle,
        criticRating = criticRating,
        studios = studios?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() } ?: emptyList(),
        tagline = tagline,
        cast = decodeCast(peopleJson),
        createdAt = createdAt,
    )
}

/** Reusable lenient Json for (de)serializing the offline cast JSON column. */
internal val offlineJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Decodes a [peopleJson] blob into a cast list, tolerating null/garbage rows. */
private fun decodeCast(peopleJson: String?): List<OfflinePersonInfo> {
    if (peopleJson.isNullOrBlank()) return emptyList()
    return runCatching {
        offlineJson.decodeFromString<List<OfflinePersonInfo>>(peopleJson)
    }.getOrDefault(emptyList())
}

/** Encodes a cast list into the persisted JSON column form. */
internal fun encodeCast(people: List<OfflinePersonInfo>): String =
    offlineJson.encodeToString(people)

