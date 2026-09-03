package com.raulshma.jellyplay.feature.music.artistdetail

import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.music.MixErrorMessage
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_mix_unavailable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pins the artist detail contract: parallel load (name from detail, albums
 * list, loading flag cleared), the detail-failure split (Raw error, albums
 * still applied), and instant-mix outcome mapping through the shared
 * [MixErrorMessage] seam — Started captures `queue.first().id`, Empty maps to
 * the shared localized resource, Failed to the cause message, Suppressed silent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val audioQueueFacade: AudioQueueFacade = mockk()

    private lateinit var viewModel: ArtistDetailViewModel

    private val albums = listOf(
        MediaItem(id = "al1", name = "Album 1", mediaType = MediaType.ALBUM),
        MediaItem(id = "al2", name = "Album 2", mediaType = MediaType.ALBUM),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        viewModel = ArtistDetailViewModel(
            mediaRepository = mediaRepository,
            imageUrlProvider = imageUrlProvider,
            audioQueueFacade = audioQueueFacade,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Loads an artist detail + albums so the screen state is populated. */
    private fun loadArtist(artistName: String = "Artist") {
        coEvery { mediaRepository.getMediaDetail("ar1", any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = artistName, mediaType = MediaType.ARTIST)),
        )
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns Result.success(albums)
        viewModel.loadArtist("ar1")
    }

    @Test
    fun loadArtist_populatesNameAlbumsAndClearsLoading() = runTest(mainDispatcher) {
        loadArtist("Artist")

        advanceUntilIdle()

        assertEquals("Artist", viewModel.artistName)
        assertEquals(albums, viewModel.albums)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
    }

    @Test
    fun loadArtist_detailFailure_setsRawErrorButKeepsAlbums() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getMediaDetail("ar1", any()) } returns
            Result.failure(RuntimeException("no artist"))
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns Result.success(albums)

        viewModel.loadArtist("ar1")
        advanceUntilIdle()

        assertEquals("no artist", (viewModel.error as MixErrorMessage.Raw).message)
        assertEquals(albums, viewModel.albums)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun startInstantMix_started_setsMixFirstTrackIdFromOutcomeQueue() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Started(
            listOf(
                AudioQueueItem(id = "m1", name = "Mix 1", artist = "A", album = null, imageUrl = null, mediaSourceId = null),
                AudioQueueItem(id = "m2", name = "Mix 2", artist = "A", album = null, imageUrl = null, mediaSourceId = null),
            ),
            startIndex = 0,
        )

        viewModel.startInstantMix("ar1")
        advanceUntilIdle()

        assertEquals("m1", viewModel.mixFirstTrackId)
        assertNull(viewModel.error)
        assertFalse(viewModel.isStartingMix)
        // No album fallback on the artist surface (the former track.album
        // fallback was a no-op) — and no guard argument.
        coVerify(exactly = 1) { audioQueueFacade.startInstantMix("ar1", null, any()) }
    }

    @Test
    fun startInstantMix_empty_setsSharedMixUnavailableError() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Empty

        viewModel.startInstantMix("ar1")
        advanceUntilIdle()

        assertSame(Res.string.music_mix_unavailable, (viewModel.error as MixErrorMessage.Resource).res)
        assertNull(viewModel.mixFirstTrackId)
        assertFalse(viewModel.isStartingMix)
    }

    @Test
    fun startInstantMix_failed_mapsCauseMessage() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns
            AudioQueueOutcome.Failed(RuntimeException("boom"))

        viewModel.startInstantMix("ar1")
        advanceUntilIdle()

        assertEquals("boom", (viewModel.error as MixErrorMessage.Raw).message)
        assertNull(viewModel.mixFirstTrackId)
        assertFalse(viewModel.isStartingMix)
    }

    @Test
    fun startInstantMix_suppressed_isSilent() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Suppressed

        viewModel.startInstantMix("ar1")
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertNull(viewModel.mixFirstTrackId)
        assertFalse(viewModel.isStartingMix)
    }

    @Test
    fun consumeMixEvent_clearsMixFirstTrackId() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Started(
            listOf(
                AudioQueueItem(id = "m1", name = "Mix 1", artist = "A", album = null, imageUrl = null, mediaSourceId = null),
            ),
            startIndex = 0,
        )
        viewModel.startInstantMix("ar1")
        advanceUntilIdle()
        assertEquals("m1", viewModel.mixFirstTrackId)

        viewModel.consumeMixEvent()

        assertNull(viewModel.mixFirstTrackId)
    }

    // ── Load failure split: albums failure is silent ─────────────────────────

    @Test
    fun loadArtist_albumsFailure_isSilentAndStillClearsLoading() = runTest(mainDispatcher) {
        // The albums branch has an onSuccess but no onFailure: a failed album
        // read leaves the list empty and surfaces no error (the detail is the
        // screen's identity — if that fails, the error path fires instead).
        coEvery { mediaRepository.getMediaDetail("ar1", any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist", mediaType = MediaType.ARTIST)),
        )
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns
            Result.failure(RuntimeException("albums gone"))

        viewModel.loadArtist("ar1")
        advanceUntilIdle()

        assertEquals("Artist", viewModel.artistName)
        assertEquals(emptyList(), viewModel.albums)
        assertNull(viewModel.error)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun refreshArtist_bypassesTheDetailCache() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()
        coEvery { mediaRepository.getMediaDetail("ar1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist", mediaType = MediaType.ARTIST)),
        )
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns Result.success(albums)

        viewModel.refreshArtist("ar1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("ar1", true) }
        assertEquals(albums, viewModel.albums)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun getImageUrl_andBackdrop_delegateToProvider() {
        every { imageUrlProvider.getImageUrl("i1") } returns "img"
        every { imageUrlProvider.getBackdropUrl("i1") } returns "bd"

        assertEquals("img", viewModel.getImageUrl("i1"))
        assertEquals("bd", viewModel.getBackdropUrl("i1"))
    }
}
