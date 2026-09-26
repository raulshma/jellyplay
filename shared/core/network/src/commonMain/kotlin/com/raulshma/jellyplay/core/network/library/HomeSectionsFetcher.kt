package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.concurrency.mapConcurrentCatching
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.DiscoverRowSource
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
import com.raulshma.jellyplay.core.model.SeerrRowMedia
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.cacheThrough
import com.raulshma.jellyplay.core.model.descriptor
import com.raulshma.jellyplay.core.model.monotonicNowMillis
import com.raulshma.jellyplay.core.model.seerr.SeerrDiscoverParams
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore

/**
 * Monotonic counter backing [HomeSectionsFetcher]'s discover-row epoch guard
 * (see the `discoverRowEpoch` field there). Expect/actual rather than
 * `kotlin.concurrent.atomics` — still experimental at this stdlib version,
 * and no commonMain atomics seam exists in this module yet. JVM actual lives
 * in jvmShared and serves both targets.
 */
internal expect class DiscoverRowEpoch() {
    fun incrementAndGet(): Long
    fun get(): Long
}

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
    suspend fun getContinueReading(limit: Int): Result<List<MediaItem>>
    suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int): Result<List<MediaItem>>
    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>
    suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<MediaItem>>
    suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>>
    suspend fun getSearchSuggestions(limit: Int): Result<SearchResult>
    suspend fun getCollectionItems(collectionId: String, startIndex: Int, limit: Int): Result<SearchResult>
    suspend fun getFavorites(mediaTypes: List<MediaType>?, limit: Int, startIndex: Int): Result<SearchResult>
    suspend fun getItemsByGenre(genreId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult>
    suspend fun getItemsByStudio(studioId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult>
    suspend fun getDiscoverRowItems(row: DiscoverRowConfig): Result<List<MediaItem>>
}

/**
 * The Seerr-side sibling of [HomeSectionSources]: exactly the two discover
 * sub-calls the custom SEERR-sourced rows need (the transport twin of what
 * the FEATURE layer used to call through `SeerrRepository.getDiscoverMovies/
 * getDiscoverTv`), plus ONE availability probe. Signatures deliberately carry
 * the [SeerrDiscoverParams] the fetcher builds from the row — the port stays
 * a dumb transport, session resolution stays with the adapter that satisfies
 * it (`LibraryApiClientImpl` cannot satisfy this one for free: Seerr session
 * state lives in the datastore layer, so the Koin construction owner wires a
 * dedicated adapter beside the client).
 *
 * [seerrAvailable] encodes the WHAT gate the feature layer used to own
 * (Seerr enabled AND a resolvable connection): false means the fetcher skips
 * the Seerr fan-out for that pass with no port calls — the feature's
 * `!prefs.enabled` / not-configured early return, moved to the only layer
 * that can still see it.
 */
public interface SeerrHomeSectionSources {
    /** Connection + preference probe, read fresh on every home fetch. */
    val seerrAvailable: Boolean

    suspend fun getDiscoverMovies(params: SeerrDiscoverParams?): Result<SeerrSearchResponse>

    suspend fun getDiscoverTv(params: SeerrDiscoverParams?): Result<SeerrSearchResponse>
}

/**
 * The fetch half of the home feed, extracted from the hand-copied client
 * choreography (`LibraryApiClientImpl.getHomeSections` JVM-side)
 * into ONE commonMain orchestrator. It turns a [HomeSectionQuery] into
 * the raw sub-call results and hands them to [assembleHomeSections] — which
 * keeps the ordering policy (what fetched data BECOMES); this class owns the
 * fetching (what/when): the concurrent deferred schedule, the semaphore
 * bounds (4 for the latest-media and pinned fan-outs, 3 for the
 * similar-items and discover-row fan-outs), the TTL sub-call caches and the
 * recommendations chain.
 *
 * Schedule (verbatim from the JVM impl it replaces):
 *  - Continue Watching / Continue Reading / Next Up / folders / pinned launch
 *    concurrently; a disabled section makes ZERO port calls (resolved locally
 *    as `Result.success(emptyList())`); folders are gated on
 *    LATEST_MEDIA || RECENTLY_ADDED; pinned sections are fetched ALWAYS,
 *    regardless of [HomeSectionQuery.enabledSections].
 *  - The recommendations chain launches only AFTER Continue Watching and
 *    Next Up resolve (deliberate serialization, not an oversight — their
 *    items seed it) and overlaps the per-folder latest-media fan-out, so
 *    home-load wall clock is max(...) of the two chains.
 *  - The latest fan-out filters music folders, caps at 4 concurrent
 *    `/Items/Latest` calls and collects in folder order.
 *  - Custom discover rows of BOTH sources fetch in one path (see
 *    [fetchDiscoverRows]): Jellyfin rows per-row memoised behind the
 *    dice-roll epoch guard, Seerr rows behind a whole-group TTL gate +
 *    last-known-good (the policy the feature layer's
 *    `fetchCustomSeerrRows` used to own, moved here when the parallel
 *    reimplementation died), emitting the DISCOVER block ALREADY ordered by
 *    row-config index so no downstream splice can disagree about order.
 *
 * Caching: the latest-media and similar-items sub-calls memoise in
 * [TtlCache]s with a [HomeFreshness.NETWORK_SUBCALL_TTL_MS] TTL, keyed
 * `"<id>_<limit>"` and scoped to the current [CacheIdentity] so a user/server
 * switch misses by construction. [force] (pull-to-refresh) bypasses cache
 * READS but still memoises WRITES — the freshly pulled rows are what the next
 * periodic refresh serves, instead of the pre-pull rows reverting for up to
 * the TTL. Identity note: memoisation now runs under
 * [CacheIdentity.UNKNOWN] before login; nothing cached under UNKNOWN can
 * leak across users, since no real identity ever collides with it. All
 * cache-through reads run the shared
 * [com.raulshma.jellyplay.core.model.cacheThrough] engine (hit-check +
 * optional force + optional epoch-guarded write).
 *
 * Error policy: partial failures ride [HomeSectionsResult.failedSectionTypes]
 * (a failing pin or per-folder latest row is dropped, never fatal); the
 * fetch throws the first error only when NOTHING rendered at all — the
 * caller wraps [fetch] in its retry/Result machinery.
 *
 * [seerrSources] is nullable only because construction sites without a Seerr
 * transport (unit fakes, platforms that wire none) must keep compiling; a
 * null source behaves exactly like [SeerrHomeSectionSources.seerrAvailable]
 * == false — zero Seerr port calls, zero Seerr rows.
 */
internal class HomeSectionsFetcher(
    private val sources: HomeSectionSources,
    private val seerrSources: SeerrHomeSectionSources?,
    private val cacheIdentity: () -> CacheIdentity?,
    /**
     * Today's ISO `yyyy-MM-dd` for the Seerr rows' `upcomingOnly` date floor
     * (`SeerrRowFilters.toSeerrDiscoverParams`) — the string the feature
     * layer used to build from `HomeClock.today()`. A constructor seam (not
     * computed here) because commonMain has no timezone-aware calendar: the
     * JVM construction site supplies `LocalDate.now()` (system zone), the
     * exact value the feature produced.
     */
    private val today: () -> String,
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

    /**
     * Custom discover rows. Longer TTL than the latest/similar caches
     * ([HomeFreshness.DISCOVER_ROW_TTL_MS]): a RANDOM-sorted row must stay
     * stable across the 60s periodic refresh (no per-minute reshuffle); the
     * dice affordance ([invalidateDiscoverRow]) and a forced fetch re-roll.
     */
    private val homeDiscoverRowCache = TtlCache<List<MediaItem>>(ttlMs = HomeFreshness.DISCOVER_ROW_TTL_MS)

    /**
     * The dice roll's stall guard, consumed as [cacheThrough]'s write guard
     * by the Jellyfin discover-row reads. Part of the roll protocol — see
     * `MediaRepository.rerollDiscoverRow` (the protocol's single owner) for
     * the three race windows and the bump-at-invalidate-AND-commit rule.
     */
    private val discoverRowEpoch = DiscoverRowEpoch()

    // ── Seerr discover rows (moved from the feature layer's ─────────────────
    // fetchCustomSeerrRows) ─────────────────────────────────────────────────
    // Whole-GROUP freshness gate + last-known-good memo, the pair the feature
    // used to own: the gate spares the Seerr round-trips on back-to-back
    // refreshes; the memo keeps the last rendered rows visible across a total
    // fetch failure (an outage can neither blank the rows nor pin the blank
    // for the TTL — the gate only stamps on a partial-or-better success).
    //
    // lastKnownSeerrRows is deliberately NOT identity-scoped, matching the
    // feature var it replaces: the Seerr connection is app-wide state (same
    // Seerr instance for every Jellyfin user of this install), so a user
    // switch neither voids nor needs to void it.
    @Volatile
    private var lastKnownSeerrRows: List<HomeSection> = emptyList()
    private val seerrRowsGate = DiscoverRowsTtlGate(HomeFreshness.DISCOVER_TTL_MS)

    /**
     * Drops both sub-call caches so the next home fetch re-hits the server for the
     * latest/similar rows. The rows carry per-item UserData (played badge,
     * favorite heart, resume bar), so a watched/favorite/progress write must
     * not let this TTL layer serve the pre-write rows — reached from the data
     * layer through [com.raulshma.jellyplay.core.network.api.LibraryApiClient.invalidateHomeSubcallCaches].
     */
    fun invalidateCaches() {
        homeLatestMediaCache.clear()
        homeSimilarCache.clear()
        homeDiscoverRowCache.clear()
    }

    /**
     * Drops ONE discover row's memoised items (dice affordance): the next home
     * fetch re-queries that row — re-rolling a RANDOM sort — while sibling
     * rows keep their cached items. Identity-scoped like every entry, so the
     * evict can never touch another user's row. Ordering and the epoch bump
     * are roll-protocol concerns — see `MediaRepository.rerollDiscoverRow`.
     */
    fun invalidateDiscoverRow(rowId: String) {
        discoverRowEpoch.incrementAndGet()
        val identity = cacheIdentity() ?: CacheIdentity.UNKNOWN
        homeDiscoverRowCache.removeByKeyPrefix(identity, "discover_$rowId")
    }

    /**
     * The discover-row sub-call cache key (minus identity scoping, which
     * [TtlCache] applies): the single home of the key grammar — both the
     * fetch path ([fetchDiscoverRows]) and [seedDiscoverRow] resolve their
     * keys through it, so the two writers cannot drift.
     */
    private fun discoverRowCacheKey(rowId: String, limit: Int): String = "discover_${rowId}_$limit"

    /**
     * Memoises a discover row's freshly fetched items as if the fetcher had
     * fetched them (the dice roll's commit): the next home fetch serves the
     * rolled items from this cache instead of re-querying the server, so the
     * row the user sees survives the next periodic refresh rather than
     * reverting to the pre-roll payload (or silently re-rolling again).
     * No-op on an empty list; the commit-time epoch bump is a roll-protocol
     * rule — see `MediaRepository.rerollDiscoverRow`.
     */
    fun seedDiscoverRow(row: DiscoverRowConfig, items: List<MediaItem>) {
        if (items.isEmpty()) return
        discoverRowEpoch.incrementAndGet()
        val identity = cacheIdentity() ?: CacheIdentity.UNKNOWN
        homeDiscoverRowCache.put(identity, discoverRowCacheKey(row.id, row.limit), items)
    }

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
        val continueReadingDeferred = async {
            if (HomeSectionType.CONTINUE_READING in enabledSections) sources.getContinueReading(limit = 20)
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

        // Custom discover rows of BOTH sources (Jellyfin + Seerr), fetched in
        // one path and emitted in row-config order. Concurrent with
        // everything else; a disabled DISCOVER section or zero enabled rows
        // resolves locally with no port calls.
        val discoverDeferred = async {
            if (HomeSectionType.DISCOVER in enabledSections) {
                fetchDiscoverRows(query.discoverRows, force = force, identity = identity)
            } else {
                emptyList()
            }
        }

        val continueWatchingResult = continueWatchingDeferred.await()
        val continueReadingResult = continueReadingDeferred.await()
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
                latestPerFolder = Semaphore(4).mapConcurrent(filteredFolders) { folder ->
                    folder to getLatestMediaForHome(folder.id, limit = 16, force = force, identity = identity)
                }
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
                continueReadingResult = continueReadingResult,
                nextUpResult = nextUpResult,
                foldersResult = foldersResult,
                latestPerFolder = latestPerFolder,
                recommendationsResult = recommendationsResult,
                suggestions = suggestions,
                pinnedSections = pinnedDeferred.await(),
                discoverSections = discoverDeferred.await(),
            ),
        )
        if (output.result.sections.isEmpty() && output.firstError != null) {
            throw output.firstError!!
        }
        output.result
    }

    /**
     * Fetches the enabled discover rows of BOTH sources and emits the
     * DISCOVER block ALREADY ordered by row-config index (list position in
     * [HomeSectionQuery.discoverRows] — the single ordering authority across
     * sources, previously enforced by the feature layer's splice; assembling
     * in config order here removes the disagreeing twin).
     *
     * Per-source policy (each deliberately different, both preserved from the
     * two implementations this unified):
     *  - JELLYFIN rows: semaphore-bounded at 3, memoised per row in
     *    [homeDiscoverRowCache] (RANDOM stability — see its KDoc) behind the
     *    dice-roll epoch guard, degraded per row (a failing row is dropped,
     *    never fatal — the pinned-section policy).
     *  - SEERR rows: semaphore-bounded at 3 behind the whole-group TTL gate
     *    ([seerrRowsGate]) with last-known-good on total failure — an outage
     *    neither blanks the rows nor pins the blank; partial success keeps
     *    the successes and stamps fresh; a failing row drops. One drift,
     *    accepted when the fetch moved down from the feature layer: the old
     *    `NetworkStatus.Local` fast-skip (rows vanish from that fetch on) is
     *    not visible here, so on a LAN-only box the rows now degrade via the
     *    ordinary failure policy (last-known-good KEEPS them) instead.
     *
     * The two fans run concurrently with each other (the feature fetched its
     * Seerr rows alongside the whole home fetch; keeping the two fans parallel
     * preserves that wall-clock shape within this deferred).
     */
    private suspend fun fetchDiscoverRows(
        rows: List<DiscoverRowConfig>,
        force: Boolean,
        identity: CacheIdentity,
    ): List<HomeSection> {
        val enabledRows = rows.filter { it.enabled }
        if (enabledRows.isEmpty()) return emptyList()
        val jellyfinRows = enabledRows.filter { it.source == DiscoverRowSource.JELLYFIN }
        val seerrRows = enabledRows.filter { it.source == DiscoverRowSource.SEERR }
        if (jellyfinRows.isEmpty() && seerrRows.isEmpty()) return emptyList()

        val sectionsByRowId = HashMap<String, HomeSection>()
        coroutineScope {
            val jellyfinDeferred = if (jellyfinRows.isNotEmpty()) {
                async { fetchJellyfinDiscoverRows(jellyfinRows, force, identity) }
            } else null
            // The Seerr WHAT gate (feature parity): no transport wired, or the
            // probe says unavailable (Seerr disabled / no resolvable session) →
            // zero port calls and zero rows this pass, memo + gate untouched.
            // (The feature's `!prefs.enabled` early return left the memo
            // intact too — a re-enable inside the TTL window re-serves it.)
            val seerrDeferred = when {
                seerrRows.isEmpty() -> {
                    // No configured Seerr rows at all: the memo must not
                    // outlive the configuration that produced it (the feature
                    // cleared it in the same case) — a stale row set can never
                    // re-serve after the user disabled their last Seerr row.
                    lastKnownSeerrRows = emptyList()
                    null
                }
                seerrSources == null || !seerrSources.seerrAvailable -> null
                else -> async { fetchSeerrDiscoverRows(seerrRows, force) }
            }
            (jellyfinDeferred?.await().orEmpty() + seerrDeferred?.await().orEmpty()).forEach { section ->
                sectionsByRowId[section.id] = section
            }
        }
        // Config order by construction: emit in the row list's order, dropping
        // rows the fetch didn't carry (disabled / empty / failed).
        return enabledRows.mapNotNull { sectionsByRowId[HomeSectionType.DISCOVER.descriptor.idFor(it.id)] }
    }

    /**
     * The JELLYFIN half of [fetchDiscoverRows] — the pre-unification body
     * verbatim. R is explicitly nullable: the transform legitimately yields
     * null for an empty/failed row, and mapConcurrentCatching drops those.
     */
    private suspend fun fetchJellyfinDiscoverRows(
        jellyfinRows: List<DiscoverRowConfig>,
        force: Boolean,
        identity: CacheIdentity,
    ): List<HomeSection> {
        val sections: List<HomeSection?> = Semaphore(3).mapConcurrentCatching(jellyfinRows) { row ->
            homeDiscoverRowCache.cacheThrough(
                identity,
                discoverRowCacheKey(row.id, row.limit),
                force = force,
                currentEpoch = discoverRowEpoch::get,
            ) {
                sources.getDiscoverRowItems(row)
            }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { items ->
                    HomeSection(
                        id = HomeSectionType.DISCOVER.descriptor.idFor(row.id),
                        title = row.title,
                        type = HomeSectionType.DISCOVER,
                        items = items,
                    )
                }
        }
        return sections.filterNotNull()
    }

    /**
     * The SEERR half of [fetchDiscoverRows] — the feature layer's
     * `fetchCustomSeerrRows` policy, moved: TTL gate (force acts as the
     * feature's Manual/PullToRefresh invalidation — and like that
     * invalidation it leaves the gate unstamped on a failed forced fetch, so
     * the next ORDINARY fetch retries instead of serving the blank), fan-out
     * bounded at 3, drop-failed-rows, and the last-known-good memo swap ONLY
     * on a partial-or-better success (which is also the only path that
     * stamps the gate).
     */
    private suspend fun fetchSeerrDiscoverRows(
        seerrRows: List<DiscoverRowConfig>,
        force: Boolean,
    ): List<HomeSection> {
        if (force) seerrRowsGate.invalidate()
        if (!seerrRowsGate.shouldFetch(monotonicNowMillis())) return lastKnownSeerrRows
        val todayString = today()
        // R is explicitly nullable (an empty/failed row yields null) —
        // mapConcurrentCatching drops those.
        val fetched: List<HomeSection?> = Semaphore(3).mapConcurrentCatching(seerrRows) { row ->
            seerrDiscoverSection(row, todayString)
        }
        val fetchedRows = fetched.filterNotNull()
        if (fetchedRows.isNotEmpty()) {
            lastKnownSeerrRows = fetchedRows
            seerrRowsGate.markFetched(monotonicNowMillis())
        }
        return lastKnownSeerrRows
    }

    /** One Seerr row: builds the discover params from the row's filters, maps to a section (or null when empty/failed). */
    private suspend fun seerrDiscoverSection(row: DiscoverRowConfig, today: String): HomeSection? {
        val filters = row.seerrFilters
        val params = filters.toSeerrDiscoverParams(today)
        val response = when (filters.media) {
            SeerrRowMedia.MOVIE -> seerrSources?.getDiscoverMovies(params)
            SeerrRowMedia.TV -> seerrSources?.getDiscoverTv(params)
        }?.getOrNull() ?: return null
        val items = response.results.take(row.limit)
        if (items.isEmpty()) return null
        return HomeSection(
            id = HomeSectionType.DISCOVER.descriptor.idFor(row.id),
            title = row.title,
            type = HomeSectionType.DISCOVER,
            items = emptyList(),
            seerrItems = items,
        )
    }

    /**
     * Home-path wrapper around [HomeSectionSources.getLatestMedia] that
     * consults [homeLatestMediaCache] first. Only the home path uses this —
     * browse/library screens still go straight to the port for fresh data.
     */
    private suspend fun getLatestMediaForHome(parentId: String, limit: Int, force: Boolean, identity: CacheIdentity): Result<List<MediaItem>> =
        homeLatestMediaCache.cacheThrough(identity, "${parentId}_$limit", force = force) { sources.getLatestMedia(parentId, limit) }

    /**
     * Home-path wrapper around [HomeSectionSources.getSimilarItems] that
     * memoises each seed's similar-items list in [homeSimilarCache]. The
     * recommendations fan-out (up to 5 concurrent similar-items calls) is
     * the single most expensive part of a home refresh; seeds rarely change
     * within the TTL window, so back-to-back refreshes skip it entirely.
     */
    private suspend fun getSimilarItemsForHome(seedId: String, limit: Int, force: Boolean, identity: CacheIdentity): Result<List<MediaItem>> =
        homeSimilarCache.cacheThrough(identity, "${seedId}_$limit", force = force) { sources.getSimilarItems(seedId, limit) }

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
    ): Result<RecommendationResult> = runCatchingRethrowingCancellation {
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

        if (seedItems.isEmpty()) return@runCatchingRethrowingCancellation RecommendationResult(emptyList(), null)

        val seedIds = seedItems.map { it.id }.toSet()
        val allSimilar = Semaphore(3).mapConcurrent(seedItems) { seed ->
            // Routed through homeSimilarCache: recommendations are the
            // most expensive part of a home refresh (up to 5 concurrent
            // similar-items calls) and seeds rarely change within the
            // TTL window, so back-to-back refreshes (60s cadence) skip
            // the fan-out. Also benefits the detail screen's re-entry.
            val perSeedLimit = limit / seedItems.size + 2
            getSimilarItemsForHome(seed.id, perSeedLimit, force, identity).getOrDefault(emptyList())
        }.flatten()

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
        // A single failing pin (e.g. deleted collection) must not break the
        // whole home screen; drop that row — mapConcurrentCatching's policy.
        return Semaphore(4).mapConcurrentCatching(pinnedSections) { pinned ->
            val items = getPinnedSectionItems(pinned)
            if (items.isNotEmpty()) {
                HomeSection(
                    id = HomeSectionType.PINNED.descriptor.idFor(pinned.id),
                    title = pinned.title,
                    type = HomeSectionType.PINNED,
                    items = items,
                )
            } else null
        }.filterNotNull()
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

/**
 * Whole-group freshness gate for the Seerr discover rows — the semantics of
 * feature/home's `TtlCacheGate`, ported (that one stays in the feature module
 * for its legacy discover-grid use; this copy serves the network-layer rows).
 * The @Volatile fields matter here: unlike the feature twin (single-writer on
 * a main-confined dispatcher), this gate is read/written from fetch
 * coroutines on the caller's dispatcher, so cross-thread visibility is
 * explicit. Racing fetchers may both pass [shouldFetch] and both stamp —
 * benign (both writes are valid fresh results; last writer wins), the same
 * not-single-flight doctrine as [TtlCache] itself.
 */
private class DiscoverRowsTtlGate(
    private val ttlMs: Long,
    private val clock: () -> Long = ::monotonicNowMillis,
) {
    @Volatile
    private var lastFetchEpochMs: Long = 0L

    @Volatile
    private var invalidated: Boolean = true

    /** True when the group is stale (never fetched, invalidated, or past the TTL). */
    fun shouldFetch(now: Long = clock()): Boolean =
        invalidated || now - lastFetchEpochMs >= ttlMs

    /** Records a successful fetch at [now]; clears any pending invalidation. */
    fun markFetched(now: Long = clock()) {
        lastFetchEpochMs = now
        invalidated = false
    }

    /** Forces the next [shouldFetch] to return true regardless of age. */
    fun invalidate() {
        invalidated = true
    }
}
