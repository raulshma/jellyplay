package com.raulshma.jellyplay.feature.player.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressReporterTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var playbackRepo: PlaybackRepository
    private lateinit var engine: MediaEngine
    private lateinit var viewModel: ViewModel
    private lateinit var uiState: StateFlowHandle<VideoPlayerUiState>
    private lateinit var state: MutableStateFlow<VideoPlayerUiState>

    private var autoSkipCalls = mutableListOf<MediaSegment>()
    private var endedNoNextCalls = 0
    private var watchedThresholdCalls = mutableListOf<String>()

    private lateinit var reporter: PlaybackProgressReporter

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        playbackRepo = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        viewModel = object : ViewModel() {}
        state = MutableStateFlow(VideoPlayerUiState())
        uiState = StateFlowHandle(state)

        autoSkipCalls = mutableListOf()
        endedNoNextCalls = 0
        watchedThresholdCalls = mutableListOf()

        every { engine.bufferedPositionMs } returns MutableStateFlow(0L)
        every { engine.videoStats } returns MutableStateFlow(EngineVideoStats())
        every { engine.isPlaying } returns MutableStateFlow(false)

        reporter = PlaybackProgressReporter(
            playbackRepository = playbackRepo,
            viewModel = viewModel,
            uiState = uiState,
            getCurrentItemId = { "item1" },
            getPlaySessionId = { "session1" },
            getResolvedPlayMethod = { PlayMethod.DIRECT_PLAY },
            getMediaEngine = { engine },
            getIncognitoModeEnabled = { false },
            onAutoSkip = { autoSkipCalls += it },
            onPlaybackEndedNoNext = { endedNoNextCalls++ },
            onWatchedThresholdReached = { watchedThresholdCalls += it },
            onPositionPersisted = {},
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startPositionTracking_updatesPositionDurationAndBufferedFromEngine() {
        every { engine.durationMs } returns 3_600_000L
        every { engine.positionFlow } returns flowOf(1_200_000L)

        reporter.startPositionTracking()

        assertEquals(1_200_000L, uiState.value.currentPosition)
        assertEquals(3_600_000L, uiState.value.duration)
    }

    @Test
    fun startPositionTracking_noEngine_returnsImmediately() {
        reporter = PlaybackProgressReporter(
            playbackRepository = playbackRepo,
            viewModel = viewModel,
            uiState = uiState,
            getCurrentItemId = { "item1" },
            getPlaySessionId = { "session1" },
            getResolvedPlayMethod = { PlayMethod.DIRECT_PLAY },
            getMediaEngine = { null },
            getIncognitoModeEnabled = { false },
            onAutoSkip = { autoSkipCalls += it },
            onPlaybackEndedNoNext = { endedNoNextCalls++ },
            onWatchedThresholdReached = { watchedThresholdCalls += it },
            onPositionPersisted = {},
        )
        reporter.startPositionTracking()
        assertEquals(0L, uiState.value.currentPosition)
    }

    @Test
    fun checkEndedNoNext_firesWhenWithin500MsOfEndAndNoNextEpisode() {
        every { engine.durationMs } returns 3_600_000L
        every { engine.positionFlow } returns flowOf(3_599_700L) // within 500ms of end

        reporter.startPositionTracking()

        assertEquals(1, endedNoNextCalls)
    }

    @Test
    fun checkEndedNoNext_doesNotFireWhenNextEpisodePresent() {
        state.value = VideoPlayerUiState(nextEpisode = mockk(relaxed = true))
        every { engine.durationMs } returns 3_600_000L
        every { engine.positionFlow } returns flowOf(3_599_700L)

        reporter.startPositionTracking()

        assertEquals(0, endedNoNextCalls)
    }

    @Test
    fun checkEndedNoNext_doesNotFireWhenMoreThan500MsRemaining() {
        every { engine.durationMs } returns 3_600_000L
        every { engine.positionFlow } returns flowOf(3_599_000L) // 1000ms before end

        reporter.startPositionTracking()

        assertEquals(0, endedNoNextCalls)
    }

    @Test
    fun checkEndedNoNext_firesOnlyOnce() {
        val positions = MutableStateFlow(3_599_700L)
        every { engine.durationMs } returns 3_600_000L
        every { engine.positionFlow } returns positions

        reporter.startPositionTracking()
        positions.value = 3_599_800L
        positions.value = 3_599_900L

        assertEquals(1, endedNoNextCalls)
    }

    @Test
    fun checkEndedNoNext_skippedWhenDurationUnknown() {
        every { engine.durationMs } returns 0L
        every { engine.positionFlow } returns flowOf(3_599_700L)

        reporter.startPositionTracking()

        assertEquals(0, endedNoNextCalls)
    }

    @Test
    fun checkAutoSkip_firesForAutoSkipSegment() {
        state.value = VideoPlayerUiState(
            segments = listOf(
                MediaSegment(
                    id = "seg1", itemId = "item1", type = MediaSegmentType.COMMERCIAL,
                    startTicks = 0, endTicks = 10_000 * 10_000, // 0..10s
                ),
            ),
            segmentBehaviors = mapOf(MediaSegmentType.COMMERCIAL to SegmentBehavior.AUTO_SKIP),
        )
        every { engine.durationMs } returns 60_000L
        every { engine.positionFlow } returns flowOf(5_000L) // inside segment

        reporter.startPositionTracking()

        assertEquals(1, autoSkipCalls.size)
        assertEquals(MediaSegmentType.COMMERCIAL, autoSkipCalls[0].type)
    }

    @Test
    fun checkAutoSkip_dedupsAcrossPositions() {
        state.value = VideoPlayerUiState(
            segments = listOf(
                MediaSegment(
                    id = "seg1", itemId = "item1", type = MediaSegmentType.COMMERCIAL,
                    startTicks = 0, endTicks = 10_000 * 10_000,
                ),
            ),
            segmentBehaviors = mapOf(MediaSegmentType.COMMERCIAL to SegmentBehavior.AUTO_SKIP),
        )
        val positions = MutableStateFlow(5_000L)
        every { engine.durationMs } returns 60_000L
        every { engine.positionFlow } returns positions

        reporter.startPositionTracking()
        positions.value = 6_000L
        positions.value = 7_000L

        assertEquals(1, autoSkipCalls.size)
    }

    @Test
    fun checkAutoSkip_ignoredForShowButtonBehavior() {
        state.value = VideoPlayerUiState(
            segments = listOf(
                MediaSegment(
                    id = "seg1", itemId = "item1", type = MediaSegmentType.INTRO,
                    startTicks = 0, endTicks = 10_000 * 10_000,
                ),
            ),
            segmentBehaviors = mapOf(MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON),
        )
        every { engine.durationMs } returns 60_000L
        every { engine.positionFlow } returns flowOf(5_000L)

        reporter.startPositionTracking()

        assertEquals(0, autoSkipCalls.size)
    }

    @Test
    fun watchedThreshold_firesAt95Percent() {
        every { engine.durationMs } returns 100_000L
        every { engine.positionFlow } returns flowOf(95_000L) // exactly 95%

        reporter.startPositionTracking()

        assertEquals(listOf("item1"), watchedThresholdCalls)
    }

    @Test
    fun watchedThreshold_firesOnlyOnce() {
        val positions = MutableStateFlow(95_000L)
        every { engine.durationMs } returns 100_000L
        every { engine.positionFlow } returns positions

        reporter.startPositionTracking()
        positions.value = 97_000L
        positions.value = 99_000L

        assertEquals(1, watchedThresholdCalls.size)
    }

    @Test
    fun watchedThreshold_doesNotFireBelow95Percent() {
        every { engine.durationMs } returns 100_000L
        every { engine.positionFlow } returns flowOf(94_999L)

        reporter.startPositionTracking()

        assertEquals(0, watchedThresholdCalls.size)
    }

    @Test
    fun reportStart_incognitoMode_skipsRepository() {
        reporter = PlaybackProgressReporter(
            playbackRepository = playbackRepo,
            viewModel = viewModel,
            uiState = uiState,
            getCurrentItemId = { "item1" },
            getPlaySessionId = { "session1" },
            getResolvedPlayMethod = { PlayMethod.DIRECT_PLAY },
            getMediaEngine = { engine },
            getIncognitoModeEnabled = { true },
            onAutoSkip = { autoSkipCalls += it },
            onPlaybackEndedNoNext = { endedNoNextCalls++ },
            onWatchedThresholdReached = { watchedThresholdCalls += it },
            onPositionPersisted = {},
        )

        kotlinx.coroutines.runBlocking {
            reporter.reportStart("item1", "session1", "source1", PlayMethod.DIRECT_PLAY)
        }

        coVerify(exactly = 0) {
            playbackRepo.reportPlaybackStart(any())
        }
    }

    @Test
    fun reportStopAndRelease_incognitoMode_skipsRepository() {
        reporter = PlaybackProgressReporter(
            playbackRepository = playbackRepo,
            viewModel = viewModel,
            uiState = uiState,
            getCurrentItemId = { "item1" },
            getPlaySessionId = { "session1" },
            getResolvedPlayMethod = { PlayMethod.DIRECT_PLAY },
            getMediaEngine = { engine },
            getIncognitoModeEnabled = { true },
            onAutoSkip = { autoSkipCalls += it },
            onPlaybackEndedNoNext = { endedNoNextCalls++ },
            onWatchedThresholdReached = { watchedThresholdCalls += it },
            onPositionPersisted = {},
        )
        every { engine.currentPositionMs } returns 5_000L

        reporter.reportStopAndRelease("item1", "session1")

        coVerify(exactly = 0) {
            playbackRepo.reportPlaybackStopped(any(), any(), any())
        }
    }

    @Test
    fun reportStopAndRelease_zeroPosition_skipsRepositoryReport() {
        every { engine.currentPositionMs } returns 0L

        reporter.reportStopAndRelease("item1", "session1")

        coVerify(exactly = 0) {
            playbackRepo.reportPlaybackStopped(any(), any(), any())
        }
    }

    @Test
    fun cancelJobs_resetsThresholdAndEndedFlags() {
        every { engine.durationMs } returns 3_600_000L
        every { engine.positionFlow } returns flowOf(3_599_700L)
        reporter.startPositionTracking()
        assertEquals(1, endedNoNextCalls)
        assertEquals(1, watchedThresholdCalls.size)

        reporter.cancelJobs()

        // After cancelJobs, a fresh tracking run must be able to re-fire once.
        every { engine.positionFlow } returns flowOf(3_599_800L)
        reporter.startPositionTracking()
        assertEquals(2, endedNoNextCalls)
    }
}
