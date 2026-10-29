package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.testfixtures.FakeTimeSource
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.CollectionApiClient
import com.raulshma.jellyplay.core.network.api.ImageUrlApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort
import com.raulshma.jellyplay.core.network.library.HomeSectionsFetcher
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import com.raulshma.jellyplay.core.testfixtures.FakeHomeSectionSources
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * THE REAL-STACK HOME CONTRACT SUITE (ADR-0008) — the one suite executing
 * `MediaRepositoryImpl` (the real data-layer repo) over the REAL core:network
 * `HomeSectionsFetcher` on a fake [HomeSectionSources]. Before this suite no
 * test ran repo + fetcher together: the repo's constructor is `internal` to
 * core:data and the fetcher was `internal` to core:network, so every
 * cross-layer invariant — the repo's post-operation bump funnel, the
 * fetcher's roll choreography (invalidate → fetch → seed behind its own
 * write guard), the single-row-refresh outcome contract (`success(section)`
 * swap / `success(null)` drop / `failure` keep-stale) — was pinned PIECEWISE
 * across three suites (`MediaRepositoryHomeSectionsCacheTest`,
 * `HomeSectionsFetcherTest`, and feature/home's `DiscoverRollContractTest`,
 * which hand-builds a protocol-faithful repo double to span the layers it
 * cannot construct). This suite closes that gap. Full module/layer coverage
 * is NOT the goal — the piecewise suites keep their jobs; these five pins are
 * the invariants whose failure mode is a DISAGREEMENT between the layers.
 *
 * STACK:
 *  - [HomeSectionsFetcher] — the real commonMain orchestrator (sub-call
 *    fan-out, TTL memos, the roll operation and its generation guard),
 *    constructed exactly as `LibraryApiClientImpl` constructs it (see the
 *    fetcher's construction-scope KDoc), minus the transport: its sources
 *    are the shared [FakeHomeSectionSources] double
 *    (:shared:core:test-fixtures — the one copy core:network's fetcher suite
 *    and this suite drive; this suite's former module-local mirror carried
 *    the "internal to that module and cannot be reused" apology).
 *  - [RealStackClientShell] — `LibraryApiClientImpl`'s home seam, test-local:
 *    the wide client surface is a relaxed double (this suite drives only the
 *    home path; anything else reaching the shell is a suite bug) and the two
 *    home members run the real fetcher, MINUS the retry wrapper (the
 *    cancellation-rethrowing runCatching is kept — the fetch's
 *    throw-when-nothing-rendered must still become the Result.failure).
 *  - [FetcherBackedCachePort] — the [HomeSectionsCachePort] adapter with the
 *    same forwards `LibraryApiClientImpl` performs internally, recording the
 *    roll ops the repo routes through the port.
 *  - `MediaRepositoryImpl` — the real repo over the above, with a relaxed
 *    [HomeSectionsSnapshotStore] double (its persist call count IS the last
 *    pin) and relaxed doubles for every surface this suite does not
 *    exercise.
 *
 * Determinism: the repo's clock seam is the shared [FakeTimeSource] and
 * nothing advances it, so the 60s in-memory home TTL can never expire
 * mid-test — every freshness transition below is EVENT-driven (a funnel
 * bump), which is exactly the mechanism these invariants live on.
 *
 * The race-window rationale each pin leans on is owned by
 * `HomeFeed.getHomeSections`' KDoc (the write guarantee) and
 * `HomeFeed.rerollDiscoverRow`'s KDoc (the roll protocol's single owner);
 * this suite only executes them.
 */
class HomeFeedRealStackContractTest {

    /** Late-binding handle the announcer's arming lambda reads (set at build time). */
    private var built: MediaRepositoryImpl? = null

    /**
     * The test-local twin of `LibraryApiClientImpl`'s internal port adapter:
     * the same forwards onto the fetcher, MINUS the retry wrapper on
     * `refreshHomeSection` (the fetcher already returns Result; production
     * unwraps into its retry machinery, which this stack has no need for).
     * Records the roll ops the repo routes through the port, so a
     * single-row pull's "never the roll op" shape stays assertable.
     */
    private class FetcherBackedCachePort(
        private val fetcher: HomeSectionsFetcher,
    ) : HomeSectionsCachePort {

        /** The rows whose roll ops the repo ran through the port. */
        val rollCalls = mutableListOf<DiscoverRowConfig>()

        override fun invalidateSubcallCaches() {
            fetcher.invalidateCaches()
        }

        override suspend fun rerollDiscoverRow(row: DiscoverRowConfig): Result<List<MediaItem>> {
            rollCalls += row
            return fetcher.rerollDiscoverRow(row)
        }

        override suspend fun refreshHomeSection(
            section: HomeSection,
            query: HomeSectionQuery,
            mergeNextUpIntoContinueWatching: Boolean,
            force: Boolean,
        ): Result<HomeSection?> = fetcher.refreshSection(section, query, mergeNextUpIntoContinueWatching, force)
    }

    /**
     * `LibraryApiClientImpl`'s home seam, test-local: the wide client surface
     * delegates to a relaxed double, and the two home members run the REAL
     * fetcher exactly as production does. `getHomeSections` wraps
     * [HomeSectionsFetcher.fetch] in the cancellation-rethrowing runCatching
     * (production's retry wrapper's unit of work is a throw — this stack
     * drops the retry, not the catch); `getDiscoverRowItems` goes straight
     * to the same fake the fetcher reads (the transport-twin shape).
     */
    private class RealStackClientShell(
        private val fetcher: HomeSectionsFetcher,
        private val sources: FakeHomeSectionSources,
    ) : LibraryApiClient by mockk(relaxed = true) {

        /** The force flags of the assembled fetches, in call order. */
        val fetchCalls = mutableListOf<Boolean>()

        override suspend fun getHomeSections(query: HomeSectionQuery, force: Boolean): Result<HomeSectionsResult> {
            fetchCalls += force
            return runCatchingRethrowingCancellation { fetcher.fetch(query, force) }
        }

        override suspend fun getDiscoverRowItems(row: DiscoverRowConfig): Result<List<MediaItem>> =
            sources.getDiscoverRowItems(row)
    }

    private data class Stack(
        val repository: MediaRepositoryImpl,
        val client: RealStackClientShell,
        val port: FetcherBackedCachePort,
    )

    // The AuthApiClient seam behind the REAL HomeSession — the proven
    // construction shape of MediaRepositoryHomeSectionsCacheTest.buildRepository.
    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val authClient: AuthApiClient = mockk(relaxed = true)

    /** The SWR snapshot-store double — the persist call count is the last test's pin. */
    private val snapshotStore: HomeSectionsSnapshotStore = mockk(relaxed = true)

    /**
     * The repo's clock seam (monotonic read for the in-memory TtlCache, wall
     * read for the store double's unused paths); [FakeTimeSource.nowMs] never
     * moves in this suite — the 60s home TTL never expires mid-test.
     */
    private val fakeTimeSource = FakeTimeSource()

    private val sources = FakeHomeSectionSources()

    private fun buildStack(): Stack {
        every { authClient.session } returns sessionFlow
        // The real fetcher, constructed exactly as LibraryApiClientImpl
        // constructs it: same sources port, null Seerr transport (the
        // production default for wirings without one), an atomic-session
        // identity read mirroring the client impl's currentHomeCacheIdentity,
        // and a fixed today-string (no Seerr rows ride it).
        val fetcher = HomeSectionsFetcher(
            sources = sources,
            seerrSources = null,
            cacheIdentity = {
                CacheIdentity.ofOrNull(sessionFlow.value?.server?.id, sessionFlow.value?.user?.id)
            },
            today = { "2026-09-25" },
        )
        val port = FetcherBackedCachePort(fetcher)
        val client = RealStackClientShell(fetcher, sources)
        val homeSession = HomeSession(
            authClient,
            // Unconfined (not Dispatchers.Default): the session assignment
            // drives the identity classifier AND the registry reactions
            // synchronously on the setter's stack — same shape as
            // MediaRepositoryHomeSectionsCacheTest.
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        val sessionCacheRegistry = SessionCacheRegistry(
            homeSession,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        // ONE shared detail cluster across the impl + its ledger (the facade
        // split's single-instance shape — the same MediaRepositoryInternals
        // single the Koin graph aliases).
        val internals = MediaRepositoryInternals(client, homeSession, sessionCacheRegistry)
        val repository = MediaRepositoryImpl(
            libraryApiClient = client,
            collectionApiClient = mockk<CollectionApiClient>(relaxed = true),
            imageUrlApiClient = mockk<ImageUrlApiClient>(relaxed = true),
            homeSectionsCachePort = port,
            homeSnapshotStore = snapshotStore,
            playedStateSync = mockk(relaxed = true),
            userDataRealtimeChannel = mockk<UserDataRealtimeChannel>(relaxed = true),
            userDataAnnouncer = announcerFor(homeSession) { built },
            internals = internals,
            ledger = MediaFreshnessLedger(
                homeSession = homeSession,
                sessionCacheRegistry = sessionCacheRegistry,
                homeSnapshotStore = snapshotStore,
                episodeCatalogue = mockk<EpisodeCatalogue>(relaxed = true),
                internals = internals,
                homeSectionsCachePort = port,
                timeSource = fakeTimeSource,
            ),
        ).also { built = it }
        return Stack(repository, client, port)
    }

    private fun signIn(serverId: String, userId: String) {
        sessionFlow.value = ActiveSession(serverInfo(serverId), userInfo(userId))
    }

    private fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    private fun discoverRow(id: String) = DiscoverRowConfig(id = id, title = "Surprise Me")

    private fun discoverQuery(row: DiscoverRowConfig) = HomeSectionQuery(
        enabledSections = setOf(HomeSectionType.DISCOVER),
        discoverRows = listOf(row),
    )

    private fun continueWatchingQuery() = HomeSectionQuery(
        enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING),
    )

    @Test
    fun `single-row refresh success swaps through the real fetcher and the next getHomeSections sees the fresh row`() = runBlocking {
        // The outcome contract's happy path ACROSS the layers: the refresh
        // re-runs the row's sub-call through the REAL fetcher (memo read
        // bypassed, fresh items memoised), the repo funnel bumps and clears
        // the assembled payload on success, so the next ORDINARY read inside
        // the frozen TTL window refetches and serves the REFRESHED row —
        // never the pre-pull assembled payload (the race window
        // HomeFeed.getHomeSections' write guarantee closes), and never a
        // re-query either (the fetcher's refreshed memo is what the periodic
        // fetch serves).
        val (repository, client, port) = buildStack()
        signIn("server-1", "user-1")
        val row = discoverRow("dr_x")
        val query = discoverQuery(row)
        sources.discoverRowResults += Result.success(listOf(item("pre-pull")))

        val section = repository.getHomeSections(query, force = false).getOrThrow().sections.single()
        assertEquals(HomeSectionType.DISCOVER, section.type)

        sources.discoverRowResults += Result.success(listOf(item("rolled-1"), item("rolled-2")))
        val refreshed = repository.refreshHomeSection(
            section = section,
            query = query,
            mergeNextUpIntoContinueWatching = false,
            force = true,
        )

        assertTrue(refreshed.isSuccess, "the fresh payload is success(section)")
        // Identity preserved — only `items` moved (the keyed list animates a
        // swap, not a removal).
        assertEquals(section.id, refreshed.getOrNull()?.id)
        assertEquals(listOf("rolled-1", "rolled-2"), refreshed.getOrNull()?.items?.map { it.id })

        // The source is deliberately left EMPTY: if the next read re-queried
        // the row instead of serving the fetcher's refreshed memo, the row
        // would drop and the assertion below would fail loudly.
        val after = repository.getHomeSections(query, force = false).getOrThrow()
        assertEquals(listOf("rolled-1", "rolled-2"), after.sections.single().items.map { it.id })

        // Leaf calls: seed fetch + refresh only. Assembled fetches: two, both
        // non-forced — the second happened because the success bump cleared
        // the in-memory payload (event-driven freshness; the frozen TTL would
        // have served the pre-pull row otherwise). The pull rode the refresh
        // op only — never the roll op (the fetcher's memo was refreshed by
        // the pull itself).
        assertEquals(listOf("discover:dr_x:20", "discover:dr_x:20"), sources.calls)
        assertEquals(listOf(false, false), client.fetchCalls)
        assertTrue(port.rollCalls.isEmpty(), "the single-row pull must ride the refresh op, never the roll op")
    }

    @Test
    fun `single-row refresh success-null drops the row`() = runBlocking {
        // Outcome contract, arm 2: the row's source legitimately returned no
        // items — `Result.success(null)`, the assembler's
        // zero-items-is-not-rendered policy at the single-row seam. NOT a
        // failure: the pull worked, the row is gone. And success still means
        // success for the funnel — the bump runs, so the next ordinary read
        // cannot replay the pre-drop assembled payload and re-render the
        // dropped row.
        val (repository, client, _) = buildStack()
        signIn("server-1", "user-1")
        val query = continueWatchingQuery()
        sources.continueWatchingResults += Result.success(listOf(item("cw-1")))

        val section = repository.getHomeSections(query, force = false).getOrThrow().sections.single()
        assertEquals(HomeSectionType.CONTINUE_WATCHING, section.type)

        sources.continueWatchingResults += Result.success(emptyList())
        val refreshed = repository.refreshHomeSection(
            section = section,
            query = query,
            mergeNextUpIntoContinueWatching = false,
            force = true,
        )

        assertTrue(refreshed.isSuccess, "an emptied source is a success, not a failure")
        assertNull(refreshed.getOrNull())

        val after = repository.getHomeSections(query, force = false).getOrThrow()
        assertTrue(
            after.sections.none { it.type == HomeSectionType.CONTINUE_WATCHING },
            "the emptied row must not be re-rendered from the pre-drop payload",
        )
        assertEquals(listOf(false, false), client.fetchCalls, "success(null) still bumped: the next read refetched")
    }

    @Test
    fun `single-row refresh failure keeps the stale row and does not bump the generation`() = runBlocking {
        // Outcome contract, arm 3: the sub-call failed — Result.failure (the
        // real fetcher's runCatching turns the getOrThrow into the failure
        // value; the shell forwards it un-retried), and the repo funnel does
        // NOT run: the assembled entry is still accurate for every row the
        // pull didn't touch, so the next ordinary read keeps serving the
        // PRE-failure cache state within the frozen TTL window (zero new
        // fetches — the exact negative of the success arm).
        val (repository, client, _) = buildStack()
        signIn("server-1", "user-1")
        val query = continueWatchingQuery()
        sources.continueWatchingResults += Result.success(listOf(item("stale-1")))

        val section = repository.getHomeSections(query, force = false).getOrThrow().sections.single()

        sources.continueWatchingResults += Result.failure(RuntimeException("server down"))
        val refreshed = repository.refreshHomeSection(
            section = section,
            query = query,
            mergeNextUpIntoContinueWatching = false,
            force = true,
        )

        assertTrue(refreshed.isFailure)

        val after = repository.getHomeSections(query, force = false).getOrThrow()
        assertEquals(listOf("stale-1"), after.sections.single().items.map { it.id })
        assertEquals(listOf(false), client.fetchCalls, "no bump on failure: the assembled payload still serves")
    }

    @Test
    fun `rerollDiscoverRow rolls the real fetcher memo and the next fetch serves the roll`() = runBlocking {
        // The repo→port→fetcher leg of the roll protocol (race windows 2+3 of
        // HomeFeed.rerollDiscoverRow's KDoc), now on the REAL stack: the
        // invalidate → fetch → seed choreography runs INSIDE the real
        // fetcher's operation — it drops the row memo, fetches the row raw
        // and re-seeds it behind its own write guard — while the repo answers
        // with its post-operation bump (the assembled payload was dropped, so
        // the next ordinary read must refetch), and that refetch serves the
        // rolled row without re-querying the source. The leaf calls pin the
        // whole shape: one raw fetch for the roll, none for the next read.
        val (repository, client, port) = buildStack()
        signIn("server-1", "user-1")
        val row = discoverRow("dr_x")
        val query = discoverQuery(row)
        sources.discoverRowResults += Result.success(listOf(item("pre-roll")))

        repository.getHomeSections(query, force = false) // the row memo is seeded (leaf call 1)
        repository.getHomeSections(query, force = false) // the in-memory payload serves; no leaf call
        assertEquals(listOf("discover:dr_x:20"), sources.calls, "the memo is real: the second read did not re-query")

        sources.discoverRowResults += Result.success(listOf(item("rolled")))
        val rolled = repository.rerollDiscoverRow(row)

        assertEquals(listOf("rolled"), rolled.getOrNull()?.map { it.id })
        assertEquals(listOf(row), port.rollCalls, "the roll routes through the port's complete operation")

        val after = repository.getHomeSections(query, force = false).getOrThrow()
        assertEquals(listOf("rolled"), after.sections.single().items.map { it.id })
        assertEquals(
            listOf("discover:dr_x:20", "discover:dr_x:20"),
            sources.calls,
            "the seeded memo serves the roll; no re-roll on the next fetch",
        )
        assertEquals(listOf(false, false), client.fetchCalls, "the roll dropped the assembled payload: the read refetched")
    }

    @Test
    fun `a single-row result never persists to the SWR snapshot store`() = runBlocking {
        // The port KDoc invariant, on the real stack: refreshHomeSection must
        // bypass the whole-plan assembled-payload cache AND the SWR snapshot
        // persist — a single-row result must never masquerade as a
        // whole-query snapshot down the persisted path (the offline layout
        // mirror picks snapshots by recency and would inherit the partial
        // payload). The store double is wired for real: a FULL fetch persists
        // exactly once through getHomeSections' onFetched hook first, so the
        // unchanged count after the refresh is a real pin, not a vacuous one.
        val (repository, _, _) = buildStack()
        signIn("server-1", "user-1")
        val query = continueWatchingQuery()
        sources.continueWatchingResults += Result.success(listOf(item("cw-1")))

        val section = repository.getHomeSections(query, force = false).getOrThrow().sections.single()
        coVerify(exactly = 1) { snapshotStore.persist(any(), any()) } // the full fetch persisted

        sources.continueWatchingResults += Result.success(listOf(item("cw-2")))
        val refreshed = repository.refreshHomeSection(
            section = section,
            query = query,
            mergeNextUpIntoContinueWatching = false,
            force = true,
        )

        assertTrue(refreshed.isSuccess)
        coVerify(exactly = 1) { snapshotStore.persist(any(), any()) } // ...the single-row pull did NOT
    }

    private fun userInfo(id: String) = UserInfo(
        id = id,
        name = id,
        serverAddress = "https://example.com",
        accessToken = "token",
        serverId = "server-1",
        isAdmin = false,
        maxParentalAgeRating = null,
        primaryImageTag = null,
        enabledFolderIds = emptyList(),
    )

    private fun serverInfo(id: String) = ServerInfo(
        id = id,
        name = "server-$id",
        address = "https://example.com",
        userId = null,
        accessToken = null,
    )
}
