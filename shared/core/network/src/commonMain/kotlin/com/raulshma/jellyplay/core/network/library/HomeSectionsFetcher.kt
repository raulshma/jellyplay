package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PinnedSectionType
import com.raulshma.jellyplay.core.model.RecommendationResult
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.descriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore

/**
 * The home feed's entire view of the transport: exactly the client sub-calls
 * the section-fetch choreography needs, with signatures borrowed verbatim
 * from [com.raulshma.jellyplay.core.network.api.LibraryApiClient] so both
 * production clients satisfy this interface for free via their common
 * supertype — neither ships a single adapter line. Pinned-section LEAVES are
 * port members; the PinnedSectionType→leaf routing table is POLICY and lives
 * in [HomeSectionsFetcher].
 *
 * Members declare NO default values: a class implementing both interfaces
 * cannot inherit defaults for the same parameter from two supertypes (the
 * compiler cannot verify they agree), so [LibraryApiClient]'s defaults stay
 * canonical and this file's call sites pass the port's literal defaults
 * explicitly.
 */
internal interface HomeSectionSources {
    suspend fun getContinueWatching(limit: Int): Result<List<MediaItem>>
    suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int): Result<List<MediaItem>>
    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>
    suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<MediaItem>>
    suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>>
    suspend fun getSearchSuggestions(limit: Int): Result<SearchResult>
    suspend fun getCollectionItems(collectionId: String, startIndex: Int, limit: Int): Result<SearchResult>
    suspend fun getFavorites(mediaTypes: List<MediaType>?, limit: Int, startIndex: Int): Result<SearchResult>
    suspend fun getItemsByGenre(genreId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult>
    suspend fun getItemsByStudio(studioId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult>
}

/**
 * The fetch half of the home feed, extracted from the two hand-copied client
 * choreographies (`LibraryApiClientImpl.getHomeSections` JVM-side and its wasm
 * twin) into ONE commonMain orchestrator. It turns a [HomeSectionQuery] into
 * the raw sub-call results and hands them to [assembleHomeSections] — which
 * keeps the ordering policy (what fetched data BECOMES); this class owns the
 * fetching (what/when): the concurrent deferred schedule, the semaphore
 * bounds (4 for the latest-media and pinned fan-outs, 3 for the
 * similar-items fan-out), the two TTL sub-call caches and the recommendations
 * chain.
 *
 * Schedule (verbatim from the JVM impl it replaces):
 *  - Continue Watching / Next Up / folders / pinned launch concurrently;
 *    a disabled section makes ZERO port calls (resolved locally as
 *    `Result.success(emptyList())`); folders are gated on
 *    LATEST_MEDIA || RECENTLY_ADDED; pinned sections are fetched ALWAYS,
 *    regardless of [HomeSectionQuery.enabledSections].
 *  - The recommendations chain launches only AFTER Continue Watching and
 *    Next Up resolve (deliberate serialization, not an oversight — their
 *    items seed it) and overlaps the per-folder latest-media fan-out, so
 *    home-load wall clock is max(...) of the two chains.
 *  - The latest fan-out filters music folders, caps at 4 concurrent
 *    `/Items/Latest` calls and collects in folder order.
 *
 * Caching: the latest-media and similar-items sub-calls memoise in
 * [TtlCache]s with a [HomeFreshness.NETWORK_SUBCALL_TTL_MS] TTL, keyed
 * `"<id>_<limit>"` and scoped to the current [CacheIdentity] so a user/server
 * switch misses by construction. [force] (pull-to-refresh) bypasses cache
 * READS but still memoises WRITES — the freshly pulled rows are what the next
 * periodic refresh serves, instead of the pre-pull rows reverting for up to
 * the TTL. Identity note — the ONE deliberate behavior delta vs the two
 * implementations this replaces: both platforms now memoise under
 * [CacheIdentity.UNKNOWN] before login (the wasm twin previously skipped
 * caching entirely when no session existed); nothing cached under UNKNOWN can
 * leak across users, since no real identity ever collides with it.
 *
 * Error policy: partial failures ride [HomeSectionsResult.failedSectionTypes]
 * (a failing pin or per-folder latest row is dropped, never fatal); the
 * fetch throws the first error only when NOTHING rendered at all — the
 * caller wraps [fetch] in its retry/Result machinery.
 */
internal class HomeSectionsFetcher(
    private val sources: HomeSectionSources,
    private val cacheIdentity: () -> CacheIdentity?,
) {

    // ── Home hot-path sub-call caches ──────────────────────────────────────
    // MediaRepository caches the whole HomeSectionsResult for 60s and the
    // HomeViewModel's periodic refresh also runs every 60s, so without these
    // each refresh re-fans-out one getLatestMedia per library folder + up to
    // 5 getSimilarItems calls. Latest/recommendations change far less often
    // than Continue Watching / Next Up, so a short TTL here skips those
    // round-trips on back-to-back refreshes while CW/NextUp stay live.
    // Mirrors the 2-minute TTL the repo uses for the same concepts — both
    // values are [HomeFreshness.NETWORK_SUBCALL_TTL_MS], one policy constant.
    private val homeLatestMediaCache = TtlCache<List<MediaItem>>(ttlMs = HomeFreshness.NETWORK_SUBCALL_TTL_MS)
    private val homeSimilarCache = TtlCache<List<MediaItem>>(ttlMs = HomeFreshness.NETWORK_SUBCALL_TTL_MS)

    suspend fun fetch(query: HomeSectionQuery, force: Boolean = false): HomeSectionsResult = coroutineScope {
        // Only enabledSections earns a local (gates every deferred launch
        // below); everything else the query bundles is read at its single
        // use site as query.<field>, so the value object stays intact
        // instead of being re-flattened into positional locals.
        val enabledSections = query.enabledSections
        // Unified identity normalization: both platforms memoise under
        // UNKNOWN pre-login — see the class KDoc for why this is deliberate.
        val identity = cacheIdentity() ?: CacheIdentity.UNKNOWN

        val continueWatchingDeferred = async {
            if (HomeSectionType.CONTINUE_WATCHING in enabledSections) sources.getContinueWatching(limit = 20)
            else Result.success(emptyList())
        }
        val nextUpDeferred = async {
            if (HomeSectionType.NEXT_UP in enabledSections) sources.getNextUp(
                limit = 20,
                enableRewatching = query.nextUpRewatching,
                maxDays = query.nextUpMaxDays,
            )
            else Result.success(emptyList())
        }
        val foldersDeferred = async {
            if (HomeSectionType.LATEST_MEDIA in enabledSections || HomeSectionType.RECENTLY_ADDED in enabledSections) {
                sources.getLibraryFolders()
            } else {
                Result.success(emptyList())
            }
        }
        // Kick off pinned-section fetches concurrently with the standard
        // sections so they add no extra wall-clock latency to home loading.
        // Fetched ALWAYS — regardless of enabledSections.
        val pinnedDeferred = async { fetchPinnedSections(query.pinnedSections) }

        val continueWatchingResult = continueWatchingDeferred.await()
        val nextUpResult = nextUpDeferred.await()
        val foldersResult = foldersDeferred.await()

        // Launch the recommendations chain now: it depends only on the
        // Continue Watching / Next Up seeds above (already resolved), not
        // on the per-folder latest-media fan-out below — overlapping the
        // two chains turns home-load wall clock from
        // latestChain + recommendationsChain into max(...) while keeping
        // section emission order unchanged (awaited at its original spot).
        val recommendationsDeferred: Deferred<Result<RecommendationResult>>? =
            if (HomeSectionType.RECOMMENDATIONS in enabledSections) {
                // Reuse the Continue Watching + Next Up lists already fetched
                // above as recommendation seeds instead of re-hitting the
                // /Items/Resume and /Shows/NextUp endpoints a second time.
                val recommendationSeeds =
                    continueWatchingResult.getOrDefault(emptyList()) +
                        nextUpResult.getOrDefault(emptyList())
                async { recommendations(limit = 20, seeds = recommendationSeeds, force = force, identity = identity) }
            } else null

        // Latest-media fan-out: one /Items/Latest per non-music folder,
        // semaphore-bounded at 4, collected in folder order for the
        // assembler — the fetch half stays here, the section-building
        // and ordering policy lives in the shared pure assembler.
        var latestPerFolder: List<Pair<LibraryFolder, Result<List<MediaItem>>>> = emptyList()
        if (HomeSectionType.LATEST_MEDIA in enabledSections || HomeSectionType.RECENTLY_ADDED in enabledSections) {
            foldersResult.onSuccess { folders ->
                val filteredFolders = folders
                    .filter { it.collectionType != "music" }
                val semaphore = Semaphore(4)
                latestPerFolder = filteredFolders
                    .map { folder ->
                        async {
                            semaphore.acquire()
                            try { folder to getLatestMediaForHome(folder.id, limit = 16, force = force, identity = identity) }
                            finally { semaphore.release() }
                        }
                    }
                    .map { it.await() }
            }
        }

        val recommendationsResult = recommendationsDeferred?.await()
        // Suggestions fallback fetched only when recommendations succeeded
        // but produced no items — the SAME predicate the assembler's
        // fallback branch (~162) renders on; the two are pinned together by
        // HomeSectionsFetcherTest.
        val suggestions = recommendationsResult
            ?.getOrNull()
            ?.takeIf { it.items.isEmpty() }
            ?.let { sources.getSearchSuggestions(limit = 20).getOrNull()?.items.orEmpty() }
            .orEmpty()

        val output = assembleHomeSections(
            HomeSectionsAssemblyInputs(
                query = query,
                continueWatchingResult = continueWatchingResult,
                nextUpResult = nextUpResult,
                foldersResult = foldersResult,
                latestPerFolder = latestPerFolder,
                recommendationsResult = recommendationsResult,
                suggestions = suggestions,
                pinnedSections = pinnedDeferred.await(),
            ),
        )
        if (output.result.sections.isEmpty() && output.firstError != null) {
            throw output.firstError!!
        }
        output.result
    }

    /**
     * Shared read/write shape of the home sub-call caches: consult [cache]
     * first unless [force] (pull-to-refresh), and memoise every successful
     * fetch — including a forced one, so the freshly pulled rows survive the
     * next periodic refresh instead of the pre-pull rows reverting for up to
     * the TTL. Keys are scoped to the current [CacheIdentity] so a
     * user/server switch can never serve the previous identity's rows.
     */
    private suspend fun cachedHomeSubCall(
        cache: TtlCache<List<MediaItem>>,
        keyPart: String,
        limit: Int,
        force: Boolean,
        identity: CacheIdentity,
        fetch: suspend () -> Result<List<MediaItem>>,
    ): Result<List<MediaItem>> {
        val cacheKey = "${keyPart}_$limit"
        if (!force) {
            cache.get(identity, cacheKey)?.let { return Result.success(it) }
        }
        return fetch().also { result ->
            result.getOrNull()?.let { cache.put(identity, cacheKey, it) }
        }
    }

    /**
     * Home-path wrapper around [HomeSectionSources.getLatestMedia] that
     * consults [homeLatestMediaCache] first. Only the home path uses this —
     * browse/library screens still go straight to the port for fresh data.
     */
    private suspend fun getLatestMediaForHome(parentId: String, limit: Int, force: Boolean, identity: CacheIdentity): Result<List<MediaItem>> =
        cachedHomeSubCall(homeLatestMediaCache, parentId, limit, force, identity) { sources.getLatestMedia(parentId, limit) }

    /**
     * Home-path wrapper around [HomeSectionSources.getSimilarItems] that
     * memoises each seed's similar-items list in [homeSimilarCache]. The
     * recommendations fan-out (up to 5 concurrent similar-items calls) is
     * the single most expensive part of a home refresh; seeds rarely change
     * within the TTL window, so back-to-back refreshes skip it entirely.
     */
    private suspend fun getSimilarItemsForHome(seedId: String, limit: Int, force: Boolean, identity: CacheIdentity): Result<List<MediaItem>> =
        cachedHomeSubCall(homeSimilarCache, seedId, limit, force, identity) { sources.getSimilarItems(seedId, limit) }

    /**
     * The recommendations ("Recommended For You") core. Preserved wart, kept
     * deliberately: with NO usable seeds it fetches its own via
     * getContinueWatching(5) / getNextUp(limit 5, rewatching false, maxDays 0)
     * — a second pair of round-trips when the sections themselves are merely
     * empty rather than disabled.
     */
    private suspend fun recommendations(
        limit: Int,
        seeds: List<MediaItem>,
        force: Boolean,
        identity: CacheIdentity,
    ): Result<RecommendationResult> = runCatching {
        // Reuse caller-supplied seeds when available (e.g. the home screen has
        // already fetched Continue Watching + Next Up) to avoid duplicate
        // /Items/Resume and /Shows/NextUp round-trips within the same load.
        val seedItems = if (seeds.isNotEmpty()) {
            seeds.distinctBy { it.id }.take(5)
        } else {
            val continueWatching = sources.getContinueWatching(limit = 5).getOrDefault(emptyList())
            val nextUp = sources.getNextUp(limit = 5, enableRewatching = false, maxDays = 0).getOrDefault(emptyList())
            (continueWatching + nextUp).distinctBy { it.id }.take(5)
        }

        if (seedItems.isEmpty()) return@runCatching RecommendationResult(emptyList(), null)

        val seedIds = seedItems.map { it.id }.toSet()
        val semaphore = Semaphore(3)
        val allSimilar = coroutineScope {
            seedItems.map { seed ->
                async {
                    semaphore.acquire()
                    try {
                        // Routed through homeSimilarCache: recommendations are the
                        // most expensive part of a home refresh (up to 5 concurrent
                        // similar-items calls) and seeds rarely change within the
                        // TTL window, so back-to-back refreshes (60s cadence) skip
                        // the fan-out. Also benefits the detail screen's re-entry.
                        val perSeedLimit = limit / seedItems.size + 2
                        getSimilarItemsForHome(seed.id, perSeedLimit, force, identity).getOrDefault(emptyList())
                    }
                    finally { semaphore.release() }
                }
            }.flatMap { it.await() }
        }

        val recommendations = allSimilar
            .filter { it.id !in seedIds }
            .distinctBy { it.id }
            .take(limit)

        RecommendationResult(recommendations, seedItems.firstOrNull())
    }

    private suspend fun fetchPinnedSections(
        pinnedSections: List<PinnedHomeSection>,
    ): List<HomeSection> {
        if (pinnedSections.isEmpty()) return emptyList()
        val semaphore = Semaphore(4)
        return coroutineScope {
            val deferred = pinnedSections.map { pinned ->
                async {
                    semaphore.acquire()
                    try {
                        val items = getPinnedSectionItems(pinned)
                        if (items.isNotEmpty()) {
                            HomeSection(
                                id = HomeSectionType.PINNED.descriptor.idFor(pinned.id),
                                title = pinned.title,
                                type = HomeSectionType.PINNED,
                                items = items,
                            )
                        } else null
                    } catch (_: Exception) {
                        // A single failing pin (e.g. deleted collection) must not
                        // break the whole home screen; just drop that row.
                        null
                    } finally {
                        semaphore.release()
                    }
                }
            }
            deferred.awaitAll().filterNotNull()
        }
    }

    /** Resolves the items for a single pinned section using its source type. */
    private suspend fun getPinnedSectionItems(pinned: PinnedHomeSection): List<MediaItem> = when (pinned.type) {
        // Playlists and collections are both parent-scoped item queries; reusing
        // getCollectionItems avoids excluding episode items (getMediaItems drops
        // seasons/episodes), which matters for video playlists.
        PinnedSectionType.COLLECTION,
        PinnedSectionType.PLAYLIST,
        -> sources.getCollectionItems(pinned.sourceId, startIndex = 0, limit = 20)
            .getOrNull()?.items.orEmpty()
        PinnedSectionType.FAVORITES -> sources.getFavorites(mediaTypes = null, limit = 20, startIndex = 0)
            .getOrNull()?.items.orEmpty()
        PinnedSectionType.GENRE -> sources.getItemsByGenre(pinned.sourceId, mediaTypes = null, startIndex = 0, limit = 20)
            .getOrNull()?.items.orEmpty()
        PinnedSectionType.STUDIO -> sources.getItemsByStudio(pinned.sourceId, mediaTypes = null, startIndex = 0, limit = 20)
            .getOrNull()?.items.orEmpty()
    }
}
