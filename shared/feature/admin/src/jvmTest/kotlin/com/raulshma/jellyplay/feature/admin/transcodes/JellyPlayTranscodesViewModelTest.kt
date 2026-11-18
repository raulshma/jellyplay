package com.raulshma.jellyplay.feature.admin.transcodes

import com.raulshma.jellyplay.core.data.session.HomeSessionTransition
import com.raulshma.jellyplay.core.data.session.JellyPlayCapabilityState
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionIdentity
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.JellyPlayActiveTranscode
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilitiesRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlayTranscodesRoutes
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule

/**
 * Pins the JellyPlay companion-plugin admin transcodes flow
 * ([JellyPlayTranscodesViewModel]):
 *
 *  - gating — the screen only touches the plugin route when the real
 *    [JellyPlayPluginStatusStore] reports AVAILABLE **and** the capabilities
 *    carry the `transcodes` feature key (the capability registry is
 *    the only gate; no fetches, polls or cancels while gated off);
 *  - mapping — the `playMethod` wire string resolves to Direct/Transcode,
 *    codecs ride along, the bitrate label is deterministic;
 *  - refresh — a 5s visibility-owned poll loop ([JellyPlayTranscodesViewModel.start] /
 *    [JellyPlayTranscodesViewModel.stop]) whose ticks are silent on failure;
 *  - cancel — confirm-dialog machine routes to `cancelTranscode`, reloads, and
 *    refuses a dismiss while the request is in flight.
 *
 * The store is the REAL [JellyPlayPluginStatusStore] over the same mocked
 * [JellyPlayTranscodesRoutes] role the ViewModel reads — gating flows through the
 * actual probe, exactly as it does in production. The registry is relaxed
 * (the store only registers its session-reset action into it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlayTranscodesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var pluginApi: JellyPlayTranscodesRoutes
    private lateinit var capabilitiesApi: JellyPlayCapabilitiesRoutes
    private lateinit var sessionCacheRegistry: SessionCacheRegistry
    private lateinit var statusStore: JellyPlayPluginStatusStore

    /**
     * The store's registered identity-reset action, captured from the relaxed
     * registry mock — firing it IS the production identity reset (user switch
     * / plugin reinstall): it flips the store back to UNKNOWN so the next
     * [JellyPlayTranscodesViewModel.start] re-probes into a fresh gate-on.
     */
    private val resetAction = slot<suspend (HomeSessionTransition) -> Unit>()

    private val transcoding = JellyPlayActiveTranscode(
        sessionId = "s-1",
        userName = "alice",
        deviceName = "Living Room TV",
        itemName = "Movie.mkv",
        videoCodec = "h264",
        audioCodec = "aac",
        playMethod = "Transcode",
        videoBitrate = 8_000_000,
        transcodeReasons = listOf("VideoCodecNotSupported", "AudioBitrateNotSupported"),
        positionTicks = 600_000_000,
        isPaused = false,
    )

    private val direct = JellyPlayActiveTranscode(
        sessionId = "s-2",
        userName = "bob",
        deviceName = "Pixel",
        itemName = "Song.flac",
        playMethod = "DirectPlay",
        videoBitrate = 900_000,
        isPaused = true,
    )

    @BeforeTest
    fun setUp() {
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
        listOf("settings-sync", JellyPlayPluginFeatures.Transcodes)

    private fun TestScope.loadedViewModel(
        rows: List<JellyPlayActiveTranscode> = listOf(transcoding, direct),
    ): JellyPlayTranscodesViewModel {
        coEvery { pluginApi.getActiveTranscodes() } returns Result.success(rows)
        return JellyPlayTranscodesViewModel(pluginApi, statusStore).also { advanceUntilIdle() }
    }

    // ── gating ──

    @Test
    fun `plugin unavailable gates the list off and never fetches transcodes`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.failure(RuntimeException("plugin absent"))
        statusStore.refresh()

        val viewModel = JellyPlayTranscodesViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        assertEquals(JellyPlayCapabilityState.Unavailable, viewModel.uiState.value.gate)
        // The screen renders the gate face; no load ever ran, so the phase
        // never left its unobserved initial (adapted from the former
        // `assertFalse(isLoading)` — the phase machine has no flag to clear).
        assertTrue(viewModel.uiState.value.loadPhase is TranscodesLoadPhase.Loading)
        coVerify(exactly = 0) { pluginApi.getActiveTranscodes() }
    }

    @Test
    fun `available plugin without the transcodes feature stays gated off`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable(features = listOf("settings-sync"))

        val viewModel = JellyPlayTranscodesViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        assertEquals(JellyPlayCapabilityState.Unavailable, viewModel.uiState.value.gate)
        coVerify(exactly = 0) { pluginApi.getActiveTranscodes() }
    }

    @Test
    fun `a null status store defaults to gated off with no requests at all`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = JellyPlayTranscodesViewModel(pluginApi, statusStore = null)
        advanceUntilIdle()

        assertEquals(JellyPlayCapabilityState.Unavailable, viewModel.uiState.value.gate)
        // Unobserved initial phase (adapted from the former `assertFalse(isLoading)`).
        assertTrue(viewModel.uiState.value.loadPhase is TranscodesLoadPhase.Loading)
        coVerify(exactly = 0) { capabilitiesApi.getCapabilities() }
        coVerify(exactly = 0) { pluginApi.getActiveTranscodes() }
    }

    // ── load + mapping ──

    @Test
    fun `available plugin with the transcodes feature loads mapped rows`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()

        val state = viewModel.uiState.value
        assertEquals(JellyPlayCapabilityState.Available, state.gate)
        val rows = (state.loadPhase as TranscodesLoadPhase.Loaded).rows
        assertEquals(2, rows.size)

        val transcodingRow = rows[0]
        assertEquals("s-1", transcodingRow.sessionId)
        assertEquals("alice", transcodingRow.userName)
        assertEquals("Living Room TV", transcodingRow.deviceName)
        assertEquals("Movie.mkv", transcodingRow.itemName)
        assertTrue(transcodingRow.isTranscoding)
        assertEquals(TranscodePlayMethod.TRANSCODE, transcodingRow.playMethod)
        assertEquals("h264", transcodingRow.videoCodec)
        assertEquals("aac", transcodingRow.audioCodec)
        assertEquals("8 Mbps", transcodingRow.bitrateLabel)
        assertEquals(listOf("VideoCodecNotSupported", "AudioBitrateNotSupported"), transcodingRow.transcodeReasons)
        assertFalse(transcodingRow.isPaused)

        val directRow = rows[1]
        assertFalse(directRow.isTranscoding)
        assertEquals(TranscodePlayMethod.DIRECT, directRow.playMethod)
        assertTrue(directRow.isPaused)
        assertEquals("900 kbps", directRow.bitrateLabel)
    }

    @Test
    fun `initial load failure surfaces the error`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable()
        coEvery { pluginApi.getActiveTranscodes() } returns Result.failure(RuntimeException("offline"))

        val viewModel = JellyPlayTranscodesViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        // Adapted from the former `error` field + `assertFalse(isLoading)` +
        // `assertTrue(rows.isEmpty())`: the Failed arm carries all three facts.
        val phase = viewModel.uiState.value.loadPhase
        assertTrue(phase is TranscodesLoadPhase.Failed)
        assertEquals("offline", (phase as TranscodesLoadPhase.Failed).message)
    }

    @Test
    fun `toRow resolves a foreign play method to OTHER with the raw string`() {
        val row = toRow(
            JellyPlayActiveTranscode(sessionId = "s-3", playMethod = "HlsSegment"),
        )
        assertEquals(TranscodePlayMethod.OTHER, row.playMethod)
        assertEquals("HlsSegment", row.playMethodRaw)
        assertFalse(row.isTranscoding)

        // Absent playMethod: OTHER with no raw string — the screen's Unknown fallback.
        val blank = toRow(JellyPlayActiveTranscode(sessionId = "s-4", playMethod = null))
        assertEquals(TranscodePlayMethod.OTHER, blank.playMethod)
        assertNull(blank.playMethodRaw)
    }

    @Test
    fun `bitrate formatting is deterministic`() {
        assertEquals("8 Mbps", formatBitrate(8_000_000))
        assertEquals("4.5 Mbps", formatBitrate(4_500_000))
        assertEquals("12.3 Mbps", formatBitrate(12_340_000))
        assertEquals("800 kbps", formatBitrate(800_000))
        assertEquals("0 kbps", formatBitrate(0))
    }

    @Test
    fun `reason names humanize into readable clauses`() {
        assertEquals("video codec not supported", "VideoCodecNotSupported".humanizeTranscodeReason())
        assertEquals("direct play error", "DirectPlayError".humanizeTranscodeReason())
        assertEquals("container bitrate exceeds limit", "ContainerBitrateExceedsLimit".humanizeTranscodeReason())
    }

    // ── auto-refresh loop ──

    @Test
    fun `auto refresh polls every five seconds while started and stops on stop`() =
        runTest(mainDispatcherRule.testDispatcher) {
            makeStoreAvailable()
            val viewModel = loadedViewModel() // fetch #1 — the initial load
            coEvery { pluginApi.getActiveTranscodes() } returns Result.success(listOf(transcoding))

            viewModel.start()
            advanceTimeBy(JellyPlayTranscodesViewModel.REFRESH_INTERVAL_MS)
            runCurrent() // fetch #2
            advanceTimeBy(JellyPlayTranscodesViewModel.REFRESH_INTERVAL_MS)
            runCurrent() // fetch #3
            coVerify(exactly = 3) { pluginApi.getActiveTranscodes() }

            viewModel.stop()
            advanceUntilIdle() // a stopped loop schedules nothing further
            coVerify(exactly = 3) { pluginApi.getActiveTranscodes() }
            // Adapted: rows live on the Loaded phase arm.
            val rows = (viewModel.uiState.value.loadPhase as TranscodesLoadPhase.Loaded).rows
            assertEquals(listOf("s-1"), rows.map { it.sessionId })
        }

    @Test
    fun `a failed poll keeps the stale list silently`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()
        coEvery { pluginApi.getActiveTranscodes() } returns Result.failure(RuntimeException("offline"))

        viewModel.start()
        advanceTimeBy(JellyPlayTranscodesViewModel.REFRESH_INTERVAL_MS)
        runCurrent()
        viewModel.stop()

        // Adapted from the former `assertEquals(2, rows)` + `assertNull(error)` +
        // `assertFalse(isRefreshing)`: the phase is untouched by the failed poll.
        val phase = viewModel.uiState.value.loadPhase
        assertTrue(phase is TranscodesLoadPhase.Loaded)
        assertEquals(2, (phase as TranscodesLoadPhase.Loaded).rows.size)
    }

    // ── gate re-arm (the load latch) ──

    @Test
    fun `a gate drop re-arms the load - the next gate-on reloads`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel() // visit 1: rows loaded, latch raised

        // Identity reset (user switch / plugin reinstall): the store's
        // registered action flips the status back to UNKNOWN — the gate
        // follows and the VM must re-arm its load latch.
        simulateIdentityReset()
        advanceUntilIdle()
        assertEquals(JellyPlayCapabilityState.Unknown, viewModel.uiState.value.gate)

        // The next visit's arm re-probes into a fresh gate-on (start also
        // starts the auto-refresh cycle — advance only to 1s, the tick is at
        // 5s, so the loop cannot fire mid-test).
        coEvery { pluginApi.getActiveTranscodes() } returns Result.success(listOf(transcoding))
        viewModel.start()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(JellyPlayCapabilityState.Available, viewModel.uiState.value.gate)
        val rows = (viewModel.uiState.value.loadPhase as TranscodesLoadPhase.Loaded).rows
        assertEquals(listOf("s-1"), rows.map { it.sessionId })
        // Before the fix the latch stayed set forever: this reload never
        // fired and the screen showed the first visit's rows.
        coVerify(exactly = 2) { pluginApi.getActiveTranscodes() }

        viewModel.stop() // kill the auto-refresh cycle before the test closes
        advanceUntilIdle()
    }

    @Test
    fun `the poll loop never fetches while gated off`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { capabilitiesApi.getCapabilities() } returns Result.failure(RuntimeException("plugin absent"))
        statusStore.refresh()
        val viewModel = JellyPlayTranscodesViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        viewModel.start()
        advanceTimeBy(JellyPlayTranscodesViewModel.REFRESH_INTERVAL_MS * 3)
        runCurrent()
        viewModel.stop()

        coVerify(exactly = 0) { pluginApi.getActiveTranscodes() }
    }

    @Test
    fun `start is idempotent - a second call does not stack a second loop`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()

        viewModel.start()
        viewModel.start()
        advanceTimeBy(JellyPlayTranscodesViewModel.REFRESH_INTERVAL_MS)
        runCurrent()
        viewModel.stop()

        // Initial load + exactly ONE poll tick, not two.
        coVerify(exactly = 2) { pluginApi.getActiveTranscodes() }
    }

    // ── cancel flow ──

    @Test
    fun `cancel routes to the api clears the dialog and reloads`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()
        coEvery { pluginApi.cancelTranscode("s-1") } returns Result.success(Unit)

        viewModel.showCancelDialog((viewModel.uiState.value.loadPhase as TranscodesLoadPhase.Loaded).rows[0])
        assertTrue(viewModel.cancelConfirmation.isPending)

        viewModel.cancelTranscode()
        advanceUntilIdle()

        coVerify(exactly = 1) { pluginApi.cancelTranscode("s-1") }
        assertFalse(viewModel.cancelConfirmation.isPending)
        assertNull(viewModel.cancelConfirmation.item)
        // cancelTranscode ends with a silent reload.
        coVerify(exactly = 2) { pluginApi.getActiveTranscodes() }
    }

    @Test
    fun `cancel without a selection is a no-op`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()

        viewModel.cancelTranscode()
        advanceUntilIdle()

        coVerify(exactly = 0) { pluginApi.cancelTranscode(any()) }
    }

    @Test
    fun `dismiss during an in-flight cancel retains the pending row`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()
        // Park the cancel mid-flight so isCancelling stays raised.
        val gate = CompletableDeferred<Unit>()
        coEvery { pluginApi.cancelTranscode("s-1") } coAnswers { gate.await(); Result.success(Unit) }

        viewModel.showCancelDialog((viewModel.uiState.value.loadPhase as TranscodesLoadPhase.Loaded).rows[0])
        viewModel.cancelTranscode()
        runCurrent() // run the launch until it suspends on the gate
        assertTrue(viewModel.uiState.value.isCancelling)

        viewModel.dismissCancelDialog()

        // The host refuses a dismiss while in flight — the dialog stays open.
        assertTrue(viewModel.cancelConfirmation.isPending)

        gate.complete(Unit)
        advanceUntilIdle()
        // Settle arm: clears on BOTH outcomes.
        assertNull(viewModel.cancelConfirmation.item)
        assertFalse(viewModel.cancelConfirmation.isPending)
    }

    @Test
    fun `a second confirm while a cancel is in flight is refused`() = runTest(mainDispatcherRule.testDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()
        val gate = CompletableDeferred<Unit>()
        coEvery { pluginApi.cancelTranscode("s-1") } coAnswers { gate.await(); Result.success(Unit) }

        viewModel.showCancelDialog((viewModel.uiState.value.loadPhase as TranscodesLoadPhase.Loaded).rows[0])
        viewModel.cancelTranscode()
        runCurrent()
        assertTrue(viewModel.uiState.value.isCancelling)

        viewModel.cancelTranscode()

        coVerify(exactly = 1) { pluginApi.cancelTranscode(any()) }
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.cancelConfirmation.item)
    }
}
