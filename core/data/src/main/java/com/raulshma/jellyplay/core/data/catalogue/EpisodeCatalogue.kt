package com.raulshma.jellyplay.core.data.catalogue

import com.raulshma.jellyplay.core.model.MediaItem

/**
 * The deep "Episode Catalogue" seam: the single owner of the
 * seasons → per-season episodes → playback-ordered snapshot for a series.
 *
 * Five callers previously each re-assembled this shape from the raw
 * seasons/episodes primitives and each kept its own private map, cache and
 * epoch:
 *  - `feature/details/DetailViewModel` (`episodesMap`, `cachedSortedEpisodes*`,
 *    `episodeDataEpoch`, the three `load*` functions, the `@Synchronized`
 *    sorted-episodes helpers);
 *  - `feature/player/video/VideoPlayerViewModel` (`resolveSeasons`/`resolveEpisodes`
 *    online/offline branches feeding `seriesSeasons`/`seasonEpisodes`);
 *  - `core/data/.../DownloadRepositoryImpl.downloadSeries` (seasons + per-season
 *    fan-out);
 *  - `core/data/.../worker/AutoDownloadWorker.doWork` (seasons + per-season
 *    fan-out);
 *  - `core/data/.../MediaRepositoryImpl` (`seasonsCache`/`episodesCache` +
 *    merge-under-mutex).
 *
 * That complexity now lives once, here. The catalogue is the single place that
 *  - groups episodes by season id,
 *  - derives the playback-sorted order,
 *  - deduplicates concurrent loads (single-flight),
 *  - guards against stale-snapshot-after-invalidation (epoch), and
 *  - branches online vs offline.
 *
 * ## Dependency direction
 *
 * The catalogue depends on `JellyfinApiClient` and `OfflineRepository` only —
 * **never** on `MediaRepository`. `MediaRepositoryImpl` instead depends on the
 * catalogue, so the three legacy methods (`getSeasons`/`getEpisodes`/
 * `getAllEpisodesGrouped`) become thin passthroughs over the snapshot. This
 * keeps the Hilt graph acyclic (both live in `core:data`, but the direction is
 * explicit).
 *
 * ## Offline-ness is a parameter, not a monitor
 *
 * The player's offline-ness is per-session
 * (`playerSessionManager.sessionState.value.isOffline`); callers pass the
 * appropriate `offline` flag rather than the catalogue subscribing to a global
 * mode. The global `OfflineModeManager` stays where it is.
 */
interface EpisodeCatalogue {

    /**
     * Assemble the full series catalogue. The single round-trip path:
     * `apiClient.getSeasons` + `apiClient.getAllEpisodes`, grouped by season id.
     * On batched-call failure, falls back to a per-season fan-out capped at
     * `MAX_PARALLEL_SEASON_FETCHES`.
     *
     * When [offline] is true, reads the local store's seasons + every season's
     * episodes (a fresh Room read per call — no network).
     *
     * @return the snapshot, or a failed result on a hard error. An empty series
     *   yields a successful empty snapshot, not a failure.
     */
    suspend fun loadSeriesEpisodes(
        seriesId: String,
        offline: Boolean = false,
    ): Result<EpisodeCatalogueSnapshot>

    /**
     * Episodes for a single season.
     *
     * Online: serves from the series snapshot if that season is already present
     * (no network); otherwise fetches the one season and merges it into the
     * shared snapshot. Offline: reads the store's per-season flow.
     *
     * @return the episode list, or a failed result on a hard error.
     */
    suspend fun loadSeasonEpisodes(
        seriesId: String,
        seasonId: String,
        offline: Boolean = false,
    ): Result<List<MediaItem>>

    /**
     * Optimistic in-place rewrite of one season's episodes, used by the detail
     * screen's `markSeasonPlayed`/`markSeasonUnplayed` to flip every episode in
     * a season without a round-trip. [transform] receives the current episodes
     * for [seasonId] and returns the rewritten list; the snapshot (and its
     * derived `sortedEpisodes`) is rebuilt and returned.
     *
     * Returns null when the series snapshot isn't loaded or the season isn't
     * present — callers should treat that as "nothing to optimistically update"
     * (the mutation still lands server-side; the next load picks it up).
     *
     * The rewrite does NOT write through to the TTL cache in a way a later
     * network refetch can pin: the snapshot is updated in the single-flight
     * cache, but its epoch is preserved, so a subsequent invalidation + reload
     * still hits the server for the authoritative post-cascade state.
     */
    suspend fun updateSeasonEpisodes(
        seriesId: String,
        seasonId: String,
        transform: (List<MediaItem>) -> List<MediaItem>,
    ): EpisodeCatalogueSnapshot?

    /** Drop [seriesId]'s cached snapshot(s) and bump the series epoch. */
    fun invalidateSeries(seriesId: String)

    /** Drop every cached snapshot and bump the long epoch. */
    fun invalidateAll()
}
