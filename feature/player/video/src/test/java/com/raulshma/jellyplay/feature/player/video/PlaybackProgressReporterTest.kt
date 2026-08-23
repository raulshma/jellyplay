package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackProgressReporterTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + testDispatcher)

    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var uiState: StateFlowHandle<VideoPlayerUiState>
    private lateinit var mediaEngine: MediaEngine
    private lateinit var reporter: PlaybackProgressReporter

    private var positionTicks: MutableList<Long> = mutableListOf()
    private var watchedThresholdItemIds: MutableList<String> = mutableListOf()
    private var autoSkippedSegments: MutableList<MediaSegment> = mutableListOf()

    @After
    fun tearDownScope() {
        scope.cancel()
    }

    @Before
    fun setUp() {
        playbackRepository = mockk(relaxed = true)
        uiState = StateFlowHandle(MutableStateFlow(VideoPlayerUiState()))
        mediaEngine = mockk(relaxed = true)

        positionTicks.clear()
        watchedThresholdItemIds.clear()
        autoSkippedSegments.clear()

        every { mediaEngine.positionFlow } returns flowOf(0L, 5_000L, 95_000L)
        every { mediaEngine.durationMs } returns 100_000L
        every { mediaEngine.bufferedPositionMs } returns MutableStateFlow(100_000L)
        every { mediaEngine.videoStats } returns MutableStateFlow(EngineVideoStats())
        every { mediaEngine.isPlaying } returns MutableStateFlow(true)
        every { mediaEngine.currentPositionMs } returns 5_000L

        reporter = PlaybackProgressReporter(
            playbackRepository = playbackRepository,
            scope = scope,
            uiState = uiState,
            getCurrentItemId = { "movie-123" },
            getPlaySessionId = { "session-456" },
            getResolvedPlayMethod = { PlayMethod.DIRECT_PLAY },
            getMediaEngine = { mediaEngine },
            getIncognitoModeEnabled = { false },
            onAutoSkip = { autoSkippedSegments.add(it) },
            onPlaybackEndedNoNext = {},
            onWatchedThresholdReached = { watchedThresholdItemIds.add(it) },
            onPositionPersisted = { positionTicks.add(it) },
            onEnginePositionUpdate = { _, _, _, _ -> },
        )
    }

    @Test
    fun startPositionTracking_triggersPositionPersistedAndWatchedThreshold() = runTest {
        reporter.startPositionTracking()

        assertTrue(positionTicks.contains(95_000L))
        assertTrue(watchedThresholdItemIds.contains("movie-123"))
    }

    @Test
    fun cancelJobs_cancelsActiveTrackingJobs() {
        reporter.startPositionTracking()
        reporter.startProgressReporting()
        reporter.cancelJobs()
    }
}
