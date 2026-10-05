package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackResolution
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.feature.player.live.data.LastChannelStore
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineFactory
import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import com.raulshma.jellyplay.feature.player.live.engine.LivePlaybackRequest
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayerEngine
import com.raulshma.jellyplay.feature.player.live.engine.LivePlayMethod
import com.raulshma.jellyplay.feature.player.live.engine.TranscodeReasonsRenderer
import com.raulshma.jellyplay.feature.player.live.generated.resources.Res
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_resolve_failed
import com.raulshma.jellyplay.feature.player.live.generated.resources.live_error_transcode_fallback
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.raulshma.jellyplay.core.ui.message.UiMessage

/**
 * The new-seam suite for [LivePlaybackSession] — the assertions for the
 * choreography that MOVED out of [LiveTvPlayerViewModel] (tune sequence,
 * deferred-zap machine, transcode fallback, watchdog execution), asserted
 * against the session's own [LivePlaybackEvent] stream. The end-to-end
 * behavior pins stay at the ViewModel seam (LiveTvPlayerViewModelTest /
 * GapsTest / EnginePolicyTest drive the funnel and fold); these rows pin
 * the session's contract directly: what the executor emits, in what order,
 * with no ViewModel in between.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LivePlaybackSessionTest {

    private val scheduler = TestCoroutineScheduler()

    private val liveTvRepo: LiveTvRepository = mockk(relaxed = true)
    private val playbackRepo: PlaybackRepository = mockk(relaxed = true)
    private val playbackStore: PlaybackStore = mockk(relaxed = true)
    private val playbackIdentity: PlaybackIdentity = mockk(relaxed = true)
    private val lastChannelStore: LastChannelStore = mockk(relaxed = true)
    private val fakeEngine: LivePlayerEngine = mockk(relaxed = true)

    private val playbackFlow = MutableStateFlow(PlaybackSlice())
    private val engineStateFlow = MutableStateFlow(LiveEngineState.IDLE)
    private val engineErrorDetailFlow = MutableStateFlow<String?>(null)

    private val capturedRequests = mutableListOf<LivePlaybackRequest>()
    private val received = mutableListOf<LivePlaybackEvent>()

    /** The engine-failure callback the session hands the factory; invoked by tests. */
    private var onTranscodeFallback: (() -> Unit)? = null

    /** Unconfined over the shared scheduler: emissions fold synchronously, delays virtual. */
    private fun TestScope.sessionScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(scheduler))

    private fun TestScope.createSession(): LivePlaybackSession {
        every { playbackStore.playback } returns playbackFlow
        every { playbackIdentity.accessToken() } returns "tok"
        every { lastChannelStore.observeLastChannelId() } returns flowOf(null)
        every { fakeEngine.state } returns engineStateFlow
        every { fakeEngine.isPlaying } returns MutableStateFlow(false)
        every { fakeEngine.isAtLiveEdge } returns MutableStateFlow(true)
        every { fakeEngine.positionMs } returns MutableStateFlow(0L)
        every { fakeEngine.durationMs } returns MutableStateFlow(-1L)
        every { fakeEngine.errorMessage } returns MutableStateFlow(null)
        every { fakeEngine.errorDetail } returns engineErrorDetailFlow
        every { fakeEngine.load(any()) } answers { capturedRequests.add(firstArg()) }

        val scope = sessionScope()
        val session = LivePlaybackSession(
            scope = scope,
            liveTvRepository = liveTvRepo,
            sessionManager = LiveSessionManager(playbackRepo),
            playbackStore = playbackStore,
            playbackIdentity = playbackIdentity,
            engineFactory = LiveEngineFactory { _, onFallback ->
                onTranscodeFallback = onFallback
                fakeEngine
            },
            lastChannelStore = lastChannelStore,
            transcodeReasonsRenderer = TranscodeReasonsRenderer { emptyList() },
            playbackRepository = playbackRepo,
        )
        scope.launch { session.events.collect { received += it } }
        return session
    }

    private fun channels(count: Int) = (0 until count).map {
        LiveTvChannel(id = "ch-$it", name = "Channel $it")
    }

    private fun stubChannels(count: Int, gate: CompletableDeferred<Unit>? = null) {
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } coAnswers {
            gate?.await()
            Result.success(channels(count))
        }
    }

    private fun stubResolve() {
        coEvery {
            playbackRepo.resolvePlayable(any())
        } returns PlaybackResolution.Resolved(
            ResolvedPlayback(
                mediaSourceId = "src",
                streamUrl = "https://srv/Videos/x/stream",
                playMethod = PlayMethod.DIRECT_STREAM,
                playSessionId = "psid",
                maxStreamingBitrate = null,
            ),
        )
    }

    // ── Tune sequence ─────────────────────────────────────────────────────────

    @Test
    fun `initial tune emits load-started, commit, play-method, engine-created, tune-started in order`() = runTest(scheduler) {
        stubChannels(2)
        stubResolve()
        val session = createSession()

        session.initialize("ch-0", null, null)

        fun idx(probe: (LivePlaybackEvent) -> Boolean) = received.indexOfFirst(probe)
        val loadStarted = idx { it is LivePlaybackEvent.ChannelLoadStarted }
        val committed = idx { it is LivePlaybackEvent.ChannelsCommitted }
        val playMethod = idx { it is LivePlaybackEvent.PlayMethodChanged && it.method == LivePlayMethod.DIRECT_STREAM }
        val engineCreated = idx { it is LivePlaybackEvent.EngineCreated }
        val tuneStarted = idx { it is LivePlaybackEvent.TuneStarted }

        assertTrue(loadStarted >= 0, "ChannelLoadStarted missing in $received")
        assertTrue(committed > loadStarted, "ChannelsCommitted must follow ChannelLoadStarted")
        assertTrue(playMethod > committed, "PlayMethodChanged must follow the commit")
        assertTrue(engineCreated > playMethod, "EngineCreated must follow the method mirror")
        assertTrue(tuneStarted > engineCreated, "TuneStarted must follow engine creation")
        assertEquals(1, capturedRequests.size)
        // The committed event carries the route-selected channel.
        val commit = received.first { it is LivePlaybackEvent.ChannelsCommitted } as LivePlaybackEvent.ChannelsCommitted
        assertEquals("ch-0", commit.channel.id)
        assertEquals(0, commit.index)
    }

    @Test
    fun `initialize is idempotent - a re-fire never reloads the channel list`() = runTest(scheduler) {
        stubChannels(2)
        stubResolve()
        val session = createSession()

        session.initialize("ch-0", null, null)
        session.initialize("ch-0", null, null)

        coVerify(exactly = 1) { liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any()) }
        assertEquals(1, capturedRequests.size)
    }

    @Test
    fun `resolve failure emits TuneFailed carrying the channel name`() = runTest {
        stubChannels(1)
        // The whole resolution misses (resolve verdict and the forced
        // fallback both).
        coEvery {
            playbackRepo.resolvePlayable(any())
        } returns PlaybackResolution.Unplayable
        val session = createSession()

        session.initialize("ch-0", null, null)

        val failed = received.filterIsInstance<LivePlaybackEvent.TuneFailed>().single()
        val message = failed.message as UiMessage.Resource
        assertEquals(Res.string.live_error_resolve_failed, message.res)
        assertEquals(listOf("Channel 0"), message.args)
        assertTrue(capturedRequests.isEmpty(), "no load without a resolved URL")
    }

    // ── Deferred-zap machine (the moved verbatim block) ──────────────────────

    @Test
    fun `a zap during the load defers, applies on commit and persists last-watched`() = runTest(scheduler) {
        val loadGate = CompletableDeferred<Unit>()
        stubChannels(3, gate = loadGate)
        stubResolve()
        val session = createSession()

        session.initialize("ch-0", null, null)
        session.channelUp()
        assertTrue(capturedRequests.isEmpty(), "no tune may start before the list commits")

        loadGate.complete(Unit)

        val selected = received.filterIsInstance<LivePlaybackEvent.ChannelSelected>().single()
        assertEquals(1, selected.index)
        assertEquals("ch-1", selected.channel.id)
        assertEquals(1, capturedRequests.size)
        assertEquals("Channel 1", capturedRequests[0].title)
        coVerify { lastChannelStore.setLastChannelId("ch-1") }
    }

    @Test
    fun `two deferred zaps keep only the last requested direction`() = runTest(scheduler) {
        val loadGate = CompletableDeferred<Unit>()
        stubChannels(3, gate = loadGate)
        stubResolve()
        val session = createSession()

        session.initialize("ch-0", null, null)
        session.channelUp()
        session.channelDown() // the later intent wins
        loadGate.complete(Unit)

        val selected = received.filterIsInstance<LivePlaybackEvent.ChannelSelected>().single()
        assertEquals(2, selected.index, "the retained down-zap wraps to the last channel")
        assertEquals("Channel 2", capturedRequests.single().title)
    }

    @Test
    fun `a failed load drops the deferred zap - the re-entry tune plays the route channel`() = runTest(scheduler) {
        val loadGate = CompletableDeferred<Unit>()
        var failLoad = true
        coEvery {
            liveTvRepo.getLiveTvChannels(any(), any(), any(), any(), any())
        } coAnswers {
            // Seal the outcome at entry (GapsTest's parked-load pattern): the
            // flip below must only affect the re-entry load.
            val shouldFail = failLoad
            loadGate.await()
            if (shouldFail) Result.failure<List<LiveTvChannel>>(RuntimeException("offline"))
            else Result.success(channels(3))
        }
        stubResolve()
        val session = createSession()

        session.initialize("ch-0", null, null)
        session.channelUp() // queued while the load is parked…
        failLoad = false // …but the in-flight load fails under it.
        loadGate.complete(Unit)

        assertEquals(1, received.filterIsInstance<LivePlaybackEvent.ChannelLoadFailed>().size)
        assertTrue(capturedRequests.isEmpty(), "the failed load must not tune")

        // Re-entry proves the queued zap was DROPPED, not retained: the fresh
        // load plays the route channel directly — no surprise zap to ch-1.
        session.release()
        session.initialize("ch-0", null, null)

        assertEquals(1, capturedRequests.size)
        assertEquals("Channel 0", capturedRequests[0].title)
    }

    @Test
    fun `release drops a pending zap - the stale load commits without replaying it`() = runTest(scheduler) {
        val loadGate = CompletableDeferred<Unit>()
        stubChannels(3, gate = loadGate)
        stubResolve()
        val session = createSession()
        session.initialize("ch-0", null, null)
        session.channelUp()

        session.release()
        loadGate.complete(Unit)

        // The stale load's coroutine was never release()'s to cancel — it
        // still commits, but tunes the ROUTE channel: the queued up-zap is
        // gone (its single-flight belonged to the released session).
        assertEquals(1, capturedRequests.size)
        assertEquals("Channel 0", capturedRequests[0].title)
    }

    // ── Transcode fallback execution ──────────────────────────────────────────

    @Test
    fun `transcode fallback re-resolves under TRANSCODE and reloads the engine`() = runTest(scheduler) {
        stubChannels(2)
        stubResolve()
        val session = createSession()
        session.initialize("ch-0", null, null)
        assertEquals(1, capturedRequests.size)

        onTranscodeFallback!!.invoke()

        assertEquals(2, capturedRequests.size)
        assertEquals(LivePlayMethod.TRANSCODE, capturedRequests.last().playMethod)
        val methodChange = received.filterIsInstance<LivePlaybackEvent.PlayMethodChanged>().last()
        assertEquals(LivePlayMethod.TRANSCODE, methodChange.method)
    }

    @Test
    fun `fallback failure emits TranscodeFallbackFailed with the engine's held detail`() = runTest(scheduler) {
        stubChannels(1)
        stubResolve()
        val session = createSession()
        session.initialize("ch-0", null, null)

        coEvery {
            playbackRepo.resolvePlayable(any())
        } returns PlaybackResolution.Unplayable
        engineErrorDetailFlow.value = "boom"

        onTranscodeFallback!!.invoke()

        val failed = received.filterIsInstance<LivePlaybackEvent.TranscodeFallbackFailed>().single()
        val message = failed.message as UiMessage.Resource
        assertEquals(Res.string.live_error_transcode_fallback, message.res)
        assertEquals(listOf("Channel 0"), message.args)
        assertEquals("boom", failed.engineErrorDetail)
        assertEquals(1, capturedRequests.size, "no reload without a resolved URL")
    }

    // ── Watchdog decision execution ───────────────────────────────────────────

    @Test
    fun `a buffering stall past the watchdog window emits BufferingWatchdogTimedOut`() = runTest(scheduler) {
        stubChannels(2)
        stubResolve()
        val session = createSession()
        session.initialize("ch-0", null, null)

        engineStateFlow.value = LiveEngineState.BUFFERING
        scheduler.advanceTimeBy(19_999L)
        assertTrue(received.none { it is LivePlaybackEvent.BufferingWatchdogTimedOut })

        scheduler.advanceTimeBy(2_000L)
        assertEquals(1, received.filterIsInstance<LivePlaybackEvent.BufferingWatchdogTimedOut>().size)
    }
}
