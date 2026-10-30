package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.feature.home.testutil.FakeTimeSource
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.BookTocCache
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.HomeFeed
import com.raulshma.jellyplay.core.data.repository.UserDataChanges
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionPrefs
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.cacheThrough
import com.raulshma.jellyplay.core.model.descriptor
import com.raulshma.jellyplay.core.network.library.HomeSectionsFetcher
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.testfixtures.FakeHomeSectionSources
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * THE DICE-ROLL CROSS-MODULE CONTRACT TEST — the one test that drives a roll
 * through MULTIPLE layers of the protocol instead of one layer against mocks
 * of its neighbours. The protocol's single owner is the KDoc on
 * `HomeFeed.rerollDiscoverRow` (three race windows, one per layer,
 * bump-at-invalidate-AND-commit); before this suite each layer was pinned in
 * isolation ([DiscoverRowsCoordinatorTest] + [HomeRefresherTest] here,
 * `MediaRepositoryHomeSectionsCacheTest` in core:data, `HomeSectionsFetcherTest`
 * in core:network) and NO test spanned the composition.
 *
 * STACK UNDER TEST — real feature layers, the REAL network half, and a
 * protocol-faithful repo double:
 *  * [HomeRefresher] (real) — the WHEN layer: fetchOnce's ordering, the
 *    book-fraction decode as the last suspension, the single sections write,
 *    the drain call site.
 *  * [DiscoverRowsCoordinator] (real, constructed inside the refresher) —
 *    race window 1: the roll registry and `drainRolls`.
 *  * [HomeSectionsFetcher] (real, over the shared :shared:core:test-fixtures
 *    [FakeHomeSectionSources] transport double) — windows 2+3's NETWORK half:
 *    the per-row memo (10 min, [HomeFreshness.DISCOVER_ROW_TTL_MS], key
 *    `discover_<rowId>_<limit>`) behind the fetcher's own generation, whose
 *    [HomeSectionsFetcher.rerollDiscoverRow] runs the invalidate → fetch →
 *    seed choreography as ONE operation. Production's own op — no longer
 *    re-stated here (the fetcher's constructor is public since the ADR-0008
 *    extraction; its construction-scope KDoc sanctions exactly this kind of
 *    test construction).
 *  * [RollProtocolRepo] (test double) — the REPO half, implemented per the
 *    protocol KDoc over the REAL shared cache engine (core:model [TtlCache] +
 *    [cacheThrough] with the epoch write guard — the same engine the real
 *    `MediaRepositoryImpl.homeSectionsCache` rides): an assembled-payload
 *    cache bumped once AFTER the operation returns (production's funnel).
 *
 * HONEST GAP (why not the real `MediaRepositoryImpl` too): its primary
 * constructor is `internal` to core:data, so no module outside core:data can
 * construct it — the real repo+fetcher pair IS pinned, on the real stack, by
 * `HomeFeedRealStackContractTest` in core:data's jvmTest. The double
 * re-states the REPO half's protocol behavior (the post-operation bump that
 * repo's own suites pin) so the COMPOSITION is what fails if any layer
 * drifts from the contract; the per-layer suites above remain the owners of
 * each layer's implementation details (the operation hand-off is pinned by
 * `MediaRepositoryHomeSectionsCacheTest` + `HomeFeedRealStackContractTest`
 * and the fetcher's own suite).
 *
 * PINS (one user action — the dice re-roll — through refresh cycles on
 * virtual time):
 *  1. refresh → roll → refresh: the rolled row's new content SURVIVES the
 *     next refresh — the seeded row memo serves it (no re-roll) and neither
 *     cache replays the pre-roll payload (no transient revert).
 *  2. a fetch IN FLIGHT across a roll (the guard-bab9b6931 bug class): the
 *     raced fetch's pre-roll transport response must not be pinned into
 *     either cache (both epoch guards refuse the write) and must not revert
 *     the on-screen roll (the registry drain re-applies it); the NEXT
 *     refresh then serves the roll, never the raced stale rows.
 *  3. drainRolls ordering: a roll landing while the fetch is parked on the
 *     book-fraction decode (the fetch's LAST suspension before the drain)
 *     registers strictly before the drain point, so the fetch's single
 *     sections write re-applies it — and the double's cache agrees with the
 *     painted roll afterwards (the composition, not just the feature half).
 *
 * Harness mirrors [HomeRefresherTest]: MockK collaborators, inlined
 * StandardTestDispatcher, a scope over the test scheduler, runCurrent (never
 * advanceUntilIdle — the periodic loop is an infinite delay chain), stop +
 * scope cancel in @After.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverRollContractTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var homeFeed: HomeFeed
    private lateinit var userDataChanges: UserDataChanges
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var fakeTimeSource: FakeTimeSource

    private var refresher: HomeRefresher? = null
    private var refresherScope: CoroutineScope? = null

    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 64)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Online)
    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)

    /**
     * While set, the TOC fake's getToc parks — parks the fetch's
     * book-fraction decode, the fetch's LAST suspension before the
     * roll-registry drain (same idiom as [HomeRefresherTest]).
     */
    private var tocGate: CompletableDeferred<Unit>? = null

    /** The shared transport double under the REAL fetcher — see the class KDoc. */
    private lateinit var sources: FakeHomeSectionSources

    /** The REAL network half of the roll protocol — see the class KDoc. */
    private lateinit var fetcher: HomeSectionsFetcher

    /** The repo-half double — see the class KDoc. */
    private lateinit var proto: RollProtocolRepo

    /** The one discover row every test here rolls. */
    private val row = DiscoverRowConfig(id = "dr_contract", title = "Contract Row")

    private val discoverSectionId: String get() = HomeSectionType.DISCOVER.descriptor.idFor(row.id)

    /** The discover sub-call's leaf key: the fake names every call `discover:<rowId>:<limit>`. */
    private val discoverCallKey: String get() = "discover:${row.id}:${row.limit}"

    /** True server hits against the discover row (a memo hit makes no raw call). */
    private fun rawRowFetches(): Int = sources.calls.count { it.startsWith("discover:") }

    private val bookTocCacheRepository = object : BookTocCacheRepository {
        override fun observeToc(itemId: String): Flow<BookTocCache?> = flowOf(null)
        override suspend fun getToc(itemId: String): BookTocCache? {
            tocGate?.await()
            return null
        }
        override suspend fun putToc(itemId: String, format: BookFormat, pageCount: Int, entries: List<BookTocEntry>) = Unit
        override suspend fun deleteToc(itemId: String) = Unit
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        offlineModeManager = mockk(relaxed = true)
        fakeTimeSource = FakeTimeSource()

        every { offlineModeManager.networkStatus } returns networkStatusFlow
        every { offlineModeManager.offlineMode } returns offlineModeFlow
        every { offlineModeManager.isOffline } returns false

        // The REAL network half over the shared transport double (no Seerr
        // transport, no session — CacheIdentity.UNKNOWN, the pre-login state
        // the repo double keys its own cache under too).
        sources = FakeHomeSectionSources()
        fetcher = HomeSectionsFetcher(
            sources = sources,
            seerrSources = null,
            cacheIdentity = { CacheIdentity.UNKNOWN },
            today = { "2026-01-01" },
        )
        proto = RollProtocolRepo(fetcher)

        // The HomeFeed seam: a relaxed mock with the roll-protocol members
        // delegated to the double (its repo-half cache and epoch behavior is
        // the code under test, not a stub echo); the change feed is its own seam.
        homeFeed = mockk(relaxed = true)
        userDataChanges = mockk(relaxed = true)
        every { userDataChanges.userDataChanges } returns userDataEvents
        coEvery { homeFeed.getCachedHomeSections(any()) } returns null
        coEvery { homeFeed.getHomeSections(any(), any<Boolean>()) } coAnswers {
            proto.getHomeSections(arg(0), arg(1))
        }
        coEvery { homeFeed.rerollDiscoverRow(any()) } coAnswers {
            proto.rerollDiscoverRow(arg(0))
        }
    }

    @AfterTest
    fun stopRefresher() {
        refresher?.stop()
        refresherScope?.cancel()

        Dispatchers.resetMain()
    }

    /**
     * Scripts the CONTINUE_READING row for the next [times] home fetches (one
     * "cr" leaf call each): the fetch's book-fraction decode — its last
     * suspension, pins 2/3's drain anchor — needs a CR row carrying an
     * in-progress book to park on.
     */
    private fun scriptContinueReading(times: Int) {
        repeat(times) {
            sources.continueReadingResults += Result.success(
                listOf(item("book1").copy(mediaType = MediaType.BOOK, playbackPositionTicks = 50_000L)),
            )
        }
    }

    /**
     * One raw discover-row response, served in ISSUE order: the fake binds
     * each scripted result to the next raw call, so every true server hit
     * serves the next shuffle (a RANDOM-sorted row re-rolls on every hit —
     * which is what makes a memo HIT distinguishable from a re-fetch).
     */
    private fun shuffle(vararg ids: String): Result<List<MediaItem>> = Result.success(ids.map { item(it) })

    private fun TestScope.buildRefresher(): HomeRefresher {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        refresherScope = scope
        // One state store shared by the refresher and its side-fetch
        // collaborator (the factory seam, mirrored here).
        val state = MutableStateFlow(HomeRefreshState())
        return HomeRefresher(
            scope = scope,
            clock = fakeTimeSource,
            homeFeed = homeFeed,
            userDataChanges = userDataChanges,
            orderHomeSections = OrderHomeSectionsUseCase(),
            widgetDataStore = mockk(relaxed = true),
            continueWatchingBroadcaster = mockk(relaxed = true),
            tvWatchNextScheduler = mockk(relaxed = true),
            librarySyncHook = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
            stateStore = state,
            discoverSources = HomeDiscoverSources(
                clock = fakeTimeSource,
                seerrRepository = mockk(relaxed = true),
                arrRepository = mockk(relaxed = true),
                offlineModeManager = offlineModeManager,
                state = state,
            ),
            awaitOutboxDrained = { true },
            fetchInputs = HomeFetchInputs(
                sectionPrefs = {
                    HomeSectionPrefs(
                        query = HomeSectionQuery(discoverRows = listOf(row)),
                        homeSectionOrder = HomeSectionType.CONFIGURABLE,
                        mergeContinueWatchingAndNextUp = false,
                    )
                },
                seerrPreferences = { SeerrPreferences() },
                discoverEnabled = { false },
                directArrEnabled = { false },
                androidTvWatchNextEnabled = { true },
            ),
            bookTocCacheRepository = bookTocCacheRepository,
        ).also { refresher = it }
    }

    private fun rolledItems(): List<MediaItem> =
        refresher!!.state.value.sections.first { it.id == discoverSectionId }.items

    private fun itemIds(): List<String> = rolledItems().map { it.id }

    // ── Pin 1: the roll survives the next refresh ───────────────────────────

    @Test
    fun `refresh roll refresh - the rolled row survives the next refresh without reverting or re-rolling`() = runTest {
        val refresher = buildRefresher()
        scriptContinueReading(times = 2) // initial refresh + the post-roll refresh
        sources.discoverRowResults += shuffle("m1", "m2", "m3") // 1st raw hit (initial refresh)
        sources.discoverRowResults += shuffle("r1", "r2")       // 2nd raw hit (the roll)

        // Initial refresh: the transport's first raw hit (shuffle 1) is
        // memoised into the fetcher's row memo and the repo's payload cache.
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("m1", "m2", "m3"), itemIds())
        assertEquals(1, rawRowFetches())

        // ONE user action: the dice roll — the network half's complete
        // operation (row memo drop → fresh fetch (shuffle 2) → memo write,
        // behind its own two generation bumps) inside the REAL fetcher,
        // answered by the repo double's post-op bump + assembled payload drop.
        refresher.rollDiscoverRow(row)
        runCurrent()
        assertEquals(listOf("r1", "r2"), itemIds(), "the roll patches the row in place")
        assertEquals(2, rawRowFetches())

        // The next refresh: must serve the ROLLED set. Not the pre-roll
        // memo (transient revert), not a fresh server hit (silent re-roll —
        // the transport count staying put pins the memo HIT: the REAL
        // fetcher's row memo committed the roll).
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("r1", "r2"), itemIds(), "the rolled content survives the next refresh")
        assertEquals(2, rawRowFetches(), "the seeded memo serves the row — the RANDOM row must not re-roll on refresh")
        assertEquals(listOf("r1", "r2"), proto.cachedHomePayload()?.sections?.first { it.id == discoverSectionId }?.items?.map { it.id })

        // runTest's teardown advances the shared scheduler until idle — no
        // refresher background work may survive the body or the teardown
        // never converges (harness rule; see HomeRefresherTest's class KDoc).
        refresher?.stop()
        refresherScope?.cancel()
    }

    // ── Pin 2: a fetch in flight across the roll (guard-bab9b6931 class) ────

    @Test
    fun `a fetch in flight across a roll never resurrects stale rows`() = runTest {
        val refresher = buildRefresher()
        scriptContinueReading(times = 3) // initial refresh + the raced forced pass + the next refresh
        sources.discoverRowResults += shuffle("m1", "m2", "m3")   // 1st raw hit (initial refresh)
        sources.discoverRowResults += shuffle("stale1", "stale2") // 2nd raw hit — the RACED fetch's stale response
        sources.discoverRowResults += shuffle("r1", "r2")         // 3rd raw hit — the roll

        // Initial refresh caches shuffle 1 in both layers.
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("m1", "m2", "m3"), itemIds())

        // A full refresh goes on the wire and parks INSIDE the transport —
        // past both epoch captures (repo assembled-payload epoch, network
        // row-memo epoch), exactly the in-flight window the protocol's
        // post-operation bump + roll-op guards must stall-guard. The fake's
        // gate is one-shot per call (the outcome was bound at this call's
        // issue time), so the roll's own raw fetch — the NEXT call of the
        // same name — runs ungated.
        val racedGate = sources.gate(discoverCallKey)
        refresher.request(RefreshTrigger.PullToRefresh)
        runCurrent()
        assertTrue(refresher.state.value.isRefreshing, "the forced fetch is parked inside the transport")

        // The roll completes while that fetch is parked: the REAL fetcher's
        // two generation bumps (drop + seed) with the memo re-seeded to
        // shuffle 3, then the repo's post-op bump.
        refresher.rollDiscoverRow(row)
        runCurrent()
        assertEquals(listOf("r1", "r2"), itemIds(), "the roll patches the row while the fetch is parked")

        // Release the raced fetch: its transport response is the shuffle
        // bound at ITS issue (shuffle 2) — PRE-roll content for the user's
        // purposes.
        racedGate.complete(Unit)
        runCurrent()

        // Window 1 (feature registry): the raced fetch's single sections
        // write re-applies the registered roll instead of reverting it.
        assertEquals(listOf("r1", "r2"), itemIds(), "the raced fetch's write must not revert the on-screen roll")

        // Window 2 (repo epoch): the raced assembled payload (shuffle 2)
        // must NOT have been pinned into the repo cache.
        assertNull(proto.cachedHomePayload(), "the raced fetch's stale payload must not be cached")

        // Windows 2+3, positively: the NEXT refresh serves the roll — the
        // raced stale rows stay dead and the raced row response (shuffle 2)
        // did NOT overwrite the seeded memo (shuffle 3): zero new raw calls.
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("r1", "r2"), itemIds(), "the next refresh serves the rolled set, never the raced stale rows")
        assertEquals(3, rawRowFetches(), "only the parked transport call served its stale shuffle; the next refresh reads the seeded memo")

        // Same in-body teardown rule as Pin 1.
        refresher?.stop()
        refresherScope?.cancel()
    }

    // ── Pin 3: drainRolls ordering at the fetch's last suspension ───────────

    @Test
    fun `a roll landing during the book-fraction decode is reapplied by the drain and agrees with the caches`() = runTest {
        val refresher = buildRefresher()
        scriptContinueReading(times = 3) // initial refresh + the parked forced pass + the next refresh
        sources.discoverRowResults += shuffle("m1", "m2", "m3") // 1st raw hit (initial refresh)
        sources.discoverRowResults += shuffle("r1", "r2")       // 2nd raw hit (the forced fetch)
        sources.discoverRowResults += shuffle("s1", "s2")       // 3rd raw hit (the roll)

        // Initial refresh: shuffle 1 cached everywhere.
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("m1", "m2", "m3"), itemIds())

        // Park the NEXT refresh at the book-fraction decode — the fetch's
        // LAST suspension before the roll-registry drain. The forced fetch
        // has already resolved its payloads (shuffle 2 from the transport)
        // and written both caches when it parks here.
        tocGate = CompletableDeferred()
        refresher.request(RefreshTrigger.PullToRefresh)
        runCurrent()
        assertTrue(refresher.state.value.isRefreshing, "the fetch is parked inside the book-fraction decode")
        assertEquals(2, rawRowFetches())

        // The roll lands while the fetch is parked at the decode — strictly
        // BEFORE the drain point. It fetches shuffle 3 and seeds it (painted
        // here by the roll's own row patch; the memo's committed set is what
        // the composition-agreement refresh below reads back).
        refresher.rollDiscoverRow(row)
        runCurrent()
        assertEquals(listOf("s1", "s2"), itemIds(), "the roll patches the row while the fetch is parked mid-decode")

        // Release the decode: the fetch's single sections write carries the
        // payloads it captured (shuffle 2) — the drain must re-apply the
        // roll registered before it. If the decode suspension ever moved
        // after the drain, this write would land post-drain and revert the
        // roll to shuffle 2 here.
        tocGate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("s1", "s2"), itemIds(), "the fetch's write re-applies the roll registered before its drain point")

        // COMPOSITION agreement: the painted roll matches what the caches
        // hold — the next plain refresh serves the same set (the REAL
        // fetcher's row memo kept the roll) with no further transport call.
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("s1", "s2"), itemIds(), "the caches and the painted roll agree — the next refresh is stable")
        assertEquals(3, rawRowFetches())

        // Same in-body teardown rule as Pin 1.
        refresher?.stop()
        refresherScope?.cancel()
    }

    // ── The repo-half double ─────────────────────────────────────────────────

    /**
     * The REPO half of the roll protocol (window 2's stall guard) over the
     * REAL shared cache engine, shaped after the production layer it stands
     * in for (`MediaRepositoryImpl`, whose primary constructor stays
     * `internal` to core:data — the HONEST GAP in the class KDoc): a
     * single-entry assembled-payload [TtlCache] (60s,
     * [HomeFreshness.REPO_MEMORY_TTL_MS]) behind `repoEpoch` (the repo-local
     * cache-write token; the funnel-shaped bump+clear pair, run ONCE after a
     * roll operation returns — the row memo changed under the repo either
     * way). The network half it stands beside is not doubled: the REAL
     * [HomeSectionsFetcher] runs its own per-row memo, generation guard and
     * roll choreography over the shared [FakeHomeSectionSources].
     */
    private inner class RollProtocolRepo(private val fetcher: HomeSectionsFetcher) {
        private val identity = CacheIdentity.UNKNOWN

        /** Window 2's stall guard — the REPO half's assembled-payload cache epoch (the ONE repo-local cache-write token). */
        private val repoEpoch = AtomicLong(0L)

        private val homeSectionsCache = TtlCache<HomeSectionsResult>(
            maxSize = 1,
            ttlMs = HomeFreshness.REPO_MEMORY_TTL_MS,
            clock = { 0L }, // frozen: no TTL expiry — the ROLL path drives every cache miss here
        )

        private var lastQuery: HomeSectionQuery = HomeSectionQuery()

        /** The repo half's epoch-guarded assembled-payload read over the REAL fetch. */
        suspend fun getHomeSections(query: HomeSectionQuery, force: Boolean): Result<HomeSectionsResult> {
            lastQuery = query
            return homeSectionsCache.cacheThrough(
                identity,
                query.cacheKey(),
                force = force,
                currentEpoch = repoEpoch::get,
            ) {
                Result.success(fetcher.fetch(query, force))
            }
        }

        /**
         * The protocol's repo half — verbatim from
         * `MediaRepositoryImpl.rerollDiscoverRow`: the REAL network operation
         * runs to completion (the fetcher's drop → raw fetch → seed
         * choreography behind its own generation), then ONE bump, AFTER the
         * operation returns: the row memo changed under the repo either way
         * (dropped, and on a real roll re-seeded), so the assembled payload
         * must never survive to be replayed.
         */
        suspend fun rerollDiscoverRow(rowConfig: DiscoverRowConfig): Result<List<MediaItem>> =
            fetcher.rerollDiscoverRow(rowConfig).also {
                repoEpoch.incrementAndGet()
                homeSectionsCache.clear()
            }

        /** Test visibility: the repo's cached assembled payload (null = not cached / refused write). */
        fun cachedHomePayload(): HomeSectionsResult? = homeSectionsCache.get(identity, lastQuery.cacheKey())
    }

    private fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)
}
