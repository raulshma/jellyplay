package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.FakeMediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct unit tests for [EngineEventCoordinator]'s policies, driven
 * purely by a [FakeMediaEngine] (state flows + `tryEmit` channels) and an
 * injected clock. **Zero mockk instances** — the coordinator's behaviour is
 * assertable without any engine mock because it only decides, never executes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineEventCoordinatorTest {

    private lateinit var fakeEngine: FakeMediaEngine
    private val passOutHours = MutableStateFlow(0)
    private var nowMs = 1_000L
    private val clock = { nowMs }

    private var playbackMode: PlaybackMode = PlaybackMode.AUTO

    /** Mirrors the production wiring's shape; arbitrary localized stand-in. */
    private val directPlayFallbackNotice: (String) -> String =
        { errorText -> "Direct Play failed — switching to transcode ($errorText)" }

    private val decisions = mutableListOf<EngineDecision>()

    /**
     * Builds a coordinator bound to [fakeEngine] and starts a decisions
     * recorder on the same test scheduler.
     */
    private fun TestScope.coordinatorWithEngine(
        config: EngineEventCoordinator.Config = EngineEventCoordinator.Config(),
    ): EngineEventCoordinator {
        fakeEngine = FakeMediaEngine()
        val coordinator = EngineEventCoordinator(
            scope = backgroundScope,
            engineFlow = MutableStateFlow<com.raulshma.jellyplay.feature.player.video.engine.MediaEngine?>(fakeEngine),
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
        assertEquals(listOf(EngineDecision.PlaybackEnded), decisions)
    }

    @Test
    fun engineError_underNonForcedMode_surfacesShowError() = runTest {
        val coordinator = coordinatorWithEngine()
        val error = EngineError.Network(null)
        fakeEngine.errorEmissions.tryEmit(error)
        testScheduler.runCurrent()
        assertEquals(listOf(EngineDecision.ShowError(error, clearBuffering = false)), decisions)
    }

    @Test
    fun subtitleEvent_emitsInformUser() = runTest {
        val coordinator = coordinatorWithEngine()
        fakeEngine.subtitleEventEmissions.tryEmit(SubtitleEvent.MalformedTrackDisabled)
        testScheduler.runCurrent()
        assertEquals(
            listOf(EngineDecision.InformUser("Subtitles disabled — malformed subtitle track detected")),
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

    // ── Initial-buffering watchdog ────────────────────────────────────────────

    @Test
    fun watchdog_initialBufferingPastTimeout_firesTimeoutErrorWithClearBuffering() = runTest {
        val coordinator = coordinatorWithEngine()
        fakeEngine.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(20_000L)
        testScheduler.runCurrent()

        assertEquals(
            listOf(EngineDecision.ShowError(EngineError.Timeout(), clearBuffering = true)),
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
        fakeEngine = FakeMediaEngine()
        val engineState = MutableStateFlow<com.raulshma.jellyplay.feature.player.video.engine.MediaEngine?>(null)
        val coordinator = EngineEventCoordinator(
            scope = backgroundScope,
            engineFlow = engineState,
            getPlaybackMode = { playbackMode },
            directPlayFallbackNotice = directPlayFallbackNotice,
            passOutHours = passOutHours,
            clock = clock,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.decisions.collect { decisions += it }
        }

        val first = FakeMediaEngine()
        engineState.value = first
        first.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()
        assertTrue(decisions.isEmpty())

        // A reload swaps in a fresh engine instance: the watchdog re-arms and
        // a stuck initial buffer on the new engine now trips.
        val second = FakeMediaEngine()
        engineState.value = second
        testScheduler.runCurrent()
        second.playbackState.value = EnginePlaybackState.BUFFERING
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(20_000L)
        testScheduler.runCurrent()

        assertEquals(
            listOf(EngineDecision.ShowError(EngineError.Timeout(), clearBuffering = true)),
            decisions,
        )
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

        assertEquals(listOf(EngineDecision.PassOutPause), decisions)
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
}
