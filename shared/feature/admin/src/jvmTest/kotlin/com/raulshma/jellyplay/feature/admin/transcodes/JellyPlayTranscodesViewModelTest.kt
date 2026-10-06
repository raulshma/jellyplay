package com.raulshma.jellyplay.feature.admin.transcodes

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.network.api.JellyPlayActiveTranscode
import com.raulshma.jellyplay.core.network.api.JellyPlayCapabilities
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
 * Pins the JellyPlay companion-plugin admin transcodes flow
 * ([JellyPlayTranscodesViewModel]):
 *
 *  - gating — the screen only touches the plugin route when the real
 *    [JellyPlayPluginStatusStore] reports AVAILABLE **and** the capabilities
 *    carry the `transcodes` feature key (ADR 0010: the capability registry is
 *    the only gate; no fetches, polls or cancels while gated off);
 *  - mapping — the `playMethod` wire string resolves to Direct/Transcode,
 *    codecs ride along, the bitrate label is deterministic;
 *  - refresh — a 5s visibility-owned poll loop ([JellyPlayTranscodesViewModel.start] /
 *    [JellyPlayTranscodesViewModel.stop]) whose ticks are silent on failure;
 *  - cancel — confirm-dialog machine routes to `cancelTranscode`, reloads, and
 *    refuses a dismiss while the request is in flight.
 *
 * The store is the REAL [JellyPlayPluginStatusStore] over the same mocked
 * [JellyPlayPluginApiClient] the ViewModel reads — gating flows through the
 * actual probe, exactly as it does in production. The registry is relaxed
 * (the store only registers its session-reset action into it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlayTranscodesViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var pluginApi: JellyPlayPluginApiClient
    private lateinit var sessionCacheRegistry: SessionCacheRegistry
    private lateinit var statusStore: JellyPlayPluginStatusStore

    private val transcoding = JellyPlayActiveTranscode(
        sessionId = "s-1",
        userName = "alice",
        deviceName = "Living Room TV",
        itemName = "Movie.mkv",
        videoCodec = "h264",
        audioCodec = "aac",
        playMethod = "Transcode",
        videoBitrate = 8_000_000,
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
        Dispatchers.setMain(mainDispatcher)
        pluginApi = mockk()
        sessionCacheRegistry = mockk(relaxed = true)
        statusStore = JellyPlayPluginStatusStore(pluginApi, sessionCacheRegistry)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Probes the real store: the capabilities stub decides AVAILABLE + feature set. */
    private suspend fun makeStoreAvailable(features: List<String> = defaultFeatures()) {
        coEvery { pluginApi.getCapabilities() } returns Result.success(
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
    fun `plugin unavailable gates the list off and never fetches transcodes`() = runTest(mainDispatcher) {
        coEvery { pluginApi.getCapabilities() } returns Result.failure(RuntimeException("plugin absent"))
        statusStore.refresh()

        val viewModel = JellyPlayTranscodesViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        assertEquals(TranscodesGate.Unavailable, viewModel.uiState.value.gate)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { pluginApi.getActiveTranscodes() }
    }

    @Test
    fun `available plugin without the transcodes feature stays gated off`() = runTest(mainDispatcher) {
        makeStoreAvailable(features = listOf("settings-sync"))

        val viewModel = JellyPlayTranscodesViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        assertEquals(TranscodesGate.Unavailable, viewModel.uiState.value.gate)
        coVerify(exactly = 0) { pluginApi.getActiveTranscodes() }
    }

    @Test
    fun `a null status store defaults to gated off with no requests at all`() = runTest(mainDispatcher) {
        val viewModel = JellyPlayTranscodesViewModel(pluginApi, statusStore = null)
        advanceUntilIdle()

        assertEquals(TranscodesGate.Unavailable, viewModel.uiState.value.gate)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { pluginApi.getCapabilities() }
        coVerify(exactly = 0) { pluginApi.getActiveTranscodes() }
    }

    // ── load + mapping ──

    @Test
    fun `available plugin with the transcodes feature loads mapped rows`() = runTest(mainDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()

        val state = viewModel.uiState.value
        assertEquals(TranscodesGate.Available, state.gate)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.rows.size)

        val transcodingRow = state.rows[0]
        assertEquals("s-1", transcodingRow.sessionId)
        assertEquals("alice", transcodingRow.userName)
        assertEquals("Living Room TV", transcodingRow.deviceName)
        assertEquals("Movie.mkv", transcodingRow.itemName)
        assertTrue(transcodingRow.isTranscoding)
        assertEquals(TranscodePlayMethod.TRANSCODE, transcodingRow.playMethod)
        assertEquals("h264", transcodingRow.videoCodec)
        assertEquals("aac", transcodingRow.audioCodec)
        assertEquals("8 Mbps", transcodingRow.bitrateLabel)
        assertFalse(transcodingRow.isPaused)

        val directRow = state.rows[1]
        assertFalse(directRow.isTranscoding)
        assertEquals(TranscodePlayMethod.DIRECT, directRow.playMethod)
        assertTrue(directRow.isPaused)
        assertEquals("900 kbps", directRow.bitrateLabel)
    }

    @Test
    fun `initial load failure surfaces the error`() = runTest(mainDispatcher) {
        makeStoreAvailable()
        coEvery { pluginApi.getActiveTranscodes() } returns Result.failure(RuntimeException("offline"))

        val viewModel = JellyPlayTranscodesViewModel(pluginApi, statusStore)
        advanceUntilIdle()

        assertEquals("offline", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.rows.isEmpty())
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

    // ── auto-refresh loop ──

    @Test
    fun `auto refresh polls every five seconds while started and stops on stop`() =
        runTest(mainDispatcher) {
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
            assertEquals(listOf("s-1"), viewModel.uiState.value.rows.map { it.sessionId })
        }

    @Test
    fun `a failed poll keeps the stale list silently`() = runTest(mainDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()
        coEvery { pluginApi.getActiveTranscodes() } returns Result.failure(RuntimeException("offline"))

        viewModel.start()
        advanceTimeBy(JellyPlayTranscodesViewModel.REFRESH_INTERVAL_MS)
        runCurrent()
        viewModel.stop()

        assertEquals(2, viewModel.uiState.value.rows.size)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `the poll loop never fetches while gated off`() = runTest(mainDispatcher) {
        coEvery { pluginApi.getCapabilities() } returns Result.failure(RuntimeException("plugin absent"))
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
    fun `start is idempotent - a second call does not stack a second loop`() = runTest(mainDispatcher) {
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
    fun `cancel routes to the api clears the dialog and reloads`() = runTest(mainDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()
        coEvery { pluginApi.cancelTranscode("s-1") } returns Result.success(Unit)

        viewModel.showCancelDialog(viewModel.uiState.value.rows[0])
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
    fun `cancel without a selection is a no-op`() = runTest(mainDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()

        viewModel.cancelTranscode()
        advanceUntilIdle()

        coVerify(exactly = 0) { pluginApi.cancelTranscode(any()) }
    }

    @Test
    fun `dismiss during an in-flight cancel retains the pending row`() = runTest(mainDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()
        // Park the cancel mid-flight so isCancelling stays raised.
        val gate = CompletableDeferred<Unit>()
        coEvery { pluginApi.cancelTranscode("s-1") } coAnswers { gate.await(); Result.success(Unit) }

        viewModel.showCancelDialog(viewModel.uiState.value.rows[0])
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
    fun `a second confirm while a cancel is in flight is refused`() = runTest(mainDispatcher) {
        makeStoreAvailable()
        val viewModel = loadedViewModel()
        val gate = CompletableDeferred<Unit>()
        coEvery { pluginApi.cancelTranscode("s-1") } coAnswers { gate.await(); Result.success(Unit) }

        viewModel.showCancelDialog(viewModel.uiState.value.rows[0])
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
