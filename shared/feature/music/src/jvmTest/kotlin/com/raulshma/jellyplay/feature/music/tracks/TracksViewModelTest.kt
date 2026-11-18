package com.raulshma.jellyplay.feature.music.tracks

import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import com.raulshma.jellyplay.core.data.repository.LibraryFeed
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.Rule

/**
 * Pins the dense-music-list width contract (MUSIC_MAX_WIDTH = 300) through
 * captured facade arguments: widths must not silently
 * homogenize.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TracksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val libraryFeed: LibraryFeed = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val audioQueueFacade: MusicQueuePlayer = mockk()

    private lateinit var viewModel: TracksViewModel

    private val tracks = listOf(
        MediaItem(id = "t1", name = "Track 1", mediaType = MediaType.AUDIO),
        MediaItem(id = "t2", name = "Track 2", mediaType = MediaType.AUDIO),
        MediaItem(id = "t3", name = "Track 3", mediaType = MediaType.AUDIO),
    )

    @BeforeTest
    fun setUp() {
        viewModel = TracksViewModel(
            libraryFeed = libraryFeed,
            imageUrlProvider = imageUrlProvider,
            artworkUrls = ArtworkUrls.EMPTY,
            audioQueueFacade = audioQueueFacade,
        )
    }

    @Test
    fun playAll_delegatesWithMusicMaxWidthAndStartIndex() = runTest(mainDispatcherRule.testDispatcher) {
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
    fun addToQueue_delegatesSingleTrackWithMusicMaxWidth() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { audioQueueFacade.enqueueTrack(any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), -1)

        viewModel.addToQueue(tracks.first())
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.enqueueTrack(tracks.first(), null, ImageUrlProvider.MUSIC_MAX_WIDTH)
        }
    }
}
