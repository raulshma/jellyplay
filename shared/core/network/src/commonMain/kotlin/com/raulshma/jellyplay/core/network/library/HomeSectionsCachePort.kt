package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * The home hot-path's cache-maintenance and row-mutation verbs, split off
 * [com.raulshma.jellyplay.core.network.api.LibraryApiClient] (which used to
 * declare them): a network API interface should not carry cache-management
 * vocabulary, and core:data's write paths should not have to know the network
 * layer's cache inventory. The port sits beside [HomeSectionsFetcher] — the
 * single owner of those caches — and its one production implementation is the
 * small adapter on `LibraryApiClientImpl` that forwards each verb to the
 * fetcher, so a future sub-call cache is a network-module-only change.
 *
 * Consumed ONLY by the data layer's write/roll paths (the user-data composite
 * eviction, the wholesale invalidation, and the dice roll + single-row
 * refresh on `MediaRepositoryImpl`) — reads go through the ordinary client
 * methods. The two row-mutation verbs are COMPLETE operations owned by the
 * fetcher (the roll's invalidate → fetch → seed choreography runs inside it,
 * behind its own write guard); they hand back only the outcome, and the data
 * layer bumps its own assembled-payload cache generation when an operation
 * reports that a row changed. No cache-write token crosses this seam — the
 * roll protocol's ordering rules live on `HomeFeed.rerollDiscoverRow` and in
 * the fetcher operations' KDoc.
 */
public interface HomeSectionsCachePort {

    /**
     * Drops the home hot-path sub-call caches (per-folder latest media,
     * per-seed similar items, the discover-row memos) so the next
     * `LibraryApiClient.getHomeSections` fetch re-hits the server for those
     * rows. Their entries carry per-item UserData, so a watched/favorite/
     * progress write calling this purges the pre-write rows instead of letting
     * them serve stale badges for the sub-call TTL. Best-effort and
     * synchronous — a no-op before any home fetch has memoised.
     */
    fun invalidateSubcallCaches()

    /**
     * The dice re-roll as ONE operation (the invalidate → fetch → seed
     * choreography, owned by the fetcher — see
     * [HomeSectionsFetcher.rerollDiscoverRow]): drops the row's memoised
     * items, fetches the row fresh from the server, and — on a non-empty
     * result — commits the rolled set into the per-row memo so the next home
     * fetch serves the roll. Returns the fresh items; a failure or an empty
     * result returns as-is with the pre-fetch drop standing (the next home
     * fetch re-queries the row rather than replaying or pinning pre-roll
     * items). Either way the row's cached state changed — the caller bumps
     * its own assembled-payload cache when this returns.
     */
    suspend fun rerollDiscoverRow(row: DiscoverRowConfig): Result<List<MediaItem>>

    /**
     * The single-row home refetch (the home screen's edge-pull refresh):
     * re-runs exactly the sub-call(s) the batch fetch runs for [section]'s
     * row — see [HomeSectionsFetcher.refreshSection] for the per-type mapping,
     * the outcome contract (`null` = the row emptied and should drop) and the
     * caching policy (sub-call memos bypassed on read, written on success;
     * the assembled-payload cache and the SWR snapshot persist untouched — a
     * single-row result must never masquerade as a whole-query snapshot down
     * that path, which the offline layout mirror would pick up by recency).
     *
     * Lives on this port rather than [com.raulshma.jellyplay.core.network.api.LibraryApiClient]
     * for the same reason the cache verbs do: its only consumer is the data
     * layer's home path, and the port keeps the fetcher's growing surface off
     * the client interface every fake and union otherwise has to track.
     */
    suspend fun refreshHomeSection(
        section: HomeSection,
        query: HomeSectionQuery,
        mergeNextUpIntoContinueWatching: Boolean = false,
        force: Boolean = true,
    ): Result<HomeSection?>
}
