package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.feature.player.live.data.LastChannelStore
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineFactory
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import com.raulshma.jellyplay.feature.player.live.engine.LivePlaybackRequest
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_no_channels
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_resolve_failed
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvPlayerViewModelTest {

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
    private val engineIsPlayingFlow = MutableStateFlow(false)
    private val engineStateFlow = MutableStateFlow(LiveEngineState.IDLE)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        liveTvRepo = mockk<MediaRepository>(relaxed = true)
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
        coEvery { appRuntimeStateStore.setFavoriteChannels(any()) } answers {
            val newFavs = firstArg<Set<String>>()
            appRuntimeFlow.value = appRuntimeFlow.value.copy(favoriteChannels = newFavs)
        }
        every { playbackRepo.getAccessToken() } returns "tok"
        every { fakeEngine.state } returns engineStateFlow
        every { fakeEngine.isPlaying } returns engineIsPlayingFlow
        every { fakeEngine.isAtLiveEdge } returns MutableStateFlow(true)
        every { fakeEngine.positionMs } returns MutableStateFlow(0L)
        every { fakeEngine.durationMs } returns MutableStateFlow(-1L)
        every { fakeEngine.errorMessage } returns MutableStateFlow(null)
        // Read by the state collector on the ERROR path (transcode-reasons
        // detail merge) — first exercised by the wave-19C PiP auto-exit test.
        every { fakeEngine.errorDetail } returns MutableStateFlow(null)
        every { fakeEngine.load(any()) } answers { capturedRequests.add(firstArg()) }

        // Default: no EPG programs. Individual tests override as needed.
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

    private fun stubResolve() {
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns ResolvedPlayback(
            mediaSourceId = "src",
            streamUrl = "https://srv/Videos/x/stream",
            playMethod = PlayMethod.DIRECT_STREAM,
            playSessionId = "psid",
            maxStreamingBitrate = null,
        )
    }

    @Test
    fun `channelUp advances index and wraps at end`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(3))
        stubResolve()

        val vm = createVm()
        vm.initialize("ch-0", null, null)

        // wait for channel load to settle
        kotlinx.coroutines.delay(50)
        vm.channelUp()
        assertEquals(1, vm.state.value.currentIndex)
        vm.channelUp()
        vm.channelUp()
        // wrap from 2 -> 0
        assertEquals(0, vm.state.value.currentIndex)
    }

    @Test
    fun `channelDown decrements index and wraps at start`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(3))
        stubResolve()

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        vm.channelDown()
        // wrap from 0 -> 2
        assertEquals(2, vm.state.value.currentIndex)
    }

    @Test
    fun `state flags reflect index bounds`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(3))
        stubResolve()

        val vm = createVm()
        vm.initialize("ch-1", null, null)
        assertEquals(1, vm.state.value.currentIndex)
        assertTrue(vm.state.value.hasNext)
        assertTrue(vm.state.value.hasPrevious)
    }

    @Test
    fun `resolvePlayback failure surfaces error`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(2))
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        // fetchPlaybackInfo fallback must also fail to surface the error.
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("server down"))

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        // allow coroutine to settle
        kotlinx.coroutines.delay(50)
        // The commonMain VM keeps the message unresolved until render time:
        // the resolve-failure resource carries the channel name as its arg.
        val error = vm.state.value.errorMessage
        assertTrue(error is LivePlayerMessage.Resource, "expected Resource error, was $error")
        assertEquals(Res.string.live_error_resolve_failed, (error as LivePlayerMessage.Resource).res)
        assertEquals(listOf("Channel 0"), error.args)
    }

    @Test
    fun `resolvePlayback null with fetchPlaybackInfo fallback builds direct-stream URL`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(1))
        // resolvePlayback returns null (e.g. device profile didn't match).
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        // fetchPlaybackInfo fallback returns a live source.
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(
            PlaybackInfoResult(
                playSessionId = "psid",
                mediaSources = listOf(
                    MediaSource(
                        id = "src-1",
                        name = "tuner",
                        supportsDirectStream = true,
                        supportsDirectPlay = false,
                        supportsTranscoding = false,
                        liveStreamId = "live-1",
                        requiresOpening = true,
                    ),
                ),
            ),
        )
        every {
            playbackRepo.getStreamUrl(
                itemId = any(),
                mediaSourceId = any(),
                startTimeTicks = any(),
                liveStreamId = any(),
            )
        } returns "https://srv/Videos/ch-0/stream?LiveStreamId=live-1"
        coEvery { liveTvRepo.getLiveTvPrograms(any(), any(), any()) } returns Result.success(emptyList<LiveTvProgram>())

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)

        assertEquals(1, capturedRequests.size)
        assertTrue(capturedRequests[0].url.contains("LiveStreamId=live-1"))
        assertEquals(LivePlayMethod.DIRECT_STREAM, capturedRequests[0].playMethod)
    }

    @Test
    fun `live source with all flags false but liveStreamId present builds URL anyway`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(1))
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(
            PlaybackInfoResult(
                playSessionId = "psid",
                mediaSources = listOf(
                    MediaSource(
                        id = "src-1",
                        name = "tuner",
                        supportsDirectStream = false,
                        supportsDirectPlay = false,
                        supportsTranscoding = false,
                        liveStreamId = "live-1",
                        requiresOpening = true,
                    ),
                ),
            ),
        )
        every {
            playbackRepo.getStreamUrl(
                itemId = any(),
                mediaSourceId = any(),
                startTimeTicks = any(),
                liveStreamId = any(),
            )
        } returns "https://srv/Videos/ch-0/stream?LiveStreamId=live-1"
        coEvery { liveTvRepo.getLiveTvPrograms(any(), any(), any()) } returns Result.success(emptyList<LiveTvProgram>())

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)

        assertEquals(1, capturedRequests.size)
        assertTrue(capturedRequests[0].url.contains("LiveStreamId=live-1"))
        // All playability flags false but liveStreamId present -> DIRECT_STREAM
        assertEquals(LivePlayMethod.DIRECT_STREAM, capturedRequests[0].playMethod)
    }

    @Test
    fun `DIRECT_STREAM preference overrides server transcode when probe failed`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(1))
        // User selected Direct Stream, but the server's live-source probe
        // failed (DirectPlayError) so it resolved a transcode.
        coEvery {
            playbackRepo.resolvePlayback(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns ResolvedPlayback(
            mediaSourceId = "src",
            streamUrl = "https://srv/Videos/x/master.m3u8",
            playMethod = PlayMethod.TRANSCODE,
            playSessionId = "psid",
            maxStreamingBitrate = null,
        )
        // Fallback fetchPlaybackInfo returns the opened live source.
        coEvery {
            playbackRepo.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(
            PlaybackInfoResult(
                playSessionId = "psid",
                mediaSources = listOf(
                    MediaSource(
                        id = "src-1",
                        name = "tuner",
                        supportsDirectStream = false,
                        supportsDirectPlay = false,
                        supportsTranscoding = true,
                        transcodeUrl = "/Videos/x/master.m3u8",
                        liveStreamId = "live-1",
                        requiresOpening = true,
                        container = "hls",
                    ),
                ),
            ),
        )
        every {
            playbackRepo.getStreamUrl(
                itemId = any(),
                mediaSourceId = any(),
                startTimeTicks = any(),
                liveStreamId = any(),
            )
        } returns "https://srv/Videos/ch-0/stream?LiveStreamId=live-1"
        coEvery { liveTvRepo.getLiveTvPrograms(any(), any(), any()) } returns Result.success(emptyList<LiveTvProgram>())

        // Opt into Direct Stream.
        playbackFlow.value = playbackFlow.value.copy(liveStreamOption = LiveStreamOption.DIRECT_STREAM)

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)

        assertEquals(1, capturedRequests.size)
        // Must play the raw direct-stream URL, NOT the transcode master.m3u8.
        assertTrue(capturedRequests[0].url.contains("LiveStreamId=live-1"))
        assertTrue(!capturedRequests[0].url.contains("master.m3u8"))
        assertEquals(LivePlayMethod.DIRECT_STREAM, capturedRequests[0].playMethod)
    }

    @Test
    fun `load request uses HLS url and direct-stream method`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(1))
        stubResolve()
        coEvery { liveTvRepo.getLiveTvPrograms(any(), any(), any()) } returns Result.success(emptyList<LiveTvProgram>())

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)

        assertEquals(1, capturedRequests.size)
        assertEquals("https://srv/Videos/x/stream", capturedRequests[0].url)
        assertEquals(LivePlayMethod.DIRECT_STREAM, capturedRequests[0].playMethod)
        assertEquals("Channel 0", capturedRequests[0].title)
    }

    @Test
    fun `empty channel list surfaces error`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(emptyList<LiveTvChannel>())

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)
        // Unresolved-resource check (the legacy suite compared the localized
        // "No channels available" text via a Context mock; the commonMain VM
        // now carries the resource itself).
        val error = vm.state.value.errorMessage
        assertTrue(error is LivePlayerMessage.Resource, "expected Resource error, was $error")
        assertEquals(Res.string.live_error_no_channels, (error as LivePlayerMessage.Resource).res)
        assertNull(vm.state.value.currentChannel)
    }

    // ── Deferred zaps (wave 20D): a zap during the channel-list load is
    // queued, not dropped — PiP SKIP maps to a zap, so SKIP used to no-op
    // while the list was loading. The gate pattern below parks
    // getLiveTvChannels mid-flight so the zap provably arrives inside the
    // loading window. ──

    @Test
    fun `zap during channel load is applied once the list arrives`() = runTest {
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
        assertTrue(vm.state.value.isLoadingChannels, "channel load should still be in flight")

        // The zap (screen D-pad or PiP SKIP_FORWARD) lands mid-load —
        // deferred, and nothing may resolve before the list commits.
        vm.channelUp()
        assertEquals(0, capturedRequests.size, "no tune may start before the channel list commits")

        loadGate.complete(Unit)
        kotlinx.coroutines.delay(50)

        // Applied after the commit: route channel was ch-0 (index 0), the
        // deferred up-zap tunes index 1 — exactly as a post-load zap would.
        assertEquals(1, vm.state.value.currentIndex)
        assertEquals("ch-1", vm.state.value.currentChannel?.id)
        assertEquals(1, capturedRequests.size)
        assertEquals("Channel 1", capturedRequests[0].title)
    }

    @Test
    fun `two zaps during load keep only the last requested direction`() = runTest {
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
        vm.channelUp() // superseded…
        vm.channelDown() // …by the later intent: down wins.
        loadGate.complete(Unit)
        kotlinx.coroutines.delay(50)

        // From route ch-0 (index 0), the retained down-zap wraps to the LAST
        // channel — not the up-zap's index 1.
        assertEquals(2, vm.state.value.currentIndex)
        assertEquals("ch-2", vm.state.value.currentChannel?.id)
        assertEquals(1, capturedRequests.size)
        assertEquals("Channel 2", capturedRequests[0].title)
    }

    @Test
    fun `queued zap is dropped when the channel load fails and does not fire on re-entry`() = runTest {
        val loadGate = CompletableDeferred<Unit>()
        var failLoad = true
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } coAnswers {
            // Sample the flag when the call STARTS (before parking on the
            // gate): the test flips failLoad while this load is parked, and
            // the flip must only affect the re-entry load below — the parked
            // call's outcome is sealed at entry.
            val shouldFail = failLoad
            loadGate.await()
            if (shouldFail) Result.failure<List<LiveTvChannel>>(RuntimeException("offline"))
            else Result.success(channels(3))
        }
        stubResolve()

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        assertTrue(vm.state.value.isLoadingChannels, "channel load should still be in flight")
        vm.channelUp() // queued while loading…
        failLoad = false // …but the in-flight load fails under it.
        loadGate.complete(Unit)
        kotlinx.coroutines.delay(50)

        // Failure path keeps the old behavior: no-channels error, no crash,
        // and no tune attempted (the zap must not retry the load).
        val error = vm.state.value.errorMessage
        assertTrue(error is LivePlayerMessage.Resource, "expected Resource error, was $error")
        assertEquals(Res.string.live_error_no_channels, (error as LivePlayerMessage.Resource).res)
        assertNull(vm.state.value.currentChannel)
        assertTrue(capturedRequests.isEmpty())

        // Re-entry proves the queued zap was DROPPED, not retained: the fresh
        // load plays the route channel directly — no surprise zap to ch-1.
        vm.stop()
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)
        assertEquals(0, vm.state.value.currentIndex)
        assertEquals("ch-0", vm.state.value.currentChannel?.id)
        assertEquals(1, capturedRequests.size)
        assertEquals("Channel 0", capturedRequests[0].title)
    }

    @Test
    fun `selectChannelById tunes the channel with matching id`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(3))
        stubResolve()

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)

        vm.selectChannelById("ch-2")
        kotlinx.coroutines.delay(50)
        assertEquals(2, vm.state.value.currentIndex)
        assertEquals("ch-2", vm.state.value.currentChannel?.id)
    }

    @Test
    fun `selectChannelById with unknown id is a no-op`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(3))
        stubResolve()

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)
        val indexBefore = vm.state.value.currentIndex

        vm.selectChannelById("does-not-exist")
        kotlinx.coroutines.delay(50)
        assertEquals(indexBefore, vm.state.value.currentIndex)
    }

    @Test
    fun `toggleFavorite adds id not already present`() = runTest {
        val vm = createVm()
        vm.toggleFavorite("ch-7")
        kotlinx.coroutines.delay(50)
        assertTrue("ch-7" in appRuntimeFlow.value.favoriteChannels)
        assertTrue("ch-7" in vm.state.value.favorites)
    }

    @Test
    fun `toggleFavorite removes already-present id`() = runTest {
        appRuntimeFlow.value = appRuntimeFlow.value.copy(favoriteChannels = setOf("ch-7"))
        val vm = createVm()
        vm.toggleFavorite("ch-7")
        kotlinx.coroutines.delay(50)
        assertTrue("ch-7" !in appRuntimeFlow.value.favoriteChannels)
        assertTrue("ch-7" !in vm.state.value.favorites)
    }

    @Test
    fun `seekWithinDvr delegates to engine seekTo`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(1))
        stubResolve()

        val vm = createVm()
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)

        vm.seekWithinDvr(12_000L)
        io.mockk.verify { fakeEngine.seekTo(12_000L) }
    }

    /**
     * Recording fake of the wave-19C live PiP seam: captures the calls the VM
     * makes so the seam assertions below read as plain list checks.
     */
    private class FakePip : PipController {
        override val isInPipMode = MutableStateFlow(false)
        override var pipTransport: PipTransport? = null
        val autoEnterRequests = mutableListOf<Boolean>()
        val playingMirrors = mutableListOf<Boolean>()
        val autoExits = mutableListOf<Unit>()
        val aspects = mutableListOf<Pair<Int, Int>?>()
        var resetCount = 0

        override fun setPlaying(playing: Boolean) {
            playingMirrors.add(playing)
        }

        override fun requestAutoEnterPip(shouldEnter: Boolean) {
            autoEnterRequests.add(shouldEnter)
        }

        override fun requestAutoExitPip() {
            autoExits.add(Unit)
        }

        override fun setPipAspectRatio(aspect: Pair<Int, Int>?) {
            aspects.add(aspect)
        }

        override fun reset() {
            resetCount++
            // Matches the real controller's reset contract: full teardown
            // nulls the transport.
            pipTransport = null
        }
    }

    @Test
    fun `pip seam arms auto-enter on tune and mirrors play state and aspect`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(2))
        stubResolve()

        val pip = FakePip()
        val vm = createVm(pip = pip)
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)

        // A successful tune arms auto-enter exactly once.
        assertEquals(listOf(true), pip.autoEnterRequests)

        // Engine play-state emissions mirror into the seam for the host
        // Activity's play/pause PiP icon.
        engineIsPlayingFlow.value = true
        assertTrue(pip.playingMirrors.contains(true))

        // Aspect feed: valid dimensions push through; a zero pair clears.
        vm.onVideoSizeChanged(1920, 1080)
        assertEquals(1920 to 1080, pip.aspects.last())
        vm.onVideoSizeChanged(0, 0)
        assertNull(pip.aspects.last())
    }

    @Test
    fun `pip transport zaps channels on skip actions and hits engine on play pause`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(3))
        stubResolve()

        val pip = FakePip()
        val vm = createVm(pip = pip)
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)

        val transport = pip.pipTransport
        assertNotNull(transport)

        // Live PiP convention: rewind/forward SKIP actions step channel
        // down/up (re-resolving with the route's stream overrides), not DVR
        // micro-seeks.
        transport.handle(PipAction.SKIP_FORWARD)
        kotlinx.coroutines.delay(50)
        assertEquals(1, vm.state.value.currentIndex)
        assertEquals(2, capturedRequests.size)
        transport.handle(PipAction.SKIP_BACKWARD)
        kotlinx.coroutines.delay(50)
        assertEquals(0, vm.state.value.currentIndex)

        transport.handle(PipAction.PLAY)
        transport.handle(PipAction.PAUSE)
        io.mockk.verify { fakeEngine.play() }
        io.mockk.verify { fakeEngine.pause() }
    }

    @Test
    fun `pip seam requests auto-exit on engine error while in pip`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(1))
        stubResolve()

        val pip = FakePip()
        val vm = createVm(pip = pip)
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)

        // An engine ERROR while the window is up must translate into the
        // Activity's dismiss path — not leave a dead stream floating.
        pip.isInPipMode.value = true
        engineStateFlow.value = LiveEngineState.ERROR
        kotlinx.coroutines.delay(50)
        assertEquals(1, pip.autoExits.size)
    }

    @Test
    fun `stop resets the pip seam`() = runTest {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } returns Result.success(channels(1))
        stubResolve()

        val pip = FakePip()
        val vm = createVm(pip = pip)
        vm.initialize("ch-0", null, null)
        kotlinx.coroutines.delay(50)
        assertNotNull(pip.pipTransport)

        vm.stop()

        assertEquals(1, pip.resetCount)
        assertNull(pip.pipTransport)
    }

    private fun createVm(pip: PipController? = null): LiveTvPlayerViewModel = LiveTvPlayerViewModel(
        mediaRepository = liveTvRepo,
        playbackRepository = playbackRepo,
        appRuntimeStateStore = appRuntimeStateStore,
        playbackStore = playbackStore,
        aggregateStore = aggregateStore,
        lastChannelStore = lastChannelStore,
        engineFactory = LiveEngineFactory { _, _ -> fakeEngine },
        imageUrlProvider = imageUrlProvider,
        pip = pip,
    )
}
