package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.feature.home.testutil.FakeTimeSource
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.BookTocCache
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
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
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
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
 * `MediaRepository.rerollDiscoverRow` (three race windows, one per layer,
 * bump-at-invalidate-AND-commit); before this suite each layer was pinned in
 * isolation ([DiscoverRowsCoordinatorTest] + [HomeRefresherTest] here,
 * `MediaRepositoryHomeSectionsCacheTest` in core:data, `HomeSectionsFetcherTest`
 * in core:network) and NO test spanned the composition.
 *
 * STACK UNDER TEST — real feature layers over a protocol-faithful repo double:
 *  * [HomeRefresher] (real) — the WHEN layer: fetchOnce's ordering, the
 *    book-fraction decode as the last suspension, the single sections write,
 *    the drain call site.
 *  * [DiscoverRowsCoordinator] (real, constructed inside the refresher) —
 *    race window 1: the roll registry and `drainRolls`.
 *  * [RollProtocolRepo] (test double) — windows 2 and 3, implemented per the
 *    protocol KDoc over the REAL shared cache engine (core:model
 *    [TtlCache] + [cacheThrough] with epoch write guards — the same engine
 *    the real `MediaRepositoryImpl.homeSectionsCache` and
 *    `HomeSectionsFetcher.homeDiscoverRowCache` both ride): an
 *    assembled-payload cache behind the repo epoch, a per-row memo behind
 *    the network row epoch, and a transport whose every raw call serves the
 *    NEXT shuffle (a RANDOM-sorted row re-rolls on every true server hit).
 *
 * HONEST GAP (why not the real `MediaRepositoryImpl` + `HomeSectionsFetcher`):
 * neither can join a test in this module — `MediaRepositoryImpl`'s primary
 * constructor is `internal` to core:data and `HomeSectionsFetcher` is
 * `internal` to core:network, and no module sees all three layers (core:data
 * cannot see the feature coordinator; the feature cannot construct the repo).
 * The double re-states the two lower layers' PROTOCOL behavior (the ordering
 * and epoch rules their own suites pin) so the COMPOSITION is what fails if
 * any layer drifts from the contract; the per-layer suites above remain the
 * owners of each layer's implementation details.
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
 *     sections write re-applies it — and the double's caches agree with the
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

    private lateinit var mediaRepository: MediaRepository
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var fakeTimeSource: FakeTimeSource

    private var refresher: HomeRefresher? = null
    private var refresherScope: CoroutineScope? = null

    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 64)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Online)
    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)

    /**
     * While set, the NEXT raw transport call parks on it (one-shot — the
     * roll's own fresh fetch must not park behind the gate that holds the
     * raced fetch). This is how a test deterministically suspends a fetch
     * mid-transport, AFTER both epoch guards have captured their epochs.
     */
    private var transportGate: CompletableDeferred<Unit>? = null

    /**
     * While set, the TOC fake's getToc parks — parks the fetch's
     * book-fraction decode, the fetch's LAST suspension before the
     * roll-registry drain (same idiom as [HomeRefresherTest]).
     */
    private var tocGate: CompletableDeferred<Unit>? = null

    /** The protocol double — see the class KDoc. */
    private lateinit var proto: RollProtocolRepo

    /** The one discover row every test here rolls. */
    private val row = DiscoverRowConfig(id = "dr_contract", title = "Contract Row")

    private val discoverSectionId: String get() = HomeSectionType.DISCOVER.descriptor.idFor(row.id)

    /** The pre-decoded shuffles the transport serves, one per raw call. */
    private val shuffles = listOf(
        listOf(item("m1"), item("m2"), item("m3")), // 1st raw hit (initial refresh)
        listOf(item("r1"), item("r2")),             // 2nd raw hit (the roll)
        listOf(item("s1"), item("s2")),             // 3rd raw hit (raced/stale response)
    )

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

        proto = RollProtocolRepo()

        // The MediaRepository seam: a relaxed mock for the wide interface,
        // with the roll-protocol members delegated to the double (its cache
        // and epoch behavior is the code under test, not a stub echo).
        mediaRepository = mockk(relaxed = true)
        every { mediaRepository.userDataChanges } returns userDataEvents
        coEvery { mediaRepository.getCachedHomeSections(any()) } returns null
        coEvery { mediaRepository.getHomeSections(any(), any<Boolean>()) } coAnswers {
            proto.getHomeSections(arg(0), arg(1))
        }
        coEvery { mediaRepository.rerollDiscoverRow(any()) } coAnswers {
            proto.rerollDiscoverRow(arg(0))
        }
    }

    @AfterTest
    fun stopRefresher() {
        refresher?.stop()
        refresherScope?.cancel()

        Dispatchers.resetMain()
    }

    private fun TestScope.buildRefresher(): HomeRefresher {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        refresherScope = scope
        // One state store shared by the refresher and its side-fetch
        // collaborator (the factory seam, mirrored here).
        val state = MutableStateFlow(HomeRefreshState())
        return HomeRefresher(
            scope = scope,
            clock = fakeTimeSource,
            mediaRepository = mediaRepository,
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

        // Initial refresh: the transport's first raw hit (shuffle 1) is
        // memoised into both caches.
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("m1", "m2", "m3"), itemIds())
        assertEquals(1, proto.rawRowFetches)

        // ONE user action: the dice roll. Per the protocol this is
        // invalidate (repo epoch bump + row memo drop + assembled payload
        // drop) → fresh fetch (shuffle 2) → seed (memo write + commit bump).
        refresher.rollDiscoverRow(row)
        runCurrent()
        assertEquals(listOf("r1", "r2"), itemIds(), "the roll patches the row in place")
        assertEquals(2, proto.rawRowFetches)
        assertEquals(listOf("r1", "r2"), proto.rowMemoItems(row).map { it.id }, "the rolled set is committed into the network row memo")

        // The next refresh: must serve the ROLLED set. Not the pre-roll
        // memo (transient revert), not a fresh server hit (silent re-roll —
        // the transport count staying put pins the memo HIT).
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("r1", "r2"), itemIds(), "the rolled content survives the next refresh")
        assertEquals(2, proto.rawRowFetches, "the seeded memo serves the row — the RANDOM row must not re-roll on refresh")
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

        // Initial refresh caches shuffle 1 in both layers.
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("m1", "m2", "m3"), itemIds())

        // A full refresh goes on the wire and parks INSIDE the transport —
        // past both epoch captures (repo assembled-payload epoch, network
        // row-memo epoch), exactly the in-flight window the protocol's
        // bump-at-invalidate-AND-commit rule must stall-guard. The gate is
        // one-shot and transport() nulls the field on consumption, so the
        // parked fetch's own copy must be captured here.
        val racedGate = CompletableDeferred<Unit>()
        transportGate = racedGate
        refresher.request(RefreshTrigger.PullToRefresh)
        runCurrent()
        assertTrue(refresher.state.value.isRefreshing, "the forced fetch is parked inside the transport")

        // The roll completes while that fetch is parked: two epoch bumps per
        // the rule (invalidate + commit), row memo re-seeded with shuffle 2.
        refresher.rollDiscoverRow(row)
        runCurrent()
        assertEquals(listOf("r1", "r2"), itemIds(), "the roll patches the row while the fetch is parked")

        // Release the raced fetch: its transport response is the NEXT raw
        // shuffle (shuffle 3) — PRE-roll content for the user's purposes.
        racedGate.complete(Unit)
        runCurrent()

        // Window 1 (feature registry): the raced fetch's single sections
        // write re-applies the registered roll instead of reverting it.
        assertEquals(listOf("r1", "r2"), itemIds(), "the raced fetch's write must not revert the on-screen roll")

        // Window 2 (repo epoch): the raced assembled payload (shuffle 3)
        // must NOT have been pinned into the repo cache.
        assertNull(proto.cachedHomePayload(), "the raced fetch's stale payload must not be cached")

        // Window 3 (network row epoch): the raced row response (shuffle 3)
        // must NOT have overwritten the seeded memo (shuffle 2).
        assertEquals(listOf("r1", "r2"), proto.rowMemoItems(row).map { it.id }, "the raced row response must not over-seed the roll")

        // The NEXT refresh serves the roll — the stale rows stay dead.
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("r1", "r2"), itemIds(), "the next refresh serves the rolled set, never the raced stale rows")
        assertEquals(3, proto.rawRowFetches, "only the parked transport call served shuffle 3; the next refresh reads the seeded memo")

        // Same in-body teardown rule as Pin 1.
        refresher?.stop()
        refresherScope?.cancel()
    }

    // ── Pin 3: drainRolls ordering at the fetch's last suspension ───────────

    @Test
    fun `a roll landing during the book-fraction decode is reapplied by the drain and agrees with the caches`() = runTest {
        val refresher = buildRefresher()

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
        assertEquals(2, proto.rawRowFetches)

        // The roll lands while the fetch is parked at the decode — strictly
        // BEFORE the drain point. It fetches shuffle 3 and seeds it.
        refresher.rollDiscoverRow(row)
        runCurrent()
        assertEquals(listOf("s1", "s2"), itemIds(), "the roll patches the row while the fetch is parked mid-decode")
        assertEquals(listOf("s1", "s2"), proto.rowMemoItems(row).map { it.id })

        // Release the decode: the fetch's single sections write carries the
        // payloads it captured (shuffle 2) — the drain must re-apply the
        // roll registered before it. If the decode suspension ever moved
        // after the drain, this write would land post-drain and revert the
        // roll to shuffle 2 here.
        tocGate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("s1", "s2"), itemIds(), "the fetch's write re-applies the roll registered before its drain point")

        // COMPOSITION agreement: the painted roll matches what the caches
        // hold — the next plain refresh serves the same set with no further
        // transport call.
        refresher.fetchOnce()
        runCurrent()
        assertEquals(listOf("s1", "s2"), itemIds(), "the caches and the painted roll agree — the next refresh is stable")
        assertEquals(3, proto.rawRowFetches)

        // Same in-body teardown rule as Pin 1.
        refresher?.stop()
        refresherScope?.cancel()
    }

    // ── The protocol double ──────────────────────────────────────────────────

    /**
     * Windows 2 + 3 of the roll protocol over the REAL shared cache engine,
     * shaped after the two production layers it stands in for:
     *  * the REPO half mirrors `MediaRepositoryImpl`: a single-entry
     *    assembled-payload [TtlCache] (60s, [HomeFreshness.REPO_MEMORY_TTL_MS])
     *    behind `repoEpoch`, with `rerollDiscoverRow` executing the
     *    protocol's ordering verbatim (invalidate: bump → row memo drop →
     *    payload drop; fetch: raw transport; seed: memo write + bump →
     *    payload drop again).
     *  * the NETWORK half mirrors `HomeSectionsFetcher`: a per-row memo
     *    (10 min, [HomeFreshness.DISCOVER_ROW_TTL_MS], key
     *    `discover_<rowId>_<limit>`) behind `rowEpoch`, consulted by every
     *    home fetch with `force` acting as its invalidation.
     * Every raw transport call serves the next shuffle — a RANDOM-sorted
     * row re-rolls on every true server hit, which is what makes a memo HIT
     * (stability) distinguishable from a re-fetch.
     */
    private inner class RollProtocolRepo {
        private val identity = CacheIdentity.UNKNOWN

        /** Window 2's stall guard — the repo's assembled-payload cache epoch. */
        private val repoEpoch = AtomicLong(0L)

        /** Window 3's stall guard — the network layer's discover-row memo epoch. */
        private val rowEpoch = AtomicLong(0L)

        private val homeSectionsCache = TtlCache<HomeSectionsResult>(
            maxSize = 1,
            ttlMs = HomeFreshness.REPO_MEMORY_TTL_MS,
            clock = { 0L }, // frozen: no TTL expiry — the ROLL path drives every cache miss here
        )

        private val rowMemo = TtlCache<List<MediaItem>>(
            ttlMs = HomeFreshness.DISCOVER_ROW_TTL_MS,
            clock = { 0L },
        )

        /** Raw transport hits served (one per true server call). */
        var rawRowFetches = 0
            private set

        private var lastQuery: HomeSectionQuery = HomeSectionQuery()

        /** One raw transport call: parks on the armed one-shot gate, then serves the next shuffle. */
        private suspend fun transport(): List<MediaItem> {
            val gate = transportGate
            transportGate = null
            gate?.await()
            rawRowFetches++
            return shuffles[rawRowFetches - 1]
        }

        /** The assembled home fetch — the network half's per-row memoized read. */
        private suspend fun assemble(query: HomeSectionQuery, force: Boolean): HomeSectionsResult {
            val continueReading = HomeSection(
                id = HomeSectionType.CONTINUE_READING.name,
                title = HomeSectionType.CONTINUE_READING.displayName,
                type = HomeSectionType.CONTINUE_READING,
                items = listOf(item("book1").copy(mediaType = MediaType.BOOK, playbackPositionTicks = 50_000L)),
            )
            val discoverSection = rowMemo.cacheThrough(
                identity,
                "discover_${row.id}_${row.limit}",
                force = force,
                currentEpoch = rowEpoch::get,
            ) {
                Result.success(transport())
            }.getOrNull().orEmpty().let { rolled ->
                HomeSection(id = discoverSectionId, title = row.title, type = HomeSectionType.DISCOVER, items = rolled)
            }
            return HomeSectionsResult(sections = listOf(continueReading, discoverSection))
        }

        /** The repo half's epoch-guarded assembled-payload read. */
        suspend fun getHomeSections(query: HomeSectionQuery, force: Boolean): Result<HomeSectionsResult> {
            lastQuery = query
            return homeSectionsCache.cacheThrough(
                identity,
                query.cacheKey(),
                force = force,
                currentEpoch = repoEpoch::get,
            ) {
                Result.success(assemble(query, force))
            }
        }

        /** The roll's fresh fetch — always raw, no cache (production delegates straight to the client). */
        suspend fun getDiscoverRowItems(): Result<List<MediaItem>> = Result.success(transport())

        /** The protocol's ordering, verbatim from `MediaRepositoryImpl.rerollDiscoverRow`. */
        suspend fun rerollDiscoverRow(rowConfig: DiscoverRowConfig): Result<List<MediaItem>> {
            // invalidate: repo-epoch bump → network per-row memo drop (the
            // fetcher's invalidate bumps ITS epoch first — HomeSectionsFetcher.
            // invalidateDiscoverRow) → assembled payload drop
            repoEpoch.incrementAndGet()
            rowEpoch.incrementAndGet()
            rowMemo.removeByKeyPrefix(identity, "discover_${rowConfig.id}")
            homeSectionsCache.clear()
            val result = getDiscoverRowItems()
            // seed only a real roll: memo write + commit-time epoch bump on
            // BOTH layers (fetcher seed bumps the row epoch too) + payload drop again
            result.onSuccess { items ->
                if (items.isNotEmpty()) {
                    repoEpoch.incrementAndGet()
                    rowEpoch.incrementAndGet()
                    rowMemo.put(identity, "discover_${rowConfig.id}_${rowConfig.limit}", items)
                    homeSectionsCache.clear()
                }
            }
            return result
        }

        /** Test visibility: the repo's cached assembled payload (null = not cached / refused write). */
        fun cachedHomePayload(): HomeSectionsResult? = homeSectionsCache.get(identity, lastQuery.cacheKey())

        /** Test visibility: the network row memo's current entry for [rowConfig]. */
        fun rowMemoItems(rowConfig: DiscoverRowConfig): List<MediaItem> =
            rowMemo.get(identity, "discover_${rowConfig.id}_${rowConfig.limit}").orEmpty()
    }

    private fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)
}
