package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine

/**
 * The [HomeSectionsCachePort]'s one production implementation — the small
 * adapter the port's KDoc describes. The verbs forward to
 * [HomeSectionsFetcher] — the single owner of the home hot-path sub-call
 * caches AND of the row-mutation choreography (the roll's invalidate →
 * fetch → seed runs inside it, behind its own write guard) — and the
 * single-row refetch keeps its exact former transport, the engine's retry
 * chassis ([JellyfinApiEngine.apiResultWithRetry]) wrapped around the
 * fetcher's per-row Result (a failure rethrows inside the retry block and
 * comes back as `Result.failure` with the retry policy applied).
 *
 * The roll op deliberately takes NO extra retry wrapper: its raw fetch
 * already rides the engine through the sources port (`LibraryApiClientImpl`
 * satisfies [HomeSectionSources] with the same wrapped methods the wide
 * client exposes), so wrapping the whole operation would re-run the
 * drop-and-seed choreography per retry attempt — a behavior change, not a
 * pass-through.
 *
 * Constructed ON the client impl — which builds the fetcher — and re-exposed
 * to the Koin construction owner through it, so the port binding, the wide
 * client, and the fetcher stay one home stack (the adapter holds the same
 * [JellyfinApiEngine] the client does; it adds no state of its own).
 */
internal class HomeSectionsCachePortAdapter(
    private val engine: JellyfinApiEngine,
    private val fetcher: HomeSectionsFetcher,
) : HomeSectionsCachePort {

    override fun invalidateSubcallCaches() {
        fetcher.invalidateCaches()
    }

    override suspend fun rerollDiscoverRow(row: DiscoverRowConfig): Result<List<MediaItem>> =
        fetcher.rerollDiscoverRow(row)

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
