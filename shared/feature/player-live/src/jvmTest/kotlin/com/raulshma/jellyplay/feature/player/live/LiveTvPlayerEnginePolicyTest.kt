package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.feature.player.live.data.LastChannelStore
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineFactory
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import com.raulshma.jellyplay.feature.player.live.engine.LivePlaybackRequest
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_buffering_timeout
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the SHARED engine-event policy core (player-contract's
 * [com.raulshma.jellyplay.feature.player.video.engine.EngineEventCoordinator],
 * consumed by this VM since candidate C4) drives live's behavior end to end —
 * raw engine flows in through the VM's [com.raulshma.jellyplay.feature.player.video.engine.EngineEventSource],
 * [com.raulshma.jellyplay.feature.player.video.engine.EngineDecision]s out
 * through the VM's execution:
 *
 *  1. the 20 s buffering watchdog (this host's EVERY_BUFFERING_EPISODE pin —
 *     it fires on mid-playback stalls, unlike the VOD player's
 *     initial-buffer-only scope) lands as a ShowError decision and surfaces
 *     `live_error_buffering_timeout` on a fully virtual clock;
 *  2. the engine's transcode-fallback callback rides the coordinator's
 *     external intake and lands as a FallbackToTranscode decision, executed
     *     as the TRANSCODE re-resolve + reload;
 *  3. the VM does NOT latch the fallback (the engine's per-load phase machine
 *     owns the one-shot counting — a deliberate divergence from the VOD
 *     FORCE_DIRECT_PLAY one-shot latch);
 *  4. stop() disposes the coordinator so post-teardown engine flips stay mute.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvPlayerEnginePolicyTest {

    /**
     * The clock the VM's coroutines (and the coordinator's watchdog) run on:
     * viewModelScope inherits Main, an [UnconfinedTestDispatcher] bound to
     * THIS scheduler — so the coordinator's `delay(20_000)` fires via
     * [TestCoroutineScheduler.advanceTimeBy] with no real waiting.
     */
    private val scheduler = TestCoroutineScheduler()

    private lateinit var liveTvRepo: LiveTvRepository
    private lateinit var playbackRepo: PlaybackRepository
    private lateinit var playbackIdentity: PlaybackIdentity
    private lateinit var appRuntimeStateStore: AppRuntimeStateStore
    private lateinit var playbackStore: PlaybackStore
    private lateinit var aggregateStore: VideoPlayerAggregateStore
    private lateinit var lastChannelStore: LastChannelStore
    private lateinit var fakeEngine: LivePlayerEngine
    private lateinit var imageUrlProvider: com.raulshma.jellyplay.core.data.util.ImageUrlProvider

    private val capturedRequests = mutableListOf<LivePlaybackRequest>()
    private val resolveOptions = mutableListOf<LiveStreamOption>()
    private val appRuntimeFlow = MutableStateFlow(AppRuntimeState())
    private val playbackFlow = MutableStateFlow(PlaybackSlice())
    private val engineStateFlow = MutableStateFlow(LiveEngineState.IDLE)
    private val engineIsPlayingFlow = MutableStateFlow(false)

    /** The engine-failure callback the VM hands the factory; invoked by tests. */
    private var onTranscodeFallback: (() -> Unit)? = null

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        liveTvRepo = mockk(relaxed = true)
        playbackRepo = mockk(relaxed = true)
        playbackIdentity = mockk(relaxed = true)
        appRuntimeStateStore = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        aggregateStore = mockk(relaxed = true)
        every { aggregateStore.aggregate } returns MutableStateFlow(VideoPlayerAggregate())
        lastChannelStore = mockk(relaxed = true)
        fakeEngine = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)

        every { lastChannelStore.observeLastChannelId() } returns flowOf(null)
        every { appRuntimeStateStore.state } returns appRuntimeFlow
        every { playbackStore.playback } returns playbackFlow
        every { playbackIdentity.accessToken() } returns "tok"
        every { fakeEngine.state } returns engineStateFlow
        every { fakeEngine.isPlaying } returns engineIsPlayingFlow
        every { fakeEngine.isAtLiveEdge } returns MutableStateFlow(true)
        every { fakeEngine.positionMs } returns MutableStateFlow(0L)
        every { fakeEngine.durationMs } returns MutableStateFlow(-1L)
        every { fakeEngine.errorDetail } returns MutableStateFlow(null)
        every { fakeEngine.errorMessage } returns MutableStateFlow(null)
        every { fakeEngine.load(any()) } answers { capturedRequests.add(firstArg()) }

        coEvery { liveTvRepo.getLiveTvPrograms(any(), any(), any()) } returns
            Result.success(emptyList<LiveTvProgram>())
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            // Record the live-stream option each resolve runs under.
            resolveOptions += arg<LiveStreamOption>(8)
            ResolvedPlayback(
                mediaSourceId = "src",
                streamUrl = "https://srv/Videos/x/stream",
                playMethod = PlayMethod.DIRECT_STREAM,
                playSessionId = "psid",
                maxStreamingBitrate = null,
            )
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun channels(count: Int) = (0 until count).map {
        LiveTvChannel(id = "ch-$it", name = "Channel $it")
    }

    private fun createVm(): LiveTvPlayerViewModel = LiveTvPlayerViewModel(
        liveTvRepository = liveTvRepo,
        playbackRepository = playbackRepo,
        playbackIdentity = playbackIdentity,
        appRuntimeStateStore = appRuntimeStateStore,
        playbackStore = playbackStore,
        aggregateStore = aggregateStore,
        lastChannelStore = lastChannelStore,
        // The VM's injected wall clock (LiveNowWindow seam) — pinned; this
        // suite never stubs programs, so any fixed value keeps the scan quiet.
        epochMillisSource = EpochMillisSource { 0L },
        engineFactory = LiveEngineFactory { _, onFallback ->
            onTranscodeFallback = onFallback
            fakeEngine
        },
        imageUrlProvider = imageUrlProvider,
        pip = null,
    )

    private fun tune(): LiveTvPlayerViewModel {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(2))
        val vm = createVm()
        vm.onEvent(LiveTvPlayerUiEvent.Initialize("ch-0", null, null))
        scheduler.runCurrent()
        return vm
    }

    // ── 1. Watchdog decisions flow through the shared coordinator ────────────

    @Test
    fun `watchdog timeout decision surfaces the buffering-timeout error through the VM`() = runTest {
        val vm = tune()
        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.runCurrent()

        // Just under the 20 s knob: still buffering, no error.
        scheduler.advanceTimeBy(19_999L)
        scheduler.runCurrent()
        assertNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.isBuffering)

        // Past the knob: the coordinator's ShowError(clearBuffering) decision
        // lifts the spinner and surfaces the retryable timeout.
        scheduler.advanceTimeBy(2_000L)
        scheduler.runCurrent()
        assertFalse(vm.state.value.isBuffering, "the stuck rebuffer spinner must lift with the error")
        val error = vm.state.value.errorMessage
        assertTrue(error is LivePlayerMessage.Resource, "expected Resource error, was $error")
        assertEquals(
            Res.string.live_error_buffering_timeout,
            (error as LivePlayerMessage.Resource).res,
        )
    }

    @Test
    fun `watchdog fires on a mid-playback stall — the every-episode scope pin`() = runTest {
        val vm = tune()
        // First episode recovers: BUFFERING → READY.
        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.advanceTimeBy(1_000L)
        engineStateFlow.value = LiveEngineState.READY
        scheduler.runCurrent()

        // A MID-PLAYBACK stall (tuner lost signal after the stream was up)
        // must trip the watchdog: live pins the every-episode scope, unlike
        // the VOD player's initial-buffer-only scope.
        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.runCurrent()
        scheduler.advanceTimeBy(20_000L)
        scheduler.runCurrent()

        val error = vm.state.value.errorMessage
        assertTrue(error is LivePlayerMessage.Resource, "expected the timeout error, was $error")
        assertEquals(
            Res.string.live_error_buffering_timeout,
            (error as LivePlayerMessage.Resource).res,
        )
    }

    @Test
    fun `watchdog window survives a channel zap without a state flip`() = runTest {
        val vm = tune()
        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.runCurrent()

        // A zap mid-stall re-loads the SAME engine instance (live does not
        // swap engines per tune) and no state flip occurs, so the armed
        // window is neither cancelled nor re-armed — the original 20 s window
        // still fires. (Pins the pre-refactor behavior: the hand-rolled job
        // was keyed on state flips, not tunes.)
        vm.onEvent(LiveTvPlayerUiEvent.ChannelUp())
        scheduler.runCurrent()
        scheduler.advanceTimeBy(19_000L)
        scheduler.runCurrent()
        assertNull(vm.state.value.errorMessage, "not yet 20s since the arm")

        scheduler.advanceTimeBy(2_000L)
        scheduler.runCurrent()
        assertNotNull(vm.state.value.errorMessage, "the original window still fires")
    }

    // ── 2. Fallback decisions flow through the coordinator's intake ──────────

    @Test
    fun `engine fallback callback lands as a FallbackToTranscode decision and re-resolves with TRANSCODE`() = runTest {
        val vm = tune()
        resolveOptions.clear()
        assertNotNull(onTranscodeFallback)

        onTranscodeFallback!!.invoke()
        scheduler.runCurrent()

        // The re-resolve ran under the TRANSCODE option…
        assertEquals(listOf(LiveStreamOption.TRANSCODE), resolveOptions)
        // …and the engine reloaded the transcoded stream.
        assertEquals(2, capturedRequests.size)
        val reload = capturedRequests.last()
        assertEquals(LivePlayMethod.TRANSCODE, reload.playMethod)
        assertEquals(LivePlayMethod.TRANSCODE, vm.state.value.playMethod)
    }

    @Test
    fun `fallback is not VM-latched — the engine owns the one-shot counting`() = runTest {
        tune()
        resolveOptions.clear() // drop the initial tune's AUTO resolve
        assertNotNull(onTranscodeFallback)

        // Two engine callbacks (two tunes, or a direct failure after a fresh
        // load) must BOTH re-resolve: the VM carries no one-shot latch — the
        // VOD player's FORCE_DIRECT_PLAY latch semantics deliberately do not
        // apply here (the engine's per-load phase machine owns them).
        onTranscodeFallback!!.invoke()
        scheduler.runCurrent()
        onTranscodeFallback!!.invoke()
        scheduler.runCurrent()

        assertEquals(
            listOf(LiveStreamOption.TRANSCODE, LiveStreamOption.TRANSCODE),
            resolveOptions,
        )
        assertEquals(3, capturedRequests.size)
    }

    // ── 3. Teardown ordering ──────────────────────────────────────────────────

    @Test
    fun `stop disposes the coordinator so a post-stop stall never surfaces the timeout`() = runTest {
        val vm = tune()
        vm.stop()
        scheduler.runCurrent()

        // The engine is gone; a stray state emission on the released engine
        // must not fire the watchdog against the fresh state.
        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.runCurrent()
        scheduler.advanceTimeBy(60_000L)
        scheduler.runCurrent()

        assertNull(vm.state.value.errorMessage)
    }
}
