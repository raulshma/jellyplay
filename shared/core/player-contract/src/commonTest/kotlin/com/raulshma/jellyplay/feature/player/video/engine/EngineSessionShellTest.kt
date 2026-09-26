package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.PlaybackMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Direct unit tests for [EngineSessionShell]'s session-structural plumbing —
 * the same flow-fixture style as the [EngineEventCoordinator] suite (zero
 * mockk): a fixture engine-source over `MutableStateFlow`s + `tryEmit`
 * channels drives the coordinator, the shell's fan-out routes decisions into
 * a recording executor, and a String-typed event pipe stands in for the
 * hosts' SessionEvent/LivePlayerEvent vocabularies.
 *
 * Pinned here (the shell owns these; the hosts must not re-derive them):
 *  - the decisions fan-out reaches the host executor while alive;
 *  - the one-shot event pipe delivers host events;
 *  - [EngineSessionShell.reArm] while alive is a no-op;
 *  - dispose stops the fan-out; reArm afterwards builds a FRESH coordinator —
 *    the FORCE_DIRECT_PLAY one-shot latch resets (the VOD re-arm semantics)
 *    — and restarts the fan-out;
 *  - the external fallback intake forwards to the current coordinator
 *    (the live tuner's trigger path).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineSessionShellTest {

    /** Flow fixture standing in for an engine — mirrors the coordinator suite's. */
    private class EngineSourceFixture {
        val isPlayingState = MutableStateFlow(false)
        val playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
        val errorEmissions = MutableSharedFlow<EngineError>(extraBufferCapacity = 4)

        val source: EngineEventSource = EngineEventSource(
            isPlaying = isPlayingState,
            playbackState = playbackState,
            errors = errorEmissions,
        )
    }

    private lateinit var engine: EngineSourceFixture
    private var playbackMode: PlaybackMode = PlaybackMode.AUTO

    private val decisions = mutableListOf<EngineDecision>()
    private val hostEvents = mutableListOf<String>()
    private var rearmPokes = 0

    private fun TestScope.shellWithEngine(
        config: EngineSessionShell.Config = EngineSessionShell.Config(),
    ): EngineSessionShell<String> {
        engine = EngineSourceFixture()
        val shell = EngineSessionShell<String>(
            scope = backgroundScope,
            engineSources = MutableStateFlow<EngineEventSource?>(engine.source),
            onDecision = { decisions += it },
            config = config,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            shell.events.collect { hostEvents += it }
        }
        testScheduler.runCurrent()
        return shell
    }

    /** The VOD-style Config pin: FORCE_DIRECT_PLAY one-shot latch policy. */
    private fun oneShotConfig() = EngineSessionShell.Config(
        getPlaybackMode = { playbackMode },
        directPlayFallbackNotice = { "fallback: $it" },
        onRearmed = { rearmPokes++ },
    )

    // ── Decision fan-out ─────────────────────────────────────────────────────

    @Test
    fun decisionFanOut_engineError_reachesTheExecutor() = runTest {
        val shell = shellWithEngine()
        val error = EngineError.Network(null)

        engine.errorEmissions.tryEmit(error)
        testScheduler.runCurrent()

        assertEquals(
            listOf<EngineDecision>(EngineDecision.ShowError(error, clearBuffering = false)),
            decisions,
        )
        assertFalse(shell.disposed)
    }

    @Test
    fun decisionFanOut_playbackEnded_reachesTheExecutor() = runTest {
        shellWithEngine()

        engine.playbackState.value = EnginePlaybackState.ENDED
        testScheduler.runCurrent()

        assertEquals(listOf<EngineDecision>(EngineDecision.PlaybackEnded), decisions)
    }

    // ── One-shot event pipe ──────────────────────────────────────────────────

    @Test
    fun eventPipe_deliversHostEventsToTheCollector() = runTest {
        val shell = shellWithEngine()

        shell.emitEvent("one")
        shell.emitEvent("two")
        testScheduler.runCurrent()

        assertEquals(listOf("one", "two"), hostEvents)
    }

    // ── reArm while alive: a no-op ───────────────────────────────────────────

    @Test
    fun reArm_whileAlive_noOp_noPoke() = runTest {
        val shell = shellWithEngine(oneShotConfig())
        val coordinatorBefore = shell.coordinator

        assertFalse(shell.reArm(), "a live shell must not re-arm")
        assertEquals(coordinatorBefore, shell.coordinator)
        assertEquals(0, rearmPokes)

        // The fan-out keeps running against the unchanged coordinator.
        val error = EngineError.Render(null)
        engine.errorEmissions.tryEmit(error)
        testScheduler.runCurrent()
        assertEquals(
            listOf<EngineDecision>(EngineDecision.ShowError(error, clearBuffering = false)),
            decisions,
        )
    }

    // ── dispose + reArm: fresh coordinator, latch reset, fan-out restart ────

    @Test
    fun dispose_stopsTheFanOut_andFlagsDisposed() = runTest {
        val shell = shellWithEngine()
        shell.dispose()

        engine.errorEmissions.tryEmit(EngineError.Network(null))
        engine.playbackState.value = EnginePlaybackState.ENDED
        testScheduler.runCurrent()

        assertTrue(decisions.isEmpty(), "post-dispose engine events must not reach the executor")
        assertTrue(shell.disposed)
    }

    @Test
    fun reArm_afterDispose_freshCoordinatorResetsTheOneShotLatch_andRestartsFanOut() = runTest {
        playbackMode = PlaybackMode.FORCE_DIRECT_PLAY
        val shell = shellWithEngine(oneShotConfig())
        val coordinatorBefore = shell.coordinator

        // First error: the one-shot latch converts it into a fallback...
        engine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()
        assertEquals(
            listOf(
                EngineDecision.InformUser("fallback: ${EngineError.Decoder("h264", null).message}"),
                EngineDecision.FallbackToTranscode(fromPositionMs = 0L),
            ),
            decisions,
        )
        // ...the latch holds: the next error surfaces the dialog instead.
        engine.errorEmissions.tryEmit(EngineError.Render(null))
        testScheduler.runCurrent()
        assertEquals(EngineDecision.ShowError(EngineError.Render(null), clearBuffering = false), decisions.last())

        // Teardown disposes; a stray error stays mute.
        shell.dispose()
        assertTrue(shell.disposed)
        engine.errorEmissions.tryEmit(EngineError.Network(null))
        testScheduler.runCurrent()
        assertEquals(3, decisions.size)

        // Re-arm builds a FRESH coordinator: the one-shot latch resets (the
        // VOD per-load re-arm semantics) and the fan-out restarts.
        shell.dispose() // idempotent — must not corrupt the next re-arm
        assertTrue(shell.reArm(), "a disposed shell must re-arm")
        assertEquals(1, rearmPokes)
        assertNotEquals(coordinatorBefore, shell.coordinator)
        assertFalse(shell.disposed)
        // Let the fresh coordinator's per-engine policy collectors subscribe
        // to the (unchanged) engine-source stream before events flow — the
        // same dispatch window the hosts' re-arm sites sit before.
        testScheduler.runCurrent()

        engine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()
        assertEquals(
            listOf(
                EngineDecision.InformUser("fallback: ${EngineError.Decoder("h264", null).message}"),
                EngineDecision.FallbackToTranscode(fromPositionMs = 0L),
            ),
            decisions.drop(3),
        )
    }

    // ── External fallback intake (the live tuner's trigger path) ────────────

    @Test
    fun onTranscodeFallbackRequested_forwardsToTheCurrentCoordinator() = runTest {
        val shell = shellWithEngine(
            EngineSessionShell.Config(
                coordinator = EngineEventCoordinator.Config(
                    fallbackPolicy = FallbackPolicy.EXTERNAL_REQUEST_ONLY,
                ),
                onRearmed = { rearmPokes++ },
            ),
        )
        engine.playbackState.value = EnginePlaybackState.READY
        testScheduler.runCurrent()

        shell.onTranscodeFallbackRequested()
        testScheduler.runCurrent()

        assertEquals(
            listOf<EngineDecision>(EngineDecision.FallbackToTranscode(fromPositionMs = 0L)),
            decisions,
        )
        // The re-arm hook tracks only actual re-arms, not intakes.
        assertEquals(0, rearmPokes)
    }

    @Test
    fun onNewItem_resetsTheFallbackLatch_throughTheShell() = runTest {
        playbackMode = PlaybackMode.FORCE_DIRECT_PLAY
        val shell = shellWithEngine(oneShotConfig())

        engine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()
        assertEquals(2, decisions.size)

        shell.onNewItem()
        engine.errorEmissions.tryEmit(EngineError.Decoder("h264", null))
        testScheduler.runCurrent()

        assertEquals(4, decisions.size)
        assertEquals(EngineDecision.FallbackToTranscode(fromPositionMs = 0L), decisions.last())
    }
}
