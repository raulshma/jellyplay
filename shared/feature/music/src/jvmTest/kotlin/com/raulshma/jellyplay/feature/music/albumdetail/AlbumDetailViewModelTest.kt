package com.raulshma.jellyplay.feature.music.albumdetail

import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * First ViewModel test in feature/music (plan 04): verifies delegation to
 * [AudioQueueFacade] and the outcome → UI-state mapping. Queue building
 * itself is covered by AudioQueueFacadeTest in core/data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { downloadRepository.getDownloadsByMediaItemIdsFlow(any()) } returns flowOf(emptyList())
        viewModel = AlbumDetailViewModel(
            mediaRepository = mediaRepository,
            imageUrlProvider = imageUrlProvider,
            audioQueueFacade = audioQueueFacade,
            downloadRepository = downloadRepository,
            downloadIntake = downloadIntake,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun startInstantMix_started_setsMixFirstTrackIdFromOutcomeQueue() = runTest(mainDispatcher) {
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
    fun startInstantMix_empty_setsSharedMixUnavailableError() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Empty

        viewModel.startInstantMix("album1")
        advanceUntilIdle()

        assertSame(Res.string.music_mix_unavailable, (viewModel.error as MixErrorMessage.Resource).res)
        assertNull(viewModel.mixFirstTrackId)
        assertFalse(viewModel.isStartingMix)
    }

    @Test
    fun startInstantMix_failed_mapsCauseMessage() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns
            AudioQueueOutcome.Failed(RuntimeException("boom"))

        viewModel.startInstantMix("album1")
        advanceUntilIdle()

        assertEquals("boom", (viewModel.error as MixErrorMessage.Raw).message)
        assertNull(viewModel.mixFirstTrackId)
        assertFalse(viewModel.isStartingMix)
    }

    @Test
    fun startInstantMix_suppressed_isSilent() = runTest(mainDispatcher) {
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
    fun playAlbum_delegatesWithDetailNameFallback() = runTest(mainDispatcher) {
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
    fun addToQueue_delegatesSingleTrackWithDetailNameFallback() = runTest(mainDispatcher) {
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

    // ── Load states ──────────────────────────────────────────────────────────

    @Test
    fun loadAlbum_success_populatesDetailTracksAndClearsLoading() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()

        assertEquals(albumTracks, viewModel.tracks)
        assertEquals("Album", viewModel.detail?.item?.name)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
    }

    @Test
    fun loadAlbum_detailFailure_setsRawErrorButKeepsTracks() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getMediaDetail("album1", any()) } returns
            Result.failure(RuntimeException("no album"))
        coEvery { mediaRepository.getAlbumTracks("album1") } returns Result.success(albumTracks)

        viewModel.loadAlbum("album1")
        advanceUntilIdle()

        assertEquals("no album", (viewModel.error as MixErrorMessage.Raw).message)
        assertEquals(albumTracks, viewModel.tracks)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun loadAlbum_tracksFailure_setsRawErrorButKeepsDetail() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getMediaDetail("album1", any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("album1") } returns Result.failure(RuntimeException("no tracks"))

        viewModel.loadAlbum("album1")
        advanceUntilIdle()

        assertEquals("no tracks", (viewModel.error as MixErrorMessage.Raw).message)
        assertEquals("Album", viewModel.detail?.item?.name)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun refreshAlbum_bypassesTheDetailCache() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { mediaRepository.getMediaDetail("album1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("album1") } returns Result.success(albumTracks)

        viewModel.refreshAlbum("album1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("album1", true) }
    }

    // ── Instant mix event consumption ────────────────────────────────────────

    @Test
    fun consumeMixEvent_clearsMixFirstTrackId() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns AudioQueueOutcome.Started(
            listOf(AudioQueueItem(id = "m1", name = "Mix 1", artist = "A", album = null, imageUrl = null, mediaSourceId = null)),
            startIndex = 0,
        )
        viewModel.startInstantMix("album1")
        advanceUntilIdle()
        assertEquals("m1", viewModel.mixFirstTrackId)

        viewModel.consumeMixEvent()

        assertNull(viewModel.mixFirstTrackId)
    }

    // ── Downloads (scoped per-track lifecycle) ────────────────────────────────

    private fun download(id: String, mediaItemId: String, status: DownloadStatus) = DownloadItem(
        id = id,
        mediaItemId = mediaItemId,
        name = "Track $mediaItemId",
        mediaType = MediaType.AUDIO,
        downloadPath = "/tmp/$id",
        downloadUrl = "https://example.com/$id",
        totalSizeBytes = 100L,
        downloadedBytes = 100L,
        status = status,
    )

    /** Subscribes trackDownloads (WhileSubscribed) so its value is live.
     *  backgroundScope is auto-cancelled at test end — the collector never
     *  completes, so a plain `launch` child would trip runTest's
     *  UncompletedCoroutinesError. */
    private fun TestScope.subscribeTrackDownloads() {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.trackDownloads.collect { }
        }
    }

    @Test
    fun trackDownloads_mapsByMediaItemIdAndSkipsTheQueryWithoutTracks() = runTest(mainDispatcher) {
        // No album loaded → no downloads query at all (the IN-scoped lookup
        // must not read the whole table for an empty screen).
        subscribeTrackDownloads()
        advanceUntilIdle()
        coVerify(exactly = 0) { downloadRepository.getDownloadsByMediaItemIdsFlow(any()) }

        // Loaded album → the query is scoped to exactly the loaded track ids
        // and the rows are keyed by mediaItemId for the per-row UI.
        val downloading = download("d1", "t1", DownloadStatus.DOWNLOADING)
        every { downloadRepository.getDownloadsByMediaItemIdsFlow(listOf("t1", "t2")) } returns
            flowOf(listOf(downloading))
        loadAlbum()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.getDownloadsByMediaItemIdsFlow(listOf("t1", "t2")) }
        assertEquals(mapOf("t1" to downloading), viewModel.trackDownloads.value)
    }

    @Test
    fun downloadTrack_completedDownload_deletesItInsteadOfRestarting() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        every { downloadRepository.getDownloadsByMediaItemIdsFlow(any()) } returns
            flowOf(listOf(download("d1", "t1", DownloadStatus.COMPLETED)))
        subscribeTrackDownloads()
        advanceUntilIdle()
        coEvery { downloadRepository.deleteDownload(any()) } returns Result.success(Unit)

        viewModel.downloadTrack(albumTracks[0])
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.deleteDownload("d1") }
        coVerify(exactly = 0) { downloadIntake.start(any()) }
    }

    @Test
    fun downloadTrack_notYetDownloaded_resolvesDetailAndStartsIntake() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        every { downloadRepository.getDownloadsByMediaItemIdsFlow(any()) } returns flowOf(emptyList())
        subscribeTrackDownloads()
        advanceUntilIdle()
        coEvery { mediaRepository.getMediaDetail("t1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "t1", name = "Track 1", mediaType = MediaType.AUDIO)),
        )

        viewModel.downloadTrack(albumTracks[0])
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("t1") }
        coVerify(exactly = 1) { downloadIntake.start(any()) }
        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
    }

    @Test
    fun downloadTrack_unresolvableDetail_startsNothing() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        every { downloadRepository.getDownloadsByMediaItemIdsFlow(any()) } returns flowOf(emptyList())
        subscribeTrackDownloads()
        advanceUntilIdle()
        coEvery { mediaRepository.getMediaDetail("t1") } returns Result.failure(RuntimeException("gone"))

        viewModel.downloadTrack(albumTracks[0])
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadIntake.start(any()) }
    }

    @Test
    fun downloadAlbum_skipsCompletedTracksAndDownloadsTheRest() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        every { downloadRepository.getDownloadsByMediaItemIdsFlow(any()) } returns
            flowOf(listOf(download("d1", "t1", DownloadStatus.COMPLETED)))
        subscribeTrackDownloads()
        advanceUntilIdle()
        coEvery { mediaRepository.getMediaDetail("t2") } returns Result.success(
            MediaDetail(item = MediaItem(id = "t2", name = "Track 2", mediaType = MediaType.AUDIO)),
        )

        viewModel.downloadAlbum()
        advanceUntilIdle()

        // t1 is COMPLETED → skipped; t2 missing → started via the intake seam.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail("t1") }
        coVerify(exactly = 1) { downloadIntake.start(any()) }
        coVerify(exactly = 0) { downloadRepository.deleteDownload(any()) }
    }

    @Test
    fun downloadAlbum_withoutTracks_isANoOp() = runTest(mainDispatcher) {
        viewModel.downloadAlbum()
        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
        coVerify(exactly = 0) { downloadIntake.start(any()) }
    }

    @Test
    fun deleteAlbumDownloads_deletesOnlyExistingDownloadRows() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        every { downloadRepository.getDownloadsByMediaItemIdsFlow(any()) } returns
            flowOf(listOf(download("d1", "t1", DownloadStatus.PAUSED)))
        subscribeTrackDownloads()
        advanceUntilIdle()
        coEvery { downloadRepository.deleteDownload(any()) } returns Result.success(Unit)

        viewModel.deleteAlbumDownloads()
        advanceUntilIdle()

        coVerify(exactly = 1) { downloadRepository.deleteDownload("d1") }
        coVerify(exactly = 1) { downloadRepository.deleteDownload(any()) } // only t1's row
    }
}
