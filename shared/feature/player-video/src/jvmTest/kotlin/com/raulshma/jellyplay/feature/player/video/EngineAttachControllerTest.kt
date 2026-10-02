package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.PipController
import com.raulshma.jellyplay.core.data.playback.PipTransport
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.testfixtures.FakeMediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [EngineAttachController]'s engine-attach choreography — the ordered
 * body the ViewModel's `init` engineFlow collector used to inline, extracted
 * so the ORDER is testable for the first time (the VM is too heavy to
 * construct in jvmTest, the [SeekUserInitiatedWiringTest] situation this
 * suite replaces with a real construction). The two load-bearing ordering
 * constraints travel from the former inline comments into assertions:
 * the subtitle style seed lands before everything after it, and
 * `trackSelectionHelper.onEngineRecreated` lands before the tracks
 * collector's first (initial) emission acts.
 *
 * Construction shape: recording fakes for the interface seams
 * ([RecordingActivePlayer] / [RecordingPip] / [RecordingMessageBus]), mockk
 * (relaxed + recording answers) for the concrete collaborators — the exact
 * choreography steps then append to ONE shared [callLog] whose order is
 * asserted. The engine is the shared [FakeMediaEngine] test double; its
 * StateFlows emit their initial value INLINE under the UnconfinedTestDispatcher
 * (as they do under the VM's Main-immediate collector), so the
 * attach-time choreography and the per-engine collector arms are observable
 * in one sequence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineAttachControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    /**
     * The choreography's own scope — the VM passes its real scope in
     * production, and the three per-engine collectors NEVER complete by
     * design (they park on the engine's StateFlows). Launching them as
     * children of the runTest coroutine would trip
     * UncompletedCoroutinesError; a dedicated scope cancelled in teardown
     * is the SyncPlayBridgeTest pattern.
     */
    private val controllerScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @AfterTest
    fun tearDown() {
        controllerScope.cancel()
    }

    /** The one shared order log every recording seam appends to. */
    private val callLog = mutableListOf<String>()

    private var aggregate = VideoPlayerAggregate(
        audio = AudioSlice(audioDelayMs = 500L),
        playback = PlaybackSlice(keepScreenOnDuringVideo = true),
    )

    private val activePlayer = RecordingActivePlayer(callLog)
    private val pip = RecordingPip(callLog)
    private val messageBus = RecordingMessageBus(callLog)

    private lateinit var subtitleStyle: SubtitleStyleController
    private lateinit var effects: VideoEffectsController
    private lateinit var cast: PlayerCastController
    private lateinit var trackSelectionHelper: TrackSelectionHelper
    private lateinit var subtitlePreview: SubtitlePreviewController
    private lateinit var syncPlay: SyncPlayBridge

    private lateinit var controller: EngineAttachController

    /** Value threads captured by the recording seams for content assertions. */
    private var seededCapabilities: EngineCapabilities? = null
    private val styleSeedCalls = mutableListOf<Pair<String?, Boolean>>()
    private val effectSeedDelays = mutableListOf<Long>()

    private val engine = FakeMediaEngine().apply {
        // The delay heads-up fires only when the engine CANNOT apply the
        // configured delay — the aggregate above seeds 500 ms.
        capabilities = EngineCapabilities(supportsAudioDelay = false)
    }

    @BeforeTest
    fun setUp() {
        subtitleStyle = mockk(relaxed = true)
        every { subtitleStyle.onEngineBound(any(), any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            styleSeedCalls += (args[1] as String?) to (args[2] as Boolean)
            callLog += "styleSeed(item=${args[1]},hdr=${args[2]})"
        }
        effects = mockk(relaxed = true)
        every {
            effects.seedFromPreferences(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            effectSeedDelays += firstArg<Long>()
            callLog += "effectsSeed(delay=${firstArg<Long>()})"
        }
        cast = mockk(relaxed = true)
        every { cast.updateCastStrategyForEngine(any()) } answers { callLog += "castStrategy" }
        trackSelectionHelper = mockk(relaxed = true)
        every { trackSelectionHelper.onEngineRecreated() } answers { callLog += "onEngineRecreated" }
        every { trackSelectionHelper.updateTracksFromEngine() } answers { callLog += "tracksCollector" }
        subtitlePreview = mockk(relaxed = true)
        every { subtitlePreview.onEngineCues(any()) } answers {
            callLog += "cuesCollector(${firstArg<List<TimedCue>>().size})"
        }
        syncPlay = mockk(relaxed = true)
        every { syncPlay.onPlaybackStateChanged(any()) } answers {
            callLog += "syncPlayState(${firstArg<EnginePlaybackState>()})"
        }

        controller = EngineAttachController(
            scope = controllerScope,
            activePlayerController = activePlayer,
            getAggregate = { aggregate },
            getCurrentItemId = { "item-1" },
            getSeriesIdForPip = { "series-1" },
            getIsHdr = { false },
            subtitleStyleController = subtitleStyle,
            effects = effects,
            cast = cast,
            userMessageBus = messageBus,
            pipController = pip,
            trackSelectionHelper = trackSelectionHelper,
            subtitlePreview = subtitlePreview,
            syncPlay = syncPlay,
            onEngineCapabilities = { capabilities, keepScreenOnDuringVideo ->
                seededCapabilities = capabilities
                callLog += "capabilitiesWrite(keepScreenOn=$keepScreenOnDuringVideo)"
            },
        )
    }

    // ── attach(null): the detach arm ──────────────────────────────────────────

    @Test
    fun attachNull_clearsTheRemoteRegistry_andRunsNoChoreographyStep() = testScope.runTest {
        controller.attach(null)

        assertEquals(1, activePlayer.clearCalls)
        assertEquals(0, activePlayer.boundEngines.size)
        // No choreography step, no PiP mirror write, no collector arm: the
        // detach arm is ONLY the registry clear.
        assertEquals(emptyList(), callLog.toList())
        assertEquals(0, pip.pipHasNextWrites.size)
    }

    // ── attach(engine): the ordered choreography ─────────────────────────────

    @Test
    fun attachEngine_runsTheChoreography_inTheLoadBearingOrder() = testScope.runTest {
        controller.attach(engine)

        // EXACT order: the remote-control bind first, the subtitle style seed
        // second (it MUST precede everything that can rebuild an engine
        // config — the former inline comment's constraint, now asserted),
        // the capability mirror + effects seed + cast strategy + delay
        // heads-up next, the PiP mirror, and the track-selection reset
        // BEFORE the tracks collector's initial emission acts.
        assertEquals(
            listOf(
                "bindEngine",
                "styleSeed(item=item-1,hdr=false)",
                "capabilitiesWrite(keepScreenOn=true)",
                "effectsSeed(delay=500)",
                "castStrategy",
                "delayNotice",
                "pipHasNext",
                "onEngineRecreated",
                "tracksCollector",
                "cuesCollector(0)",
                "syncPlayState(IDLE)",
            ),
            callLog.toList(),
        )
        // Value threads: the capability lambda saw THE engine's capabilities
        // object; the style seed got the lambda-fed item id + isHdr.
        assertEquals(engine.capabilities, seededCapabilities)
        assertEquals<List<Pair<String?, Boolean>>>(listOf("item-1" to false), styleSeedCalls)
    }

    @Test
    fun attachEngine_seedsEffectsFromTheCachedAggregate_snapshotOnce() = testScope.runTest {
        aggregate = aggregate.copy(
            audio = aggregate.audio.copy(audioDelayMs = 700L),
            playback = aggregate.playback.copy(keepScreenOnDuringVideo = false),
        )

        controller.attach(engine)

        assertEquals(listOf(700L), effectSeedDelays)
        assertTrue(
            "capabilitiesWrite(keepScreenOn=false)" in callLog,
            "the keep-screen-on seed must read the attach-time aggregate snapshot, not a later one",
        )
    }

    @Test
    fun attachEngine_suppressesTheDelayHeadsUp_whenDelayIsZeroOrSupported() = testScope.runTest {
        // Supported by the engine → silent even with a configured delay.
        engine.capabilities = EngineCapabilities(supportsAudioDelay = true)
        controller.attach(engine)
        assertEquals(0, messageBus.infoMessages.size)

        // Delay == 0 → silent (the common case stays quiet) even unsupported.
        setUp()
        engine.capabilities = EngineCapabilities(supportsAudioDelay = false)
        aggregate = aggregate.copy(audio = aggregate.audio.copy(audioDelayMs = 0L))
        controller.attach(engine)
        assertEquals(0, messageBus.infoMessages.size)
    }

    // ── the per-engine collectors ────────────────────────────────────────────

    @Test
    fun availableTracksEmission_forwardsToTheTrackHelper() = testScope.runTest {
        controller.attach(engine)
        callLog.clear()

        engine.tracks.value = listOf(track(TrackType.AUDIO, index = 0))

        assertEquals(listOf("tracksCollector"), callLog.toList())
    }

    @Test
    fun currentCuesEmission_forwardsToTheCuePreview() = testScope.runTest {
        controller.attach(engine)
        callLog.clear()

        engine.currentCuesState.value = listOf(TimedCue(0L, 1_000L, "hello"))

        assertEquals(listOf("cuesCollector(1)"), callLog.toList())
    }

    @Test
    fun playbackStateEmission_forwardsToSyncPlay_andAutoExitsPipOnEndOrError_only() = testScope.runTest {
        controller.attach(engine)
        pip.isInPip.value = true
        callLog.clear()

        // PAUSE-adjacent states never exit (users pause to read — the former
        // inline comment's exclusion, pinned here via READY).
        engine.simulateState(EnginePlaybackState.READY)
        assertEquals(listOf("syncPlayState(READY)"), callLog.toList())
        assertEquals(0, pip.autoExitRequests)

        // ENDED exits.
        engine.simulateState(EnginePlaybackState.ENDED)
        assertEquals(
            listOf("syncPlayState(READY)", "syncPlayState(ENDED)"),
            callLog.toList(),
        )
        assertEquals(1, pip.autoExitRequests)

        // ERROR exits again.
        engine.simulateState(EnginePlaybackState.ERROR)
        assertEquals(2, pip.autoExitRequests)
    }

    @Test
    fun playbackStateAutoExit_doesNotFire_whenNotInPip() = testScope.runTest {
        controller.attach(engine)
        callLog.clear()

        engine.simulateState(EnginePlaybackState.ENDED)
        engine.simulateState(EnginePlaybackState.ERROR)

        assertEquals(
            listOf("syncPlayState(ENDED)", "syncPlayState(ERROR)"),
            callLog.toList(),
        )
        assertEquals(0, pip.autoExitRequests)
    }

    // ── re-attach cancels the previous collectors ────────────────────────────

    @Test
    fun reattach_cancelsThePreviousCollectionJob() = testScope.runTest {
        val first = FakeMediaEngine().apply { capabilities = EngineCapabilities(supportsAudioDelay = true) }
        val second = FakeMediaEngine().apply { capabilities = EngineCapabilities(supportsAudioDelay = true) }

        controller.attach(first)
        controller.attach(second)
        val tracksFired = callLog.count { it == "tracksCollector" }
        assertEquals(2, tracksFired, "each attach's initial availableTracks emission fans out once")

        callLog.clear()
        // The cancelled first attach's collector must be dead: emitting on the
        // OLD engine forwards nothing.
        first.tracks.value = listOf(track(TrackType.AUDIO, index = 0))
        first.simulateState(EnginePlaybackState.ENDED)
        assertEquals(emptyList(), callLog.toList())
        assertEquals(0, pip.autoExitRequests)

        // The new engine's collectors are alive.
        second.tracks.value = listOf(track(TrackType.AUDIO, index = 0))
        assertEquals(listOf("tracksCollector"), callLog.toList())
    }

    private fun track(type: TrackType, index: Int) = MediaTrack(
        id = "$type-$index",
        index = index,
        label = "track-$index",
        language = null,
        isSelected = false,
        type = type,
    )
}

/** Recording fake of the remote-control engine registry. */
private class RecordingActivePlayer(private val log: MutableList<String>) : ActivePlayerController {
    val boundEngines = mutableListOf<com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine>()
    var clearCalls = 0

    override val engine: com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine? get() = boundEngines.lastOrNull()
    override val screenshotRequests: MutableSharedFlow<Unit> = MutableSharedFlow()

    override fun bindEngine(engine: com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine) {
        boundEngines += engine
        log += "bindEngine"
    }

    override fun clearEngine() {
        clearCalls++
    }
}

/** Recording fake of the PiP port: only the writes the choreography makes. */
private class RecordingPip(private val log: MutableList<String>) : PipController {
    val isInPip = MutableStateFlow(false)
    val pipHasNextWrites = mutableListOf<Boolean>()
    var autoExitRequests = 0

    override val isInPipMode: StateFlow<Boolean> get() = isInPip
    override val pipDismissed: StateFlow<Boolean> = MutableStateFlow(false)
    override var pipTransport: PipTransport? = null
    override var pipHasNext: Boolean
        get() = pipHasNextWrites.lastOrNull() ?: false
        set(value) {
            pipHasNextWrites += value
            log += "pipHasNext"
        }

    override fun setPlaying(playing: Boolean) = Unit
    override fun setControlsLocked(locked: Boolean) = Unit
    override fun requestAutoEnterPip(shouldEnter: Boolean) = Unit
    override fun requestAutoExitPip() {
        autoExitRequests++
    }
    override fun consumeAutoExitPip() = Unit
    override fun clearPipDismissed() = Unit
    override fun setPipAspectRatio(aspect: Pair<Int, Int>?) = Unit
    override fun updatePipSourceRect(left: Int, top: Int, right: Int, bottom: Int) = Unit
    override fun reset() = Unit
}

/** Recording fake of the player message bus: only the dynamic-text info arm. */
private class RecordingMessageBus(private val log: MutableList<String>) : PlayerVideoMessageBus {
    val infoMessages = mutableListOf<String>()

    override fun info(message: String) {
        infoMessages += message
        log += "delayNotice"
    }

    override fun error(message: String) = Unit
    override fun info(message: PlayerVideoMessage) = Unit
}
