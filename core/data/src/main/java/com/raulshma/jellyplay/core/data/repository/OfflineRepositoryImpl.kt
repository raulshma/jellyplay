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
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import com.raulshma.jellyplay.core.model.offlineSyncStateOf
import java.io.File
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
                    // Resolve disk-backed local artwork for every media type so
                    // the offline detail hero/poster render without network.
                    // Covers legacy rows (remote URLs written before local-file
                    // persistence) and image-write-failure fallbacks — the
                    // resolver substitutes an existing local file when one is
                    // beside the download, else preserves the original value.
                    flow<OfflineMediaItem?> { emit(resolveItemArtwork(item)) }
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
                }.flatMapLatest { items -> flow { emit(resolveArtworkList(items)) } }
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
                }.flatMapLatest { items -> flow { emit(resolveArtworkList(items)) } }
            }
        }.distinctUntilChanged()

    override fun getSeasonsForSeries(seriesId: String): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getSeasonsForSeries(seriesId).map { entities ->
            entities.map { it.toOfflineMediaItem() }
        }.flatMapLatest { items -> flow { emit(resolveArtworkList(items)) } }
            .distinctUntilChanged()

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
            }.flatMapLatest { items -> flow { emit(resolveArtworkList(items)) } }
        }.distinctUntilChanged()

    override suspend fun getOfflineItem(id: String): OfflineMediaItem? =
        offlineMediaDao.getById(id)?.toOfflineMediaItem()?.let { resolveItemArtwork(it) }

    override fun getOfflineItemCount(): Flow<Int> =
        offlineMediaDao.getOfflineItemCount()

    override suspend fun deleteOfflineItem(id: String) {
        val download = downloadDao.getDownloadByMediaItemId(id)
        // Capture the row + its artifact dir before the DB delete so the cast
        // images written beside the download at fetch time can be pruned
        // afterward. Reference counting keeps a person's shared image when
        // another offline item still references them — a person can appear
        // across many movies/episodes and the `personId`-keyed image serves
        // all of them.
        val entity = offlineMediaDao.getById(id)
        val parentDir = download?.takeIf { it.downloadPath.isNotBlank() }
            ?.let { File(it.downloadPath).parentFile }
        download?.let {
            if (it.downloadPath.isNotBlank()) {
                val file = File(it.downloadPath)
                if (file.exists()) file.delete()
                DownloadArtifacts.cleanup(parentDir, it.mediaItemId)
            }
        }
        database.withTransaction {
            download?.let { downloadDao.deleteDownloadById(it.id) }
            offlineMediaDao.deleteById(id)
        }
        // Prune cast images after the row is gone so the reference scan only
        // counts surviving rows.
        cleanupOrphanedCastArtwork(
            parentDirs = listOfNotNull(parentDir),
            candidateCastIds = entity?.let(::castIdsOf).orEmpty(),
        )
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
        val episodeDirs = downloads
            .asSequence()
            .mapNotNull { it.downloadPath.takeIf { p -> p.isNotBlank() } }
            .mapNotNull { File(it).parentFile }
            .distinct()
            .toList()
        episodeDirs.forEach { DownloadArtifacts.cleanupSeriesArtwork(it, seriesId) }
        // Capture the deleted episodes' cast ids before the rows are removed so
        // the cast images written beside each episode at fetch time can be
        // pruned afterward (reference scan runs post-delete).
        val deletedCastIds = downloads.map { offlineMediaDao.getById(it.mediaItemId) }
            .filterNotNull()
            .flatMap(::castIdsOf)
            .distinct()
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            offlineMediaDao.deleteBySeriesId(seriesId)
        }
        cleanupOrphanedCastArtwork(episodeDirs, deletedCastIds)
        cleanupOrphans()
    }

    override suspend fun deleteOfflineSeason(seasonId: String) {
        val downloads = (
            downloadDao.getDownloadsForSeason(seasonId) +
            downloadDao.getDownloadsForSeasonViaOfflineMedia(seasonId)
        ).distinctBy { it.id }
        deleteArtifactsParallel(downloads)
        // Capture the season's episode dirs + cast ids before the rows are
        // removed so the cast images can be pruned afterward. The reference
        // scan runs post-delete and counts only surviving rows, so a person
        // still referenced by a sibling season's row keeps their shared image.
        val episodeDirs = downloads
            .asSequence()
            .mapNotNull { it.downloadPath.takeIf { p -> p.isNotBlank() } }
            .mapNotNull { File(it).parentFile }
            .distinct()
            .toList()
        val deletedCastIds = downloads.map { offlineMediaDao.getById(it.mediaItemId) }
            .filterNotNull()
            .flatMap(::castIdsOf)
            .distinct()
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            offlineMediaDao.deleteBySeasonId(seasonId)
        }
        cleanupOrphanedCastArtwork(episodeDirs, deletedCastIds)
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
     * the shared flat downloads dir. Cast images are pruned separately via
     * [cleanupOrphanedCastArtwork] (they are keyed by `personId`, not `mediaItemId`).
     */
    private suspend fun deleteArtifactsParallel(downloads: List<DownloadEntity>) {
        val nonBlank = downloads.filter { it.downloadPath.isNotBlank() }
        if (nonBlank.isEmpty()) return
        coroutineScope {
            nonBlank.map { entity ->
                async(Dispatchers.IO) {
                    val file = File(entity.downloadPath)
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

    override fun getOfflineSyncState(id: String): Flow<OfflineSyncState?> =
        offlineMediaDao.getByIdFlow(id).map { entity ->
            // Project the persisted sync columns to a UI-facing state via the
            // shared mapper (same one OfflineSyncManager uses) so the DB-driven
            // badge and the check/resync-driven state agree. The reactive
            // getByIdFlow re-emits on any row write, so a check/resync that flips
            // a flag refreshes the badge on its own.
            if (entity == null) null
            else offlineSyncStateOf(
                checking = entity.syncChecking,
                error = entity.syncError,
                mediaChanged = entity.syncMediaChanged,
                updateAvailable = entity.syncUpdateAvailable,
                lastSyncedAt = entity.lastSyncedAt,
            )
        }

    override fun getUpdatesCount(): Flow<Int> = offlineMediaDao.getUpdatesCount()

    override fun getItemsWithUpdates(): Flow<List<OfflineSyncUpdate>> =
        offlineMediaDao.getItemsWithUpdates().map { rows ->
            rows.map {
                OfflineSyncUpdate(
                    id = it.id,
                    name = it.name,
                    mediaFileChanged = it.mediaFileChanged == 1,
                    // Map the raw DB string to the typed enum at the repository
                    // boundary so the UI never compares against a magic string.
                    // Matches DownloadRepositoryImpl's mediaType mapping.
                    mediaType = it.mediaType?.let { mt ->
                        com.raulshma.jellyplay.core.model.MediaType.values()
                            .firstOrNull { e -> e.name.equals(mt, ignoreCase = true) }
                    },
                    seriesName = it.seriesName,
                    seasonNumber = it.seasonNumber,
                    episodeNumber = it.episodeNumber,
                )
            }
        }

    override suspend fun getDownloadedItemIds(): List<String> =
        offlineMediaDao.getDownloadedItemIds()

    /**
     * Resolves disk-backed local artwork for an item of ANY media type so it
     * renders without a network connection.
     *
     * Runs in every read path (library grid, episode lists, detail, series),
     * not just the detail screen — otherwise rows whose persisted
     * `posterPath`/`backdropPath` are blank or a remote URL (legacy downloads
     * from before local-file persistence, or image-write-failure fallbacks at
     * download time) render broken offline even when the local file sits on
     * disk right beside the media.
     *
     * Resolution order per field:
     *  1. Keep an already-local path as-is.
     *  2. Look for the item's own artifact beside its download
     *     (`${itemId}_poster.jpg` / `_backdrop.jpg`) — covers MOVIE/AUDIO and
     *     per-episode posters.
     *  3. For episodes, fall back to the parent series' artwork (series row's
     *     local path, then the series artifact beside the episode dir) — the
     *     online detail hero mirrors this, and Jellyfin rarely has a backdrop
     *     for an episode.
     *  4. For series, fall back to the artwork written beside a downloaded
     *     episode (the series row itself has no download path).
     *  5. Preserve the original value (a remote URL may still load online, and
     *     a null stays null), so this never degrades a working row.
     *
     * Cheap: a couple of `File.exists()` stats per item; Room only re-emits on
     * table writes, not per recomposition.
     */
    private suspend fun resolveItemArtwork(item: OfflineMediaItem): OfflineMediaItem {
        // First: the item's own artifact beside its media file (all types).
        val ownDir = item.downloadPath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).parentFile }
        val ownPoster = ownDir?.let { localArtifactOrNull(it, DownloadArtifacts.posterFile(item.id)) }
        val ownBackdrop = ownDir?.let { localArtifactOrNull(it, DownloadArtifacts.backdropFile(item.id)) }
        val (resolvedPoster, resolvedBackdrop) = when (item.mediaType) {
            MediaType.EPISODE -> resolveEpisodeSeriesArtwork(item, ownPoster, ownBackdrop)
            MediaType.SERIES -> resolveSeriesArtwork(item, ownPoster, ownBackdrop)
            else -> ownPoster to ownBackdrop
        }
        val posterResolved = if (needsArtworkResolution(item.posterPath)) resolvedPoster ?: item.posterPath else item.posterPath
        val backdropResolved = if (needsArtworkResolution(item.backdropPath)) resolvedBackdrop ?: item.backdropPath else item.backdropPath
        // Resolve cast image paths last: cast images are written beside the same
        // parent dir as posters/backdrops (keyed by personId), so reuse whichever
        // artifact dir was located above. Movies/standalone items use ownDir;
        // series rows resolve their first episode's dir; episodes inherit their
        // parent series dir. Skipped entirely when there is no cast to resolve.
        val castDir = castDirFor(item, ownDir)
        val resolvedCast = if (castDir != null && item.cast.isNotEmpty()) {
            resolveCastArtwork(item.cast, castDir)
        } else {
            item.cast
        }
        // Fast path: nothing on the row needed artwork resolution (no remote
        // poster/backdrop and no cast, or cast but no artifact dir). Avoids a
        // copy() allocation on the hot library-grid read path.
        return if (posterResolved == item.posterPath &&
            backdropResolved == item.backdropPath &&
            resolvedCast === item.cast
        ) {
            item
        } else {
            item.copy(
                posterPath = posterResolved,
                backdropPath = backdropResolved,
                cast = resolvedCast,
            )
        }
    }

    /**
     * Resolves local artwork for a list of items (library grid, episode lists,
     * album tracks). Each item that already has a local path short-circuits
     * with zero FS work; only rows needing resolution stat the artifact files.
     * See [resolveItemArtwork] for the per-field policy.
     */
    private suspend fun resolveArtworkList(items: List<OfflineMediaItem>): List<OfflineMediaItem> =
        if (items.isEmpty()) items else items.map { resolveItemArtwork(it) }

    /**
     * Episode fallback: prefer the series' local artwork (series row's local
     * path, then the series artifact found beside the episode dir) since
     * Jellyfin rarely carries a backdrop per episode and the online detail
     * hero resolves to the series backdrop. The item's own poster/backdrop
     * (passed in) win when present.
     */
    private suspend fun resolveEpisodeSeriesArtwork(
        item: OfflineMediaItem,
        ownPoster: String?,
        ownBackdrop: String?,
    ): Pair<String?, String?> {
        val seriesId = item.seriesId ?: return ownPoster to ownBackdrop
        val seriesRow = offlineMediaDao.getById(seriesId)
        val episodeDir = item.downloadPath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).parentFile }
        val seriesBackdrop = seriesRow?.backdropPath?.takeIf(::isLocalPath)
            ?: episodeDir?.let { localArtifactOrNull(it, DownloadArtifacts.backdropFile(seriesId)) }
        val seriesPoster = seriesRow?.posterPath?.takeIf(::isLocalPath)
            ?: episodeDir?.let { localArtifactOrNull(it, DownloadArtifacts.posterFile(seriesId)) }
        return (ownPoster ?: seriesPoster) to (ownBackdrop ?: seriesBackdrop)
    }

    /**
     * Series fallback: the series row has no download path of its own, so its
     * local artwork (written beside the first enqueued episode) is resolved by
     * scanning a downloaded episode's directory. The item's own artifact
     * (passed in) wins when present.
     */
    private suspend fun resolveSeriesArtwork(
        item: OfflineMediaItem,
        ownPoster: String?,
        ownBackdrop: String?,
    ): Pair<String?, String?> {
        val seriesDir = downloadDao.getDownloadsForSeries(item.id)
            .asSequence()
            .mapNotNull { it.downloadPath.takeIf { p -> p.isNotBlank() } }
            .mapNotNull { File(it).parentFile }
            .firstOrNull()
        val seriesBackdrop = seriesDir?.let { localArtifactOrNull(it, DownloadArtifacts.backdropFile(item.id)) }
        val seriesPoster = seriesDir?.let { localArtifactOrNull(it, DownloadArtifacts.posterFile(item.id)) }
        return (ownPoster ?: seriesPoster) to (ownBackdrop ?: seriesBackdrop)
    }

    /** True when [path] is absent or a server URL — i.e. not a local file. */
    private fun needsArtworkResolution(path: String?): Boolean =
        path.isNullOrBlank() || isRemoteUrl(path)

    private fun isRemoteUrl(path: String): Boolean =
        path.startsWith("http://") || path.startsWith("https://")

    /** True when [path] is an existing local file (not a server URL). */
    private fun isLocalPath(path: String): Boolean =
        path.isNotBlank() && !isRemoteUrl(path)

    /**
     * Stats `[dir]/[filename]` and returns its absolute path when present, else
     * null. Collapses the repeated `File(dir, …).takeIf { it.exists() }?.absolutePath`
     * shape that poster/backdrop/cast resolution all share.
     */
    private fun localArtifactOrNull(dir: File, filename: String): String? =
        File(dir, filename).takeIf { it.exists() }?.absolutePath

    /**
     * Cast ids carried by [item]'s persisted `peopleJson`, decoded via [decodeCast].
     * Empty when the row has no cast column (older downloads) so cast cleanup is a
     * no-op for them.
     */
    private fun castIdsOf(item: OfflineMediaEntity): List<String> =
        decodeCast(item.peopleJson).map { it.id }

    /**
     * Prunes the cast-image files for [candidateCastIds] from every [parentDirs],
     * keeping any person still referenced by a *surviving* offline row. A person
     * can appear across many movies/episodes and the `personId`-keyed image file
     * serves all of them, so a file is deleted only when its person is no longer
     * referenced anywhere. **Must be called after the deleted rows are removed
     * from the DB** so [offlineMediaDao.getAllPeopleJson] reflects only surviving
     * references — otherwise the just-deleted rows would still count as references
     * and nothing would be pruned.
     */
    private suspend fun cleanupOrphanedCastArtwork(
        parentDirs: List<File>,
        candidateCastIds: List<String>,
    ) {
        if (parentDirs.isEmpty() || candidateCastIds.isEmpty()) return
        val stillReferenced = referencedPersonIds()
        val orphans = candidateCastIds.filter { it !in stillReferenced }
        if (orphans.isEmpty()) return
        parentDirs.forEach { DownloadArtifacts.cleanupCastArtwork(it, orphans) }
    }

    /**
     * Person ids that still appear in any surviving offline row's `peopleJson`.
     * A coarse scan over the decoded cast is sufficient: Jellyfin person ids are
     * stable UUIDs, so membership means the person is still referenced and their
     * shared image file must be kept. Reflects the post-delete state because it
     * is called after the deletion transaction commits.
     */
    private suspend fun referencedPersonIds(): Set<String> {
        val rows = offlineMediaDao.getAllPeopleJson()
        return buildSet {
            for (row in rows) {
                for (person in decodeCast(row.peopleJson)) add(person.id)
            }
        }
    }

    /**
     * The directory used to locate cast-image artifacts for [item]. Cast images
     * are written beside the item's media file (movies/standalone) or beside a
     * downloaded episode's media file (series, which have no media file of their
     * own). Returns null when neither is available so callers can skip cast
     * resolution rather than stat a path that cannot exist.
     */
    private suspend fun castDirFor(item: OfflineMediaItem, ownDir: File?): File? {
        if (ownDir != null) return ownDir
        // Series row: locate any downloaded episode's dir. Mirrors
        // resolveSeriesArtwork's scan so the cast row resolves to the same
        // shared downloads dir the series poster/backdrop already use.
        if (item.mediaType == MediaType.SERIES) {
            return downloadDao.getDownloadsForSeries(item.id)
                .asSequence()
                .mapNotNull { it.downloadPath.takeIf { p -> p.isNotBlank() } }
                .mapNotNull { File(it).parentFile }
                .firstOrNull()
        }
        return null
    }

    /**
     * Returns [cast] with [OfflinePersonInfo.localImagePath] populated for any
     * person whose image artifact exists beside [dir]. Persons whose file is
     * absent keep `localImagePath = null` and the detail screen falls back to
     * the remote URL (online) / blurHash (offline), exactly as before. Cheap:
     * one `File.exists()` stat per cast member, only on detail reads.
     */
    private fun resolveCastArtwork(
        cast: List<OfflinePersonInfo>,
        dir: File,
    ): List<OfflinePersonInfo> {
        var changed = false
        val resolved = ArrayList<OfflinePersonInfo>(cast.size)
        for (person in cast) {
            val localPath = localArtifactOrNull(dir, DownloadArtifacts.personImageFile(person.id))
            if (localPath != null) {
                changed = true
                resolved.add(person.copy(localImagePath = localPath))
            } else {
                resolved.add(person)
            }
        }
        return if (changed) resolved else cast
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
        providerIds = decodeProviderIds(providerIdsJson),
        externalUrls = decodeExternalUrls(externalUrlsJson),
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

/** Encodes provider ids into the persisted JSON column form. */
internal fun encodeProviderIds(providerIds: Map<String, String>): String =
    offlineJson.encodeToString(providerIds)

/** Encodes external URLs into the persisted JSON column form. */
internal fun encodeExternalUrls(urls: List<com.raulshma.jellyplay.core.model.ExternalUrl>): String =
    offlineJson.encodeToString(urls)

/** Decodes a [providerIdsJson] blob into a map, tolerating null/garbage. */
private fun decodeProviderIds(providerIdsJson: String?): Map<String, String> {
    if (providerIdsJson.isNullOrBlank()) return emptyMap()
    return runCatching {
        offlineJson.decodeFromString<Map<String, String>>(providerIdsJson)
    }.getOrDefault(emptyMap())
}

/** Decodes an [externalUrlsJson] blob into a URL list, tolerating null/garbage. */
private fun decodeExternalUrls(externalUrlsJson: String?): List<com.raulshma.jellyplay.core.model.ExternalUrl> {
    if (externalUrlsJson.isNullOrBlank()) return emptyList()
    return runCatching {
        offlineJson.decodeFromString<List<com.raulshma.jellyplay.core.model.ExternalUrl>>(externalUrlsJson)
    }.getOrDefault(emptyList())
}

