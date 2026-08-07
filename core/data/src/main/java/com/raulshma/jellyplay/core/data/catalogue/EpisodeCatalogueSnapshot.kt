package com.raulshma.jellyplay.core.data.catalogue

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * An immutable, self-describing snapshot of one series' catalogue: its seasons
 * (server order), the episodes grouped per season id, the flattened playback
 * order, and the epoch this snapshot was built under.
 *
 * One of these absorbs the five duplicate "seasons → per-season episodes →
 * ordered map" re-assemblies that previously lived across `feature/details`,
 * `feature/player/video`, `DownloadRepositoryImpl.downloadSeries`,
 * `AutoDownloadWorker.doWork` and `MediaRepositoryImpl`. Each caller rebuilt its
 * own `Map<String, List<MediaItem>>` + `List<MediaItem>` pair from the same
 * seasons/episodes primitives; this type is the single shared shape they all
 * read now, behind [EpisodeCatalogue].
 *
 * Field semantics (pinned by the catalogues tests and by the transplanted
 * `MediaRepositoryImpl`/`DetailViewModel` cases):
 *
 * - [seasons] — the server-ordered season list (`apiClient.getSeasons`), server
 *   order preserved. Never reordered locally.
 * - [episodesBySeason] — keyed by the **season id** that each episode carries
 *   (`MediaItem.seasonId`). This is NOT the `""`-key that a naive `groupBy {
 *   it.seasonId ?: "" }` produces: an episode whose `seasonId` is null groups
 *   under `""` and stays there, but the canonical season list is keyed by real
 *   ids, so the two only coincide when every episode reports a real season id.
 * - [fetchedSeasonIds] — only the season ids whose episodes are actually
 *   present in [episodesBySeason]. Seasons whose episodes grouped under a
 *   different key (the `""`-key edge, ported from the
 *   `episodes_batchReturnsMismatchedSeasonKey_…` regression) stay absent so a
 *   per-season refetch can still fire — marking them fetched here would pin
 *   them empty.
 * - [sortedEpisodes] — the canonical playback order
 *   (`seasonNumber ?: Int.MAX`, then `episodeNumber ?: indexNumber ?: Int.MAX`,
 *   then `name`), flattened across every season. The single source of truth for
 *   smart-play resolution and adjacency; callers must not re-sort.
 * - [epoch] — the catalogue epoch captured when this snapshot was built. Bumped
 *   on every invalidation; a fetch that completes after a concurrent
 *   invalidation must not write a snapshot whose epoch is now stale.
 *
 * @see EpisodeCatalogue
 */
@Immutable
data class EpisodeCatalogueSnapshot(
    val seriesId: String,
    val seasons: List<MediaItem>,
    val episodesBySeason: Map<String, List<MediaItem>>,
    val fetchedSeasonIds: Set<String>,
    val sortedEpisodes: List<MediaItem>,
    val epoch: Long,
) {
    /** Episodes for [seasonId], or empty if that season hasn't been fetched. */
    fun seasonEpisodes(seasonId: String): List<MediaItem> = episodesBySeason[seasonId].orEmpty()

    /** Every episode id in playback order — the playlist-expansion shape. */
    val allEpisodeIds: List<String> get() = sortedEpisodes.map { it.id }

    /**
     * Returns a copy with [episodes] installed for [seasonId], recomputing the
     * derived `sortedEpisodes` and `fetchedSeasonIds` so the snapshot's
     * invariants stay intact after a per-season fetch or an optimistic rewrite.
     *
     * Owns two invariants the impl must not re-derive ad hoc:
     *  - [sortedEpisodes] is rebuilt from the whole map via the canonical
     *    `sortedByPlaybackOrder` (no intermediate `toSortedMap()` — the
     *    comparator already orders across seasons).
     *  - [fetchedSeasonIds] adds [seasonId] only when it is a real season id
     *    present in [seasons]. A `""` season key (the orphan-episode edge,
     *    `fetchedSeasonIds_excludesSeasonsGroupedUnderBlankKey`) must stay
     *    absent so a per-season refetch can still fire. Set [markFetched] =
     *    false for the optimistic `markSeasonPlayed` rewrite — the season was
     *    already fetched, we're only mutating its episodes.
     */
    fun withSeasonEpisodes(
        seasonId: String,
        episodes: List<MediaItem>,
        markFetched: Boolean,
    ): EpisodeCatalogueSnapshot {
        val updatedMap = episodesBySeason + (seasonId to episodes)
        val updatedFetched = if (markFetched && seasonId in seasons.map { it.id }) {
            fetchedSeasonIds + seasonId
        } else {
            fetchedSeasonIds
        }
        return copy(
            episodesBySeason = updatedMap,
            fetchedSeasonIds = updatedFetched,
            sortedEpisodes = updatedMap.values.flatten().distinctBy { it.id }.sortedByPlaybackOrder(),
        )
    }

    val isEmpty: Boolean get() = seasons.isEmpty() && episodesBySeason.isEmpty()

    companion object {
        /**
         * An empty snapshot for [seriesId] — the fallback a caller uses when a
         * load fails (e.g. the player resolves to "no seasons, no episodes"
         * rather than crashing). Distinct per seriesId so it never masquerades
         * as a real load of a different series.
         */
        fun empty(seriesId: String): EpisodeCatalogueSnapshot = EpisodeCatalogueSnapshot(
            seriesId = seriesId,
            seasons = emptyList(),
            episodesBySeason = emptyMap(),
            fetchedSeasonIds = emptySet(),
            sortedEpisodes = emptyList(),
            epoch = 0L,
        )
    }
}
