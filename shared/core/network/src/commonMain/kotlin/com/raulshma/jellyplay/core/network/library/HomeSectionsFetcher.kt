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
import com.raulshma.jellyplay.core.model.JellyPlayRowEntry
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
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.network.api.JellyPlayRowItem
import com.raulshma.jellyplay.core.network.api.JellyPlayRowResult
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
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
 *
 * Construction scope (ADR-0008): implemented by `LibraryApiClientImpl` in
 * production (for free — the signatures above are borrowed verbatim from its
 * [LibraryApiClient] supertype, so the same overrides serve both interfaces)
 * and by test fakes — the real-stack contract suite in core:data's jvmTest
 * (`HomeFeedRealStackContractTest`) builds the fetcher on a module-local
 * fake of this interface. Public only because the fetcher's constructor
 * exposes it; direct implementations elsewhere are not supported.
 */
public interface HomeSectionSources {
    suspend fun getContinueWatching(limit: Int, classicRows: Boolean): Result<List<MediaItem>>
    suspend fun getContinueReading(limit: Int): Result<List<MediaItem>>
    suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int): Result<List<MediaItem>>
    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>
    suspend fun getLatestMedia(parentId: String, limit: Int, classicEpisodePool: Int?): Result<List<MediaItem>>
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

    suspend fun getDiscoverMovies(params: SeerrDiscoverParams?): Result<List<SeerrSearchItem>>

    suspend fun getDiscoverTv(params: SeerrDiscoverParams?): Result<List<SeerrSearchItem>>
}

/**
 * The plugin-side sibling of [SeerrHomeSectionSources]: the two jellyplay row
 * reads the PLUGIN_ROW home rows need (the transport twin of
 * `JellyPlayPluginApiClient.getSeasonalRow/getCustomRow`), the plugin's
 * local-library matches resolved to full [MediaItem]s, and the capability
 * gates. Signatures deliberately carry the raw wire payload — the port stays
 * a dumb transport; payload→[HomeSection] mapping, TTL memoisation and the
 * resolve-then-map order stay with the fetcher. `LibraryApiClientImpl` cannot
 * satisfy this one for free (the probe lives in the data layer's
 * `JellyPlayPluginStatusStore`), so the Koin construction owner wires a
 * dedicated adapter beside the client — the exact shape of the Seerr port's
 * `SeerrHomeSectionSourcesImpl`.
 *
 * The gates are SUSPEND (unlike the Seerr port's val probe) because the
 * plugin's probe is a network handshake, not a preference read: the adapter
 * runs the once-per-session capabilities probe when the store still reports
 * UNKNOWN, then reads the registry — gating exactly on
 * `AVAILABLE && JellyPlayPluginFeatures.X` per ADR 0010 (never on per-endpoint
 * 404s; nothing here consumes raw probe results). An UNAVAILABLE store is
 * trusted for the session (the store's own semantics), so a plugin-absent
 * server costs at most one probe per session, never a per-refresh round-trip.
 */
public interface JellyPlayHomeSectionSources {

    /** Capability gate for the seasonal row (probe + `seasonal-rows` feature). */
    suspend fun seasonalRowsEnabled(): Boolean

    /**
     * Capability gate for admin-defined custom rows (probe + `custom-rows`
     * feature). Unused by the batch home path in v1 — see
     * [HomeSectionsFetcher.fetchPluginCustomRow] for the one blocker.
     */
    suspend fun customRowsEnabled(): Boolean

    /** The plugin's seasonal row; null = unconfigured this season (plugin 404). */
    suspend fun getSeasonalRow(keyword: String?): Result<JellyPlayRowResult?>

    /** The admin-defined titled row; null = no such row (plugin 404). */
    suspend fun getCustomRow(title: String): Result<JellyPlayRowResult?>

    /** The admin-defined row catalog (the enumerate capability — titles in plugin config order); null = unavailable. */
    suspend fun getCustomRowCatalog(): Result<com.raulshma.jellyplay.core.network.api.JellyPlayRowCatalog?>

    /**
     * The batched library read resolving a row entry's `localItemId` to the
     * full [MediaItem] the native home cards render from (`/Items?Ids=…`,
     * the same projection the home section sub-calls use, so resolved entries
     * carry the per-item UserData the cards render). Failures degrade to
     * fallback tiles, never a dropped row.
     */
    suspend fun getItemsByIds(ids: List<String>): Result<List<MediaItem>>
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
 *    dice-roll generation guard (the repo-owned cache-write token this
 *    class mirrors — see [observedGeneration]), Seerr rows behind a
 *    whole-group TTL gate +
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
 *
 * [jellyPlaySources] is nullable with a default for the same reason (and
 * because the production construction owner — the network module — cannot see
 * the data-layer probe the adapter reads, so the adapter arrives through a
 * cross-module `getOrNull` that is null in graphs without the plugin cluster):
 * a null source behaves exactly like a gate reading false — zero plugin port
 * calls, zero plugin rows.
 *
 * Construction scope (ADR-0008): the production constructor caller is
 * `LibraryApiClientImpl` (which also satisfies [HomeSectionSources] for free
 * and adapts the [HomeSectionsCachePort] verbs onto this class); the other
 * sanctioned caller is the real-stack contract suite in core:data's jvmTest
 * (`HomeFeedRealStackContractTest`) — the one suite executing this fetcher
 * and the data layer's repository together. Direct construction elsewhere is
 * not supported — go through the client.
 */
public class HomeSectionsFetcher(
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
    /**
     * The jellyfin-plugin-jellyplay row transport (seasonal today, admin
     * custom rows plumbed beside it) — nullable-with-default, see the class
     * KDoc. Null (or a gate reading false) means zero plugin port calls and
     * zero PLUGIN_ROW sections.
     */
    private val jellyPlaySources: JellyPlayHomeSectionSources? = null,
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

    /** Per-folder latest-media row size — shared by the batch fetch and [refreshSection]. */
    private val latestRowLimit = 16

    /**
     * The write guard for the Jellyfin discover-row memo reads
     * ([fetchJellyfinDiscoverRows] and [refreshSection]'s DISCOVER arm): a
     * MIRROR of the data layer's ONE home cache-write generation token
     * (`MediaRepositoryImpl.homeWriteGeneration`). This class owns no
     * counter — the only writes here are the [generation] parameter of the
     * two mutating [HomeSectionsCachePort] verbs ([invalidateDiscoverRow] /
     * [seedDiscoverRow]), each carrying the repo funnel's post-bump value,
     * so the mirror is provably fed by the token's single owner and the
     * bump-at-invalidate-AND-commit rule has exactly one writer (see
     * `HomeFeed.rerollDiscoverRow`, the roll protocol's single owner).
     * `@Volatile` because the writers arrive through port calls on the
     * caller's thread while the guard reads ride fetch coroutines — the
     * same explicit-visibility idiom as [lastKnownSeerrRows]. The guard
     * compares captured-vs-current values only (never against a constant),
     * so the mirror skipping the repo bump that has no port verb (the
     * single-row refetch's) changes no outcome — that bump never guarded a
     * row memo before either. One decision window IS tighter than the
     * retired counter: the seed verb also advances the mirror (the counter
     * moved only on invalidate), so a row writer that captured between the
     * roll's invalidate and its seed is now rejected instead of pinning a
     * pre-roll payload — strictly safer, never looser.
     */
    @Volatile
    private var observedGeneration: Long = 0L

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
     * Plugin rows (PLUGIN_ROW — the seasonal row today). Identity-scoped like
     * every entry (a user/server switch misses by construction) with the
     * sibling sub-call TTL ([HomeFreshness.NETWORK_SUBCALL_TTL_MS]): the row
     * carries resolved MediaItems with per-item UserData, so it rides the
     * same 2-minute memo the latest/similar rows ride and the same
     * user-data-write invalidation ([invalidateCaches]). Session-scoped by
     * construction — the fetcher lives as long as the client single. The
     * cached VALUE is the mapped row (title + resolved entries), so a cache
     * hit costs zero port calls and the plugin's row title survives the
     * memo.
     */
    private val homePluginRowCache = TtlCache<PluginRowValue>(ttlMs = HomeFreshness.NETWORK_SUBCALL_TTL_MS)

    /**
     * Drops both sub-call caches so the next home fetch re-hits the server for the
     * latest/similar rows. The rows carry per-item UserData (played badge,
     * favorite heart, resume bar), so a watched/favorite/progress write must
     * not let this TTL layer serve the pre-write rows — reached from the data
     * layer through [HomeSectionsCachePort.invalidateSubcallCaches].
     */
    fun invalidateCaches() {
        homeLatestMediaCache.clear()
        homeSimilarCache.clear()
        homeDiscoverRowCache.clear()
        // Plugin rows carry resolved MediaItems with per-item UserData, so a
        // watched/favorite write must not let this memo serve the pre-write
        // row either.
        homePluginRowCache.clear()
    }

    /**
     * Drops ONE discover row's memoised items (dice affordance): the next home
     * fetch re-queries that row — re-rolling a RANDOM sort — while sibling
     * rows keep their cached items. Identity-scoped like every entry, so the
     * evict can never touch another user's row. Ordering and the generation
     * value are roll-protocol concerns owned by the data layer's funnel —
     * [generation] IS the repo's post-bump cache-write token, mirrored BEFORE
     * the drop (the same bump-then-drop order this guard always observed);
     * see `HomeFeed.rerollDiscoverRow`.
     */
    fun invalidateDiscoverRow(rowId: String, generation: Long) {
        observedGeneration = generation
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
     * No-op on an empty list (which also skips the mirror write — an empty
     * commit advances no generation, matching the repo funnel's early
     * return); the commit-time generation arrives as the [generation]
     * parameter — the repo funnel's post-bump token, a roll-protocol rule
     * owned by the data layer (see `HomeFeed.rerollDiscoverRow`).
     */
    fun seedDiscoverRow(row: DiscoverRowConfig, items: List<MediaItem>, generation: Long) {
        if (items.isEmpty()) return
        observedGeneration = generation
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
            if (HomeSectionType.CONTINUE_WATCHING in enabledSections) sources.getContinueWatching(
                limit = 20,
                classicRows = query.classicRows,
            )
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

        // Plugin rows (the seasonal row today): fetched ALWAYS regardless of
        // enabledSections (PLUGIN_ROW is not user-configurable — the
        // capability registry is its gate), concurrently with everything
        // else. A null transport or a gate reading false resolves locally
        // with no port calls — the row is silently absent, never an error.
        val pluginRowsDeferred = async {
            fetchSeasonalPluginRow(force = force, identity = identity) +
                fetchTitledPluginRows(force = force, identity = identity)
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
                async {
                    recommendations(
                        limit = 20,
                        seeds = recommendationSeeds,
                        force = force,
                        identity = identity,
                        classicRows = query.classicRows,
                    )
                }
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
                    folder to latestForLibrary(
                        libraryId = folder.id,
                        collectionType = folder.collectionType,
                        classicRows = query.classicRows,
                        force = force,
                        identity = identity,
                    )
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
                pluginRowSections = pluginRowsDeferred.await(),
            ),
        )
        if (output.result.sections.isEmpty() && output.firstError != null) {
            throw output.firstError!!
        }
        output.result
    }

    /**
     * The single-row refetch behind the home screen's edge-pull refresh:
     * re-runs EXACTLY the sub-call(s) [fetch]'s schedule runs for [section]'s
     * row and reshapes the result with the same identity (id, title, type,
     * libraryId, collectionType, seedItem — only `items` moves).
     *
     * Outcomes:
     *  - `Result.success(section)`: fresh items; the caller swaps the row in
     *    place (identity is preserved, so a keyed lazy list animates the
     *    change, not a removal).
     *  - `Result.success(null)`: the row's source legitimately returned no
     *    items — the row should disappear, matching the assembler's
     *    zero-items-is-not-rendered policy.
     *  - `Result.failure`: the sub-call failed; the caller keeps the stale
     *    row on screen. Unlike [fetch], no partial-result semantics exist —
     *    a single row has nothing partial to publish.
     *
     * Caching: [force] (always true from the refresh paths) bypasses the
     * sub-call memo READS but keeps the WRITES — the fresh rows are what the
     * next periodic fetch serves, instead of the pre-pull rows reverting for
     * the TTL (same policy as [fetch]'s forced path). This method NEVER
     * touches the whole-plan assembled-payload cache or the SWR snapshot
     * persist — those live a layer up (MediaRepository), keyed by the whole
     * query, and a single-row result must never masquerade as one.
     *
     * Refreshable types and their mapping:
     *  - CONTINUE_WATCHING / CONTINUE_READING / NEXT_UP: the direct port call
     *    with the [query]'s parameters, then the assembler's filters verbatim
     *    (hidden-CW set; Next Up's CW-overlap + excluded-series drops — the
     *    CW ids come from one extra Continue Watching read, mirroring the
     *    assembler's seed reuse). CONTINUE_WATCHING additionally honours
     *    [mergeNextUpIntoContinueWatching]: when the user's layout folds Next
     *    Up into this row (OrderHomeSectionsUseCase, batch time), the refetch
     *    rebuilds that fold from BOTH fresh sources — a CW-only refetch would
     *    swap away the row's Next Up half (and the CW-empty + Next Up-present
     *    relabel arm would drop a non-empty row).
     *  - LATEST_MEDIA: one `/Items/Latest` for the row's library (resolved
     *    from the `latest_<libraryId>` id), through the same TTL memo.
     *  - RECENTLY_ADDED: the whole latest fan-out (folders → per-library
     *    latest, music filtered, semaphore-bounded) re-aggregated with the
     *    assembler's per-folder override + CW-overlap filters — the row is an
     *    aggregate, so its refetch costs the aggregate.
     *  - DISCOVER (JELLYFIN rows only): the row's memoised query, behind the
     *    same dice-roll generation guard ([observedGeneration]).
     *  - PINNED: the pin's item resolution, same routing table as the batch.
     *  - PLUGIN_ROW: the same plugin leaf read the batch runs (seasonal under
     *    the `seasonal` instance id, a titled row under `custom_<title>`),
     *    rebuilt through the shared section builder so only the payload
     *    moves.
     *
     * RECOMMENDATIONS and Seerr-sourced DISCOVER rows are deliberately NOT
     * refreshable here (the recommendations seed chain and the Seerr group
     * gate/last-known-good policy are batch-shaped); the feature layer keeps
     * the edge-pull gesture off those rows, and an unguarded call fails with
     * [IllegalStateException] rather than guessing.
     */
    suspend fun refreshSection(
        section: HomeSection,
        query: HomeSectionQuery,
        mergeNextUpIntoContinueWatching: Boolean = false,
        force: Boolean = true,
    ): Result<HomeSection?> = runCatchingRethrowingCancellation {
        val identity = cacheIdentity() ?: CacheIdentity.UNKNOWN
        when (section.type) {
            HomeSectionType.CONTINUE_WATCHING -> {
                val cw = sources.getContinueWatching(limit = 20, classicRows = query.classicRows)
                    .getOrThrow()
                    .excludingHiddenItemIds(query.hiddenCwItemIds)
                if (!mergeNextUpIntoContinueWatching) {
                    cw.takeIf { it.isNotEmpty() }
                        ?.let { HomeSectionType.CONTINUE_WATCHING.descriptor.section(it) }
                } else {
                    // Merged row: the batch assembler folded Next Up into this
                    // row (OrderHomeSectionsUseCase), so the refetch rebuilds
                    // that fold from BOTH fresh sources — fresh CW first,
                    // fresh Next Up (same eligibility filters as the NEXT_UP
                    // arm, deduped by id) appended. A Next Up failure degrades
                    // to the CW half (the batch's per-source failure policy);
                    // CW empty + Next Up present is the merge's relabel arm —
                    // the row survives carrying Next Up; both empty drops it.
                    val nextUp = sources.getNextUp(
                        limit = 20,
                        enableRewatching = query.nextUpRewatching,
                        maxDays = query.nextUpMaxDays,
                    )
                        .getOrDefault(emptyList())
                        .filterNextUpEligible(cw.map { it.id }.toSet(), query.nextUpExcludedSeriesIds)
                    val merged = (cw + nextUp).distinctBy { it.id }
                    merged.takeIf { it.isNotEmpty() }
                        ?.let { HomeSectionType.CONTINUE_WATCHING.descriptor.section(it) }
                }
            }

            HomeSectionType.CONTINUE_READING ->
                sources.getContinueReading(limit = 20)
                    .getOrThrow()
                    .excludingHiddenItemIds(query.hiddenCwItemIds)
                    .takeIf { it.isNotEmpty() }
                    ?.let { HomeSectionType.CONTINUE_READING.descriptor.section(it) }

            HomeSectionType.NEXT_UP -> {
                val cwIds = continueWatchingIdsForFilters(query)
                sources.getNextUp(
                    limit = 20,
                    enableRewatching = query.nextUpRewatching,
                    maxDays = query.nextUpMaxDays,
                )
                    .getOrThrow()
                    .filterNextUpEligible(cwIds, query.nextUpExcludedSeriesIds)
                    .takeIf { it.isNotEmpty() }
                    ?.let { HomeSectionType.NEXT_UP.descriptor.section(it) }
            }

            HomeSectionType.LATEST_MEDIA -> {
                val libraryId = HomeSectionType.LATEST_MEDIA.descriptor.instanceIdFor(section.id)
                    ?: error("Latest Media row ${section.id} carries no library id")
                val latest = latestForLibrary(
                    libraryId = libraryId,
                    collectionType = section.collectionType,
                    classicRows = query.classicRows,
                    force = force,
                    identity = identity,
                ).getOrThrow()
                refreshedOrNull(section, latest)
            }

            HomeSectionType.RECENTLY_ADDED -> {
                val folders = sources.getLibraryFolders().getOrThrow()
                    .filter { it.collectionType != "music" }
                val allLatest = Semaphore(4).mapConcurrent(folders) { folder ->
                    // The assembler feeds the aggregate only from libraries the
                    // user hasn't disabled Recently Added for.
                    if (HomeSectionType.RECENTLY_ADDED in query.libraryHomeSectionOverrides[folder.id].orEmpty()) {
                        emptyList()
                    } else {
                        latestForLibrary(
                            libraryId = folder.id,
                            collectionType = folder.collectionType,
                            classicRows = query.classicRows,
                            force = force,
                            identity = identity,
                        ).getOrDefault(emptyList())
                    }
                }.flatten()
                val cwIds = continueWatchingIdsForFilters(query)
                refreshedOrNull(section, allLatest.distinctByIdExcluding(cwIds))
            }

            HomeSectionType.DISCOVER -> {
                val row = query.discoverRows.firstOrNull {
                    it.enabled && HomeSectionType.DISCOVER.descriptor.idFor(it.id) == section.id
                } ?: error("No enabled discover row for section ${section.id}")
                check(row.source == DiscoverRowSource.JELLYFIN) {
                    "Seerr discover rows are not edge-refreshable (${section.id})"
                }
                homeDiscoverRowCache.cacheThrough(
                    identity,
                    discoverRowCacheKey(row.id, row.limit),
                    force = force,
                    currentEpoch = { observedGeneration },
                ) {
                    sources.getDiscoverRowItems(row)
                }
                    .getOrThrow()
                    .let { refreshedOrNull(section, it) }
            }

            HomeSectionType.PINNED -> {
                val pin = query.pinnedSections.firstOrNull {
                    HomeSectionType.PINNED.descriptor.idFor(it.id) == section.id
                } ?: error("No pinned section configured for ${section.id}")
                refreshedOrNull(section, getPinnedSectionItems(pin))
            }

            HomeSectionType.PLUGIN_ROW -> {
                // The plugin row's single-row refetch (edge pull): the same
                // leaf read the batch path runs, forced (the gesture means
                // "fetch fresh") and rebuilt through the SAME builder, so the
                // row id stays stable and only the payload moves. An emptied
                // source (plugin row removed mid-session) returns null — the
                // row drops, matching the batch's zero-items policy.
                val instanceId = HomeSectionType.PLUGIN_ROW.descriptor.instanceIdFor(section.id)
                    ?: error("Plugin row ${section.id} carries no instance id")
                val rowIdentity = identity
                if (instanceId.startsWith("custom_")) {
                    fetchPluginCustomRow(
                        title = instanceId.removePrefix("custom_"),
                        force = force,
                        identity = rowIdentity,
                    )
                } else {
                    if (jellyPlaySources == null || !jellyPlaySources.seasonalRowsEnabled()) null
                    else pluginRowCacheThrough(
                        cacheKey = "jellyplay_seasonal",
                        force = force,
                        identity = rowIdentity,
                    ) { jellyPlaySources.getSeasonalRow(keyword = null) }
                }
            }

            // Never constructed by the network (FAVORITES, LIVE_TV, DOWNLOADED)
            // or deliberately unrefreshable (RECOMMENDATIONS — the seed chain
            // is batch-shaped). The gesture is gated off these; reaching here
            // is a caller bug.
            else -> error("Home section type ${section.type} is not refreshable")
        }
    }

    /**
     * The Continue Watching id set the Next Up / Recently Added filters key
     * on — the assembler derives it from the HIDDEN-FILTERED CW list (an
     * item hidden from Continue Watching stays eligible for Next Up /
     * Recently Added, its series' next episode being the intended resume
     * path), so a single-row refetch derives it identically; a single-row
     * refetch reads it fresh instead (one extra port call, only when the
     * user's layout actually enables Continue Watching).
     */
    private suspend fun continueWatchingIdsForFilters(query: HomeSectionQuery): Set<String> =
        if (HomeSectionType.CONTINUE_WATCHING in query.enabledSections) {
            continueWatchingFilterIds(
                continueWatchingItems = sources.getContinueWatching(limit = 20, classicRows = query.classicRows)
                    .getOrDefault(emptyList()),
                hiddenItemIds = query.hiddenCwItemIds,
            )
        } else {
            emptySet()
        }

    /**
     * The one item-swap shape for instance-typed rows (LATEST_MEDIA /
     * RECENTLY_ADDED / DISCOVER / PINNED): fresh items, every identity field
     * preserved; an empty result drops the row (the assembler's
     * zero-items-is-not-rendered policy).
     */
    private fun refreshedOrNull(section: HomeSection, items: List<MediaItem>): HomeSection? =
        if (items.isEmpty()) null else section.copy(items = items)

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
     *    dice-roll generation guard ([observedGeneration] — the repo-owned
     *    token mirrored in), degraded per row (a failing row is dropped,
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
                currentEpoch = { observedGeneration },
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
        val items = response.take(row.limit)
        if (items.isEmpty()) return null
        return HomeSection(
            id = HomeSectionType.DISCOVER.descriptor.idFor(row.id),
            title = row.title,
            type = HomeSectionType.DISCOVER,
            items = emptyList(),
            seerrItems = items,
        )
    }

    // ── Plugin rows (PLUGIN_ROW — the companion server plugin, ADR 0010) ────
    //
    // The WHAT gate is the capability registry (the port's suspend gates —
    // UNKNOWN probes once per session, UNAVAILABLE is trusted), the TTL memo
    // is [homePluginRowCache], and failures degrade to a dropped row (the
    // pinned-section policy: a curated plugin row must never fail home, and a
    // plugin 404 legitimately means "no row this season"). The cached value
    // is the MAPPED row (title + resolved entries), so a hit costs zero port
    // calls.

    /**
     * The seasonal row for the batch home fetch: gate → TTL memo →
     * [pluginRowCacheThrough] under the `seasonal` instance id. Empty list =
     * gated off / unconfigured / empty payload / failed — silently absent,
     * never an error (the assembler emits nothing for it).
     */
    private suspend fun fetchSeasonalPluginRow(force: Boolean, identity: CacheIdentity): List<HomeSection> {
        if (jellyPlaySources == null || !jellyPlaySources.seasonalRowsEnabled()) return emptyList()
        val section = pluginRowCacheThrough(
            cacheKey = "jellyplay_seasonal",
            force = force,
            identity = identity,
        ) { jellyPlaySources.getSeasonalRow(keyword = null) }
        return listOfNotNull(section)
    }

    /**
     * The admin-defined titled custom rows, in the plugin's configured order:
     * the enumerate endpoint (`GET jellyplay/rows`) lists the titles, each is
     * mapped through [fetchPluginCustomRow] (same gate, same TTL memo, same
     * batched id resolution). A catalog failure or empty catalog = no rows,
     * silently absent. Public for the leaf-test suite.
     */
    public suspend fun fetchTitledPluginRows(
        force: Boolean = false,
        identity: CacheIdentity? = null,
    ): List<HomeSection> {
        if (jellyPlaySources == null || !jellyPlaySources.customRowsEnabled()) return emptyList()
        val resolvedIdentity = identity ?: cacheIdentity() ?: CacheIdentity.UNKNOWN
        val catalog = runCatchingRethrowingCancellation {
            jellyPlaySources.getCustomRowCatalog().getOrNull()
        }.getOrNull() ?: return emptyList()
        return catalog.rows.mapNotNull { definition ->
            fetchPluginCustomRow(title = definition.title, force = force, identity = resolvedIdentity)
        }
    }

    /**
     * ONE admin-defined titled custom row, keyed by its exact title — mapped
     * through the enumerate catalog by [fetchTitledPluginRows] for the batch
     * home path; also public for direct single-row consumers (deep links).
     */
    public suspend fun fetchPluginCustomRow(
        title: String,
        force: Boolean = false,
        identity: CacheIdentity? = null,
    ): HomeSection? {
        if (jellyPlaySources == null || !jellyPlaySources.customRowsEnabled()) return null
        return pluginRowCacheThrough(
            cacheKey = "jellyplay_custom_$title",
            force = force,
            identity = identity ?: cacheIdentity() ?: CacheIdentity.UNKNOWN,
        ) { jellyPlaySources.getCustomRow(title = title) }
    }

    /**
     * The one cache-through + section-builder shape both plugin row reads
     * share: a miss fetches the raw payload, resolves every `localItemId`
     * through one batched [JellyPlayHomeSectionSources.getItemsByIds] call,
     * and maps to the PLUGIN_ROW [HomeSection] under [cacheKey]'s instance
     * id. A null payload (plugin 404 — no such row), an empty item list, or a
     * failed read yields null (no section); a resolution failure degrades the
     * affected entries to fallback tiles instead of dropping the row.
     */
    private suspend fun pluginRowCacheThrough(
        cacheKey: String,
        force: Boolean,
        identity: CacheIdentity,
        fetch: suspend () -> Result<JellyPlayRowResult?>,
    ): HomeSection? {
        val value = homePluginRowCache.cacheThrough(identity, cacheKey, force = force) {
            // A failed plugin read is a dropped row, not a fetch failure —
            // normalize to an empty success so the memo policy (cache-through,
            // drop-empty) stays the pinned-section one.
            val payload = runCatchingRethrowingCancellation { fetch().getOrNull() }
            Result.success(payload.getOrNull()?.let { row ->
                PluginRowValue(
                    title = row.title,
                    entries = resolvePluginRowEntries(row.items),
                )
            } ?: PluginRowValue.EMPTY)
        }.getOrDefault(PluginRowValue.EMPTY)
        if (value.entries.isEmpty()) return null
        val instanceId = cacheKey.removePrefix("jellyplay_")
        return HomeSection(
            id = HomeSectionType.PLUGIN_ROW.descriptor.idFor(instanceId),
            // The plugin's row title is authoritative (it names the curated
            // list, e.g. a Letterboxd collection); the descriptor's
            // displayName is only the degenerate fallback.
            title = value.title.ifBlank { HomeSectionType.PLUGIN_ROW.descriptor.displayName },
            type = HomeSectionType.PLUGIN_ROW,
            items = emptyList(),
            jellyPlayRowEntries = value.entries,
        )
    }

    /**
     * Maps the raw wire items to [JellyPlayRowEntry]s in payload order,
     * resolving the entries that carry a `localItemId` through ONE batched
     * ids read. Per-entry resolution failure (or an id the server no longer
     * knows) degrades that entry to a fallback tile — the row's shape is the
     * plugin's curation order, preserved end-to-end.
     */
    private suspend fun resolvePluginRowEntries(items: List<JellyPlayRowItem>): List<JellyPlayRowEntry> {
        if (items.isEmpty()) return emptyList()
        val localIds = items.mapNotNull { it.localItemId }.distinct()
        val resolved: Map<String, MediaItem> =
            if (localIds.isEmpty()) {
                emptyMap()
            } else {
                // Degrade, not fail: a lost ids read must not drop the row —
                // every entry falls back to its title+year tile (the same
                // policy as the classic-latest ids read in the client impl).
                jellyPlaySources?.getItemsByIds(localIds)
                    ?.getOrDefault(emptyList())
                    .orEmpty()
                    .associateBy { it.id }
            }
        return items.map { item ->
            JellyPlayRowEntry(
                title = item.title,
                year = item.year,
                localItem = item.localItemId?.let(resolved::get),
            )
        }
    }

    /**
     * Home-path wrapper around [HomeSectionSources.getLatestMedia] that
     * consults [homeLatestMediaCache] first. Only the home path uses this —
     * browse/library screens still go straight to the port for fresh data.
     * [classicEpisodePool] rides the cache key: a classic-rows flip must not
     * serve the other mode's rows for the sub-call TTL window.
     */
    private suspend fun getLatestMediaForHome(
        parentId: String,
        limit: Int,
        classicEpisodePool: Int?,
        force: Boolean,
        identity: CacheIdentity,
    ): Result<List<MediaItem>> =
        homeLatestMediaCache.cacheThrough(identity, "${parentId}_${limit}_pool${classicEpisodePool ?: 0}", force = force) {
            sources.getLatestMedia(parentId, limit, classicEpisodePool)
        }

    /**
     * The one per-library Latest Media shape — the batch fan-out and
     * [refreshSection]'s two latest arms (a row's own library, the Recently
     * Added aggregate) share it: the row limit and the classic-rows episode
     * pool derived from the folder's collection type, in one place so the
     * call sites cannot drift.
     */
    private suspend fun latestForLibrary(
        libraryId: String,
        collectionType: String?,
        classicRows: Boolean,
        force: Boolean,
        identity: CacheIdentity,
    ): Result<List<MediaItem>> = getLatestMediaForHome(
        parentId = libraryId,
        limit = latestRowLimit,
        // Classic rows (#168): TV folders fetch a raw-Episode pool for the
        // client-side pre-12 grouping; modern (default) = unconstrained
        // server behavior.
        classicEpisodePool = if (classicRows) classicLatestEpisodePool(collectionType, limit = latestRowLimit) else null,
        force = force,
        identity = identity,
    )

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
        classicRows: Boolean,
    ): Result<RecommendationResult> = runCatchingRethrowingCancellation {
        // Reuse caller-supplied seeds when available (e.g. the home screen has
        // already fetched Continue Watching + Next Up) to avoid duplicate
        // /Items/Resume and /Shows/NextUp round-trips within the same load.
        val seedItems = if (seeds.isNotEmpty()) {
            seeds.distinctBy { it.id }.take(5)
        } else {
            val continueWatching = sources.getContinueWatching(limit = 5, classicRows = classicRows).getOrDefault(emptyList())
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
 * One mapped plugin row as the [homePluginRowCache] memo value: the plugin's
 * row title (authoritative header; [JellyPlayRowResult.title]) plus the
 * local-library-resolved entries. [EMPTY] is the "no row" memo (a null
 * payload or an all-dropped item list) — the section builder drops it.
 */
private data class PluginRowValue(
    val title: String,
    val entries: List<JellyPlayRowEntry>,
) {
    companion object {
        val EMPTY = PluginRowValue(title = "", entries = emptyList())
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
