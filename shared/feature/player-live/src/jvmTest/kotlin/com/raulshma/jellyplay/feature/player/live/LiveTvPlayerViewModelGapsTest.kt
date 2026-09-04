package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
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
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerAudio
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.feature.player.live.engine.TranscodeReasonsRenderer
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_buffering_timeout
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_transcode_fallback
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_canceled
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_record_success
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gap suite for [LiveTvPlayerViewModel] — the branches its sibling suite
 * ([LiveTvPlayerViewModelTest]) does not reach:
 *
 *  1. the 20 s buffering watchdog, on a fully virtual clock (the VM's
     *     `viewModelScope` runs on the injected [TestCoroutineScheduler], so the timeout
 *     fires via `advanceTimeBy` — no real waiting);
 *  2. the mute toggle's pre-mute volume capture/restore contract, including
 *     the stop() reset that must never restore a stale volume onto a fresh
 *     engine;
 *  3. the in-player recording actions and their one-shot [messages] feedback
 *     (success / failure / no-current-program / no-timer guards);
 *  4. the engine-error transcode fallback (re-resolve with TRANSCODE, engine
 *     reload, failure error) and the transcode-reasons detail merge on
 *     engine ERROR;
 *  5. `setLiveStreamOption`'s optimistic state + reload;
 *  6. DVR-window guards (`playFromStart`), route-vs-stored-vs-first channel
 *     selection priority, `stop()`'s full reset + re-init, and `logoUrlFor`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvPlayerViewModelGapsTest {

    /**
     * The clock the VM's coroutines run on: [viewModelScope] inherits Main,
     * which is an [UnconfinedTestDispatcher] bound to THIS scheduler — so the
     * buffering watchdog's `delay(20_000)` and the transcode-reasons refresher's
     * wait-then-fetch delays are driven by `scheduler.advanceTimeBy`.
     */
    private val scheduler = TestCoroutineScheduler()

    private lateinit var liveTvRepo: MediaRepository
    private lateinit var playbackRepo: PlaybackRepository
    private lateinit var appRuntimeStateStore: AppRuntimeStateStore
    private lateinit var playbackStore: PlaybackStore
    private lateinit var aggregateStore: VideoPlayerAggregateStore
    private lateinit var lastChannelStore: LastChannelStore
    private lateinit var fakeEngine: LivePlayerEngine
    private lateinit var imageUrlProvider: com.raulshma.jellyplay.core.data.util.ImageUrlProvider

    private val capturedRequests = mutableListOf<LivePlaybackRequest>()
    private val appRuntimeFlow = MutableStateFlow(AppRuntimeState())
    private val playbackFlow = MutableStateFlow(PlaybackSlice())
    private val engineStateFlow = MutableStateFlow(LiveEngineState.IDLE)
    private val engineIsPlayingFlow = MutableStateFlow(false)
    private val enginePositionFlow = MutableStateFlow(0L)
    private val engineDurationFlow = MutableStateFlow(-1L)
    private val engineErrorDetailFlow = MutableStateFlow<String?>(null)
    private val engineErrorMessageFlow = MutableStateFlow<String?>(null)

    /** The engine-failure callback the VM hands the factory; invoked by tests. */
    private var onTranscodeFallback: (() -> Unit)? = null

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        liveTvRepo = mockk(relaxed = true)
        playbackRepo = mockk(relaxed = true)
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
        every { playbackRepo.getAccessToken() } returns "tok"
        every { fakeEngine.state } returns engineStateFlow
        every { fakeEngine.isPlaying } returns engineIsPlayingFlow
        every { fakeEngine.isAtLiveEdge } returns MutableStateFlow(true)
        every { fakeEngine.positionMs } returns enginePositionFlow
        every { fakeEngine.durationMs } returns engineDurationFlow
        every { fakeEngine.errorDetail } returns engineErrorDetailFlow
        every { fakeEngine.errorMessage } returns engineErrorMessageFlow
        every { fakeEngine.load(any()) } answers { capturedRequests.add(firstArg()) }

        coEvery { liveTvRepo.getLiveTvPrograms(any(), any(), any()) } returns
            Result.success(emptyList<LiveTvProgram>())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun channels(count: Int) = (0 until count).map {
        LiveTvChannel(id = "ch-$it", name = "Channel $it")
    }

    private fun stubResolve(
        method: PlayMethod = PlayMethod.DIRECT_STREAM,
        url: String = "https://srv/Videos/x/stream",
    ) {
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns ResolvedPlayback(
            mediaSourceId = "src",
            streamUrl = url,
            playMethod = method,
            playSessionId = "psid",
            maxStreamingBitrate = null,
        )
    }

    private fun createVm(
        audio: LivePlayerAudio? = null,
        pip: PipController? = null,
        renderer: TranscodeReasonsRenderer = TranscodeReasonsRenderer { emptyList() },
    ): LiveTvPlayerViewModel = LiveTvPlayerViewModel(
        mediaRepository = liveTvRepo,
        playbackRepository = playbackRepo,
        appRuntimeStateStore = appRuntimeStateStore,
        playbackStore = playbackStore,
        aggregateStore = aggregateStore,
        lastChannelStore = lastChannelStore,
        engineFactory = LiveEngineFactory { config, onFallback ->
            assertEquals("tok", config.authToken, "auth token flows via the engine config")
            onTranscodeFallback = onFallback
            fakeEngine
        },
        imageUrlProvider = imageUrlProvider,
        audio = audio,
        transcodeReasonsRenderer = renderer,
        pip = pip,
    )

    private fun tune(
        routeChannelId: String = "ch-0",
        channelCount: Int = 2,
        audio: LivePlayerAudio? = null,
        vm: LiveTvPlayerViewModel = createVm(audio = audio),
    ): LiveTvPlayerViewModel {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(channelCount))
        stubResolve()
        vm.initialize(routeChannelId, null, null)
        scheduler.runCurrent()
        return vm
    }

    // ── 1. Buffering watchdog (virtual clock) ────────────────────────────────

    @Test
    fun `watchdog stalls - buffering past 20s surfaces the timeout error`() = runTest {
        val vm = tune()
        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.runCurrent()

        // Just under the timeout: still buffering, no error.
        scheduler.advanceTimeBy(19_999L)
        scheduler.runCurrent()
        assertNull(vm.state.value.errorMessage)
        assertTrue(vm.state.value.isBuffering)

        // Past the timeout: the spinner lifts and the retryable error surfaces.
        scheduler.advanceTimeBy(2_000L)
        scheduler.runCurrent()
        assertFalse(vm.state.value.isBuffering, "the stuck rebuffer spinner must lift with the error")
        val error = vm.state.value.errorMessage
        assertTrue(error is LivePlayerMessage.Resource, "expected Resource error, was $error")
        assertEquals(Res.string.live_error_buffering_timeout, (error as LivePlayerMessage.Resource).res)
    }

    @Test
    fun `watchdog - reaching READY before the timeout never fires it`() = runTest {
        val vm = tune()
        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.advanceTimeBy(19_000L)

        // The tuner recovers just before the deadline.
        engineStateFlow.value = LiveEngineState.READY
        scheduler.runCurrent()
        scheduler.advanceTimeBy(60_000L)
        scheduler.runCurrent()

        assertNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isBuffering)
    }

    @Test
    fun `watchdog - IDLE counts as buffering but never arms the timeout`() = runTest {
        val vm = tune()
        engineStateFlow.value = LiveEngineState.IDLE
        scheduler.runCurrent()

        assertTrue(vm.state.value.isBuffering, "IDLE renders the buffering spinner")

        scheduler.advanceTimeBy(60_000L)
        scheduler.runCurrent()
        assertNull(vm.state.value.errorMessage, "the watchdog only arms on BUFFERING")
    }

    @Test
    fun `watchdog - a state flip away from BUFFERING cancels the pending timeout`() = runTest {
        val vm = tune()
        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.runCurrent()

        // Leave BUFFERING: the watchdog job is cancelled...
        engineStateFlow.value = LiveEngineState.READY
        scheduler.runCurrent()
        // ...so re-entering BUFFERING starts a FRESH 20 s window.
        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.runCurrent()
        scheduler.advanceTimeBy(19_000L)

        assertNull(vm.state.value.errorMessage, "the second BUFFERING window must be re-armed from zero")
        assertTrue(vm.state.value.isBuffering)
    }

    // ── 2. Mute toggle (pre-mute volume contract) ────────────────────────────

    /** Recording [LivePlayerAudio] fake: captures bind/lifecycle + volume calls. */
    private class FakeAudio : LivePlayerAudio {
        var boundOwner: Any? = null
        val volumeWrites = mutableListOf<Float>()
        var currentVolume: Float? = 0.7f
        var engineCreatedCount = 0
        var releasedCount = 0

        override fun bind(owner: LiveTvPlayerViewModel) { boundOwner = owner }
        override fun playerVolume(): Float? = currentVolume
        override fun setPlayerVolume(volume: Float) {
            volumeWrites.add(volume)
            currentVolume = volume
        }
        override fun onEngineCreated() { engineCreatedCount++ }
        override fun onReleased() { releasedCount++ }
    }

    @Test
    fun `toggleMute captures pre-mute volume and restores it exactly on unmute`() = runTest {
        val audio = FakeAudio()
        val vm = tune(audio = audio)

        vm.toggleMute()
        assertTrue(vm.state.value.isMuted)
        assertEquals(listOf(0.0f), audio.volumeWrites, "mute writes volume 0")

        vm.toggleMute()
        assertFalse(vm.state.value.isMuted)
        assertEquals(listOf(0.0f, 0.7f), audio.volumeWrites, "unmute restores the captured 0.7f, not a default")
    }

    @Test
    fun `toggleMute with no player volume is a no-op`() = runTest {
        val audio = FakeAudio().apply { currentVolume = null }
        val vm = tune(audio = audio)

        vm.toggleMute()

        assertFalse(vm.state.value.isMuted)
        assertTrue(audio.volumeWrites.isEmpty())
    }

    @Test
    fun `toggleMute without an audio seam never crashes`() = runTest {
        val vm = tune() // audio = null (the jvmTest default)
        vm.toggleMute()
        assertFalse(vm.state.value.isMuted)
    }

    @Test
    fun `stop clears the pre-mute volume so a stale level never lands on a fresh engine`() = runTest {
        val audio = FakeAudio()
        val vm = tune(audio = audio)
        vm.toggleMute() // captures 0.7f, writes 0f
        assertEquals(listOf(0.0f), audio.volumeWrites)

        vm.stop()
        assertEquals(1, audio.releasedCount, "audio lifecycle torn down before the engine release")

        // Fresh entry: a new player reports its own level (0.5f). Muting must
        // capture 0.5f — the 0.7f captured before stop() must be gone.
        audio.currentVolume = 0.5f
        audio.volumeWrites.clear()
        vm.toggleMute()
        assertTrue(vm.state.value.isMuted)
        vm.toggleMute()
        assertEquals(
            listOf(0.0f, 0.5f),
            audio.volumeWrites,
            "unmute must restore the fresh engine's level, not the pre-stop 0.7f",
        )
    }

    @Test
    fun `engine creation installs the audio seam exactly once per engine instance`() = runTest {
        val audio = FakeAudio()
        tune(audio = audio)
        assertEquals(1, audio.engineCreatedCount)
    }

    // ── 3. In-player recording actions ───────────────────────────────────────

    /** ISO instant ~1 h before now, for a program window that is airing. */
    private fun hourBeforeNow(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now().minusSeconds(3_600))

    private fun hourAfterNow(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(3_600))

    private fun airingProgram(
        id: String = "prog-1",
        timerId: String? = null,
        seriesTimerId: String? = null,
    ) = LiveTvProgram(
        id = id,
        name = "Evening News",
        channelId = "ch-0",
        startDate = hourBeforeNow(),
        endDate = hourAfterNow(),
        timerId = timerId,
        seriesTimerId = seriesTimerId,
    )

    private fun tuneWithProgram(program: LiveTvProgram): LiveTvPlayerViewModel {
        coEvery { liveTvRepo.getLiveTvPrograms(any(), any(), any()) } returns
            Result.success(listOf(program))
        return tune()
    }

    @Test
    fun `recordCurrentProgramOnce success posts the record message and refreshes programs`() = runTest {
        val vm = tuneWithProgram(airingProgram())
        assertNotNull(vm.state.value.currentProgram)
        coEvery { liveTvRepo.createTimer("prog-1") } returns Result.success(Unit)

        val messages = mutableListOf<LivePlayerMessage>()
        backgroundCollectMessages(vm, messages)
        vm.recordCurrentProgramOnce()
        scheduler.runCurrent()

        assertEquals(listOf<LivePlayerMessage>(LivePlayerMessage.Resource(Res.string.live_record_success)), messages)
        // The Record ↔ Cancel sheet follows the server: the program window is
        // re-fetched after the timer call.
        coVerify(atLeast = 2) { liveTvRepo.getLiveTvPrograms(any(), any(), any()) }
    }

    @Test
    fun `recordCurrentProgramOnce failure posts the raw error text`() = runTest {
        val vm = tuneWithProgram(airingProgram())
        coEvery { liveTvRepo.createTimer("prog-1") } returns
            Result.failure(RuntimeException("tuner busy"))

        val messages = mutableListOf<LivePlayerMessage>()
        backgroundCollectMessages(vm, messages)
        vm.recordCurrentProgramOnce()
        scheduler.runCurrent()

        assertEquals(listOf<LivePlayerMessage>(LivePlayerMessage.Raw("tuner busy")), messages)
    }

    @Test
    fun `recordCurrentProgramOnce without a current program is a silent no-op`() = runTest {
        // No channel was ever loaded: currentProgram stays null.
        val vm = createVm()
        val messages = mutableListOf<LivePlayerMessage>()
        backgroundCollectMessages(vm, messages)

        vm.recordCurrentProgramOnce()
        scheduler.runCurrent()

        coVerify(exactly = 0) { liveTvRepo.createTimer(any()) }
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `recordCurrentProgramSeries success posts the record message`() = runTest {
        val vm = tuneWithProgram(airingProgram())
        coEvery { liveTvRepo.createSeriesTimer("prog-1") } returns Result.success(Unit)

        val messages = mutableListOf<LivePlayerMessage>()
        backgroundCollectMessages(vm, messages)
        vm.recordCurrentProgramSeries()
        scheduler.runCurrent()

        assertEquals(listOf<LivePlayerMessage>(LivePlayerMessage.Resource(Res.string.live_record_success)), messages)
    }

    @Test
    fun `cancelCurrentProgramTimer cancels the program's timer id and posts the cancel message`() = runTest {
        val vm = tuneWithProgram(airingProgram(timerId = "timer-9"))
        coEvery { liveTvRepo.cancelTimer("timer-9") } returns Result.success(Unit)

        val messages = mutableListOf<LivePlayerMessage>()
        backgroundCollectMessages(vm, messages)
        vm.cancelCurrentProgramTimer()
        scheduler.runCurrent()

        assertEquals(listOf<LivePlayerMessage>(LivePlayerMessage.Resource(Res.string.live_record_canceled)), messages)
    }

    @Test
    fun `cancelCurrentProgramTimer without a timer id is a silent no-op`() = runTest {
        val vm = tuneWithProgram(airingProgram(timerId = null))

        vm.cancelCurrentProgramTimer()
        scheduler.runCurrent()

        coVerify(exactly = 0) { liveTvRepo.cancelTimer(any()) }
    }

    @Test
    fun `cancelCurrentProgramSeries without a series timer id is a silent no-op`() = runTest {
        val vm = tuneWithProgram(airingProgram(seriesTimerId = null))

        vm.cancelCurrentProgramSeries()
        scheduler.runCurrent()

        coVerify(exactly = 0) { liveTvRepo.cancelSeriesTimer(any()) }
    }

    private fun TestScope.backgroundCollectMessages(vm: LiveTvPlayerViewModel, into: MutableList<LivePlayerMessage>) {
        backgroundScope.launch(UnconfinedTestDispatcher(scheduler)) {
            vm.messages.collect { into.add(it) }
        }
        scheduler.runCurrent()
    }

    // ── 4. Transcode fallback + error-detail merge ───────────────────────────

    @Test
    fun `transcode fallback re-resolves with TRANSCODE and reloads the engine`() = runTest {
        val vm = tune()

        // The engine reports a direct/direct-stream decode failure; the VM's
        // factory callback drives the fallback.
        assertNotNull(onTranscodeFallback)
        onTranscodeFallback!!.invoke()
        scheduler.runCurrent()

        assertEquals(2, capturedRequests.size)
        val reload = capturedRequests.last()
        assertEquals(LivePlayMethod.TRANSCODE, reload.playMethod)
        assertEquals(LivePlayMethod.TRANSCODE, vm.state.value.playMethod)
        assertEquals("Channel 0", reload.title)
    }

    @Test
    fun `transcode fallback failure surfaces the fallback error with the channel name`() = runTest {
        val vm = tune()
        // Both resolution paths fail under TRANSCODE.
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("no transcode"))

        // The engine captured its originating error detail while holding the
        // BUFFERING state through the fallback.
        engineErrorDetailFlow.value = "boom"
        onTranscodeFallback!!.invoke()
        scheduler.runCurrent()

        assertEquals(1, capturedRequests.size, "no reload may be attempted without a resolved URL")
        val error = vm.state.value.errorMessage
        assertTrue(error is LivePlayerMessage.Resource)
        assertEquals(Res.string.live_error_transcode_fallback, (error as LivePlayerMessage.Resource).res)
        assertEquals(listOf("Channel 0"), error.args)
        assertFalse(vm.state.value.isBuffering, "the engine's held buffering must lift with the error")
        assertEquals("boom", vm.state.value.errorDetail, "the originating engine error detail surfaces")
    }

    @Test
    fun `engine ERROR on a transcode appends rendered reasons to the detail block`() = runTest {
        // Tune lands on a transcode (option AUTO accepts the server's verdict).
        val rendered = mutableListOf<String>()
        coEvery { playbackRepo.fetchActiveTranscodeReasons(any()) } returns listOf("ContainerNotSupported")
        val vm = createVm(
            renderer = TranscodeReasonsRenderer { reasons ->
                rendered += reasons
                reasons.map { "reason: $it" }
            },
        )
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(1))
        stubResolve(method = PlayMethod.TRANSCODE)
        vm.initialize("ch-0", null, null)
        scheduler.runCurrent()
        assertEquals(LivePlayMethod.TRANSCODE, vm.state.value.playMethod)

        // The reasons refresher waits 2 s for the server to register the live
        // session, then fetches.
        scheduler.advanceTimeBy(2_500L)
        scheduler.runCurrent()
        assertEquals(listOf("ContainerNotSupported"), vm.state.value.transcodeReasons)

        // Now the engine errors: raw detail + rendered reasons join as one block.
        // The renderer runs at detail-build time (on the engine error), not at
        // reason-landing time, so `rendered` only fills in below.
        engineErrorDetailFlow.value = "playback failed"
        engineErrorMessageFlow.value = "boom"
        engineStateFlow.value = LiveEngineState.ERROR
        scheduler.runCurrent()

        assertEquals("playback failed\n\nreason: ContainerNotSupported", vm.state.value.errorDetail)
        assertEquals(LivePlayerMessage.Raw("boom"), vm.state.value.errorMessage)
        assertEquals(listOf("ContainerNotSupported"), rendered)
    }

    @Test
    fun `engine ERROR on a direct stream carries no reasons block`() = runTest {
        val vm = tune() // direct-stream resolution → no reasons fetched
        scheduler.advanceTimeBy(5_000L)
        scheduler.runCurrent()
        assertTrue(vm.state.value.transcodeReasons.isEmpty())

        engineErrorDetailFlow.value = "playback failed"
        engineStateFlow.value = LiveEngineState.ERROR
        scheduler.runCurrent()

        assertEquals("playback failed", vm.state.value.errorDetail)
    }

    // ── 5. setLiveStreamOption: optimistic state + reload ────────────────────

    @Test
    fun `setLiveStreamOption persists the choice and reloads the current channel`() = runTest {
        val vm = tune()
        coEvery { playbackStore.setLiveStreamOption(any()) } returns Unit

        vm.setLiveStreamOption(LiveStreamOption.TRANSCODE)
        scheduler.runCurrent()

        coVerify(exactly = 1) { playbackStore.setLiveStreamOption(LiveStreamOption.TRANSCODE) }
        assertEquals(LiveStreamOption.TRANSCODE, vm.state.value.liveStreamOption)
        assertEquals(2, capturedRequests.size, "the current channel re-resolves and reloads")
        assertEquals("Channel 0", capturedRequests.last().title)
        assertFalse(vm.state.value.isSwitchingChannel, "the switching chrome clears once the reload lands")
        assertNull(vm.state.value.errorMessage, "a reload clears a prior error")
    }

    @Test
    fun `setLiveStreamOption without an active channel is a no-op`() = runTest {
        val vm = createVm()
        vm.setLiveStreamOption(LiveStreamOption.DIRECT_STREAM)
        scheduler.runCurrent()
        coVerify(exactly = 0) { playbackStore.setLiveStreamOption(any()) }
        assertEquals(0, capturedRequests.size)
    }

    // ── 6. DVR guards, selection priority, stop/reset, logo urls ─────────────

    @Test
    fun `playFromStart seeks to 0 when a DVR window exists`() = runTest {
        val vm = tune()
        engineDurationFlow.value = 3_600_000L

        vm.playFromStart()

        io.mockk.verify { fakeEngine.seekTo(0L) }
    }

    @Test
    fun `playFromStart is a no-op on pure-live streams without a DVR window`() = runTest {
        val vm = tune()
        engineDurationFlow.value = -1L

        vm.playFromStart()

        io.mockk.verify(exactly = 0) { fakeEngine.seekTo(any()) }
    }

    @Test
    fun `route channel wins over the stored last-watched channel`() = runTest {
        every { lastChannelStore.observeLastChannelId() } returns flowOf("ch-1")
        val vm = tune(routeChannelId = "ch-2", channelCount = 3)
        assertEquals(2, vm.state.value.currentIndex)
        assertEquals("ch-2", vm.state.value.currentChannel?.id)
    }

    @Test
    fun `stored last-watched channel is the fallback when the route id is missing`() = runTest {
        every { lastChannelStore.observeLastChannelId() } returns flowOf("ch-1")
        val vm = tune(routeChannelId = "missing", channelCount = 3)
        assertEquals(1, vm.state.value.currentIndex)
        assertEquals("ch-1", vm.state.value.currentChannel?.id)
    }

    @Test
    fun `first channel is the last resort when neither route nor stored id match`() = runTest {
        every { lastChannelStore.observeLastChannelId() } returns flowOf("nope")
        val vm = tune(routeChannelId = "also-nope", channelCount = 3)
        assertEquals(0, vm.state.value.currentIndex)
    }

    @Test
    fun `a successful zap persists the channel as last-watched`() = runTest {
        val vm = tune(channelCount = 2)
        vm.channelUp()
        scheduler.runCurrent()
        coVerify { lastChannelStore.setLastChannelId("ch-1") }
    }

    @Test
    fun `stop resets position, duration and the whole ui state, and re-init reloads`() = runTest {
        val vm = tune()
        enginePositionFlow.value = 42_000L
        engineDurationFlow.value = 3_600_000L
        engineStateFlow.value = LiveEngineState.READY

        vm.stop()

        assertEquals(0L, vm.positionMs.value)
        assertEquals(-1L, vm.durationMs.value)
        val fresh = vm.state.value
        assertTrue(fresh.channels.isEmpty())
        assertNull(fresh.currentChannel)
        assertTrue(fresh.isBuffering, "fresh state is the pre-load first frame")

        // The activity-scoped VM is reused across screen entries: a re-init
        // after stop() must run the full load again.
        vm.initialize("ch-0", null, null)
        scheduler.runCurrent()
        assertEquals(2, capturedRequests.size)
        assertEquals("ch-0", vm.state.value.currentChannel?.id)
    }

    @Test
    fun `stop drops a pending zap from the previous session`() = runTest {
        val loadGate = CompletableDeferred<Unit>()
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } coAnswers {
            loadGate.await()
            Result.success(channels(3))
        }
        stubResolve()
        val vm = createVm()
        vm.initialize("ch-0", null, null)
        vm.channelUp() // queued while the load is parked

        vm.stop() // the zap belongs to the torn-down session
        loadGate.complete(Unit)

        // Re-entry loads the route channel directly — no surprise zap to ch-1.
        vm.initialize("ch-0", null, null)
        scheduler.runCurrent()
        assertEquals(0, vm.state.value.currentIndex)
    }

    @Test
    fun `logoUrlFor resolves only when the channel carries an image tag`() = runTest {
        every { imageUrlProvider.getImageUrl("ch-1", any()) } returns "https://srv/Items/ch-1/Images/Primary"
        val vm = createVm()

        assertEquals("https://srv/Items/ch-1/Images/Primary", vm.logoUrlFor(LiveTvChannel(id = "ch-1", name = "One", imageTag = "abc")))
        assertNull(vm.logoUrlFor(LiveTvChannel(id = "ch-2", name = "Two", imageTag = null)))
        assertNull(vm.logoUrlFor(LiveTvChannel(id = "ch-3", name = "Three", imageTag = "")))
    }
}
