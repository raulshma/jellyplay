package com.raulshma.jellyplay.feature.music.tracks

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Pins the dense-music-list width contract (MUSIC_MAX_WIDTH = 300) through
 * captured facade arguments (plan 04 risk 4: widths must not silently
 * homogenize during the facade migration).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TracksViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val audioQueueFacade: AudioQueueFacade = mockk()

    private lateinit var viewModel: TracksViewModel

    private val tracks = listOf(
        MediaItem(id = "t1", name = "Track 1", mediaType = MediaType.AUDIO),
        MediaItem(id = "t2", name = "Track 2", mediaType = MediaType.AUDIO),
        MediaItem(id = "t3", name = "Track 3", mediaType = MediaType.AUDIO),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        viewModel = TracksViewModel(
            mediaRepository = mediaRepository,
            imageUrlProvider = imageUrlProvider,
            audioQueueFacade = audioQueueFacade,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun playAll_delegatesWithMusicMaxWidthAndStartIndex() = runTest(mainDispatcher) {
        coEvery {
            audioQueueFacade.playTracks(any(), any(), any(), any(), any())
        } returns AudioQueueOutcome.Started(emptyList(), 0)

        viewModel.playAll(tracks, startIndex = 3)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(tracks, 3, false, null, ImageUrlProvider.MUSIC_MAX_WIDTH)
        }
    }

    @Test
    fun addToQueue_delegatesSingleTrackWithMusicMaxWidth() = runTest(mainDispatcher) {
        coEvery { audioQueueFacade.enqueueTrack(any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), -1)

        viewModel.addToQueue(tracks.first())
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.enqueueTrack(tracks.first(), null, ImageUrlProvider.MUSIC_MAX_WIDTH)
        }
    }
}
