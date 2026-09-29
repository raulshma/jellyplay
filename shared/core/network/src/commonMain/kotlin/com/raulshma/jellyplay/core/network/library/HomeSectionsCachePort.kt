package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * The home hot-path's cache-maintenance verbs, split off
 * [com.raulshma.jellyplay.core.network.api.LibraryApiClient] (which used to
 * declare them): a network API interface should not carry cache-management
 * vocabulary, and core:data's write paths should not have to know the network
 * layer's cache inventory. The port sits beside [HomeSectionsFetcher] — the
 * single owner of those caches — and its one production implementation is the
 * small adapter on `LibraryApiClientImpl` that forwards each verb to the
 * fetcher, so a future sub-call cache is a network-module-only change.
 *
 * Consumed ONLY by the data layer's write/roll paths (the user-data composite
 * eviction, the wholesale invalidation, and the dice roll's
 * invalidate→fetch→seed halves on `MediaRepositoryImpl`) — reads go through
 * the ordinary client methods. Ordering and epoch roles are roll-protocol
 * concerns with their single owner on `MediaRepository.rerollDiscoverRow`;
 * this port is the transport those protocol steps land on, nothing more.
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
     * Drops ONE discover row's memoised items (the dice affordance): the next
     * home fetch re-rolls a RANDOM row instead of replaying the cached set for
     * the sub-call TTL. Best-effort and synchronous — a no-op when the row has
     * not been memoised.
     */
    fun invalidateDiscoverRow(rowId: String)

    /**
     * Memoises one discover row's freshly fetched items in the home fetcher's
     * per-row sub-call cache (the dice roll's commit step): the next home
     * fetch serves the rolled items instead of re-querying the server, so a
     * roll survives the periodic refresh. No-op on an empty list; the key
     * derivation matches the fetch path's.
     */
    fun seedDiscoverRow(row: DiscoverRowConfig, items: List<MediaItem>)
}
