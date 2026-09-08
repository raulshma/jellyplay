package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaDetail

/**
 * Module-internal cache-maintenance seam on the [MediaRepositoryImpl]
 * singleton (plan 08). The per-type "which caches does this detail affect"
 * dispatch used to exist twice — as the ordering rule in the
 * `invalidateUserDataCaches` KDoc on [MediaRepository] and as the
 * `invalidateByType` table in UnifiedMediaDetailProviderImpl — guaranteeing
 * drift. It now has exactly one owner inside the repository, exposed through
 * this narrow internal interface so the provider (same Gradle module) depends
 * on the seam rather than on cache knobs leaking through the public
 * [MediaRepository] surface.
 *
 * Bound in [com.raulshma.jellyplay.core.data.di.DataModule] to the same
 * singleton as [MediaRepository] — a real seam in the type graph, not a
 * second set of caches.
 */
// Visibility: was `internal` in the legacy module; public since the move — the
// staying-legacy MediaRepositoryImpl (implements it) and
// UnifiedMediaDetailProviderImpl (injects it) still reference it.
interface MediaRepositoryCacheInvalidation {

    /**
     * Drops every cache affected by [detail], per its type:
     *  - SERIES: the series' own detail entry + its seasons/episodes catalogue
     *  - EPISODE: the parent series' seasons/episodes catalogue
     *  - ALBUM: the composite user-data drop (detail + tracks + latest media)
     *  - COLLECTION: the collection's paged items cache
     *  - anything else: nothing (the caller-scoped invalidation already ran)
     */
    fun invalidateFor(detail: MediaDetail)

    /**
     * Composite "user data for [itemId] changed" drop — the same eviction set
     * the repository runs around its own played/favorite writes: the item's
     * detail cluster (detail + similar + themes + album tracks), the home
     * latest-media rows, the network layer's home sub-call caches, and — when
     * the item belongs to a series (discovered from the cached detail or
     * [seriesIdHint] when the item itself is not detail-cached, e.g. seasons)
     * — that series' seasons/episodes catalogue. Also reached for playback-
     * position changes (playback stop), which mutate the same served fields
     * (resume position, Continue Watching) without going through a
     * played/favorite flip.
     *
     * Deliberately does NOT eagerly clear the home sections cache: it is
     * scroll- and flicker-sensitive. It heals through the synthetic
     * user-data-change event instead — live via the consumers (throttled
     * silent forced refresh), and lazily via the staleness marker the
     * announcement arms (#157): the next home READ refetches within the TTL
     * window even when no consumer was collecting, so the guarantee the old
     * eager clear provided survives without the blocking refetch.
     */
    fun invalidateForUserDataChange(itemId: String, seriesIdHint: String? = null)
}
