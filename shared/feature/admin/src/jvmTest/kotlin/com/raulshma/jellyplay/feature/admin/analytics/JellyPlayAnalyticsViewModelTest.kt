package com.raulshma.jellyplay.feature.admin.analytics

import com.raulshma.jellyplay.core.data.session.HomeSessionTransition
import com.raulshma.jellyplay.core.data.session.JellyPlayCapabilityState
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionIdentity
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsDay
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsOverview
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsSession
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsSessions
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsTopItem
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsTotals
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsUser
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayAnalyticsRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilitiesRoutes
import com.raulshma.jellyplay.feature.admin.transcodes.TranscodePlayMethod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the JellyPlay companion-plugin admin analytics flow
 * ([JellyPlayAnalyticsViewModel]):
 *
 *  - gating — the screen only touches the plugin routes when the real
 *    [JellyPlayPluginStatusStore] reports AVAILABLE **and** the capabilities
 *    carry the `analytics` feature key (the capability registry is
 *    the only gate; no fetches while gated off — the transcodes monitor's
 *    discipline over the analytics surface);
 *  - mapping — the overview payload resolves into humanized durations
 *    ("12h 30m"), chart points and ranked rows; the session's `playMethod`
 *    wire string resolves like the transcodes monitor's;
 *  - degradation — a 404 (null payload) is a QUIET degraded state, never an
 *    error; the initial overview failure is the only surfaced error;
 *  - interaction — the 7/30/90 window chips refetch the overview, the user
 *    filter (tap + dropdown) re-scopes the sessions fetch and toggles off,
 *    Load-more pages with `since = oldest endedAt` and de-duplicates.
 *
 * The store is the REAL [JellyPlayPluginStatusStore] over the same mocked
 * [JellyPlayAnalyticsRoutes] role the ViewModel reads — gating flows through the
 * actual probe, exactly as it does in production (the transcodes suite's
 * harness shape).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlayAnalyticsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var pluginApi: JellyPlayAnalyticsRoutes
    private lateinit var capabilitiesApi: JellyPlayCapabilitiesRoutes
    private lateinit var sessionCacheRegistry: SessionCacheRegistry
    private lateinit var statusStore: JellyPlayPluginStatusStore

    /**
     * The store's registered identity-reset action, captured from the relaxed
     * registry mock — firing it IS the production identity reset (user switch
     * / plugin reinstall): it flips the store back to UNKNOWN so the next
     * [JellyPlayAnalyticsViewModel.start] re-probes into a fresh gate-on.
     */
    private val resetAction = slot<suspend (HomeSessionTransition) -> Unit>()

    private val overview = JellyPlayAnalyticsOverview(
        days = 30,
        totals = JellyPlayAnalyticsTotals(
            plays = 42,
            playSeconds = 45_000,
            transcodeSeconds = 3_600,
            uniqueUsers = 3,
            uniqueItems = 17,
        ),
        perDay = listOf(
            JellyPlayAnalyticsDay(day = "2026-10-01", plays = 5),
            JellyPlayAnalyticsDay(day = "2026-10-02", plays = 2),
        ),
        perUser = listOf(
            JellyPlayAnalyticsUser(userId = "u-1", userName = "alice", plays = 30, playSeconds = 30_000, transcodeSeconds = 1_800),
        ),
        topItems = listOf(
            JellyPlayAnalyticsTopItem(itemId = "i-1", itemName = "Movie.mkv", itemType = "Movie", plays = 12, playSeconds = 40_000),
        ),
    )

    private val transcodeSession = JellyPlayAnalyticsSession(
        userId = "u-1",
        itemId = "i-1",
        itemName = "Movie.mkv",
        itemType = "Movie",
        playMethod = "Transcode",
        videoCodec = "h264",
        audioCodec = "aac",
        bitrate = 8_000_000,
        transcodeReasons = listOf("VideoCodecNotSupported"),
        positionTicks = 600_000_000,
        startedAt = 1_000,
        endedAt = 2_000,
        clientName = "JellyPlay Android",
        deviceName = "Pixel",
    )

    private val directSession = JellyPlayAnalyticsSession(
        userId = "u-2",
        itemId = "i-2",
        itemName = "Song.flac",
        itemType = "Audio",
        playMethod = "DirectPlay",
        positionTicks = 0,
        startedAt = 3_000,
        endedAt = 4_000,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        pluginApi = mockk()
        capabilitiesApi = mockk()
        sessionCacheRegistry = mockk(relaxed = true)
        every { sessionCacheRegistry.registerAction(any(), capture(resetAction)) } just runs
        statusStore = JellyPlayPluginStatusStore(capabilitiesApi, sessionCacheRegistry)
    }

    /** Fires the store's captured identity-reset action — the gate drops to Unknown. */
    private suspend fun simulateIdentityReset() {
        resetAction.captured.invoke(HomeSessionTransition.UserSwitched(SessionIdentity("srv-1", "user-2")))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Probes the real store: the capabilities stub decides AVAILABLE + feature set. */
    private suspend fun makeStoreAvailable(features: List<String> = defaultFeatures()) {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.success(
            JellyPlayCapabilities(
                contractVersion = 1,
                pluginVersion = "1.0.0",
                features = features,
            ),
        )
        statusStore.refresh()
    }

    private fun defaultFeatures(): List<String> =
        listOf("settings-sync", JellyPlayPluginFeatures.Analytics)

    private fun stubOverview(payload: JellyPlayAnalyticsOverview? = overview) {
        coEvery { pluginApi.getAnalyticsOverview(any()) } returns Result.success(payload)
    }

    private fun stubSessions(sessions: List<JellyPlayAnalyticsSession> = listOf(transcodeSession, directSession)) {
        coEvery { pluginApi.getAnalyticsSessions(any(), any(), any()) } returns
            Result.success(JellyPlayAnalyticsSessions(sessions = sessions))
    }

    private suspend fun TestScope.loadedViewModel(
        overviewPayload: JellyPlayAnalyticsOverview? = overview,
        sessions: List<JellyPlayAnalyticsSession> = listOf(transcodeSession, directSession),
    ): JellyPlayAnalyticsViewModel {
        // The gate-open probe is part of the harness: without it the store
        // stays UNKNOWN and the VM never loads.
        makeStoreAvailable()
        stubOverview(overviewPayload)
        stubSessions(sessions)
        return JellyPlayAnalyticsViewModel(pluginApi, statusStore).also { advanceUntilIdle() }
    }

    // ── gating ──

    @Test
    fun `plugin unavailable gates the screen off and never fetches analytics`() = runTest(mainDispatcher) {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.failure(RuntimeException("plugin absent"))
        statusStore.refresh()

        val viewModel = JellyPlayAnalyticsViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        assertEquals(JellyPlayCapabilityState.Unavailable, viewModel.uiState.value.gate)
        // The screen renders the gate face; no load ever ran, so the phase
        // never left its unobserved initial (adapted from the former
        // `assertFalse(isLoading)` — the phase machine has no flag to clear).
        assertTrue(viewModel.uiState.value.loadPhase is AnalyticsLoadPhase.Loading)
        coVerify(exactly = 0) { pluginApi.getAnalyticsOverview(any()) }
        coVerify(exactly = 0) { pluginApi.getAnalyticsSessions(any(), any(), any()) }
    }

    @Test
    fun `available plugin without the analytics feature stays gated off`() = runTest(mainDispatcher) {
        makeStoreAvailable(features = listOf("settings-sync"))

        val viewModel = JellyPlayAnalyticsViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        assertEquals(JellyPlayCapabilityState.Unavailable, viewModel.uiState.value.gate)
        coVerify(exactly = 0) { pluginApi.getAnalyticsOverview(any()) }
        coVerify(exactly = 0) { pluginApi.getAnalyticsSessions(any(), any(), any()) }
    }

    @Test
    fun `a null status store defaults to gated off with no requests at all`() = runTest(mainDispatcher) {
        val viewModel = JellyPlayAnalyticsViewModel(pluginApi, statusStore = null)
        advanceUntilIdle()

        assertEquals(JellyPlayCapabilityState.Unavailable, viewModel.uiState.value.gate)
        // Unobserved initial phase (adapted from the former `assertFalse(isLoading)`).
        assertTrue(viewModel.uiState.value.loadPhase is AnalyticsLoadPhase.Loading)
        coVerify(exactly = 0) { capabilitiesApi.getCapabilities() }
        coVerify(exactly = 0) { pluginApi.getAnalyticsOverview(any()) }
    }

    // ── load + mapping ──

    @Test
    fun `available plugin with the analytics feature loads the mapped overview and sessions`() =
        runTest(mainDispatcher) {
            val viewModel = loadedViewModel()

            val state = viewModel.uiState.value
            assertEquals(JellyPlayCapabilityState.Available, state.gate)
            val loadedPhase = state.loadPhase as AnalyticsLoadPhase.Loaded
            assertFalse(loadedPhase.degraded)

            val loaded = loadedPhase.overview!!
            assertEquals(42, loaded.totals.plays)
            assertEquals("12h 30m", loaded.totals.watchTimeLabel)
            assertEquals("1h", loaded.totals.transcodeTimeLabel)
            assertEquals(3, loaded.totals.uniqueUsers)
            assertEquals(17, loaded.totals.uniqueItems)

            // The chart feed rides the statistics charts' point type.
            assertEquals(listOf("2026-10-01" to 5L, "2026-10-02" to 2L), loaded.perDay.map { it.date to it.value })

            val user = loaded.perUser.single()
            assertEquals("alice", user.userName)
            assertEquals(30, user.plays)
            assertEquals("8h 20m", user.watchTimeLabel)
            assertEquals("30m", user.transcodeTimeLabel)

            val topItem = loaded.topItems.single()
            assertEquals("Movie.mkv", topItem.itemName)
            assertEquals("Movie", topItem.itemType)
            assertEquals(12, topItem.plays)
            assertEquals("11h 6m", topItem.watchTimeLabel)

            // The initial sessions fetch is unscoped (no user filter, no cursor).
            coVerify(exactly = 1) { pluginApi.getAnalyticsSessions(null, null, 50) }
            assertEquals(2, state.sessions.size)

            val transcoding = state.sessions[0]
            assertEquals(TranscodePlayMethod.TRANSCODE, transcoding.playMethod)
            assertEquals("8 Mbps", transcoding.bitrateLabel)
            assertEquals("1m", transcoding.watchedLabel)
            assertEquals(listOf("VideoCodecNotSupported"), transcoding.transcodeReasons)
            assertEquals("Pixel", transcoding.deviceName)

            val direct = state.sessions[1]
            assertEquals(TranscodePlayMethod.DIRECT, direct.playMethod)
            assertNull(direct.bitrateLabel)
            assertNull(direct.watchedLabel)
        }

    @Test
    fun `initial overview failure surfaces the error`() = runTest(mainDispatcher) {
        makeStoreAvailable()
        coEvery { pluginApi.getAnalyticsOverview(any()) } returns Result.failure(RuntimeException("offline"))
        stubSessions(listOf(transcodeSession))

        val viewModel = JellyPlayAnalyticsViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        // Adapted from the former `error` field + `assertFalse(isLoading)` +
        // `assertNull(overview)`: the Failed arm carries all three facts.
        val phase = viewModel.uiState.value.loadPhase
        assertTrue(phase is AnalyticsLoadPhase.Failed)
        assertEquals("offline", (phase as AnalyticsLoadPhase.Failed).message)
    }

    @Test
    fun `an overview 404 degrades quietly instead of erroring`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel(overviewPayload = null)

        // Adapted from the former `overviewDegraded` + null `overview` pair:
        // one Loaded arm, degraded as declared data.
        val phase = viewModel.uiState.value.loadPhase
        assertTrue(phase is AnalyticsLoadPhase.Loaded)
        assertTrue((phase as AnalyticsLoadPhase.Loaded).degraded)
        assertNull(phase.overview)
    }

    @Test
    fun `a sessions failure keeps the overview and stays silent`() = runTest(mainDispatcher) {
        makeStoreAvailable()
        stubOverview()
        coEvery { pluginApi.getAnalyticsSessions(any(), any(), any()) } returns
            Result.failure(RuntimeException("offline"))

        val viewModel = JellyPlayAnalyticsViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        val phase = viewModel.uiState.value.loadPhase as AnalyticsLoadPhase.Loaded
        assertFalse(viewModel.uiState.value.isLoadingSessions)
        assertTrue(viewModel.uiState.value.sessions.isEmpty())
        assertEquals(42, phase.overview?.totals?.plays)
    }

    // ── mapping corners ──

    @Test
    fun `toSessionRow resolves a foreign play method to OTHER with the raw string`() {
        val row = toSessionRow(
            JellyPlayAnalyticsSession(userId = "u-3", itemId = "i-3", playMethod = "HlsSegment"),
        )
        assertEquals(TranscodePlayMethod.OTHER, row.playMethod)
        assertEquals("HlsSegment", row.playMethodRaw)

        // Absent playMethod: OTHER with no raw string — the screen's Unknown fallback.
        val blank = toSessionRow(JellyPlayAnalyticsSession(userId = "u-4", itemId = "i-4", playMethod = ""))
        assertEquals(TranscodePlayMethod.OTHER, blank.playMethod)
        assertNull(blank.playMethodRaw)
    }

    // ── window chips ──

    @Test
    fun `selecting a days range refetches the overview with that window`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()
        viewModel.selectDays(7)
        advanceUntilIdle()

        coVerify(exactly = 1) { pluginApi.getAnalyticsOverview(7) }
        assertEquals(7, viewModel.uiState.value.days)
    }

    @Test
    fun `selecting the already-selected window is a no-op`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()

        viewModel.selectDays(30)
        advanceUntilIdle()

        coVerify(exactly = 1) { pluginApi.getAnalyticsOverview(any()) } // the initial load only
    }

    // ── user filter ──

    @Test
    fun `selecting a user re-scopes the sessions fetch and tapping again clears it`() =
        runTest(mainDispatcher) {
            val viewModel = loadedViewModel()

            viewModel.selectUser("u-1")
            advanceUntilIdle()

            assertEquals("u-1", viewModel.uiState.value.selectedUserId)
            coVerify(exactly = 1) { pluginApi.getAnalyticsSessions("u-1", null, 50) }

            viewModel.selectUser("u-1")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.selectedUserId)
            // The initial load + the toggle-off refetch both ran unscoped.
            coVerify(exactly = 2) { pluginApi.getAnalyticsSessions(null, null, 50) }
        }

    // ── pagination ──

    @Test
    fun `a full page offers load more and the next page appends with the since cursor`() =
        runTest(mainDispatcher) {
            val firstPage = List(JellyPlayAnalyticsViewModel.SESSIONS_PAGE_SIZE) { index ->
                JellyPlayAnalyticsSession(
                    userId = "u-1",
                    itemId = "i-$index",
                    itemName = "Item $index",
                    playMethod = "DirectPlay",
                    startedAt = 1_000L + index,
                    endedAt = 2_000L + index,
                )
            }
            makeStoreAvailable()
            stubOverview()
            coEvery { pluginApi.getAnalyticsSessions(any(), any(), any()) } returns
                Result.success(JellyPlayAnalyticsSessions(sessions = firstPage))

            val viewModel = JellyPlayAnalyticsViewModel(pluginApi, statusStore)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasMoreSessions)
            assertEquals(JellyPlayAnalyticsViewModel.SESSIONS_PAGE_SIZE, viewModel.uiState.value.sessions.size)

            // The cursor is the oldest endedAt of the loaded page.
            val secondPage = listOf(
                JellyPlayAnalyticsSession(
                    userId = "u-1",
                    itemId = "i-new",
                    itemName = "Newer",
                    playMethod = "DirectPlay",
                    endedAt = 5_000,
                ),
            )
            coEvery { pluginApi.getAnalyticsSessions(any(), any(), any()) } returns
                Result.success(JellyPlayAnalyticsSessions(sessions = secondPage))

            viewModel.loadMoreSessions()
            advanceUntilIdle()

            coVerify(exactly = 1) { pluginApi.getAnalyticsSessions(null, 2_000L, 50) }
            assertEquals(
                JellyPlayAnalyticsViewModel.SESSIONS_PAGE_SIZE + 1,
                viewModel.uiState.value.sessions.size,
            )
            // A short page closes the pagination.
            assertFalse(viewModel.uiState.value.hasMoreSessions)
        }

    @Test
    fun `an appended page de-duplicates a session already loaded`() = runTest(mainDispatcher) {
        val firstPage = List(JellyPlayAnalyticsViewModel.SESSIONS_PAGE_SIZE) { index ->
            JellyPlayAnalyticsSession(
                userId = "u-1",
                itemId = "i-$index",
                playMethod = "DirectPlay",
                endedAt = 2_000L + index,
            )
        }
        makeStoreAvailable()
        stubOverview()
        coEvery { pluginApi.getAnalyticsSessions(any(), any(), any()) } returns
            Result.success(JellyPlayAnalyticsSessions(sessions = firstPage))
        val viewModel = JellyPlayAnalyticsViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        // Page 2 replays the cursor-boundary session verbatim + one new one.
        coEvery { pluginApi.getAnalyticsSessions(any(), any(), any()) } returns
            Result.success(
                JellyPlayAnalyticsSessions(
                    sessions = listOf(
                        firstPage.first().copy(endedAt = 2_000L), // duplicate of the loaded oldest
                        JellyPlayAnalyticsSession(userId = "u-1", itemId = "i-new", playMethod = "DirectPlay", endedAt = 9_000),
                    ),
                ),
            )
        viewModel.loadMoreSessions()
        advanceUntilIdle()

        assertEquals(JellyPlayAnalyticsViewModel.SESSIONS_PAGE_SIZE + 1, viewModel.uiState.value.sessions.size)
    }

    @Test
    fun `load more without a full page is refused`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel()

        viewModel.loadMoreSessions()
        advanceUntilIdle()

        coVerify(exactly = 1) { pluginApi.getAnalyticsSessions(any(), any(), any()) } // the initial load only
        assertFalse(viewModel.uiState.value.hasMoreSessions)
    }

    // ── refresh ──

    @Test
    fun `refresh refetches both surfaces and a failure keeps the stale data silently`() =
        runTest(mainDispatcher) {
            val viewModel = loadedViewModel()

            coEvery { pluginApi.getAnalyticsOverview(any()) } returns Result.failure(RuntimeException("offline"))
            coEvery { pluginApi.getAnalyticsSessions(any(), any(), any()) } returns
                Result.failure(RuntimeException("offline"))
            viewModel.refresh()
            advanceUntilIdle()

            // Adapted from the former `assertNull(error)` + `assertFalse(isRefreshing)`:
            // the failure-restore rule lands the pre-refresh Loaded face back.
            val phase = viewModel.uiState.value.loadPhase
            assertTrue(phase is AnalyticsLoadPhase.Loaded)
            assertEquals(42, (phase as AnalyticsLoadPhase.Loaded).overview?.totals?.plays)
            assertEquals(2, viewModel.uiState.value.sessions.size)
        }

    // ── gate re-arm (the load latch) ──

    @Test
    fun `a gate drop re-arms the load - the next gate-on reloads`() = runTest(mainDispatcher) {
        val viewModel = loadedViewModel() // visit 1: data loaded, latch raised

        // Identity reset (user switch / plugin reinstall): the store's
        // registered action flips the status back to UNKNOWN — the gate
        // follows and the VM must re-arm its load latch.
        simulateIdentityReset()
        advanceUntilIdle()
        assertEquals(JellyPlayCapabilityState.Unknown, viewModel.uiState.value.gate)

        // The next visit's arm re-probes into a fresh gate-on. Serve a
        // DIFFERENT overview so the reload's landing is observable.
        coEvery { pluginApi.getAnalyticsOverview(any()) } returns Result.success(
            overview.copy(totals = overview.totals.copy(plays = 43)),
        )
        viewModel.start()
        advanceUntilIdle()

        assertEquals(JellyPlayCapabilityState.Available, viewModel.uiState.value.gate)
        val phase = viewModel.uiState.value.loadPhase as AnalyticsLoadPhase.Loaded
        assertEquals(43, phase.overview?.totals?.plays)
        // Before the fix the latch stayed set forever: this reload never
        // fired and the screen showed the first visit's data.
        coVerify(exactly = 2) { pluginApi.getAnalyticsOverview(any()) }
        coVerify(exactly = 2) { pluginApi.getAnalyticsSessions(any(), any(), any()) }
    }

    // ── refresh ⊕ re-filter serialization (the sessions slot) ──

    @Test
    fun `a user switch during an in-flight refresh serializes - the late refresh write never lands under the old filter`() =
        runTest(mainDispatcher) {
            val viewModel = loadedViewModel()

            // Park any further sessions fetch: refresh()'s session reset lands
            // in the TaskBundle slot and suspends there.
            val refreshGate = CompletableDeferred<Unit>()
            coEvery { pluginApi.getAnalyticsOverview(any()) } returns Result.success(overview)
            coEvery { pluginApi.getAnalyticsSessions(any(), any(), any()) } coAnswers {
                refreshGate.await()
                Result.success(
                    JellyPlayAnalyticsSessions(
                        sessions = listOf(
                            JellyPlayAnalyticsSession(
                                userId = "u-old",
                                itemId = "i-old",
                                itemName = "Old filter row",
                                playMethod = "DirectPlay",
                                endedAt = 1_000,
                            ),
                        ),
                    ),
                )
            }

            viewModel.refresh()
            testScheduler.runCurrent() // overview settled; the session reset parks in the slot

            // The user re-filters MID-refresh: cancels-and-replaces the parked
            // fetch (before the fix, refresh's untracked fetch kept running
            // and its late write landed old-filter rows under the new chip).
            coEvery { pluginApi.getAnalyticsSessions(any(), any(), any()) } returns
                Result.success(
                    JellyPlayAnalyticsSessions(
                        sessions = listOf(
                            JellyPlayAnalyticsSession(
                                userId = "u-1",
                                itemId = "i-new",
                                itemName = "New filter row",
                                playMethod = "DirectPlay",
                                endedAt = 2_000,
                            ),
                        ),
                    ),
                )
            viewModel.selectUser("u-1")
            advanceUntilIdle()

            // Release the replaced fetch's response — it must never land.
            refreshGate.complete(Unit)
            advanceUntilIdle()

            assertEquals("u-1", viewModel.uiState.value.selectedUserId)
            val sessions = viewModel.uiState.value.sessions
            assertTrue(sessions.isNotEmpty())
            assertEquals(listOf("u-1"), sessions.map { it.userId })
            assertEquals(listOf("i-new"), sessions.map { it.itemId })
            assertTrue(viewModel.uiState.value.loadPhase is AnalyticsLoadPhase.Loaded)
        }
}
