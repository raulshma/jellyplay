package com.raulshma.jellyplay.feature.music.albumdetail

import android.content.Context
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import com.raulshma.jellyplay.feature.music.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * First ViewModel test in feature/music (plan 04): verifies delegation to
 * [AudioQueueFacade] and the outcome → UI-state mapping. Queue building
 * itself is covered by AudioQueueFacadeTest in core/data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val audioQueueFacade: AudioQueueFacade = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val downloadIntake: DownloadIntake = mockk(relaxed = true)

    private lateinit var viewModel: AlbumDetailViewModel

    private val albumTracks = listOf(
        MediaItem(id = "t1", name = "Track 1", mediaType = MediaType.AUDIO),
        MediaItem(id = "t2", name = "Track 2", mediaType = MediaType.AUDIO),
    )

    @Before
    fun setUp() {
        every { downloadRepository.getDownloadsByMediaItemIdsFlow(any()) } returns flowOf(emptyList())
        viewModel = AlbumDetailViewModel(
            context = context,
            mediaRepository = mediaRepository,
            imageUrlProvider = imageUrlProvider,
            audioQueueFacade = audioQueueFacade,
            downloadRepository = downloadRepository,
            downloadIntake = downloadIntake,
        )
    }

    /** Loads an album detail + tracks so `detail?.item?.name` fallbacks resolve. */
    private fun loadAlbum(albumName: String = "Album") {
        coEvery { mediaRepository.getMediaDetail("album1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = albumName, mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("album1") } returns Result.success(albumTracks)
        viewModel.loadAlbum("album1")
    }

    @Test
    fun startInstantMix_started_setsMixFirstTrackIdFromOutcomeQueue() = runTest(mainDispatcherRule.testDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Started(
            listOf(
                AudioQueueItem(id = "m1", name = "Mix 1", artist = "A", album = null, imageUrl = null, mediaSourceId = null),
                AudioQueueItem(id = "m2", name = "Mix 2", artist = "A", album = null, imageUrl = null, mediaSourceId = null),
            ),
            startIndex = 0,
        )

        viewModel.startInstantMix("album1")
        advanceUntilIdle()

        assertEquals("m1", viewModel.mixFirstTrackId)
        assertNull(viewModel.error)
        assertFalse(viewModel.isStartingMix)
        coVerify(exactly = 1) { audioQueueFacade.startInstantMix("album1", "Album", any()) }
    }

    @Test
    fun startInstantMix_empty_setsSharedMixUnavailableError() = runTest(mainDispatcherRule.testDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Empty
        every { context.getString(R.string.music_mix_unavailable) } returns "no mix tracks"

        viewModel.startInstantMix("album1")
        advanceUntilIdle()

        assertEquals("no mix tracks", viewModel.error)
        assertNull(viewModel.mixFirstTrackId)
        assertFalse(viewModel.isStartingMix)
    }

    @Test
    fun startInstantMix_failed_mapsCauseMessage() = runTest(mainDispatcherRule.testDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns
            AudioQueueOutcome.Failed(RuntimeException("boom"))

        viewModel.startInstantMix("album1")
        advanceUntilIdle()

        assertEquals("boom", viewModel.error)
        assertNull(viewModel.mixFirstTrackId)
        assertFalse(viewModel.isStartingMix)
    }

    @Test
    fun startInstantMix_suppressed_isSilent() = runTest(mainDispatcherRule.testDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Suppressed

        viewModel.startInstantMix("album1")
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertNull(viewModel.mixFirstTrackId)
        assertFalse(viewModel.isStartingMix)
    }

    @Test
    fun playAlbum_delegatesWithDetailNameFallback() = runTest(mainDispatcherRule.testDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery {
            audioQueueFacade.playTracks(any(), any(), any(), any(), any())
        } returns AudioQueueOutcome.Started(emptyList(), 0)

        viewModel.playAlbum(albumTracks, startIndex = 2)
        advanceUntilIdle()

        // Default detail-surface width (400); album fallback = the album's name.
        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(albumTracks, 2, false, "Album", ImageUrlProvider.DEFAULT_MAX_WIDTH)
        }
    }

    @Test
    fun addToQueue_delegatesSingleTrackWithDetailNameFallback() = runTest(mainDispatcherRule.testDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { audioQueueFacade.enqueueTrack(any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), -1)

        viewModel.addToQueue(albumTracks.first())
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.enqueueTrack(albumTracks.first(), "Album", ImageUrlProvider.DEFAULT_MAX_WIDTH)
        }
    }
}
