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
}
