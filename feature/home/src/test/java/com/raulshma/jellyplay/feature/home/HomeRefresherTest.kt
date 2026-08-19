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
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    /** Backs the androidTvWatchNextEnabledProvider handed to the refresher. */
    private var androidTvWatchNextEnabled = true

    @Before
    fun setUp() {
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
        every { offlineModeManager.isOffline } returns false
    }

    @After
    fun stopRefresher() {
        refresher?.stop()
        refresherScope?.cancel()
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
            planProvider = {
                HomeSectionPrefs(
                    query = HomeSectionQuery(),
                    homeSectionOrder = HomeSectionType.CONFIGURABLE,
                    mergeContinueWatchingAndNextUp = false,
                )
            },
            seerrPreferencesProvider = { SeerrPreferences() },
            discoverEnabledProvider = { false },
            directArrEnabledProvider = { false },
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
