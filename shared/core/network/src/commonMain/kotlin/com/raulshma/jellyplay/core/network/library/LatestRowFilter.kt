package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * One resolved card of the classic TV-latest row (#168): the pre-12
 * `/Items/Latest` pipeline returned either a Series container (a series with
 * more than one episode in the fetched pool) or the single most-recent
 * episode itself. [toClassicLatestCards] reproduces that split client-side;
 * the caller resolves [Grouped] cards against the real Series items and
 * falls back to [Grouped.synthesizedSeries] when that fetch fails.
 */
internal sealed interface ClassicLatestCard {
    /** The pool's most-recent episode of this card — the single-episode card itself, or the group's newest. */
    val mostRecentEpisode: MediaItem

    /** A series with exactly one episode in the pool: pre-12 rendered the episode itself. */
    data class Single(override val mostRecentEpisode: MediaItem) : ClassicLatestCard

    /** A series with more than one episode in the pool: pre-12 rendered the Series container. */
    data class Grouped(
        val seriesId: String,
        val episodes: List<MediaItem>,
    ) : ClassicLatestCard {
        override val mostRecentEpisode: MediaItem get() = episodes.first()
    }
}

/**
 * The classic-rows TV-latest grouping — the client-side twin of Jellyfin
 * 10.x's `UserViewManager.GetLatestItems` + the `GetLatestMedia` controller
 * pick, run over a raw-Episode pool fetched with
 * `IncludeItemTypes=Episode&groupItems=false` (the wire shape that behaves
 * identically on every server generation):
 *
 *  - episodes arrive DateCreated-descending; each series keeps the position
 *    of its FIRST encounter (later episodes append to that group, they do
 *    not open a new card);
 *  - an episode with no `seriesId` stands alone (10.x: a null
 *    `LatestItemsIndexContainer` rendered the item itself);
 *  - grouping stops the way 10.x's `list.Count >= request.Limit` break did:
 *    checked after EVERY item, so the item that fills the row breaks the
 *    walk immediately — nothing after it (appends included) is ever reached;
 *  - after grouping, a group of >1 episode becomes a Series card
 *    ([ClassicLatestCard.Grouped]) and a group of exactly 1 becomes the
 *    episode card ([ClassicLatestCard.Single]) — the 10.x controller's
 *    `Item2.Count > 1` pick.
 *
 * [limit] is the ROW size; the caller feeds a pool of
 * `limit * [CLASSIC_LATEST_POOL_MULTIPLIER]` episodes — the same 5× pool the
 * 10.x server over-fetched before its own grouping, so the candidate set and
 * therefore the grouping outcome match the pre-12 wire result exactly.
 */
internal fun List<MediaItem>.toClassicLatestCards(limit: Int): List<ClassicLatestCard> {
    if (limit <= 0) return emptyList()
    val groups = ArrayList<Pair<String?, MutableList<MediaItem>>>(minOf(limit, size))
    val groupIndexBySeriesId = HashMap<String, Int>()
    for (episode in this) {
        val seriesId = episode.seriesId
        if (seriesId == null) {
            groups.add(null to mutableListOf(episode))
        } else {
            val existing = groupIndexBySeriesId[seriesId]
            if (existing != null) {
                groups[existing].second += episode
            } else {
                groupIndexBySeriesId[seriesId] = groups.size
                groups.add(seriesId to mutableListOf(episode))
            }
        }
        // The 10.x break sits after EVERY item — an append landing once the
        // row is full stops the walk exactly where the server's did.
        if (groups.size >= limit) break
    }
    return groups.map { (seriesId, episodes) ->
        if (seriesId != null && episodes.size > 1) {
            ClassicLatestCard.Grouped(seriesId, episodes)
        } else {
            ClassicLatestCard.Single(episodes.first())
        }
    }
}

/**
 * The degraded [ClassicLatestCard.Grouped] resolution: a Series
 * [MediaItem] synthesized from the group's newest episode when the real
 * Series item fetch failed. Carries the series' own id (so poster/refresh
 * resolve against the real item) and the group size as [MediaItem.childCount]
 * — the one field the 10.x controller also set on grouped cards. Loses only
 * what a Series DTO would add (production year, unplayed count); a degrade,
 * never the primary path.
 */
internal fun ClassicLatestCard.Grouped.synthesizedSeries(): MediaItem = MediaItem(
    id = seriesId,
    name = mostRecentEpisode.seriesName ?: mostRecentEpisode.name,
    mediaType = MediaType.SERIES,
    childCount = episodes.size,
)

/**
 * The latest-media rows' client-side kind narrowing — the #168 classic-rows
 * belt-and-braces applied by `LibraryApiClientImpl.getLatestMedia` when the
 * call carries a kind narrowing (today: the classic TV pool's `Episode` pin).
 *
 * `/Items/Latest` on a Jellyfin 12.x server returns a mix of Series, Season
 * and Episode rows for TV folders (upstream regression jellyfin#17887 /
 * jellyfin#17978). The server-side `includeItemTypes` narrowing is the
 * primary fix; this fold guarantees the semantics regardless of server
 * generation — old servers may ignore the query param entirely, the same
 * rationale the books resume fold records. Null allowed-kinds =
 * unconstrained (the modern path).
 *
 * Like every post-limit fold (the parental filter, [resumableOnly]), this can
 * under-fill the row relative to `limit` — accepted, pre-existing tradeoff.
 */
internal fun List<MediaItem>.toFilteredLatestRows(
    maxParentalRating: Int?,
    allowedKinds: Set<MediaType>?,
): List<MediaItem> {
    val filtered = filterByParentalRating(maxParentalRating)
    return filtered.filterAllowedKinds(allowedKinds)
}
