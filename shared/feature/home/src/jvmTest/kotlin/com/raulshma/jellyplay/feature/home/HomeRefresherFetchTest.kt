package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import kotlin.test.Test
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionPrefs
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Invariants pinned (the fetch-core + trigger branches [HomeRefresherTest]
 * does not cover):
 *  - `fetchOnce` success with failed section types raises `partialLoadError`
 *    while keeping the loaded sections (zero-item sections are NOT failures).
 *  - `fetchOnce` failure over EMPTY sections sets `error` (and therefore
 *    `fetchFailedEmpty`); failure over EXISTING sections keeps the stale
 *    sections, never sets `error`, and clears `partialLoadError`.
 *  - An offline `fetchOnce` never touches the repository yet still stamps the
 *    refresh clock fresh (the onStart stale check must not re-fetch right
 *    after an offline attempt).
 *  - `fetchOnce(force = true)` bypasses the home-sections cache; the
 *    PrefsChanged trigger fetches NON-forced.
 *  - The DiscoverEnabled trigger runs a standalone discover-only fan-out that
 *    never touches `getHomeSections`.
 *  - Manual refresh clears sections + raises the full-screen loader while the
 *    fetch is in flight; PullToRefresh keeps content and raises only the
 *    inline spinner. Both clear their flag when the fetch resolves.
 *
 * Harness mirrors [HomeRefresherTest]: MockK collaborators, inlined
 * StandardTestDispatcher (Dispatchers.setMain), a scope over the test
 * scheduler (NOT a child of TestScope), and a [stopRefresher] teardown —
 * never `advanceUntilIdle`, the periodic loop is an infinite delay chain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRefresherFetchTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var widgetDataStore: WidgetDataStore
    private lateinit var continueWatchingBroadcaster: ContinueWatchingBroadcaster
    private lateinit var tvWatchNextScheduler: TvWatchNextScheduler
    private lateinit var librarySyncHook: LibrarySyncHook
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var fakeTimeSource: FakeTimeSource

    private var refresher: HomeRefresher? = null
    private var refresherScope: CoroutineScope? = null

    private val userDataEvents = MutableSharedFlow<com.raulshma.jellyplay.core.model.UserDataChange>(extraBufferCapacity = 64)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Online)
    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)
        widgetDataStore = mockk(relaxed = true)
        continueWatchingBroadcaster = mockk(relaxed = true)
        tvWatchNextScheduler = mockk(relaxed = true)
        librarySyncHook = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        fakeTimeSource = FakeTimeSource()

        every { mediaRepository.userDataChanges } returns userDataEvents
        every { offlineModeManager.networkStatus } returns networkStatusFlow
        every { offlineModeManager.offlineMode } returns offlineModeFlow
        every { offlineModeManager.isOffline } returns false
    }

    @AfterTest
    fun stopRefresher() {
        refresher?.stop()
        refresherScope?.cancel()

        Dispatchers.resetMain()
    }

    private fun TestScope.buildRefresher(
        seerrPreferences: SeerrPreferences = SeerrPreferences(),
    ): HomeRefresher {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        refresherScope = scope
        return HomeRefresher(
            scope = scope,
            timeSource = fakeTimeSource,
            mediaRepository = mediaRepository,
            seerrRepository = seerrRepository,
            arrRepository = arrRepository,
            orderHomeSections = OrderHomeSectionsUseCase(),
            widgetDataStore = widgetDataStore,
            continueWatchingBroadcaster = continueWatchingBroadcaster,
            tvWatchNextScheduler = tvWatchNextScheduler,
            librarySyncHook = librarySyncHook,
            offlineModeManager = offlineModeManager,
            awaitOutboxDrained = {},
            sectionPrefsProvider = {
                HomeSectionPrefs(
                    query = HomeSectionQuery(),
                    homeSectionOrder = HomeSectionType.CONFIGURABLE,
                    mergeContinueWatchingAndNextUp = false,
                )
            },
            seerrPreferencesProvider = { seerrPreferences },
            discoverEnabledProvider = { false },
            directArrEnabledProvider = { false },
            androidTvWatchNextEnabledProvider = { true },
        ).also { refresher = it }
    }

    // ── fetchOnce: partial load + failure/stale policy ──────────────────────

    @Test
    fun fetchOnce_successWithFailedSectionTypes_raisesPartialLoadError_butKeepsSections() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns Result.success(
            HomeSectionsResult(
                sections = listOf(section(HomeSectionType.LATEST_MEDIA, listOf(item("m1")))),
                failedSectionTypes = setOf(HomeSectionType.NEXT_UP),
            ),
        )
        val refresher = buildRefresher()

        refresher.fetchOnce()
        runCurrent()

        assertTrue(
            refresher.state.value.partialLoadError,
            "a failed section type must raise the non-blocking partial-load notice",
        )
        assertEquals(1, refresher.state.value.sections.size)
        assertNull(refresher.state.value.error)
        assertFalse(refresher.state.value.isLoading)
    }

    @Test
    fun fetchOnce_failureWithEmptySections_setsError_andFetchFailedEmpty() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.failure(RuntimeException("server down"))
        val refresher = buildRefresher()

        refresher.fetchOnce()
        runCurrent()

        assertEquals("server down", refresher.state.value.error)
        assertTrue(refresher.state.value.sections.isEmpty())
        assertTrue(
            refresher.state.value.fetchFailedEmpty,
            "error + empty sections is the implicit-offline precondition",
        )
        assertFalse(refresher.state.value.partialLoadError)
    }

    @Test
    fun fetchOnce_failureWithExistingSections_keepsStaleSections_andNeverSetsError() = runTest {
        val stale = section(HomeSectionType.LATEST_MEDIA, listOf(item("m1")))
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.success(HomeSectionsResult(sections = listOf(stale)))
        val refresher = buildRefresher()
        refresher.fetchOnce()
        runCurrent()
        assertTrue(refresher.state.value.sections.isNotEmpty())

        // The follow-up fetch fails — the stale content must survive untouched.
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.failure(RuntimeException("flaky network"))
        refresher.fetchOnce()
        runCurrent()

        assertNull(
            refresher.state.value.error,
            "a failure with content on screen must degrade to stale, not a hard error",
        )
        assertEquals(listOf(stale), refresher.state.value.sections)
        assertFalse(refresher.state.value.partialLoadError)
        assertFalse(refresher.state.value.fetchFailedEmpty)
    }

    @Test
    fun fetchOnce_whileOffline_skipsRepository_andStillStampsFreshness() = runTest {
        every { offlineModeManager.isOffline } returns true
        fakeTimeSource.nowMs = 50_000L
        val refresher = buildRefresher()

        try {
            refresher.fetchOnce()
            runCurrent()
            coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
            assertNull(refresher.state.value.error)

            // The offline attempt counted as fresh: 50s later (past no other
            // threshold, under the 60s onStart staleness window) going back
            // online and foregrounding must NOT immediately re-fetch.
            every { offlineModeManager.isOffline } returns false
            fakeTimeSource.nowMs = 100_000L
            refresher.start()
            runCurrent()

            coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
        } finally {
            // stop() in the test body, not just @AfterTest: runTest advances
            // virtual time to idle before returning, which would fire the
            // periodic loop against the unstubbed (relaxed) getHomeSections
            // and fail THIS test with a ClassCastException after its own
            // assertions already passed.
            refresher.stop()
        }
    }

    @Test
    fun fetchOnce_force_bypassesHomeSectionsCache() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.success(HomeSectionsResult(sections = emptyList()))
        val refresher = buildRefresher()

        refresher.fetchOnce(force = true)
        runCurrent()

        coVerify { mediaRepository.getHomeSections(any(), force = true) }
    }

    // ── request(): PrefsChanged + DiscoverEnabled policies ──────────────────

    @Test
    fun request_prefsChanged_fetchesNonForced_withNewQuery() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.success(HomeSectionsResult(sections = emptyList()))
        val refresher = buildRefresher()

        try {
            refresher.request(RefreshTrigger.PrefsChanged)
            runCurrent()

            // Non-forced: the new query is what matters, not cache bypass.
            coVerify(exactly = 1) { mediaRepository.getHomeSections(any(), force = false) }
            coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), force = true) }
            assertFalse(refresher.state.value.isLoading)
        } finally {
            refresher.stop()
        }
    }

    @Test
    fun request_discoverEnabled_fetchesDiscoverStandalone_withoutTouchingSections() = runTest {
        // Trending-only: SeerrPreferences defaults the other four discover
        // types on, and their endpoints are not stubbed below — a relaxed
        // mock returns Result.success(Object) for them and the fan-out dies
        // with a ClassCastException before the state write.
        val prefs = SeerrPreferences(
            enabled = true,
            discoverEnabled = true,
            discoverTrending = true,
            discoverPopularMovies = false,
            discoverPopularTv = false,
            discoverUpcomingMovies = false,
            discoverUpcomingTv = false,
        )
        val trending = listOf(seerrItem(1, "Trending Movie"))
        coEvery { seerrRepository.getTrending(any()) } returns
            Result.success(SeerrSearchResponse(results = trending))
        val refresher = buildRefresher(seerrPreferences = prefs)

        refresher.request(RefreshTrigger.DiscoverEnabled)
        runCurrent()

        coVerify(exactly = 1) { seerrRepository.getTrending(any()) }
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
        assertEquals(mapOf(DiscoverSectionType.TRENDING to trending), refresher.state.value.discoverSections)
        assertNull(refresher.state.value.error)
    }

    // ── request(): Manual vs PullToRefresh preamble, observed mid-flight ────

    @Test
    fun request_manual_clearsContent_andRaisesFullScreenLoader_whileFetchInFlight() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.success(HomeSectionsResult(sections = listOf(section(HomeSectionType.LATEST_MEDIA, listOf(item("m1"))))))
        val refresher = buildRefresher()
        refresher.fetchOnce()
        runCurrent()
        assertTrue(refresher.state.value.sections.isNotEmpty())

        val fetchGate = CompletableDeferred<Result<HomeSectionsResult>>()
        coEvery { mediaRepository.getHomeSections(any(), any()) } coAnswers { fetchGate.await() }

        try {
            refresher.request(RefreshTrigger.Manual)
            runCurrent()

            assertTrue(refresher.state.value.isLoading, "Manual raises the full-screen loader")
            assertTrue(
                refresher.state.value.sections.isEmpty(),
                "Manual clears the on-screen content for the loader",
            )
            assertTrue(refresher.state.value.discoverSections.isEmpty())
            assertNull(refresher.state.value.error)

            fetchGate.complete(
                Result.success(HomeSectionsResult(sections = listOf(section(HomeSectionType.NEXT_UP, listOf(item("n1")))))),
            )
            runCurrent()

            assertFalse(refresher.state.value.isLoading)
            assertEquals(1, refresher.state.value.sections.size)
        } finally {
            refresher.stop()
        }
    }

    @Test
    fun request_pullToRefresh_keepsContent_andRaisesInlineSpinner_whileFetchInFlight() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.success(HomeSectionsResult(sections = listOf(section(HomeSectionType.LATEST_MEDIA, listOf(item("m1"))))))
        val refresher = buildRefresher()
        refresher.fetchOnce()
        runCurrent()
        val onScreen = refresher.state.value.sections
        assertTrue(onScreen.isNotEmpty())

        val fetchGate = CompletableDeferred<Result<HomeSectionsResult>>()
        coEvery { mediaRepository.getHomeSections(any(), any()) } coAnswers { fetchGate.await() }

        try {
            refresher.request(RefreshTrigger.PullToRefresh)
            runCurrent()

            assertTrue(refresher.state.value.isRefreshing, "PullToRefresh raises the inline spinner")
            assertFalse(refresher.state.value.isLoading, "PullToRefresh must NOT raise the full-screen loader")
            assertEquals(
                onScreen,
                refresher.state.value.sections,
                "PullToRefresh keeps the content on screen",
            )

            fetchGate.complete(Result.success(HomeSectionsResult(sections = onScreen)))
            runCurrent()

            assertFalse(refresher.state.value.isRefreshing)
        } finally {
            refresher.stop()
        }
    }

    // ── HomeRefreshState.fetchFailedEmpty boundary ──────────────────────────

    @Test
    fun fetchFailedEmpty_errorWithSectionsOnScreen_isFalse() {
        val state = HomeRefreshState(
            sections = listOf(section(HomeSectionType.LATEST_MEDIA)),
            error = "server down",
        )
        assertFalse(
            state.fetchFailedEmpty,
            "content still on screen disqualifies the implicit-offline fallback",
        )
        assertTrue(HomeRefreshState(error = "x").fetchFailedEmpty)
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private fun section(
        type: HomeSectionType,
        items: List<MediaItem> = emptyList(),
    ) = HomeSection(
        id = type.name,
        title = type.displayName,
        type = type,
        items = items,
    )

    private fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    private fun seerrItem(id: Int, title: String) = SeerrSearchItem(
        id = id,
        mediaType = "movie",
        title = title,
    )

    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: java.time.ZoneId): java.time.LocalDate = java.time.LocalDate.of(2026, 1, 1)
    }
}
