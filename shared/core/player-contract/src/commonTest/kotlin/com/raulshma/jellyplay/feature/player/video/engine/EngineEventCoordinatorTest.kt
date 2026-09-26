package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.PlaybackMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Direct unit tests for [EngineEventCoordinator]'s policies, driven
 * purely by flow fixtures (an [EngineSourceFixture] over `MutableStateFlow`s
 * + `tryEmit` channels) and an injected clock. **Zero mockk instances** — the
 * coordinator's behaviour is assertable without any engine mock because it
 * only decides, never executes.
 *
 * Moved verbatim from `:shared:feature:player-video`'s jvmTest with the
 * coordinator (candidate C4: the live player consumes the same policies): the
 * old `FakeMediaEngine` twin degrades to the [EngineSourceFixture] — the
 * coordinator now consumes [EngineEventSource], so the tests drive exactly
 * the raw flows the production hosts feed it (VOD: `MediaEngine.toEngineEventSource`;
 * live: the tuner engine's state flows mapped onto [EnginePlaybackState]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineEventCoordinatorTest {

    /**
     * Flow fixture standing in for an engine: every state-holding member is a
     * mutable flow the test can drive, mirroring the production
     * [EngineEventSource] slice.
     */
    private class EngineSourceFixture {
        val isPlayingState = MutableStateFlow(false)
        val playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
        val errorEmissions = MutableSharedFlow<EngineError>(extraBufferCapacity = 4)
        val subtitleEventEmissions = MutableSharedFlow<SubtitleEvent>(extraBufferCapacity = 4)
        var positionMs: Long = 0L

        val source: EngineEventSource = EngineEventSource(
            isPlaying = isPlayingState,
            playbackState = playbackState,
            errors = errorEmissions,
            subtitleEvents = subtitleEventEmissions,
            currentPositionMs = { positionMs },
        )

        fun advanceTo(ms: Long) {
            positionMs = ms
        }
    }

    private lateinit var fakeEngine: EngineSourceFixture
    private val passOutHours = MutableStateFlow(0)
    private var nowMs = 1_000L
    private val clock = { nowMs }

    private var playbackMode: PlaybackMode = PlaybackMode.AUTO

    /** Mirrors the production wiring's shape; arbitrary localized stand-in. */
    private val directPlayFallbackNotice: (String) -> String =
        { errorText -> "Direct Play failed — switching to transcode ($errorText)" }

    private val decisions = mutableListOf<EngineDecision>()

    /**
     * Builds a coordinator bound to [fakeEngine]'s source and starts a
     * decisions recorder on the same test scheduler.
     */
    private fun TestScope.coordinatorWithEngine(
        config: EngineEventCoordinator.Config = EngineEventCoordinator.Config(),
    ): EngineEventCoordinator {
        fakeEngine = EngineSourceFixture()
        val coordinator = EngineEventCoordinator(
            scope = backgroundScope,
            engineSource = MutableStateFlow<EngineEventSource?>(fakeEngine.source),
            getPlaybackMode = { playbackMode },
            directPlayFallbackNotice = directPlayFallbackNotice,
            passOutHours = passOutHours,
            clock = clock,
            config = config,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.decisions.collect { decisions += it }
        }
        testScheduler.runCurrent()
        return coordinator
    }

    // ── Mirrors ───────────────────────────────────────────────────────────────

    @Test
    fun isPlayingMirror_conflatesToEngineState() = runTest {
        val coordinator = coordinatorWithEngine()
        fakeEngine.isPlayingState.value = true
        testScheduler.runCurrent()
        assertTrue(coordinator.isPlaying.value)

        fakeEngine.isPlayingState.value = false
        testScheduler.runCurrent()
        assertFalse(coordinator.isPlaying.value)
    }

    @Test
    fun isBufferingMirror_tracksBufferingState() = runTest {
        val coordinator = coordinatorWithEngine()
        assertFalse(coordinator.isBuffering.value)

        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()
        assertTrue(coordinator.isBuffering.value)

        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()
        assertFalse(coordinator.isBuffering.value)
    }

    // ── ENDED / errors / subtitle toasts ─────────────────────────────────────

    @Test
    fun playbackEnded_emitsDecision() = runTest {
        val coordinator = coordinatorWithEngine()
        fakeEngine.playbackState.value = EnginePlaybackState.ENDED
        testScheduler.runCurrent()
        assertEquals(listOf<EngineDecision>(EngineDecision.PlaybackEnded), decisions)
    }

    @Test
    fun engineError_underNonForcedMode_surfacesShowError() = runTest {
        val coordinator = coordinatorWithEngine()
        val error = EngineError.Network(null)
        fakeEngine.errorEmissions.tryEmit(error)
        testScheduler.runCurrent()
        assertEquals(listOf<EngineDecision>(EngineDecision.ShowError(error, clearBuffering = false)), decisions)
    }

    @Test
    fun subtitleEvent_emitsInformUser() = runTest {
        val coordinator = coordinatorWithEngine()
        fakeEngine.subtitleEventEmissions.tryEmit(SubtitleEvent.MalformedTrackDisabled)
        testScheduler.runCurrent()
        assertEquals(
            listOf<EngineDecision>(EngineDecision.InformUser("Subtitles disabled — malformed subtitle track detected")),
            decisions,
        )
    }

    // ── Direct-play fallback latch ────────────────────────────────────────────

    @Test
    fun directPlayFallback_offeredOnceWithEnginePosition_thenLatchHolds() = runTest {
        playbackMode = PlaybackMode.FORCE_DIRECT_PLAY
        val coordinator = coordinatorWithEngine()
        fakeEngine.advanceTo(12_345L)

        fakeEngine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()

        assertEquals(
            listOf(
                EngineDecision.InformUser(
                    "Direct Play failed — switching to transcode" +
                        " (${EngineError.Decoder("h264", null).message})"
                ),
                EngineDecision.FallbackToTranscode(fromPositionMs = 12_345L),
            ),
            decisions,
        )

        // Second error: latch holds — surfaces the dialog instead.
        fakeEngine.errorEmissions.tryEmit(EngineError.Render(null))
        testScheduler.runCurrent()
        assertEquals(
            EngineDecision.ShowError(EngineError.Render(null), clearBuffering = false),
            decisions.last(),
        )
        assertEquals(3, decisions.size)
    }

    @Test
    fun directPlayFallback_onPlaybackModeChanged_reArmsLatch() = runTest {
        playbackMode = PlaybackMode.FORCE_DIRECT_PLAY
        val coordinator = coordinatorWithEngine()

        fakeEngine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()
        assertEquals(2, decisions.size)

        // Explicit user mode change re-arms the one-shot latch.
        playbackMode = PlaybackMode.FORCE_DIRECT_PLAY
        coordinator.onPlaybackModeChanged()
        fakeEngine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()

        assertEquals(4, decisions.size)
        assertEquals(EngineDecision.FallbackToTranscode(fromPositionMs = 0L), decisions.last())
    }

    @Test
    fun directPlayFallback_onNewItem_reArmsLatch() = runTest {
        playbackMode = PlaybackMode.FORCE_DIRECT_PLAY
        val coordinator = coordinatorWithEngine()

        fakeEngine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()
        assertEquals(2, decisions.size)

        coordinator.onNewItem()
        fakeEngine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()

        assertEquals(4, decisions.size)
        assertEquals(EngineDecision.FallbackToTranscode(fromPositionMs = 0L), decisions.last())
    }

    // ── External fallback intake (the live tuner's trigger model) ─────────────

    @Test
    fun fallbackExternalRequest_intakeEmitsDecisionUnlatched() = runTest {
        val coordinator = coordinatorWithEngine(
            EngineEventCoordinator.Config(fallbackPolicy = FallbackPolicy.EXTERNAL_REQUEST_ONLY),
        )
        fakeEngine.advanceTo(7_000L)

        // The live engine's per-load phase machine decides WHEN to request;
        // each request emits one decision. Two requests → two decisions.
        coordinator.onTranscodeFallbackRequested()
        testScheduler.runCurrent()
        coordinator.onTranscodeFallbackRequested()
        testScheduler.runCurrent()

        assertEquals(
            listOf<EngineDecision>(
                EngineDecision.FallbackToTranscode(fromPositionMs = 7_000L),
                EngineDecision.FallbackToTranscode(fromPositionMs = 7_000L),
            ),
            decisions,
        )
    }

    @Test
    fun fallbackExternalRequestPolicy_engineErrorAlwaysSurfaces_neverAutoFallsBack() = runTest {
        // Even with the FORCE_DIRECT_PLAY preference set, the external-request
        // policy never converts an error into an automatic fallback.
        playbackMode = PlaybackMode.FORCE_DIRECT_PLAY
        val coordinator = coordinatorWithEngine(
            EngineEventCoordinator.Config(fallbackPolicy = FallbackPolicy.EXTERNAL_REQUEST_ONLY),
        )

        fakeEngine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()

        assertEquals(
            listOf<EngineDecision>(
                EngineDecision.ShowError(EngineError.Decoder("h264", null), clearBuffering = false)
            ),
            decisions,
        )
    }

    // ── Initial-buffering watchdog ────────────────────────────────────────────

    @Test
    fun watchdog_initialBufferingPastTimeout_firesTimeoutErrorWithClearBuffering() = runTest {
        val coordinator = coordinatorWithEngine()
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(20_000L)
        testScheduler.runCurrent()

        assertEquals(
            listOf<EngineDecision>(EngineDecision.ShowError(EngineError.Timeout(), clearBuffering = true)),
            decisions,
        )
    }

    @Test
    fun watchdog_readyBeforeTimeout_doesNotFire() = runTest {
        val coordinator = coordinatorWithEngine()
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.advanceTimeBy(19_000L)
        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(60_000L)
        testScheduler.runCurrent()

        assertTrue(decisions.isEmpty())
    }

    @Test
    fun watchdog_bufferingAfterReady_doesNotReArm() = runTest {
        val coordinator = coordinatorWithEngine()
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.advanceTimeBy(1_000L)
        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()

        // A mid-playback rebuffer must not trip the start-up watchdog.
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(120_000L)
        testScheduler.runCurrent()

        assertTrue(decisions.isEmpty())
    }

    /**
     * follow-up: the READY latch is scoped to the engine instance.
     * All real reload paths re-create the engine (fresh collector, re-armed
     * watchdog), but an engine *instance* that survives a reload — e.g. a
     * reclaimed mini-player engine or a same-instance retry — stays disarmed
     * for the subsequent load. Pinned as-is; the change moves policy, it does
     * not change it.
     */
    @Test
    fun watchdog_sameEngineInstanceStaysDisarmed_quirk() = runTest {
        val coordinator = coordinatorWithEngine()
        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()

        // Same instance re-emits BUFFERING (as a surviving engine would after
        // a reload) — the collector's hasReachedReady latch still holds.
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.advanceTimeBy(30_000L)
        testScheduler.runCurrent()

        assertTrue(decisions.isEmpty())
    }

    @Test
    fun watchdog_newEngineInstance_reArms() = runTest {
        playbackMode = PlaybackMode.AUTO
        fakeEngine = EngineSourceFixture()
        val engineState = MutableStateFlow<EngineEventSource?>(null)
        val coordinator = EngineEventCoordinator(
            scope = backgroundScope,
            engineSource = engineState,
            getPlaybackMode = { playbackMode },
            directPlayFallbackNotice = directPlayFallbackNotice,
            passOutHours = passOutHours,
            clock = clock,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.decisions.collect { decisions += it }
        }

        val first = EngineSourceFixture()
        engineState.value = first.source
        first.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()
        assertTrue(decisions.isEmpty())

        // A reload swaps in a fresh engine instance: the watchdog re-arms and
        // a stuck initial buffer on the new engine now trips.
        val second = EngineSourceFixture()
        engineState.value = second.source
        testScheduler.runCurrent()
        second.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(20_000L)
        testScheduler.runCurrent()

        assertEquals(
            listOf<EngineDecision>(EngineDecision.ShowError(EngineError.Timeout(), clearBuffering = true)),
            decisions,
        )
    }

    // ── Every-episode watchdog scope (the live tuner's pin) ──────────────────

    @Test
    fun watchdog_everyEpisodeScope_firesOnMidPlaybackRebuffer() = runTest {
        val coordinator = coordinatorWithEngine(
            EngineEventCoordinator.Config(watchdogScope = WatchdogScope.EVERY_BUFFERING_EPISODE),
        )
        // First episode recovers — hasReachedReady latches, irrelevant here.
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.advanceTimeBy(1_000L)
        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()

        // A MID-PLAYBACK stall is exactly the live tuner's failure mode: the
        // every-episode scope re-arms a fresh window and it trips.
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(20_000L)
        testScheduler.runCurrent()

        assertEquals(
            listOf<EngineDecision>(EngineDecision.ShowError(EngineError.Timeout(), clearBuffering = true)),
            decisions,
        )
    }

    @Test
    fun watchdog_everyEpisodeScope_stateFlipBeforeExpiry_cancelsAndReArms() = runTest {
        val coordinator = coordinatorWithEngine(
            EngineEventCoordinator.Config(watchdogScope = WatchdogScope.EVERY_BUFFERING_EPISODE),
        )
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()

        // Leave BUFFERING before expiry: the pending window cancels...
        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.advanceTimeBy(19_000L)
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()

        // ...so the second episode is armed from zero: no fire at the first
        // window's would-be deadline (19s into it), fire 20s after the re-arm.
        testScheduler.advanceTimeBy(1_000L)
        testScheduler.runCurrent()
        assertTrue(decisions.isEmpty(), "the second episode must be armed from zero")

        testScheduler.advanceTimeBy(19_000L)
        testScheduler.runCurrent()
        assertEquals(
            listOf<EngineDecision>(EngineDecision.ShowError(EngineError.Timeout(), clearBuffering = true)),
            decisions,
        )
    }

    @Test
    fun watchdog_everyEpisodeScope_expiryAfterStateFlip_doesNotFire() = runTest {
        val coordinator = coordinatorWithEngine(
            EngineEventCoordinator.Config(watchdogScope = WatchdogScope.EVERY_BUFFERING_EPISODE),
        )
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()

        // The state flips away just before expiry (and stays there): the
        // fresh-read fire guard must not surface the timeout.
        testScheduler.advanceTimeBy(19_500L)
        fakeEngine.playbackState.value = EnginePlaybackState.READY
        testScheduler.advanceTimeBy(60_000L)
        testScheduler.runCurrent()

        assertTrue(decisions.isEmpty())
    }

    // ── Pass-out protection ───────────────────────────────────────────────────

    @Test
    fun passOut_inactiveWhilePlaybackPausedOrEngineMissing() = runTest {
        val coordinator = coordinatorWithEngine()
        passOutHours.value = 1

        // Engine exists but is paused — the poller must never trip.
        nowMs += 5L * 60L * 60L * 1000L
        testScheduler.advanceTimeBy(5L * 60L * 60L * 1000L)
        testScheduler.runCurrent()

        assertTrue(decisions.isEmpty())
    }

    @Test
    fun passOut_elapsedThresholdWhilePlaying_emitsPassOutPause() = runTest {
        val coordinator = coordinatorWithEngine()
        passOutHours.value = 1

        fakeEngine.isPlayingState.value = true
        testScheduler.runCurrent() // resume transition resets the clock at nowMs

        nowMs += 61L * 60L * 60L * 1000L
        testScheduler.advanceTimeBy(60_000L)
        testScheduler.runCurrent()

        assertEquals(listOf<EngineDecision>(EngineDecision.PassOutPause), decisions)
    }

    @Test
    fun passOut_userInteractionResetsClock() = runTest {
        val coordinator = coordinatorWithEngine()
        passOutHours.value = 1

        fakeEngine.isPlayingState.value = true
        testScheduler.runCurrent()

        // Two 50-minute quiet windows separated by an interaction: neither
        // reaches the 1-hour threshold.
        nowMs += 50L * 60L * 1000L
        testScheduler.advanceTimeBy(50L * 60L * 1000L)
        testScheduler.runCurrent()
        coordinator.onUserInteraction()
        nowMs += 50L * 60L * 1000L
        testScheduler.advanceTimeBy(50L * 60L * 1000L)
        testScheduler.runCurrent()

        assertTrue(decisions.isEmpty())
    }

    @Test
    fun passOut_resumeAfterLongPause_resetsClock_noImmediateTrip() = runTest {
        val coordinator = coordinatorWithEngine()
        passOutHours.value = 1

        // Long paused period (2h) — no trip while paused.
        nowMs += 2L * 60L * 60L * 1000L
        testScheduler.advanceTimeBy(2L * 60L * 60L * 1000L)
        testScheduler.runCurrent()
        assertTrue(decisions.isEmpty())

        // Resume: the false→true transition resets the interaction clock, so
        // the stale 2-hour gap must not immediately trip the timer.
        fakeEngine.isPlayingState.value = true
        testScheduler.runCurrent()
        nowMs += 5L * 60L * 1000L
        testScheduler.advanceTimeBy(5L * 60L * 1000L)
        testScheduler.runCurrent()

        assertTrue(decisions.isEmpty())
    }

    @Test
    fun passOut_disabledHours_neverTrips() = runTest {
        val coordinator = coordinatorWithEngine()
        passOutHours.value = 0

        fakeEngine.isPlayingState.value = true
        testScheduler.runCurrent()
        nowMs += 48L * 60L * 60L * 1000L
        testScheduler.advanceTimeBy(48L * 60L * 60L * 1000L)
        testScheduler.runCurrent()

        assertTrue(decisions.isEmpty())
    }

    // ── Disposal ──────────────────────────────────────────────────────────────

    @Test
    fun dispose_stopsAllPolicies() = runTest {
        val coordinator = coordinatorWithEngine()
        coordinator.dispose()

        // Post-dispose engine events produce no decisions.
        fakeEngine.errorEmissions.tryEmit(EngineError.Network(null))
        fakeEngine.playbackState.value = EnginePlaybackState.ENDED
        testScheduler.runCurrent()
        assertTrue(decisions.isEmpty())
        assertTrue(coordinator.disposed)
    }

    // ── MediaEngine adapter ───────────────────────────────────────────────────

    /**
     * The VOD engines arrive via [MediaEngine.toEngineEventSource]; the
     * adapter must wire every slice (and keep [EngineEventSource.currentPositionMs]
     * a LIVE read of the engine, not a snapshot).
     */
    @Test
    fun mediaEngineAdapter_wiresEverySlice_withLivePositionRead() {
        val engine = AdapterFakeEngine().apply {
            playbackState.value = EnginePlaybackState.BUFFERING
            currentPositionMs = 4_321L
        }
        val source = engine.toEngineEventSource()

        assertTrue(source.isPlaying.value == false)
        assertEquals(EnginePlaybackState.BUFFERING, source.playbackState.value)
        assertEquals(4_321L, source.currentPositionMs())

        // Live read: mutating the engine's position flows through the lambda.
        engine.currentPositionMs = 9_999L
        assertEquals(9_999L, source.currentPositionMs())
    }

    /**
     * Minimal [MediaEngine] stub for the adapter test — every member no-ops;
     * only the slices [toEngineEventSource] reads hold state.
     */
    @Suppress("LargeClass", "unused", "MemberVisibilityCanBePrivate")
    private class AdapterFakeEngine : MediaEngine {
        override val playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
        override val isPlaying = MutableStateFlow(false)
        override var currentPositionMs: Long = 0L
        override val displayName: String = "AdapterFakeEngine"
        override val durationMs: Long = 0L
        override val positionFlow = MutableStateFlow(0L)
        override val errorFlow = MutableSharedFlow<EngineError>(extraBufferCapacity = 1)
        override val subtitleEvents = MutableSharedFlow<SubtitleEvent>(extraBufferCapacity = 1)
        override val bufferedPositionMs = MutableStateFlow(0L)
        override val videoStats = MutableStateFlow(EngineVideoStats())
        override val currentCues = MutableStateFlow<List<TimedCue>>(emptyList())
        override val liveSubtitleCue = MutableStateFlow<CharSequence?>(null)
        override val pollingIntervalMs = MutableStateFlow(100L)
        override val videoStatsEnabled = MutableStateFlow(false)
        override val audioSessionId: Int = -1
        override val capabilities = EngineCapabilities()
        override val availableTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
        override val playbackSpeed: Float = 1f
        override val volume: Float = 1f

        override fun load(request: PlaybackRequest) = Unit
        override fun release() = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun selectTrack(type: com.raulshma.jellyplay.core.model.TrackType, index: Int) = Unit
        override fun setMaxVideoBitrate(bps: Int?) = Unit
        override fun setVolume(value: Float, isUserChange: Boolean) = Unit
        override fun increaseVolume(delta: Float) = Unit
        override fun decreaseVolume(delta: Float) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun setPlaybackSpeed(speed: Float) = Unit
        override fun setPollingIntervalMs(ms: Long) = Unit
        override fun setVideoStatsEnabled(enabled: Boolean) = Unit
        override fun updateConfig(config: EngineConfig) = Unit
        override fun applySubtitleStyle(style: com.raulshma.jellyplay.core.model.SubtitleStyle) = Unit
        override fun setAspectRatio(ratio: AspectRatio) = Unit
    }
}
