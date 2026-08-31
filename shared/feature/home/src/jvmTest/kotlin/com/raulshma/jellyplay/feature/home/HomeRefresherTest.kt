package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
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
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers

/**
 * Direct [HomeRefresher] tests — the suite this extraction exists to enable:
 * refresh cadence, throttle, CW side-effects and user-data-push deferral
 * tested WITHOUT the Android lifecycle stack (no Robolectric, no
 * ProcessLifecycleOwner, no VM construction). Plain JUnit +
 * [MainDispatcherRule] + MockK for the constructor interfaces;
 * [FakeTimeSource] is a real fake.
 *
 * The refresher runs on its own [CoroutineScope] over the test scheduler
 * (mirroring the production viewModelScope hand-off), so:
 *  - use runCurrent / advanceTimeBy, NEVER advanceUntilIdle — the periodic
 *    loop is an infinite delay chain;
 *  - every test calls [HomeRefresher.stop] before returning so no loop
 *    survives the body (runTest's teardown advances the shared scheduler
 *    until idle and would otherwise never converge). [stopRefresher] in
 *    @After additionally cancels the scope — the harness leak this ticket
 *    exists to fix must not reappear here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRefresherTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
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

    /** Hot flow behind mediaRepository.userDataChanges; tests emit into it. */
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 64)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Online)

    /**
     * Real flow behind offlineModeManager.offlineMode — the refresher's
     * offline-mode observer (mirror + transition policy) collects it from
     * init, so it must be a real StateFlow, not a relaxed mock. Tests drive
     * transitions by emitting here, with the mocked manager's
     * `toggleManualOffline` flipping the flow like the real one.
     */
    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)

    /** Backs the androidTvWatchNextEnabledProvider handed to the refresher. */
    private var androidTvWatchNextEnabled = true

    /** Backs the directArrEnabledProvider handed to the refresher. */
    private var directArrEnabled = false

    /**
     * Fake for the refresher's `awaitOutboxDrained` seam: counts invocations
     * and (while [drainGate] is set) parks, so GoingOnline tests can observe
     * the handshake mid-flight — flag up, loader up, fetch not yet started.
     */
    private var drainCalls = 0
    private var drainGate: CompletableDeferred<Unit>? = null

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

    /**
     * Builds the refresher on a scope that mirrors the production viewModelScope
     * hand-off (SupervisorJob over the test scheduler — NOT a child of the
     * TestScope, so the periodic loop's delay chain never blocks runTest's
     * completion wait; stopping it remains each test's job, see the class KDoc).
     */
    private fun TestScope.buildRefresher(): HomeRefresher {
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
            awaitOutboxDrained = {
                drainCalls++
                drainGate?.await()
            },
            sectionPrefsProvider = {
                HomeSectionPrefs(
                    query = HomeSectionQuery(),
                    homeSectionOrder = HomeSectionType.CONFIGURABLE,
                    mergeContinueWatchingAndNextUp = false,
                )
            },
            seerrPreferencesProvider = { SeerrPreferences() },
            discoverEnabledProvider = { false },
            directArrEnabledProvider = { directArrEnabled },
            androidTvWatchNextEnabledProvider = { androidTvWatchNextEnabled },
        ).also { refresher = it }
    }

    @Test
    fun continueWatchingChange_firesBroadcaster_andTvScheduler() = runTest {
        // CW publishing is gated on the androidTvWatchNextEnabled pref (default true).
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(
            HomeSectionsResult(
                sections = listOf(
                    section(HomeSectionType.CONTINUE_WATCHING, items = listOf(item("cw1"))),
                ),
            ),
        )
        val refresher = buildRefresher()

        refresher.fetchOnce()
        runCurrent()

        verify { continueWatchingBroadcaster.refreshContinueWatching() }
        coVerify { tvWatchNextScheduler.scheduleRefresh() }
    }

    @Test
    fun continueWatchingChange_skipsTvScheduler_whenPrefDisabled() = runTest {
        androidTvWatchNextEnabled = false
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(
            HomeSectionsResult(
                sections = listOf(
                    section(HomeSectionType.CONTINUE_WATCHING, items = listOf(item("cw1"))),
                ),
            ),
        )
        val refresher = buildRefresher()

        refresher.fetchOnce()
        runCurrent()

        verify { continueWatchingBroadcaster.refreshContinueWatching() }
        coVerify(exactly = 0) { tvWatchNextScheduler.scheduleRefresh() }
    }

    @Test
    fun `user switch cancelling cold-start fetch never exposes empty loaded state`() = runTest {
        // The cold-launch "No Content Available" flash: lifecycle start()
        // begins the stale-check fetch, then the session SignedIn transition
        // cancels it mid-flight via refreshForUserSwitch. The cancelled
        // fetch's finally must not clear the spinner flags — that write
        // lands between the switch's paint and its replacement fetch,
        // exposing sections=empty + isLoading=false + error=null, which
        // renders as the empty state until the replacement fetch lands.
        val coldStartGate = CompletableDeferred<Unit>()
        var fetchCalls = 0
        coEvery { mediaRepository.getHomeSections(any(), any()) } coAnswers {
            fetchCalls++
            if (fetchCalls == 1) {
                coldStartGate.await() // parked network call — cancelled before completing
                Result.success(HomeSectionsResult(sections = emptyList()))
            } else {
                Result.success(
                    HomeSectionsResult(sections = listOf(section(HomeSectionType.CONTINUE_WATCHING, items = listOf(item("cw1"))))),
                )
            }
        }
        coEvery { mediaRepository.getCachedHomeSections(any()) } returns null
        fakeTimeSource.nowMs = 120_000 // past the 60s foreground interval: start()'s stale check fetches
        val refresher = buildRefresher()

        val states = mutableListOf<HomeRefreshState>()
        refresherScope!!.launch { refresher.state.toList(states) }
        runCurrent()

        refresher.start()
        runCurrent() // the cold-start fetch acquires the mutex and parks on the gate
        refresher.request(RefreshTrigger.UserSwitched) // SignedIn: cancels the parked fetch, re-fetches
        runCurrent()
        refresher.stop()

        assertEquals(2, fetchCalls)
        val exposedEmptyLoaded = states.any { it.sections.isEmpty() && !it.isLoading && it.error == null }
        assertTrue(!exposedEmptyLoaded, "empty+loaded+no-error state exposed during the switch: $states")
    }

    @Test
    fun `stop clears the spinner flags when cancelling an in-flight fetch`() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } coAnswers {
            CompletableDeferred<Unit>().await() // parked forever — cancelled by stop()
            Result.success(HomeSectionsResult(sections = emptyList()))
        }
        fakeTimeSource.nowMs = 120_000 // past the 60s foreground interval: start()'s stale check fetches
        val refresher = buildRefresher()
        refresher.start()
        runCurrent() // fetch parks with isLoading=true (initial state)

        refresher.stop()
        runCurrent()

        assertFalse(refresher.state.value.isLoading)
        assertTrue(refresher.state.value.sections.isEmpty())
    }

    @Test
    fun userDataChange_inForeground_triggersForcedRefresh() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any(), any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        val refresher = buildRefresher()
        runCurrent()
        // Prime the refresh clock the way a sign-in fetch would (the fake's
        // 1_000 becomes lastRefreshTime).
        refresher.fetchOnce()
        runCurrent()

        // Past the 60s user-data throttle window since the priming fetch
        // (lastRefreshTime = the fake's 1_000), so the change refresh may run.
        fakeTimeSource.nowMs = 61_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { mediaRepository.getHomeSections(any(), force = true) }
        refresher.stop()
    }

    @Test
    fun userDataChange_burstIsDebouncedIntoSingleForcedRefresh() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any(), any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        val refresher = buildRefresher()
        runCurrent()
        refresher.fetchOnce()
        runCurrent()

        fakeTimeSource.nowMs = 61_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        // A second change inside the debounce window restarts the timer; only
        // the merged refresh may run.
        advanceTimeBy(500)
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i2")))
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 1) { mediaRepository.getHomeSections(any(), force = true) }
        refresher.stop()
    }

    @Test
    fun userDataChange_withinMinRefreshInterval_isThrottled() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any(), any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        val refresher = buildRefresher()
        runCurrent()
        refresher.fetchOnce()
        runCurrent()

        // lastRefreshTime is the priming fetch's 1_000; a change 5s later sits
        // inside the 60s user-data throttle window → no forced fetch.
        fakeTimeSource.nowMs = 6_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), force = true) }
    }

    @Test
    fun userDataChange_whileBackgrounded_defersRefreshUntilOnStart() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any(), any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        val refresher = buildRefresher()
        runCurrent()
        refresher.fetchOnce()
        runCurrent()

        refresher.stop()
        runCurrent()
        // Past the 60s user-data throttle window. start()'s stale check fires
        // at this age too, but its fetch is non-forced — so any forced fetch
        // below can only come from the deferred user-data refresh.
        fakeTimeSource.nowMs = 61_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_000)
        runCurrent()
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), force = true) }

        refresher.start()
        runCurrent()
        coVerify(exactly = 1) { mediaRepository.getHomeSections(any(), force = true) }
        refresher.stop()
    }

    @Test
    fun userDataChange_whileOffline_defersRefreshUntilOnStart() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any(), any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        val refresher = buildRefresher()
        runCurrent()
        refresher.fetchOnce()
        runCurrent()
        every { offlineModeManager.isOffline } returns true

        // Past the 60s user-data throttle window; the change lands during a
        // disconnect, so it must arm the pending flag instead of vanishing.
        fakeTimeSource.nowMs = 61_000L
        userDataEvents.tryEmit(UserDataChange(userId = "u1", itemIds = listOf("i1")))
        advanceTimeBy(1_000)
        runCurrent()
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), force = true) }

        every { offlineModeManager.isOffline } returns false
        refresher.start()
        runCurrent()
        coVerify(exactly = 1) { mediaRepository.getHomeSections(any(), force = true) }
        refresher.stop()
    }

    @Test
    fun startAndStop_controlPeriodicRefresh() = runTest {
        // Fresh refresher: lastRefreshTime = 0 and the fake clock sits at
        // 1_000, so start()'s stale check must NOT fetch — only the loop
        // starts. Smoke: both lifecycle entries run cleanly, and stop()
        // cancels the loop before the test ends (see class KDoc).
        val refresher = buildRefresher()

        refresher.start()
        runCurrent()

        refresher.stop()
        runCurrent()
        // Successfully starts and stops the periodic-refresh loop without
        // throwing, and no fetch fires inside the staleness window.
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
    }

    @Test
    fun goingOnline_drainsOutboxBeforeCappedFetch_thenClearsFlags() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns Result.success(
            HomeSectionsResult(
                sections = listOf(section(HomeSectionType.CONTINUE_WATCHING, items = listOf(item("cw1")))),
            ),
        )
        val refresher = buildRefresher()
        runCurrent() // offline-mode observer subscribes; initial ONLINE emission mirrors only

        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent() // ONLINE→offline: content dropped
        drainGate = CompletableDeferred()
        every { offlineModeManager.toggleManualOffline() } answers { offlineModeFlow.value = OfflineMode.ONLINE }

        refresher.request(RefreshTrigger.GoingOnline)
        runCurrent() // flag raised → manager toggled → ONLINE emission starts the handshake

        // Mid-handshake: busy flag + full-screen loader up, drain in flight,
        // and the fetch strictly NOT started (it must wait for the drain so
        // Continue Watching reflects the server's post-sync state).
        assertTrue(refresher.state.value.isGoingOnline)
        assertTrue(refresher.state.value.isLoading)
        assertEquals(1, drainCalls)
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }

        drainGate!!.complete(Unit)
        runCurrent()

        assertFalse(refresher.state.value.isGoingOnline)
        assertFalse(refresher.state.value.isLoading)
        coVerify(exactly = 1) { mediaRepository.getHomeSections(any(), any()) }
        assertTrue(refresher.state.value.sections.isNotEmpty())
        refresher.stop()
    }

    @Test
    fun goingOnline_fetchTimeout_clearsFlagsInsteadOfHanging() = runTest {
        // Regression pin: a hung getHomeSections call previously parked the
        // handshake forever, leaving isGoingOnline (and the loader) stuck on.
        coEvery { mediaRepository.getHomeSections(any(), any()) } coAnswers {
            CompletableDeferred<Result<HomeSectionsResult>>().await() // never completes
        }
        val refresher = buildRefresher()
        runCurrent()

        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()
        every { offlineModeManager.toggleManualOffline() } answers { offlineModeFlow.value = OfflineMode.ONLINE }

        refresher.request(RefreshTrigger.GoingOnline)
        runCurrent()
        assertTrue(refresher.state.value.isGoingOnline, "isGoingOnline must be observable while the fetch hangs")

        advanceTimeBy(31_000) // past GOING_ONLINE_TIMEOUT_MS
        runCurrent()

        assertFalse(refresher.state.value.isGoingOnline)
        assertFalse(refresher.state.value.isLoading)
        refresher.stop()
    }

    @Test
    fun goingOnline_toggleNeverLands_fallbackClearsFlag() = runTest {
        // Regression pin: toggleManualOffline() is a fire-and-forget
        // preference write on the manager's own scope — if that write is
        // lost, the mode flow never emits ONLINE and the observer's
        // handshake (whose finally clears the flag) never runs. The
        // request's own fallback must clear the busy flag instead of
        // leaving the Go Online spinner on until restart. The relaxed mock
        // leaves toggleManualOffline() as a no-op — exactly that scenario.
        val refresher = buildRefresher()
        runCurrent()

        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()

        refresher.request(RefreshTrigger.GoingOnline)
        runCurrent()
        assertTrue(refresher.state.value.isGoingOnline)

        advanceTimeBy(31_000) // past GOING_ONLINE_TIMEOUT_MS
        runCurrent()

        assertFalse(refresher.state.value.isGoingOnline)
        refresher.stop()
    }

    @Test
    fun wentOnlineSpontaneously_drainsOutboxBeforeFetch_andRepopulatesSections() = runTest {
        // Regression pin: an external/auto offline→online flip (the nav ⋮
        // "Go Online" toggles the manager directly from the app shell;
        // auto-detect flips OFFLINE_AUTO→ONLINE on network return) lands in
        // the observer WITHOUT a GoingOnline request. That transition used to
        // mirror the field only — after the offline content drop the home sat
        // on "No content available" until a manual refresh or the next
        // periodic tick. Every reconnect must run the same drain+fetch
        // handshake as the user-initiated one.
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns Result.success(
            HomeSectionsResult(
                sections = listOf(section(HomeSectionType.CONTINUE_WATCHING, items = listOf(item("cw1")))),
            ),
        )
        val refresher = buildRefresher()
        runCurrent()

        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent() // ONLINE→offline: content dropped
        assertTrue(refresher.state.value.sections.isEmpty())

        drainGate = CompletableDeferred()
        offlineModeFlow.value = OfflineMode.ONLINE // spontaneous — NO GoingOnline request
        runCurrent()

        // Mid-handshake: full-screen loader up, outbox draining, fetch
        // strictly not started (same ordering contract as the user-initiated
        // handshake so Continue Watching reflects the server's post-sync state).
        assertTrue(refresher.state.value.isLoading)
        assertEquals(1, drainCalls)
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }

        drainGate!!.complete(Unit)
        runCurrent()

        assertFalse(refresher.state.value.isLoading)
        assertFalse(refresher.state.value.isGoingOnline)
        coVerify(exactly = 1) { mediaRepository.getHomeSections(any(), any()) }
        assertTrue(refresher.state.value.sections.isNotEmpty())
        refresher.stop()
    }

    @Test
    fun wentOffline_dropsCachedOnlineContent() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns Result.success(
            HomeSectionsResult(
                sections = listOf(section(HomeSectionType.LATEST_MEDIA, items = listOf(item("m1")))),
            ),
        )
        // *arr row rides the same drop as discover: its cards route to
        // Seerr/TMDB, unreachable offline.
        directArrEnabled = true
        coEvery { arrRepository.refreshCalendar(any(), any()) } returns Result.success(Unit)
        coEvery { arrRepository.calendar(any(), any()) } returns flowOf(
            listOf(ArrCalendarItem(title = "Coming", mediaType = ArrMediaType.MOVIE)),
        )
        val refresher = buildRefresher()
        runCurrent()
        refresher.fetchOnce()
        runCurrent()
        assertTrue(refresher.state.value.sections.isNotEmpty())
        assertTrue(refresher.state.value.recentlyGrabbed.isNotEmpty())

        // Production path: the manager flips the mode and the refresher's own
        // observer reacts — cached online sections + discover rows + the *arr
        // row dropped, going-online spinner (if any) cleared. The
        // offline→online side of this transition (including the spontaneous
        // flavour) is pinned by
        // wentOnlineSpontaneously_drainsOutboxBeforeFetch_andRepopulatesSections.
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()
        assertTrue(refresher.state.value.sections.isEmpty())
        assertTrue(refresher.state.value.discoverSections.isEmpty())
        assertTrue(refresher.state.value.recentlyGrabbed.isEmpty())
        assertFalse(refresher.state.value.isGoingOnline)
        refresher.stop()
    }

    @Test
    fun userSwitched_paintsCachedSnapshotWhileReplacementFetchRuns() = runTest {
        coEvery { mediaRepository.getCachedHomeSections(any()) } returns HomeSectionsResult(
            sections = listOf(section(HomeSectionType.LATEST_MEDIA, items = listOf(item("cached1")))),
        )
        val fetchGate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getHomeSections(any(), any()) } coAnswers {
            fetchGate.await()
            Result.success(HomeSectionsResult(sections = emptyList()))
        }
        val refresher = buildRefresher()
        runCurrent()

        refresher.request(RefreshTrigger.UserSwitched)
        runCurrent()

        // Stale-while-revalidate paint: the persisted snapshot is on screen
        // with NO full-screen loader while the network fetch is still parked.
        assertFalse(refresher.state.value.isLoading)
        assertEquals(listOf("cached1"), refresher.state.value.sections.single().items.map { it.id })
        assertNull(refresher.state.value.error)

        fetchGate.complete(Unit)
        runCurrent()
        refresher.stop()
    }

    @Test
    fun signedOut_resetsToEmptyLoadingState() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns Result.success(
            HomeSectionsResult(sections = listOf(section(HomeSectionType.LATEST_MEDIA))),
        )
        coEvery { mediaRepository.getCachedHomeSections(any()) } returns null
        val refresher = buildRefresher()
        refresher.fetchOnce()
        runCurrent()
        assertTrue(refresher.state.value.sections.isNotEmpty())

        refresher.request(RefreshTrigger.SignedOut)

        val state = refresher.state.value
        assertTrue(state.sections.isEmpty())
        assertTrue(state.discoverSections.isEmpty())
        assertNull(state.error)
        assertTrue(state.isLoading)
        refresher.stop()
    }

    @Test
    fun patchItems_flipsMatchingItemInEverySectionAndTouchesNothingElse() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns Result.success(
            HomeSectionsResult(
                sections = listOf(
                    section(HomeSectionType.CONTINUE_WATCHING, items = listOf(item("cw1"), item("other"))),
                    section(HomeSectionType.LATEST_MEDIA, items = listOf(item("cw1"))),
                ),
            ),
        )
        val refresher = buildRefresher()
        refresher.fetchOnce()
        runCurrent()
        val otherBefore = refresher.state.value.sections[0].items.last()

        refresher.patchItems("cw1") { it.copy(isPlayed = true) }

        val sections = refresher.state.value.sections
        assertTrue(sections[0].items.first { it.id == "cw1" }.isPlayed)
        assertTrue(sections[1].items.single { it.id == "cw1" }.isPlayed)
        // The sibling card keeps its exact instance — the patch maps ONLY
        // matching ids, in every section.
        assertSame(otherBefore, sections[0].items.last())
        assertFalse(sections[0].items.last().isPlayed)
        refresher.stop()
    }

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

    /**
     * Controllable [TimeSource] whose clock defaults to a fixed epoch so the
     * periodic-refresh and TTL gates stay on one side of their thresholds;
     * tests move [nowMs] to deliberately cross one.
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
