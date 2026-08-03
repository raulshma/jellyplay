package com.raulshma.jellyplay.core.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.SeriesSizeAggregate
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
                downloadDao.getDownloadByMediaItemIdFlow(id).flatMapLatest { download ->
                    val item = entity.toOfflineMediaItem().copy(
                        downloadPath = download?.downloadPath,
                        downloadStatus = download?.status?.let { safeDownloadStatusOf(it) },
                        downloadedBytes = download?.downloadedBytes ?: 0L,
                        totalSizeBytes = download?.totalSizeBytes ?: 0L,
                    )
                    // Episode/series rows carry no self-contained local artwork
                    // by design (episodes use the series backdrop; the series
                    // row resolves its images beside downloaded episodes), so
                    // resolve the local-file fallback here. Skips everything
                    // else (movies, albums) whose own downloads always produce
                    // local files.
                    if (entity.mediaType == MediaType.EPISODE.name || entity.mediaType == MediaType.SERIES.name) {
                        flow { emit(resolveLocalArtwork(item)) }
                    } else {
                        flowOf(item)
                    }
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
                // Partition top-level ids by type: SERIES has no `downloads`
                // row of its own (episodes are downloaded individually, each
                // carrying `seriesId`), so its size must be aggregated from its
                // episodes. Movies/standalone audio keep the direct per-row
                // join. Querying each partition only by the ids it can match
                // avoids a wasted left-join against every series id.
                val seriesIds = entities.asSequence()
                    .filter { it.mediaType == MediaType.SERIES.name }
                    .map { it.id }
                    .toList()
                val directIds = entities.asSequence()
                    .filter { it.mediaType != MediaType.SERIES.name }
                    .map { it.id }
                    .toList()
                val directDownloadsFlow: Flow<Map<String, DownloadEntity>> =
                    if (directIds.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        downloadDao.getDownloadsByMediaItemIdsFlow(directIds)
                            .map { it.associateBy { d -> d.mediaItemId } }
                    }
                val seriesAggregatesFlow: Flow<Map<String, SeriesSizeAggregate>> =
                    if (seriesIds.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        downloadDao.getSeriesSizeAggregatesFlow(seriesIds)
                            .map { it.associateBy { a -> a.seriesId } }
                    }
                // Combine both maps so the summary re-emits as episode
                // downloads progress (Room re-emits each Flow on writes to its
                // tables). SERIES rows take their bytes from the aggregate;
                // everything else from its direct download row.
                combine(directDownloadsFlow, seriesAggregatesFlow) { downloadMap, aggregateMap ->
                    entities.map { entity ->
                        if (entity.mediaType == MediaType.SERIES.name) {
                            val agg = aggregateMap[entity.id]
                            entity.toOfflineMediaItem().copy(
                                downloadedBytes = agg?.downloadedBytes ?: 0L,
                                totalSizeBytes = agg?.totalSizeBytes ?: 0L,
                            )
                        } else {
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
                DownloadArtifacts.cleanup(file.parentFile, it.mediaItemId)
            }
        }
        database.withTransaction {
            download?.let { downloadDao.deleteDownloadById(it.id) }
            offlineMediaDao.deleteById(id)
        }
        cleanupOrphans()
    }

    override suspend fun deleteOfflineSeries(seriesId: String) {
        // Union the direct seriesId lookup with the offline_media join so legacy
        // episode downloads (whose downloads.seriesId was never populated before
        // the series link was propagated at download time) are recovered and
        // cleaned up rather than orphaned alongside a deleted series.
        val downloads = (
            downloadDao.getDownloadsForSeries(seriesId) +
            downloadDao.getDownloadsForSeriesViaOfflineMedia(seriesId)
        ).distinctBy { it.id }
        deleteArtifactsParallel(downloads)
        // Series-scoped artwork (${seriesId}_poster.jpg / _backdrop.jpg) lives
        // beside each downloaded episode; prune it from every episode dir now
        // that no row references the series anymore.
        downloads
            .asSequence()
            .mapNotNull { it.downloadPath.takeIf { p -> p.isNotBlank() } }
            .mapNotNull { java.io.File(it).parentFile }
            .distinct()
            .forEach { DownloadArtifacts.cleanupSeriesArtwork(it, seriesId) }
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            offlineMediaDao.deleteBySeriesId(seriesId)
        }
        cleanupOrphans()
    }

    override suspend fun deleteOfflineSeason(seasonId: String) {
        val downloads = (
            downloadDao.getDownloadsForSeason(seasonId) +
            downloadDao.getDownloadsForSeasonViaOfflineMedia(seasonId)
        ).distinctBy { it.id }
        deleteArtifactsParallel(downloads)
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
     *
     * Each download's per-item poster/backdrop are scoped by [DownloadEntity.mediaItemId]
     * so deleting one item's artifacts never clobbers another item's images in
     * the shared flat downloads dir.
     */
    private suspend fun deleteArtifactsParallel(downloads: List<DownloadEntity>) {
        val nonBlank = downloads.filter { it.downloadPath.isNotBlank() }
        if (nonBlank.isEmpty()) return
        coroutineScope {
            nonBlank.map { entity ->
                async(Dispatchers.IO) {
                    val file = java.io.File(entity.downloadPath)
                    if (file.exists()) file.delete()
                    DownloadArtifacts.cleanup(file.parentFile, entity.mediaItemId)
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

    override suspend fun applyPlayedState(itemId: String, isPlayed: Boolean) {
        // The batch UPDATE matches the item and every row in its hierarchy
        // (parentId / seasonId / seriesId), mirroring Jellyfin's server-side
        // cascade for markPlayedItem / markUnplayedItem. Zero rows match for a
        // non-downloaded item, so there is no existence guard here. Stamp
        // lastPlayedDate on mark-played (matches server UserData semantics) and
        // clear it on mark-unplayed so the offline row reflects the reset.
        offlineMediaDao.applyPlayedStateToHierarchy(
            itemId = itemId,
            isPlayed = isPlayed,
            lastPlayedDate = if (isPlayed) java.time.OffsetDateTime.now().toString() else null,
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

    /**
     * Resolves the local-file artwork fallbacks for episode/series rows whose
     * persisted paths are blank or remote URLs (legacy rows, or episodes that
     * by design store no backdrop of their own).
     *
     * **Episode rows** — the detail-screen hero mirrors the online screen,
     * which resolves episode backdrops to the SERIES backdrop. When the
     * episode's persisted backdrop/poster is blank or a remote URL, it is
     * substituted with the series' local files (from the series row, or found
     * beside the episode's own download for legacy single-episode seeding).
     *
     * **Series rows** — the batch download path writes `${seriesId}_poster.jpg`
     * / `_backdrop.jpg` beside the first enqueued episode but the pre-seeded
     * row can hold remote URLs (legacy). The local files are resolved beside
     * any downloaded episode.
     *
     * Already-local paths are kept as-is; when no local substitute exists the
     * original value is preserved (it may still load online), so this never
     * degrades a working row.
     */
    private suspend fun resolveLocalArtwork(item: OfflineMediaItem): OfflineMediaItem = when (item.mediaType) {
        MediaType.EPISODE -> resolveEpisodeArtwork(item)
        MediaType.SERIES -> resolveSeriesArtwork(item)
        else -> item
    }

    private suspend fun resolveEpisodeArtwork(item: OfflineMediaItem): OfflineMediaItem {
        if (!needsArtworkResolution(item.backdropPath) && !needsArtworkResolution(item.posterPath)) return item
        val seriesId = item.seriesId ?: return item
        // Legacy single-episode downloads seeded the series artwork beside the
        // episode file itself; prefer the series row's local paths, then fall
        // back to files found in the episode's own directory.
        val seriesRow = offlineMediaDao.getById(seriesId)
        val episodeDir = item.downloadPath
            ?.takeIf { it.isNotBlank() }
            ?.let { java.io.File(it).parentFile }
        val seriesBackdrop = seriesRow?.backdropPath?.takeIf(::isLocalPath)
            ?: episodeDir?.let { dir ->
                java.io.File(dir, DownloadArtifacts.backdropFile(seriesId))
                    .takeIf { it.exists() }?.absolutePath
            }
        val seriesPoster = seriesRow?.posterPath?.takeIf(::isLocalPath)
            ?: episodeDir?.let { dir ->
                java.io.File(dir, DownloadArtifacts.posterFile(seriesId))
                    .takeIf { it.exists() }?.absolutePath
            }
        return item.copy(
            // Local substitute wins; keep the original (remote) path only when
            // no local file exists — it may still load while online.
            backdropPath = if (needsArtworkResolution(item.backdropPath)) seriesBackdrop ?: item.backdropPath else item.backdropPath,
            posterPath = if (needsArtworkResolution(item.posterPath)) seriesPoster ?: item.posterPath else item.posterPath,
        )
    }

    private suspend fun resolveSeriesArtwork(item: OfflineMediaItem): OfflineMediaItem {
        if (!needsArtworkResolution(item.backdropPath) && !needsArtworkResolution(item.posterPath)) return item
        // The series has no download row of its own; its local artwork is
        // written beside downloaded episodes, so scan their directories.
        val seriesDir = downloadDao.getDownloadsForSeries(item.id)
            .asSequence()
            .mapNotNull { it.downloadPath.takeIf { p -> p.isNotBlank() } }
            .mapNotNull { java.io.File(it).parentFile }
            .firstOrNull()
        val backdrop = seriesDir?.let { dir ->
            java.io.File(dir, DownloadArtifacts.backdropFile(item.id))
                .takeIf { it.exists() }?.absolutePath
        }
        val poster = seriesDir?.let { dir ->
            java.io.File(dir, DownloadArtifacts.posterFile(item.id))
                .takeIf { it.exists() }?.absolutePath
        }
        return item.copy(
            backdropPath = if (needsArtworkResolution(item.backdropPath)) backdrop ?: item.backdropPath else item.backdropPath,
            posterPath = if (needsArtworkResolution(item.posterPath)) poster ?: item.posterPath else item.posterPath,
        )
    }

    /** True when [path] is absent or a server URL — i.e. not a local file. */
    private fun needsArtworkResolution(path: String?): Boolean =
        path.isNullOrBlank() || isRemoteUrl(path)

    private fun isRemoteUrl(path: String): Boolean =
        path.startsWith("http://") || path.startsWith("https://")

    /** True when [path] is an existing local file (not a server URL). */
    private fun isLocalPath(path: String): Boolean =
        path.isNotBlank() && !isRemoteUrl(path)

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

