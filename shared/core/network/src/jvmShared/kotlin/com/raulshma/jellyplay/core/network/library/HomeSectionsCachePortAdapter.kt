package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine

/**
 * The [HomeSectionsCachePort]'s one production implementation — the small
 * adapter the port's KDoc describes. The four verbs moved verbatim off
 * [com.raulshma.jellyplay.core.network.api.LibraryApiClientImpl] (which used
 * to satisfy the port directly): each cache verb forwards one line to
 * [HomeSectionsFetcher] — the single owner of the home hot-path sub-call
 * caches — and the single-row refetch keeps its exact former transport, the
 * engine's retry chassis ([JellyfinApiEngine.apiResultWithRetry]) wrapped
 * around the fetcher's per-row Result (a failure rethrows inside the retry
 * block and comes back as `Result.failure` with the retry policy applied).
 *
 * Constructed ON the client impl — which builds the fetcher — and re-exposed
 * to the Koin construction owner through it, so the port binding, the wide
 * client, and the fetcher stay one home stack (the adapter holds the same
 * [JellyfinApiEngine] the client does; it adds no state of its own). The
 * discover-row verbs' [HomeWriteGeneration] token rides along untouched: it
 * is the data layer's post-bump cache-write token, and this adapter neither
 * derives nor alters it — pure pass-through to the fetcher's mirror.
 */
internal class HomeSectionsCachePortAdapter(
    private val engine: JellyfinApiEngine,
    private val fetcher: HomeSectionsFetcher,
) : HomeSectionsCachePort {

    override fun invalidateSubcallCaches() {
        fetcher.invalidateCaches()
    }

    override fun invalidateDiscoverRow(rowId: String, generation: HomeWriteGeneration) {
        fetcher.invalidateDiscoverRow(rowId, generation)
    }

    override fun seedDiscoverRow(row: DiscoverRowConfig, items: List<MediaItem>, generation: HomeWriteGeneration) {
        fetcher.seedDiscoverRow(row, items, generation)
    }

    override suspend fun refreshHomeSection(
        section: HomeSection,
        query: HomeSectionQuery,
        mergeNextUpIntoContinueWatching: Boolean,
        force: Boolean,
    ): Result<HomeSection?> = engine.apiResultWithRetry {
        // The fetcher returns Result (per-row failures are values); the retry
        // wrapper's unit of work is a throw, so unwrap — a failure rethrows
        // here and comes back as Result.failure with the retry policy applied.
        fetcher.refreshSection(section, query, mergeNextUpIntoContinueWatching, force).getOrThrow()
    }
}
