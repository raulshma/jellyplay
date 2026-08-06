package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The tested seam for every [MediaEngine] backend.
 *
 * This abstract JUnit4 base holds the invariant assertions every engine must
 * satisfy; each concrete backend extends it and supplies a specimen via
 * [createEngine]. JUnit4 does not instantiate abstract classes, so the base
 * itself is never "run" — only the inherited `@Test`s of each concrete
 * subclass execute, each against its own specimen (the standard "specimen"
 * / shared-contract pattern).
 *
 * The invariants are split into two levels so the same suite can cover
 * backends with very different runtime profiles:
 *
 *  - **Level 0 (universal)** — pure construction-time invariants and "control
 *    calls before load do not throw" guards. Every engine, including
 *    [NoOpEngine], must pass these. No driving of internal state is required.
 *  - **Level 1 (behavioral)** — exercised by driving the engine's internal
 *    state via the `drive…` hooks and asserting via the [MediaEngine] reactive
 *    surface. Gated by [behavioralDrivingSupported]; engines whose internal
 *    state cannot be driven from a unit test (the real Android/JNI backends
 *    in this phase) leave the hook returning `false` and Level-1 tests are
 *    auto-skipped via [assumeTrue].
 *
 * Adding a new universal invariant → add one `@Test` here; it runs against
 * every specimen automatically. Engine-specific behavior → `@Test`s in that
 * engine's concrete subclass.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MediaEngineContractTest {

    protected lateinit var engine: MediaEngine

    @Before
    fun setUp() {
        engine = createEngine()
    }

    // ── Specimen contract ──
    protected abstract fun createEngine(): MediaEngine

    /**
     * Whether [engine]'s internal state can be driven from a unit test so the
     * Level-1 behavioral invariants are meaningful. `true` only for the fake
     * (whose every state-holding property is a mutable flow the test can poke).
     */
    protected open fun behavioralDrivingSupported(): Boolean = false

    /**
     * Whether [engine] resolves its [MediaEngine.currentPositionMs] synchronously
     * from a `seekTo` call (so a Level-1 seek invariant can assert immediately).
     */
    protected open fun supportsSynchronousSeek(): Boolean = false

    /**
     * Whether [engine.createSurfaceView] is expected to return a usable View in
     * this test environment. Engines whose surface path touches native code that
     * cannot run under Robolectric return `false` (the View test is skipped).
     */
    protected open fun supportsViewCreation(): Boolean = false

    /**
     * Whether the specimen's [MediaEngine.capabilities] must exactly equal a
     * known [EngineCapabilityMatrix] entry, and if so which one. When non-null,
     * `capabilities_matchMatrix_whenDeclared` asserts identity with it.
     */
    protected open fun expectedCapabilityMatrix(): EngineCapabilities? = null

    /**
     * The expected [MediaEngine.displayName] for this specimen, or `null` to
     * assert only that the name is non-blank. Real engines return their matching
     * [com.raulshma.jellyplay.core.model.PlayerType.displayName].
     */
    protected open fun expectedDisplayName(): String? = null

    // ── Driver hooks (the fake implements these; other specimens leave them
    //     as the default no-ops so Level-1 tests `assumeTrue`-skip). ──
    protected open fun drivePlaybackState(state: EnginePlaybackState) {}
    protected open fun driveIsPlaying(value: Boolean) {}
    protected open fun driveBufferedPosition(ms: Long) {}
    protected open fun drivePosition(ms: Long) {}
    protected open fun driveCurrentCues(cues: List<TimedCue>) {}
    protected open fun driveLiveSubtitleCue(text: CharSequence?) {}
    protected open fun emitError(error: EngineError) {}
    protected open fun emitSubtitleEvent(event: SubtitleEvent) {}

    // ═══════════════════════════════════════════════════════════════════════
    // LEVEL 0 — universal invariants. Every engine, including NoOpEngine.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun initialState_playbackState_isIdle() {
        assertEquals(EnginePlaybackState.IDLE, engine.playbackState.value)
    }

    @Test
    fun initialState_isPlaying_isFalse() {
        assertFalse(engine.isPlaying.value)
    }

    @Test
    fun initialState_currentPosition_isZero() {
        assertEquals(0L, engine.currentPositionMs)
    }

    @Test
    fun initialState_duration_isZero() {
        assertEquals(0L, engine.durationMs)
    }

    @Test
    fun initialState_playbackSpeed_isOne() {
        assertEquals(1f, engine.playbackSpeed, 0f)
    }

    @Test
    fun initialState_volume_isFull() {
        assertEquals(1f, engine.volume, 0f)
    }

    @Test
    fun initialState_availableTracks_isEmpty() {
        assertTrue(engine.availableTracks.value.isEmpty())
    }

    @Test
    fun initialState_bufferedPosition_isZero() {
        assertEquals(0L, engine.bufferedPositionMs.value)
    }

    @Test
    fun initialState_currentCues_isEmpty() {
        assertTrue(engine.currentCues.value.isEmpty())
    }

    @Test
    fun initialState_liveSubtitleCue_isNull() {
        assertNull(engine.liveSubtitleCue.value)
    }

    @Test
    fun initialState_videoStatsEnabled_isFalse() {
        assertFalse(engine.videoStatsEnabled.value)
    }

    @Test
    fun initialState_videoStats_isDefaults() {
        assertEquals(EngineVideoStats(), engine.videoStats.value)
    }

    @Test
    fun initialState_underlyingPlayer_isNull() {
        // Every engine starts with no built player; the contract guarantees a
        // nullable default rather than a stale handle.
        assertNull(engine.underlyingPlayer)
    }

    @Test
    fun controlCalls_beforeLoad_doNotThrow() {
        // None of these may throw before load(); a freshly constructed engine
        // must tolerate the full control surface. `load()` is deliberately
        // excluded: for the real Android/JNI backends it builds the native
        // player (media3 ExoPlayer, libmpv, libVLC), which is neither safe nor
        // in scope for a Level-0 unit test (see plan §7 risk #3). The fake and
        // NoOp cover their own `load()` no-throw contract directly.
        engine.play()
        engine.pause()
        engine.stop()
        engine.seekTo(0L)
        engine.setPlaybackSpeed(1f)
        engine.setVolume(0.5f)
        engine.setMuted(true)
        engine.setMuted(false)
        engine.selectTrack(TrackType.AUDIO, 0)
        engine.setMaxVideoBitrate(null)
        engine.setAspectRatio(0)
        engine.updateConfig(EngineConfig())
        engine.setPollingIntervalMs(500L)
        engine.setVideoStatsEnabled(true)
    }

    @Test
    fun release_isIdempotent_andSafe() {
        // release() may be called multiple times (lifecycle races, error
        // paths) and control calls after release must not crash the host.
        engine.release()
        engine.release()
        engine.play()
        engine.pause()
        engine.stop()
    }

    @Test
    fun displayName_isNotBlank() {
        assertTrue(
            "displayName must not be blank",
            engine.displayName.isNotBlank(),
        )
        expectedDisplayName()?.let { expected ->
            assertEquals(expected, engine.displayName)
        }
    }

    @Test
    fun capabilities_matchMatrix_whenDeclared() {
        val expected = expectedCapabilityMatrix() ?: return
        assertEquals(expected, engine.capabilities)
    }

    @Test
    fun createSurfaceView_returnsNonNullView() {
        assumeTrue(
            "specimen does not create a usable View in this environment",
            supportsViewCreation(),
        )
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = engine.createSurfaceView(context)
        assertNotNull(view)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LEVEL 1 — behavioral invariants. Driven via hooks, asserted via the
    // interface. Auto-skipped when behavioralDrivingSupported() is false.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun playbackStateMachine_idleToBufferingToReadyToEnded() {
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        assertEquals(EnginePlaybackState.IDLE, engine.playbackState.value)

        drivePlaybackState(EnginePlaybackState.BUFFERING)
        assertEquals(EnginePlaybackState.BUFFERING, engine.playbackState.value)

        drivePlaybackState(EnginePlaybackState.READY)
        assertEquals(EnginePlaybackState.READY, engine.playbackState.value)

        drivePlaybackState(EnginePlaybackState.ENDED)
        assertEquals(EnginePlaybackState.ENDED, engine.playbackState.value)
    }

    @Test
    fun isPlaying_tracksDrivenValues() {
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        assertFalse(engine.isPlaying.value)

        driveIsPlaying(true)
        assertTrue(engine.isPlaying.value)

        driveIsPlaying(false)
        assertFalse(engine.isPlaying.value)
    }

    @Test
    fun bufferedPosition_tracksDrivenValues() {
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        assertEquals(0L, engine.bufferedPositionMs.value)

        driveBufferedPosition(5_000L)
        assertEquals(5_000L, engine.bufferedPositionMs.value)
    }

    @Test
    fun liveSubtitleCue_exposesDrivenLine() {
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        assertNull(engine.liveSubtitleCue.value)

        driveLiveSubtitleCue("hello world")
        assertEquals("hello world", engine.liveSubtitleCue.value.toString())

        driveLiveSubtitleCue(null)
        assertNull(engine.liveSubtitleCue.value)
    }

    @Test
    fun currentCues_accumulateInOrder() {
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        assertTrue(engine.currentCues.value.isEmpty())

        val first = TimedCue(0L, 1_000L, "one")
        val second = TimedCue(1_000L, 2_000L, "two")
        driveCurrentCues(listOf(first))
        assertEquals(listOf(first), engine.currentCues.value)

        driveCurrentCues(listOf(first, second))
        assertEquals(listOf(first, second), engine.currentCues.value)
    }

    @Test
    fun errorFlow_carriesTaxonomyAndRetryableFlag() {
        // Skip BEFORE entering runTest: runTest arms a global uncaught-exception
        // handler on entry, which would otherwise capture exceptions leaked by
        // unrelated tests sharing the same JVM fork and attribute them to this
        // (skipped) test. Checking the assumption first keeps the skip clean.
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        runTest {
            // errorFlow is a hot SharedFlow with no replay: the collector must be
            // subscribed before the emission or the value is lost. Collect first,
            // then drive.
            val received = CompletableDeferred<EngineError>()
            val collector = launch {
                engine.errorFlow.collect { received.complete(it) }
            }
            runCurrent()
            emitError(EngineError.Network(cause = null))
            val error = received.await()
            collector.cancel()
            assertTrue("Network errors must be retryable", error.retryable)
        }
    }

    @Test
    fun subtitleEvents_areForwarded() {
        // See errorFlow_carriesTaxonomyAndRetryableFlag: skip before runTest.
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        runTest {
            // subtitleEvents is also a hot SharedFlow with no replay — subscribe
            // before emitting (see errorFlow_carriesTaxonomyAndRetryableFlag).
            val received = CompletableDeferred<SubtitleEvent>()
            val collector = launch {
                engine.subtitleEvents.collect { received.complete(it) }
            }
            runCurrent()
            emitSubtitleEvent(SubtitleEvent.MalformedTrackDisabled)
            val event = received.await()
            collector.cancel()
            assertEquals(SubtitleEvent.MalformedTrackDisabled, event)
        }
    }

    // ── Volume clamp (behavioral). Real engines only apply/clamp volume once
    //    the native player exists, so these post-set state checks run only for
    //    the fake (which clamps unconditionally). NoOp's own clamp contract is
    //    pinned in NoOpEngineTest, and each real engine's is covered by its
    //    instrumented test. Kept in Level 1 because they gate on the same
    //    behavioral-driving flag as the rest of this block. ──

    @Test
    fun volume_clampsToUnitRange_highValue() {
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        engine.setVolume(2f)
        assertEquals(1f, engine.volume, 0f)
    }

    @Test
    fun volume_clampsToUnitRange_negativeValue() {
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        engine.setVolume(-1f)
        assertEquals(0f, engine.volume, 0f)
    }

    @Test
    fun seekTo_updatesCurrentPosition() {
        assumeTrue(
            "specimen does not resolve seek synchronously",
            supportsSynchronousSeek(),
        )
        engine.seekTo(3_000L)
        assertEquals(3_000L, engine.currentPositionMs)
    }

    @Test
    fun positionFlow_emitsDrivenPositions() {
        assumeTrue("behavioral driving not supported", behavioralDrivingSupported())
        runTest {
            // positionFlow resolves to the engine's current position source.
            // Drive first (the specimen swaps that source), then collect and
            // assert the first emission — collecting before the drive would
            // observe the pre-drive source instead.
            drivePosition(8_000L)
            val received = CompletableDeferred<Long>()
            val collector = launch {
                engine.positionFlow.collect { received.complete(it) }
            }
            runCurrent()
            assertEquals(8_000L, received.await())
            collector.cancel()
        }
    }
}
