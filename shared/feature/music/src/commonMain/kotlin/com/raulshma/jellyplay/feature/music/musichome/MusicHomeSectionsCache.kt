package com.raulshma.jellyplay.feature.music.musichome

import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionIdentityProvider
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.monotonicNowMillis

/**
 * The music home's section-aggregate TTL cache — the home screen's
 * serve-stale chassis applied to the five-query fan-out
 * ([MusicHomeViewModel.fetchSections]): a fresh entry into the music home
 * within [TTL_MS] replays the last complete aggregate instead of re-running
 * the five round-trips.
 *
 * One entry, [TtlCache]-keyed by the `(serverId, userId)` identity
 * ([SessionIdentityProvider.cacheIdentity] — the source-flow read, so a fetch
 * landing right after a server switch keys against the NEW identity and can
 * never read the previous one's entries): a wrong identity is a guaranteed
 * miss by construction. The registry registration is the secondary reclaim —
 * identity transitions drop the previous identity's entry immediately instead
 * of waiting out the TTL (the same two-part ritual the core:data identity
 * caches run; this cache is a process-lifetime single, so it outlives any one
 * [MusicHomeViewModel] instance and needs the transition hook to keep memory
 * bounded).
 *
 * [get]/[put] suspend on the identity read ([fetchSections] already runs on a
 * coroutine); [invalidate] is the non-suspend eviction for user-initiated
 * refresh — the mirror-based identity read there is the documented
 * best-effort shape (identity switches clear the cache wholesale via the
 * registry regardless).
 */
class MusicHomeSectionsCache(
    private val sessionIdentity: SessionIdentityProvider,
    sessionCaches: SessionCacheRegistry,
    clock: () -> Long = ::monotonicNowMillis,
) {

    private val cache = TtlCache<List<MusicHomeSection>>(
        maxSize = 1,
        ttlMs = TTL_MS,
        clock = clock,
    )

    init {
        sessionCaches.registerCaches(OWNER, cache)
    }

    /** The last complete aggregate for the current identity, when still fresh. */
    suspend fun get(): List<MusicHomeSection>? =
        cache.get(sessionIdentity.cacheIdentity(), KEY)

    /** Caches a complete aggregate under the current identity. */
    suspend fun put(sections: List<MusicHomeSection>) {
        cache.put(sessionIdentity.cacheIdentity(), KEY, sections)
    }

    /** Drops the entry so the next load re-fetches — the pull-to-refresh lever. */
    fun invalidate() {
        cache.remove(sessionIdentity.cacheIdentitySnapshot(), KEY)
    }

    companion object {
        private const val OWNER = "musicHomeSections"
        private const val KEY = "sections"

        /**
         * Recently-played and favorites move on listening activity, not per
         * minute — two minutes, the default [TtlCache] horizon the sibling
         * near-static caches run.
         */
        const val TTL_MS = 2 * 60_000L
    }
}
