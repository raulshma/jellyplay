package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflinePersonInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The disk-backed local-artwork resolution subsystem behind every offline read
 * path, extracted verbatim from [OfflineRepositoryImpl] (D4) so the FS-stat +
 * fallback-ladder logic is testable against two DAO seams instead of the full
 * five-DAO + database repository construction.
 *
 * Resolves poster/backdrop/cast image paths for items of ANY media type so they
 * render without a network connection. Rows whose persisted
 * `posterPath`/`backdropPath` are blank or a remote URL (legacy downloads from
 * before local-file persistence, or image-write-failure fallbacks at download
 * time) would otherwise render broken offline even when the local file sits on
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
 * Ownership split (the [OfflineDeletionCore] / [DownloadSidecarCore]
 * precedent): this core owns ARTWORK RESOLUTION only — the memo cache, the
 * episode→series and series→episode-dir fallback ladders, and the
 * [SeriesPrefetch] bulk reads over its two DAOs. [OfflineRepositoryImpl] keeps
 * the flow choreography (which rows are resolved when) and owns the memo's
 * lifecycle through [evictMemo] — its delete scopes pass it as the deletion
 * core's `evictArtworkMemo` hook so a deleted download's cached paths dangle
 * no longer than the delete transaction.
 *
 * The repository constructs this from its own constructor dependencies (the
 * [OfflineDeletionCore] precedent), so the repository's public constructor —
 * and every existing test construction of it — is unchanged.
 */
internal class OfflineArtworkResolver(
    private val offlineMediaDao: OfflineMediaDao,
    private val downloadDao: DownloadDao,
) {

    /**
     * Per-item artwork memo (see [ArtworkInputKey]): maps item id to its
     * last resolution inputs and result so download progress ticks skip the
     * FS stats + series-prefetch queries entirely. Same memoization pattern
     * as the file-level JSON decode caches in OfflineRepositoryImpl.kt.
     */
    private val artworkMemoCache =
        androidx.collection.LruCache<String, Pair<ArtworkInputKey, ResolvedArtwork>>(512)

    /**
     * Drops every cached resolution — the delete paths' hook. The deleted
     * row's artifacts (and possibly its whole dir) are gone; cached local
     * artwork paths for it and its series siblings would dangle until process
     * death. Deletes are rare — a full evict is cheap and the next read
     * re-resolves from disk.
     */
    fun evictMemo() {
        artworkMemoCache.evictAll()
    }

    /**
     * Resolves disk-backed local artwork for a single item of ANY media type.
     * Runs on the caller's dispatcher — the single-item read paths wrap this
     * in `Dispatchers.IO` themselves (the resolver keeps no thread-hop of its
     * own so an already-deferred caller pays none).
     */
    suspend fun resolveItemArtwork(item: OfflineMediaItem): OfflineMediaItem =
        resolveItemArtwork(item, artworkInputKey(item, episodeSeriesArtworkFallback = true), SeriesPrefetch())

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
    suspend fun resolveArtworkList(
        items: List<OfflineMediaItem>,
        episodeSeriesArtworkFallback: Boolean = true,
    ): List<OfflineMediaItem> {
        if (items.isEmpty()) return items
        return withContext(Dispatchers.IO) {
            val keys = items.map { artworkInputKey(it, episodeSeriesArtworkFallback) }
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
     * Memoization seam for the artwork pass: everything the resolver reads
     * (its FS stats and series-prefetch queries) is a pure function of these
     * inputs, so a cached result can be replayed when they are unchanged.
     * Byte-count-only download progress ticks — the 2 s cadence during active
     * transfers — never appear in the key, which is what keeps ~1000 stats +
     * 2 queries per tick off the offline screen. Only the download's
     * completed-ness is keyed, not the full status: artwork appears beside the
     * file when the download completes, so COMPLETED↔not transitions must
     * re-resolve — but a PAUSED↔DOWNLOADING flip changes nothing the resolver
     * reads and keeps the memo hit. [episodeSeriesArtworkFallback] is keyed
     * because the same episode resolves differently in the home episodes flow
     * (own artwork only) vs the detail paths (series substitution).
     */
    private data class ArtworkInputKey(
        val id: String,
        val mediaType: MediaType,
        val seriesId: String?,
        val posterPath: String?,
        val backdropPath: String?,
        val downloadPath: String?,
        val downloadIsComplete: Boolean,
        val episodeSeriesArtworkFallback: Boolean,
        val cast: List<OfflinePersonInfo>,
    )

    private data class ResolvedArtwork(
        val posterPath: String?,
        val backdropPath: String?,
        val cast: List<OfflinePersonInfo>,
    )

    private fun artworkInputKey(item: OfflineMediaItem, episodeSeriesArtworkFallback: Boolean) = ArtworkInputKey(
        id = item.id,
        mediaType = item.mediaType,
        seriesId = item.seriesId,
        posterPath = item.posterPath,
        backdropPath = item.backdropPath,
        downloadPath = item.downloadPath,
        downloadIsComplete = item.downloadStatus == DownloadStatus.COMPLETED,
        episodeSeriesArtworkFallback = episodeSeriesArtworkFallback,
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
            MediaType.EPISODE -> if (key.episodeSeriesArtworkFallback) {
                resolveEpisodeArtwork(item, ownPoster, ownBackdrop, prefetch)
            } else {
                ownPoster to ownBackdrop
            }
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
     * (passed in) win when present. Only the detail/read paths use this —
     * the offline home's episodes flow resolves own artwork only (the
     * repo's getOfflineEpisodes passes `episodeSeriesArtworkFallback=false`)
     * so its Continue Watching cards match the online row's backdrop→primary
     * fallback chain.
     */
    private suspend fun resolveEpisodeArtwork(
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
}
