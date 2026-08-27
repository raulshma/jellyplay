package com.raulshma.jellyplay.core.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.raulshma.jellyplay.core.data.sync.toOfflineSyncState
import com.raulshma.jellyplay.core.data.sync.toOfflineSyncUpdate
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaWithPlayback
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SeriesSizeAggregate
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.OfflineSyncUpdate
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import androidx.collection.LruCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimum query length (in characters) before offline search is performed.
 * Anything shorter would match too many unrelated items.
 */
const val MIN_OFFLINE_SEARCH_LENGTH: Int = 2

private val MEDIA_TYPE_BY_NAME: Map<String, MediaType> = MediaType.entries.associateBy { it.name }
private val DOWNLOAD_STATUS_BY_NAME: Map<String, DownloadStatus> = DownloadStatus.entries.associateBy { it.name }

/**
 * Joins a metadata row's model with its `downloads` row (null when absent) —
 * the shape every download-aware read path projects: path + status + byte
 * counters, all defaulting when there is no download.
 */
private fun OfflineMediaItem.withDownload(download: DownloadEntity?): OfflineMediaItem = copy(
    downloadPath = download?.downloadPath,
    downloadStatus = download?.status?.let { DOWNLOAD_STATUS_BY_NAME[it] },
    downloadedBytes = download?.downloadedBytes ?: 0L,
    totalSizeBytes = download?.totalSizeBytes ?: 0L,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OfflineRepositoryImpl @Inject constructor(
    private val offlineMediaDao: OfflineMediaDao,
    private val playbackStateDao: PlaybackStateDao,
    private val syncBaselineDao: SyncBaselineDao,
    private val downloadDao: DownloadDao,
    private val database: JellyPlayDatabase,
) : OfflineRepository {

    /**
     * Per-item artwork memo (see [ArtworkInputKey]): maps item id to its
     * last resolution inputs and result so download progress ticks skip the
     * FS stats + series-prefetch queries entirely. Same memoization pattern
     * as the file-level JSON decode caches below.
     */
    private val artworkMemoCache =
        androidx.collection.LruCache<String, Pair<ArtworkInputKey, ResolvedArtwork>>(512)

    override fun getOfflineDetail(id: String): Flow<OfflineMediaItem?> =
        offlineMediaDao.getByIdWithPlaybackFlow(id).flatMapLatest { row ->
            if (row == null) {
                flowOf(null)
            } else {
                downloadDao.getDownloadByMediaItemIdFlow(id).flatMapLatest { download ->
                    val item = row.toOfflineMediaItem().withDownload(download)
                    // Resolve disk-backed local artwork for every media type so
                    // the offline detail hero/poster render without network.
                    // Covers legacy rows (remote URLs written before local-file
                    // persistence) and image-write-failure fallbacks — the
                    // resolver substitutes an existing local file when one is
                    // beside the download, else preserves the original value.
                    flow<OfflineMediaItem?> { emit(withContext(Dispatchers.IO) { resolveItemArtwork(item) }) }
                }.distinctUntilChanged()
            }
        }.distinctUntilChanged()

    override fun getChildren(parentId: String): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getChildrenByParent(parentId).flatMapLatest { rows ->
            val ids = rows.map { it.media.id }
            if (ids.isEmpty()) {
                flowOf(emptyList())
            } else {
                downloadDao.getDownloadsByMediaItemIdsFlow(ids).map { downloads ->
                    val downloadMap = downloads.associateBy { it.mediaItemId }
                    rows.map { row ->
                        row.toOfflineMediaItem().withDownload(downloadMap[row.media.id])
                    }
                }.flatMapLatest { items -> flow { emit(resolveArtworkList(items)) } }
            }
        }.distinctUntilChanged()

    override fun getOfflineLibrary(): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getTopLevelItems().flatMapLatest { rows ->
            if (rows.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                // Partition top-level ids by type: SERIES has no `downloads`
                // row of its own (episodes are downloaded individually, each
                // carrying `seriesId`), so its size must be aggregated from its
                // episodes. Movies/standalone audio keep the direct per-row
                // join. Querying each partition only by the ids it can match
                // avoids a wasted left-join against every series id.
                val seriesIds = rows.asSequence()
                    .filter { it.media.mediaType == MediaType.SERIES.name }
                    .map { it.media.id }
                    .toList()
                val directIds = rows.asSequence()
                    .filter { it.media.mediaType != MediaType.SERIES.name }
                    .map { it.media.id }
                    .toList()
                // Map metadata rows to models once per metadata emission: the
                // mapping depends solely on offline_media/playback_state
                // columns, so re-running it inside the combine below would only
                // repeat the JSON/CSV decodes on every downloads re-emission
                // (2 s progress ticks during transfers). The combine instead
                // projects just the download-derived fields onto these items
                // via cheap data-class copies.
                val baseItems = rows.map { it.toOfflineMediaItem() }
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
                // everything else from its direct download row. Both branches
                // are cheap copies over the pre-mapped items — only the
                // download-derived fields are re-projected.
                combine(directDownloadsFlow, seriesAggregatesFlow) { downloadMap, aggregateMap ->
                    baseItems.map { item ->
                        if (item.mediaType == MediaType.SERIES) {
                            val agg = aggregateMap[item.id]
                            item.copy(
                                downloadedBytes = agg?.downloadedBytes ?: 0L,
                                totalSizeBytes = agg?.totalSizeBytes ?: 0L,
                            )
                        } else {
                            item.withDownload(downloadMap[item.id])
                        }
                    }
                }.distinctUntilChanged()
                    .flatMapLatest { items -> flow { emit(resolveArtworkList(items)) } }
            }
        }.distinctUntilChanged()

    override fun getSeasonsForSeries(seriesId: String): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getSeasonsForSeries(seriesId).map { rows ->
            rows.map { it.toOfflineMediaItem() }
        }.flatMapLatest { items -> flow { emit(resolveArtworkList(items)) } }
            .distinctUntilChanged()

    override fun getEpisodesForSeason(seasonId: String): Flow<List<OfflineMediaItem>> =
        offlineMediaDao.getEpisodesForSeason(seasonId).flatMapLatest { rows ->
            val ids = rows.map { it.media.id }
            if (ids.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
            else downloadDao.getDownloadsByMediaItemIdsFlow(ids).map { downloads ->
                val downloadMap = downloads.associateBy { it.mediaItemId }
                rows.map { row ->
                    row.toOfflineMediaItem().withDownload(downloadMap[row.media.id])
                }
            }.distinctUntilChanged()
                .flatMapLatest { items -> flow { emit(resolveArtworkList(items)) } }
        }.distinctUntilChanged()

    override suspend fun getEpisodesForSeries(seriesId: String): List<OfflineMediaItem> {
        val rows = offlineMediaDao.getEpisodesForSeries(seriesId)
        val ids = rows.map { it.media.id }
        if (ids.isEmpty()) return emptyList()
        val downloadMap = downloadDao.getDownloadsByMediaItemIds(ids).associateBy { it.mediaItemId }
        return resolveArtworkList(rows.map { row ->
            row.toOfflineMediaItem().withDownload(downloadMap[row.media.id])
        })
    }

    override suspend fun getOfflineItem(id: String): OfflineMediaItem? =
        offlineMediaDao.getByIdWithPlayback(id)?.toOfflineMediaItem()
            ?.let { withContext(Dispatchers.IO) { resolveItemArtwork(it) } }

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
            playbackStateDao.deleteById(id)
            syncBaselineDao.deleteById(id)
        }
        // The deleted row's artifacts (and possibly its whole dir) are gone;
        // cached local artwork paths for it and its series siblings would
        // dangle until process death. Deletes are rare — a full evict is
        // cheap and the next read re-resolves from disk.
        artworkMemoCache.evictAll()
        // Prune cast images after the row is gone so the reference scan only
        // counts surviving rows.
        cleanupOrphanedCastArtwork(
            parentDirs = listOfNotNull(parentDir),
            candidateCastIds = entity?.let(::castIdsOf).orEmpty(),
        )
        cleanupOrphans()
    }

    private suspend fun offlineMediaById(downloads: List<DownloadEntity>): Map<String, OfflineMediaEntity> {
        if (downloads.isEmpty()) return emptyMap()
        return offlineMediaDao.getByIds(downloads.map { it.mediaItemId }).associateBy { it.id }
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
        val mediaById = offlineMediaById(downloads)
        val deletedCastIds = downloads.mapNotNull { mediaById[it.mediaItemId] }
            .flatMap(::castIdsOf)
            .distinct()
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            offlineMediaDao.deleteBySeriesId(seriesId)
            playbackStateDao.deleteBySeriesId(seriesId)
            syncBaselineDao.deleteBySeriesId(seriesId)
        }
        // Series-scoped artwork cleanup above removed episode-dir artifacts
        // the memo may still resolve to — drop it and re-resolve from disk.
        artworkMemoCache.evictAll()
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
        val mediaById = offlineMediaById(downloads)
        val deletedCastIds = downloads.mapNotNull { mediaById[it.mediaItemId] }
            .flatMap(::castIdsOf)
            .distinct()
        database.withTransaction {
            val ids = downloads.map { it.id }
            if (ids.isNotEmpty()) downloadDao.deleteDownloadsByIds(ids)
            offlineMediaDao.deleteBySeasonId(seasonId)
            playbackStateDao.deleteBySeasonId(seasonId)
            syncBaselineDao.deleteBySeasonId(seasonId)
        }
        // Deleted episode artifacts may have served as this series' cached
        // artwork source — drop the memo and re-resolve from disk.
        artworkMemoCache.evictAll()
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
        // Remove orphaned season/series metadata rows, then any playback /
        // baseline rows whose metadata row just disappeared (or drifted from
        // older data). One transaction so a concurrent read never sees a
        // half-cleaned state.
        database.withTransaction {
            offlineMediaDao.cleanupOrphans()
            playbackStateDao.deleteUnreferenced()
            syncBaselineDao.deleteUnreferenced()
        }
    }

    override suspend fun updatePlaybackProgress(
        itemId: String,
        positionTicks: Long?,
        percentage: Double,
        isPlayed: Boolean,
    ) {
        // The targeted UPSERT's `WHERE id` matches nothing for a server-only
        // item (no playback row), so there is no existence guard here — the
        // INSERT branch is harmless because the repository only calls this for
        // items it has seeded.
        playbackStateDao.updatePlaybackProgress(
            itemId = itemId,
            positionTicks = positionTicks,
            percentage = percentage.coerceIn(0.0, 100.0),
            isPlayed = isPlayed,
            lastPlayedDate = java.time.OffsetDateTime.now().toString(),
        )
    }

    override suspend fun applyPlayedState(itemId: String, isPlayed: Boolean) {
        // The batch UPSERT matches the item and every row in its hierarchy
        // (parentId / seasonId / seriesId resolved against offline_media),
        // mirroring Jellyfin's server-side cascade for markPlayedItem /
        // markUnplayedItem. Zero rows match for a non-downloaded item, so there
        // is no existence guard here. Stamp lastPlayedDate on mark-played
        // (matches server UserData semantics) and clear it on mark-unplayed so
        // the offline row reflects the reset.
        playbackStateDao.applyPlayedStateToHierarchy(
            itemId = itemId,
            isPlayed = isPlayed,
            lastPlayedDate = if (isPlayed) java.time.OffsetDateTime.now().toString() else null,
        )
    }

    override suspend fun applyFavoriteState(itemId: String, isFavorite: Boolean) {
        // Favorite is per-item (no hierarchy cascade — the Jellyfin favorite
        // endpoints act on one item only), so this is a single-row UPSERT. Zero
        // rows match for a non-downloaded item, so there is no existence guard.
        playbackStateDao.applyFavoriteState(
            itemId = itemId,
            isFavorite = isFavorite,
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

    override suspend fun getLocalRelated(
        currentId: String,
        genres: List<String>,
        studios: List<String>,
        limit: Int,
    ): List<MediaItem> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>()
        val matches = ArrayList<OfflineMediaItem>()

        suspend fun addAll(rows: List<OfflineMediaWithPlayback>) {
            for (row in rows) {
                val item = row.toOfflineMediaItem()
                if (seen.add(item.id)) {
                    matches.add(item)
                    if (matches.size >= limit) return
                }
            }
        }

        // Genre matches first (strongest signal); one query per genre because
        // Room cannot expand a list bind into multiple LIKE OR clauses.
        for (genre in genres) {
            if (matches.size >= limit) break
            addAll(offlineMediaDao.getRelatedByGenre(currentId, genre, limit))
        }
        // Studio fallback only when genre matches were sparse.
        if (matches.size < limit) {
            for (studio in studios) {
                if (matches.size >= limit) break
                addAll(offlineMediaDao.getRelatedByStudio(currentId, studio, limit))
            }
        }
        if (matches.isEmpty()) return emptyList()
        return resolveArtworkList(matches).map { it.toMediaItem() }
    }

    /**
     * Freshness reads project straight from the `sync_baseline` table via the
     * shared, lossless [toOfflineSyncState] / [toOfflineSyncUpdate] functions.
     * The decision + write persistence lives in `OfflineSyncManager`; the
     * projection is a single pure function shared by the read path (here, the
     * reactive DB-driven badge) and the write path's TTL / error short-circuits,
     * so the two can no longer drift. The repository surface is preserved so
     * existing consumers keep a stable seam.
     */
    override fun getOfflineSyncState(id: String): Flow<OfflineSyncState?> =
        syncBaselineDao.getBaselineFlow(id).map { row -> row?.toOfflineSyncState() }

    override fun getUpdatesCount(): Flow<Int> = syncBaselineDao.getUpdatesCount()

    override fun getItemsWithUpdates(): Flow<List<OfflineSyncUpdate>> =
        syncBaselineDao.getItemsWithUpdates().map { rows -> rows.map { it.toOfflineSyncUpdate() } }

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
    private suspend fun resolveItemArtwork(item: OfflineMediaItem): OfflineMediaItem =
        resolveItemArtwork(item, artworkInputKey(item), SeriesPrefetch())

    /**
     * Memoization seam for the artwork pass: everything the resolver reads
     * (its FS stats and series-prefetch queries) is a pure function of these
     * inputs, so a cached result can be replayed when they are unchanged.
     * Byte-count-only download progress ticks — the 2 s cadence during active
     * transfers — never appear in the key, which is what keeps ~1000 stats +
     * 2 queries per tick off the offline screen. Only the download's
     * completed-ness is keyed, not the full status: artwork appears beside
     * the file when the download completes, so COMPLETED↔not transitions must
     * re-resolve — but a PAUSED↔DOWNLOADING flip changes nothing the resolver
     * reads and keeps the memo hit.
     */
    private data class ArtworkInputKey(
        val id: String,
        val mediaType: MediaType,
        val seriesId: String?,
        val posterPath: String?,
        val backdropPath: String?,
        val downloadPath: String?,
        val downloadIsComplete: Boolean,
        val cast: List<OfflinePersonInfo>,
    )

    private data class ResolvedArtwork(
        val posterPath: String?,
        val backdropPath: String?,
        val cast: List<OfflinePersonInfo>,
    )

    private fun artworkInputKey(item: OfflineMediaItem) = ArtworkInputKey(
        id = item.id,
        mediaType = item.mediaType,
        seriesId = item.seriesId,
        posterPath = item.posterPath,
        backdropPath = item.backdropPath,
        downloadPath = item.downloadPath,
        downloadIsComplete = item.downloadStatus == DownloadStatus.COMPLETED,
        cast = item.cast,
    )

    private fun applyResolvedArtwork(
        item: OfflineMediaItem,
        resolved: ResolvedArtwork,
    ): OfflineMediaItem =
        if (resolved.posterPath == item.posterPath &&
            resolved.backdropPath == item.backdropPath &&
            resolved.cast === item.cast
        ) {
            item
        } else {
            item.copy(
                posterPath = resolved.posterPath,
                backdropPath = resolved.backdropPath,
                cast = resolved.cast,
            )
        }

    private suspend fun resolveItemArtwork(
        item: OfflineMediaItem,
        key: ArtworkInputKey,
        prefetch: SeriesPrefetch,
    ): OfflineMediaItem {
        artworkMemoCache.get(item.id)?.let { (cachedKey, resolved) ->
            if (cachedKey == key) return applyResolvedArtwork(item, resolved)
        }
        // First: the item's own artifact beside its media file (all types).
        val ownDir = item.downloadPath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).parentFile }
        val ownPoster = ownDir?.let { localArtifactOrNull(it, DownloadArtifacts.posterFile(item.id)) }
        val ownBackdrop = ownDir?.let { localArtifactOrNull(it, DownloadArtifacts.backdropFile(item.id)) }
        val (resolvedPoster, resolvedBackdrop) = when (item.mediaType) {
            MediaType.EPISODE -> resolveEpisodeSeriesArtwork(item, ownPoster, ownBackdrop, prefetch)
            MediaType.SERIES -> resolveSeriesArtwork(item, ownPoster, ownBackdrop, prefetch)
            else -> ownPoster to ownBackdrop
        }
        val posterResolved = if (needsArtworkResolution(item.posterPath)) resolvedPoster ?: item.posterPath else item.posterPath
        val backdropResolved = if (needsArtworkResolution(item.backdropPath)) resolvedBackdrop ?: item.backdropPath else item.backdropPath
        // Resolve cast image paths last: cast images are written beside the same
        // parent dir as posters/backdrops (keyed by personId), so reuse whichever
        // artifact dir was located above. Movies/standalone items use ownDir;
        // series rows resolve their first episode's dir; episodes inherit their
        // parent series dir. Skipped entirely when there is no cast to resolve.
        val castDir = castDirFor(item, ownDir, prefetch)
        val resolvedCast = if (castDir != null && item.cast.isNotEmpty()) {
            resolveCastArtwork(item.cast, castDir)
        } else {
            item.cast
        }
        val resolved = ResolvedArtwork(posterResolved, backdropResolved, resolvedCast)
        artworkMemoCache.put(item.id, key to resolved)
        return applyResolvedArtwork(item, resolved)
    }

    /**
     * Resolves local artwork for a list of items (library grid, episode lists,
     * album tracks). Each item that already has a local path short-circuits
     * with zero FS work; only rows needing resolution stat the artifact files.
     * See [resolveItemArtwork] for the per-field policy.
     *
     * Runs on [Dispatchers.IO]: these flows are collected on the Main
     * dispatcher (ViewModel `stateIn`) and Room re-emits them on every write
     * to `offline_media`/`downloads` — i.e. continuously during active
     * downloads — so the `File.exists()` stats must stay off Main.
     */
    private suspend fun resolveArtworkList(items: List<OfflineMediaItem>): List<OfflineMediaItem> {
        if (items.isEmpty()) return items
        return withContext(Dispatchers.IO) {
            val keys = items.map(::artworkInputKey)
            // Snapshot each entry ONCE: a second get() after the all-cached
            // check could miss (a concurrent collector's puts evicting LRU
            // entries between the two reads) and trip the non-null assertion.
            val cachedEntries = items.map { artworkMemoCache.get(it.id) }
            val allCached = cachedEntries.indices.all { i -> cachedEntries[i]?.first == keys[i] }
            if (allCached) {
                items.mapIndexed { i, item ->
                    applyResolvedArtwork(item, cachedEntries[i]!!.second)
                }
            } else {
                // All episodes of a season share one seriesId — prefetch each
                // distinct parent series row once instead of re-querying it per
                // episode on every emission. Same for each SERIES item's
                // downloaded-episode dir (one projected query for the whole
                // list instead of a getDownloadsForSeries round-trip per series).
                val prefetch = SeriesPrefetch(
                    seriesRowsById = prefetchSeriesRows(items),
                    seriesDirsById = prefetchSeriesArtifactDirs(items),
                )
                items.mapIndexed { i, item -> resolveItemArtwork(item, keys[i], prefetch) }
            }
        }
    }

    /**
     * Bulk-prefetched parent-series context for list artwork resolution:
     * [seriesRowsById] / [seriesDirsById] each come from one projected query
     * instead of a per-item DAO round-trip. A `null` map means nothing of
     * that kind was prefetched (the single-item detail path passes a bare
     * `SeriesPrefetch()`), so [seriesRowOrNull]/[seriesDirOrNull] fall back
     * to the per-id query; a non-null map missing an id is a definitive
     * miss, so the fallback is skipped (no redundant re-query).
     */
    private inner class SeriesPrefetch(
        val seriesRowsById: Map<String, OfflineMediaEntity>? = null,
        val seriesDirsById: Map<String, File>? = null,
    ) {
        suspend fun seriesRowOrNull(seriesId: String): OfflineMediaEntity? =
            if (seriesRowsById != null) seriesRowsById[seriesId] else offlineMediaDao.getById(seriesId)

        suspend fun seriesDirOrNull(seriesId: String): File? =
            if (seriesDirsById != null) seriesDirsById[seriesId] else firstEpisodeDirForSeries(seriesId)
    }

    private suspend fun prefetchSeriesRows(
        items: List<OfflineMediaItem>,
    ): Map<String, OfflineMediaEntity>? {
        val seriesIds = items.asSequence()
            .filter { it.mediaType == MediaType.EPISODE }
            .mapNotNull { it.seriesId }
            .distinct()
            .toList()
        if (seriesIds.isEmpty()) return null
        return offlineMediaDao.getByIds(seriesIds).associateBy { it.id }
    }

    /**
     * Each distinct SERIES item's artifact dir (parent of a downloaded
     * episode's file) in ONE projected query. Blank paths and parentless
     * paths are skipped; the first surviving row per series wins, matching
     * the per-series [com.raulshma.jellyplay.core.database.dao.DownloadDao.getDownloadsForSeries]
     * scan's table order. Returns null when the list has no SERIES items
     * (no query at all); a non-null map missing an id means that series has
     * no downloaded episode, so no redundant re-query for it.
     */
    private suspend fun prefetchSeriesArtifactDirs(
        items: List<OfflineMediaItem>,
    ): Map<String, File>? {
        val seriesIds = items.asSequence()
            .filter { it.mediaType == MediaType.SERIES }
            .map { it.id }
            .distinct()
            .toList()
        if (seriesIds.isEmpty()) return null
        val dirBySeries = LinkedHashMap<String, File>()
        for (row in downloadDao.getDownloadPathsForSeries(seriesIds)) {
            if (row.downloadPath.isBlank()) continue
            val parent = File(row.downloadPath).parentFile ?: continue
            if (row.seriesId !in dirBySeries) dirBySeries[row.seriesId] = parent
        }
        return dirBySeries
    }

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
        prefetch: SeriesPrefetch,
    ): Pair<String?, String?> {
        val seriesId = item.seriesId ?: return ownPoster to ownBackdrop
        val seriesRow = prefetch.seriesRowOrNull(seriesId)
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
        prefetch: SeriesPrefetch,
    ): Pair<String?, String?> {
        val seriesDir = prefetch.seriesDirOrNull(item.id)
        val seriesBackdrop = seriesDir?.let { localArtifactOrNull(it, DownloadArtifacts.backdropFile(item.id)) }
        val seriesPoster = seriesDir?.let { localArtifactOrNull(it, DownloadArtifacts.posterFile(item.id)) }
        return (ownPoster ?: seriesPoster) to (ownBackdrop ?: seriesBackdrop)
    }

    /** Dir of the series' first downloaded episode; null when there is none. */
    private suspend fun firstEpisodeDirForSeries(seriesId: String): File? =
        downloadDao.getDownloadsForSeries(seriesId)
            .asSequence()
            .mapNotNull { it.downloadPath.takeIf { p -> p.isNotBlank() } }
            .mapNotNull { File(it).parentFile }
            .firstOrNull()

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
    private suspend fun castDirFor(
        item: OfflineMediaItem,
        ownDir: File?,
        prefetch: SeriesPrefetch,
    ): File? {
        if (ownDir != null) return ownDir
        // Series row: locate any downloaded episode's dir — the same lookup
        // [resolveSeriesArtwork] uses (prefetched in lists, per-id on detail
        // reads) so the cast row resolves to the same shared downloads dir
        // the series poster/backdrop already use.
        if (item.mediaType == MediaType.SERIES) {
            return prefetch.seriesDirOrNull(item.id)
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

    /**
     * Maps a metadata + playback join row to the UI model. Playback fields come
     * from the LEFT JOIN'd `playback_state` columns and fall back to the same
     * "not started" defaults a missing row carried under the old single-table
     * shape.
     */
    private fun OfflineMediaWithPlayback.toOfflineMediaItem(): OfflineMediaItem {
        val m = media
        return OfflineMediaItem(
            id = m.id,
            name = m.name,
            mediaType = safeMediaTypeOf(m.mediaType),
            overview = m.overview,
            year = m.year,
            communityRating = m.communityRating,
            officialRating = m.officialRating,
            runTimeTicks = m.runTimeTicks,
            seriesId = m.seriesId,
            seasonId = m.seasonId,
            seriesName = m.seriesName,
            seasonName = m.seasonName,
            episodeNumber = m.episodeNumber,
            seasonNumber = m.seasonNumber,
            posterPath = m.posterPath,
            backdropPath = m.backdropPath,
            blurHashPrimary = m.blurHashPrimary,
            blurHashBackdrop = m.blurHashBackdrop,
            genres = m.genres?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() } ?: emptyList(),
            childCount = m.childCount ?: 0,
            playbackPositionTicks = playbackPositionTicks,
            playedPercentage = playedPercentage ?: 0.0,
            isPlayed = isPlayed ?: false,
            isFavorite = isFavorite ?: false,
            lastPlayedDate = lastPlayedDate,
            originalTitle = m.originalTitle,
            criticRating = m.criticRating,
            studios = m.studios?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() } ?: emptyList(),
            tagline = m.tagline,
            cast = decodeCast(m.peopleJson),
            providerIds = decodeProviderIds(m.providerIdsJson),
            externalUrls = decodeExternalUrls(m.externalUrlsJson),
            chapters = decodeChapters(m.chaptersJson),
            createdAt = m.createdAt,
        )
    }
}

/** Reusable lenient Json for (de)serializing the offline JSON-blob columns. */
internal val offlineJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Memoized encode/decode codec for one offline_media JSON-blob column.
 *
 * Single home for the null/garbage-tolerant decode + memo-cache shape every
 * blob column shares (cast, provider ids, external urls, chapters): the
 * library/episodes/season flows re-map every row on each Room re-emission
 * (2 s progress ticks during transfers), and the decodes are deterministic,
 * so equal inputs always yield equal outputs.
 */
internal class JsonBlobCodec<T : Any>(
    private val serializer: KSerializer<T>,
    private val fallback: T,
) {
    private val cache = LruCache<String, T>(256)
    /** Encodes [value] into the persisted JSON column form. */
    fun encode(value: T): String = offlineJson.encodeToString(serializer, value)

    /** Decodes [blob], tolerating null/blank/garbage by yielding [fallback]. */
    fun decode(blob: String?): T {
        if (blob.isNullOrBlank()) return fallback
        cache.get(blob)?.let { return it }
        val value = runCatching { offlineJson.decodeFromString(serializer, blob) }
            .getOrDefault(fallback)
        cache.put(blob, value)
        return value
    }
}

private inline fun <reified T : Any> jsonBlobCodec(fallback: T): JsonBlobCodec<T> =
    JsonBlobCodec(serializer(), fallback)

private val castCodec = jsonBlobCodec<List<OfflinePersonInfo>>(emptyList())
private val providerIdsCodec = jsonBlobCodec<Map<String, String>>(emptyMap())
private val externalUrlsCodec = jsonBlobCodec<List<ExternalUrl>>(emptyList())
private val chaptersCodec = jsonBlobCodec<List<ChapterInfo>>(emptyList())

/** Decodes a [peopleJson] blob into a cast list, tolerating null/garbage rows. */
internal fun decodeCast(peopleJson: String?): List<OfflinePersonInfo> =
    castCodec.decode(peopleJson)

/** Encodes a cast list into the persisted JSON column form. */
internal fun encodeCast(people: List<OfflinePersonInfo>): String =
    castCodec.encode(people)

/** Encodes provider ids into the persisted JSON column form. */
internal fun encodeProviderIds(providerIds: Map<String, String>): String =
    providerIdsCodec.encode(providerIds)

/** Decodes a [providerIdsJson] blob into a map, tolerating null/garbage. */
private fun decodeProviderIds(providerIdsJson: String?): Map<String, String> =
    providerIdsCodec.decode(providerIdsJson)

/** Encodes external URLs into the persisted JSON column form. */
internal fun encodeExternalUrls(urls: List<ExternalUrl>): String =
    externalUrlsCodec.encode(urls)

/** Decodes a [externalUrlsJson] blob into a URL list, tolerating null/garbage. */
private fun decodeExternalUrls(externalUrlsJson: String?): List<ExternalUrl> =
    externalUrlsCodec.decode(externalUrlsJson)

/** Encodes a chapter list into the persisted JSON column form. */
internal fun encodeChapters(chapters: List<ChapterInfo>): String =
    chaptersCodec.encode(chapters)

/** Decodes a [chaptersJson] blob into a chapter list, tolerating null/garbage. */
internal fun decodeChapters(chaptersJson: String?): List<ChapterInfo> =
    chaptersCodec.decode(chaptersJson)
