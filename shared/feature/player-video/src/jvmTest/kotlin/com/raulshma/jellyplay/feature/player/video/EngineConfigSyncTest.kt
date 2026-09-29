package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.DeinterlaceMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.core.testfixtures.FakeMediaEngine
import com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [EngineConfigSync] — the runtime engine-config sync extracted
 * from the ViewModel (the immediate `updateConfigWithUiState` and the
 * drag-settling `updateConfigWithUiStateDebounced` paths). Pins the
 * load-bearing debounce semantics that moved VERBATIM: a burst of rapid
 * dirty-marks coalesces into exactly one build+dispatch after
 * [CONFIG_SYNC_DEBOUNCE_MS]; the coalesced build reads the state at FIRE
 * time and dispatches to the engine current at that moment (an engine swap
 * in flight never leaks the dispatch to the swapped-out engine — the
 * pre-extraction `configSyncJob` had no swap-cancellation path; its only
 * cancellation is its scope dying); and the immediate path stays
 * synchronous. StandardTestDispatcher + virtual time. No ViewModel, no
 * uiState.
 *
 * The collector lives on a production-shaped [CoroutineScope] (the
 * ViewModel-scope stand-in) whose scheduler is shared with the [TestScope]
 * driving the virtual clock — the SyncPlayBridgeTest teardown pattern,
 * because the debounce collector is deliberately persistent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineConfigSyncTest {

    private val collectorDispatcher = StandardTestDispatcher()
    private val collectorScope = CoroutineScope(SupervisorJob() + collectorDispatcher)
    private val testScope = TestScope(collectorDispatcher)

    // Mirror fakes (the VM-side state the narrow lambdas read).
    private var subtitleStyle = SubtitleStyle()
    private var videoEffects = VideoEffectsConfig()
    private var dialogueBoostEnabled = false
    private var dialogueBoostStrength = EffectStrength.MODERATE
    private var mediaStreams: List<MediaStream> = emptyList()
    private var effectsState = AudioEffectsState()
    private var aggregate = VideoPlayerAggregate()
    private var engineSpecific: com.raulshma.jellyplay.core.model.EngineSpecificConfig? = null
    private var deinterlace = DeinterlaceMode.AUTO
    private var engine: FakeMediaEngine? = FakeMediaEngine()

    // Counts every dispatch attempt (the engine handle consulted) — the
    // sink for the without-engine no-op pin and the window-timing pins.
    private var engineReads = 0

    private lateinit var controller: EngineConfigSync

    @BeforeTest
    fun setUp() {
        engineReads = 0
        engine = FakeMediaEngine()
        controller = buildController(collectorScope)
        // StandardTestDispatcher queues the constructor's collector launch;
        // runCurrent subscribes it (in production viewModelScope starts the
        // collector promptly on the main dispatcher).
        testScope.runCurrent()
    }

    @AfterTest
    fun tearDown() {
        // The debounce collector is persistent by design — without this
        // teardown a leaked collector can throw into the next test class in
        // the same JVM (UncaughtExceptionsBeforeTest).
        collectorScope.cancel()
    }

    private fun buildController(scope: CoroutineScope): EngineConfigSync = EngineConfigSync(
        scope = scope,
        getSubtitleStyle = { subtitleStyle },
        getVideoEffects = { videoEffects },
        isDialogueBoostEnabled = { dialogueBoostEnabled },
        getDialogueBoostStrength = { dialogueBoostStrength },
        getMediaStreams = { mediaStreams },
        getEffectsState = { effectsState },
        getAggregate = { aggregate },
        getEngineSpecific = { engineSpecific },
        getDeinterlace = { deinterlace },
        getEngine = { engineReads++; engine },
    )

    // ── the immediate path (the former updateConfigWithUiState) ────────────

    @Test
    fun markDirty_buildsAndDispatchesSynchronously_noVirtualTime() {
        videoEffects = VideoEffectsConfig(brightness = 40f)

        controller.markDirty()

        // One build+dispatch, synchronously on the caller.
        assertEquals(1, engineReads)
        assertEquals(1, engine!!.appliedConfigs.size)
        assertEquals(VideoEffectsConfig(brightness = 40f), engine!!.appliedConfigs.single().videoEffects)
        // The immediate path does not ride the debounce: when the window
        // elapses nothing further fires.
        testScope.advanceTimeBy(CONFIG_SYNC_DEBOUNCE_MS)
        testScope.runCurrent()
        assertEquals(1, engineReads, "no deferred second dispatch behind markDirty")
    }

    @Test
    fun markDirty_carriesTheBuilderMapping_throughTheNarrowSlices() {
        subtitleStyle = SubtitleStyle(offsetMs = 42L, fontSize = 30)
        videoEffects = VideoEffectsConfig(saturation = 1.5f)
        deinterlace = DeinterlaceMode.ON
        mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "HDR10"))

        controller.markDirty()

        val config = engine!!.appliedConfigs.single()
        assertEquals(42L, config.subtitleDelayMs)
        assertEquals(subtitleStyle, config.subtitleStyle)
        assertEquals(VideoEffectsConfig(saturation = 1.5f), config.videoEffects)
        assertEquals(DeinterlaceMode.ON, config.deinterlace)
        assertTrue(config.hdrSource, "the HDR gate rides every runtime build via the streams slice")
    }

    @Test
    fun markDirty_withoutEngine_buildsButDoesNotDispatch() {
        engine = null

        controller.markDirty()
        controller.markDirtyDebounced()
        testScope.advanceTimeBy(CONFIG_SYNC_DEBOUNCE_MS)
        testScope.runCurrent()

        // Both paths consulted the (absent) engine handle — the build ran,
        // the `engine?.updateConfig` dispatch no-oped, nothing crashed.
        assertEquals(2, engineReads)
    }

    // ── the debounced path (the former updateConfigWithUiStateDebounced) ───

    @Test
    fun markDirtyDebounced_burstOfDragFramesCoalescesIntoOneDispatchAtWindowEnd() {
        // A slider drag: 20 value-changed callbacks inside the window.
        repeat(20) { controller.markDirtyDebounced() }
        testScope.runCurrent()
        assertTrue(engine!!.appliedConfigs.isEmpty(), "inside the window nothing has dispatched yet")

        // One millisecond short of the window: still nothing.
        testScope.advanceTimeBy(CONFIG_SYNC_DEBOUNCE_MS - 1)
        testScope.runCurrent()
        assertEquals(0, engineReads, "no dispatch before the debounce window elapses")

        testScope.advanceTimeBy(1)
        testScope.runCurrent()
        assertEquals(
            1,
            engine!!.appliedConfigs.size,
            "the whole burst coalesces into ONE build+dispatch at window end",
        )
    }

    @Test
    fun markDirtyDebounced_buildsFromTheStateAtFireTime_lastValueWins() {
        videoEffects = VideoEffectsConfig(brightness = 10f)
        controller.markDirtyDebounced()
        // The drag continues; the mirrors move before the window settles.
        videoEffects = VideoEffectsConfig(brightness = 80f)
        subtitleStyle = SubtitleStyle(offsetMs = 42L)
        controller.markDirtyDebounced()

        testScope.advanceTimeBy(CONFIG_SYNC_DEBOUNCE_MS)
        testScope.runCurrent()

        assertEquals(1, engine!!.appliedConfigs.size)
        val dispatched = engine!!.appliedConfigs.single()
        assertEquals(VideoEffectsConfig(brightness = 80f), dispatched.videoEffects, "the last state wins")
        assertEquals(42L, dispatched.subtitleDelayMs)
    }

    // ── engine-swap semantics (the configSyncJob cancellation shape) ───────

    @Test
    fun engineSwapWhileDebounceInFlight_dispatchLandsOnTheNewEngine() {
        val first = FakeMediaEngine()
        engine = first
        controller.markDirtyDebounced()
        testScope.runCurrent()
        assertTrue(first.appliedConfigs.isEmpty(), "still inside the debounce window")

        // Engine swap (mode/quality reload, engine switch, retry) while the
        // debounce is in flight. The pre-extraction `configSyncJob` had NO
        // swap-cancellation path — the collector survived and dispatched
        // through the live engine read at fire time. Mirrored: the pending
        // dispatch is neither dropped nor leaked to the swapped-out engine.
        val second = FakeMediaEngine()
        engine = second

        testScope.advanceTimeBy(CONFIG_SYNC_DEBOUNCE_MS)
        testScope.runCurrent()

        assertEquals(1, second.appliedConfigs.size, "the settled debounce lands on the NEW engine")
        assertTrue(first.appliedConfigs.isEmpty(), "the old engine never sees the stale dispatch")
    }

    @Test
    fun collectorJobDiesWithItsScope_pendingDispatchIsAbandoned() {
        // A dedicated scope for the collector (the production job lives in
        // the ViewModel scope — its ONLY cancellation path is that scope
        // dying, exactly as the pre-extraction `configSyncJob` had), sharing
        // the test scheduler.
        val scopedCollector = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScope.testScheduler))
        val scopedController = buildController(scopedCollector)
        testScope.runCurrent()

        scopedController.markDirtyDebounced()
        testScope.runCurrent()
        assertEquals(0, engineReads, "inside the window nothing has dispatched yet")

        // Scope death cancels the in-flight debounce — the pending dispatch
        // is abandoned even after the window elapses.
        scopedCollector.cancel()
        testScope.advanceTimeBy(CONFIG_SYNC_DEBOUNCE_MS)
        testScope.runCurrent()

        assertEquals(0, engineReads, "the scope-cancelled debounce never dispatches")
    }
}
