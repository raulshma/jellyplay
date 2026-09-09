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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

    /** Driven by the deferred-refresh tests; collected by the VM for its lifetime. */
    private val userDataEvents =
        MutableSharedFlow<com.raulshma.jellyplay.core.model.UserDataChange>(extraBufferCapacity = 16)

    private val albumTracks = listOf(
        MediaItem(id = "t1", name = "Track 1", mediaType = MediaType.AUDIO),
        MediaItem(id = "t2", name = "Track 2", mediaType = MediaType.AUDIO),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { downloadRepository.getDownloadsByMediaItemIdsFlow(any()) } returns flowOf(emptyList())
        // The deferred refresher collects this for the whole VM lifetime.
        every { mediaRepository.userDataChanges } returns userDataEvents
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
    fun loadAlbum_thrownRepoFailure_clearsSpinnerSetsErrorAndReloadsOnReEntry() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getMediaDetail("album1", any()) } throws IllegalStateException("engine blew up")
        coEvery { mediaRepository.getAlbumTracks("album1") } returns Result.success(albumTracks)

        viewModel.loadAlbum("album1")
        advanceUntilIdle()

        // The coordinator swallows the throw (re-arm + no uncaught handler);
        // without the error hook the spinner would stay up with no error UI.
        assertEquals("engine blew up", (viewModel.error as MixErrorMessage.Raw).message)
        assertFalse(viewModel.isLoading)

        // The throw must count as a failed loud load: re-entry reloads
        // (no no-op guard) and succeeds.
        coEvery { mediaRepository.getMediaDetail("album1", any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)),
        )
        viewModel.loadAlbum("album1")
        advanceUntilIdle()

        assertEquals("Album", viewModel.detail?.item?.name)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
    }

    @Test
    fun refreshAlbum_bypassesTheDetailCache() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        coEvery { mediaRepository.getMediaDetail("album1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("album1", true) } returns Result.success(albumTracks)

        viewModel.refreshAlbum("album1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("album1", true) }
    }

    @Test
    fun loadAlbum_onAnAlreadyLoadedAlbumIsANoOp() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()

        // Back-stack re-entry re-runs the screen's LaunchedEffect; a second
        // loud load must not refetch on top of the deferred refresh's silent
        // regeneration.
        viewModel.loadAlbum("album1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("album1") }
        coVerify(exactly = 1) { mediaRepository.getAlbumTracks("album1") }
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun loadAlbum_afterAFailedInstantMixIsStillANoOp() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        // A failed mix shares the screen's `error` field; the re-entry guard
        // must key on LOAD failures only, or every re-entry after a failed
        // mix would flash a loud reload over loaded content.
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns
            AudioQueueOutcome.Failed(RuntimeException("mix boom"))
        viewModel.startInstantMix("album1")
        advanceUntilIdle()
        assertEquals("mix boom", (viewModel.error as MixErrorMessage.Raw).message)

        viewModel.loadAlbum("album1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("album1") }
        coVerify(exactly = 1) { mediaRepository.getAlbumTracks("album1") }
        assertFalse(viewModel.isLoading)
        // The mix error survives the skipped re-entry.
        assertEquals("mix boom", (viewModel.error as MixErrorMessage.Raw).message)
    }

    @Test
    fun loadAlbum_afterAFailedLoudLoadReloads() = runTest(mainDispatcher) {
        // Detail half succeeds, tracks half fails: the detail is on screen
        // but the loud load FAILED, so the re-entry guard must not skip —
        // this is the flag's positive case beyond the null-detail guard.
        coEvery { mediaRepository.getMediaDetail("album1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("album1") } returns Result.failure(RuntimeException("no tracks"))
        viewModel.loadAlbum("album1")
        advanceUntilIdle()
        assertEquals("no tracks", (viewModel.error as MixErrorMessage.Raw).message)
        assertEquals("Album", viewModel.detail?.item?.name)

        // Re-entry retries the loud load and heals the failed half.
        coEvery { mediaRepository.getAlbumTracks("album1") } returns Result.success(albumTracks)
        viewModel.loadAlbum("album1")
        advanceUntilIdle()

        coVerify(exactly = 2) { mediaRepository.getMediaDetail("album1") }
        coVerify(exactly = 2) { mediaRepository.getAlbumTracks("album1") }
        assertNull(viewModel.error)
    }

    @Test
    fun deferredRefresh_successHealsAFailedLoudLoadForReEntry() = runTest(mainDispatcher) {
        // Loud partial failure: detail on screen, tracks failed, load error
        // set — the re-entry guard is armed by the failed loud load.
        coEvery { mediaRepository.getMediaDetail("album1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("album1") } returns Result.failure(RuntimeException("no tracks"))
        viewModel.loadAlbum("album1")
        advanceUntilIdle()
        assertEquals("no tracks", (viewModel.error as MixErrorMessage.Raw).message)

        // The deferred silent regeneration succeeds and heals the screen.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        coEvery { mediaRepository.getMediaDetail("album1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("album1", true) } returns Result.success(albumTracks)
        userDataEvents.emit(com.raulshma.jellyplay.core.model.UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // Healed: the load error cleared with the fresh pair published —
        // a stranded error would pin the error screen over fresh content.
        assertNull(viewModel.error)
        assertEquals(albumTracks, viewModel.tracks)

        // ...and the healed album is an already-loaded album again: re-entry
        // no-ops instead of flash-reloading over the healed content.
        viewModel.loadAlbum("album1")
        advanceUntilIdle()
        coVerify(exactly = 1) { mediaRepository.getMediaDetail("album1") }
        coVerify(exactly = 1) { mediaRepository.getMediaDetail("album1", true) }
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun deferredRefresh_rerunsSilentlyWithoutBlankingContent() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()
        assertFalse(viewModel.isLoading)

        // A write confirmed while the album screen is NOT on screen only
        // marks the track list stale.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        coEvery { mediaRepository.getMediaDetail("album1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("album1", true) } returns Result.success(albumTracks)
        userDataEvents.emit(com.raulshma.jellyplay.core.model.UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()

        // Re-entry fires the single deferred regeneration — force + silent:
        // the fetch runs but never drops the content into a loading state.
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("album1", true) }
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
        assertEquals("Album", viewModel.detail?.item?.name)
    }

    @Test
    fun deferredRefresh_failureKeepsLastContentInsteadOfFlashingError() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()

        viewModel.deferredRefresher.onScreenActiveChanged(false)
        coEvery { mediaRepository.getMediaDetail("album1", true) } returns Result.failure(RuntimeException("offline blip"))
        coEvery { mediaRepository.getAlbumTracks("album1", true) } returns Result.success(albumTracks)
        userDataEvents.emit(com.raulshma.jellyplay.core.model.UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // The silent refetch failed — serve-stale-while-revalidate keeps the
        // last detail/tracks on screen instead of flashing an error.
        coVerify(exactly = 1) { mediaRepository.getMediaDetail("album1", true) }
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
        assertEquals("Album", viewModel.detail?.item?.name)
        assertEquals(albumTracks, viewModel.tracks)

        // The failed silent half re-arms the deferred refresh: the next
        // re-entry retries the regeneration instead of trusting the consumed
        // flag (which would pin the pre-change data forever).
        coEvery { mediaRepository.getMediaDetail("album1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album 2", mediaType = MediaType.ALBUM)),
        )
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        assertEquals("Album 2", viewModel.detail?.item?.name)
    }

    @Test
    fun deferredRefresh_failedHalfKeepsTheWholeStalePairInsteadOfMixing() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()

        // Detail re-fetches fine, the track list fails: publishing only the
        // fresh half would show new detail beside the pre-change track rows.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        coEvery { mediaRepository.getMediaDetail("album1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "album1", name = "Album 2", mediaType = MediaType.ALBUM)),
        )
        coEvery { mediaRepository.getAlbumTracks("album1", true) } returns Result.failure(RuntimeException("tracks blip"))
        userDataEvents.emit(com.raulshma.jellyplay.core.model.UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertEquals("Album", viewModel.detail?.item?.name, "the stale pair stays whole — no fresh/stale mix")
        assertEquals(albumTracks, viewModel.tracks)
    }

    @Test
    fun deferredRefresh_doesNotStackSilentTwinOnInFlightLoudLoad() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()

        // Armed while off-screen...
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(com.raulshma.jellyplay.core.model.UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()

        // ...then a loud refresh starts and parks mid-fetch when the deferred
        // effect consumes the flag: the loud load is the regeneration, so no
        // silent twin may stack on top of it.
        val gate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getMediaDetail("album1", any()) } coAnswers {
            gate.await()
            Result.success(MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)))
        }
        coEvery { mediaRepository.getAlbumTracks("album1", true) } returns Result.success(albumTracks)
        viewModel.refreshAlbum("album1")
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // Two track fetches total (initial + in-flight loud), not three.
        coVerify(exactly = 2) { mediaRepository.getAlbumTracks("album1", any()) }

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Album", viewModel.detail?.item?.name)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun deferredRefresh_skippedByInFlightLoudLoad_rearmsForTheNextReentry() = runTest(mainDispatcher) {
        loadAlbum()
        advanceUntilIdle()

        // Armed while off-screen...
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(com.raulshma.jellyplay.core.model.UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()

        // ...then a loud refresh parks mid-fetch when the deferred effect
        // consumes the flag: the silent twin skips (two fetches, not three) —
        // but the skip must re-arm, or the parked loud load's pre-change
        // result strands the change until the next WS event.
        val gate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getMediaDetail("album1", any()) } coAnswers {
            gate.await()
            Result.success(MediaDetail(item = MediaItem(id = "album1", name = "Album", mediaType = MediaType.ALBUM)))
        }
        coEvery { mediaRepository.getAlbumTracks("album1", any()) } returns Result.success(albumTracks)
        viewModel.refreshAlbum("album1")
        advanceUntilIdle()
        // The loud load is now PARKED mid-detail-fetch when the deferred
        // effect consumes the flag: the silent twin skips itself, and the
        // skip must re-arm — the parked load dispatched before the change
        // landed, so its result is pre-change data.
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Album", viewModel.detail?.item?.name)
        coVerify(exactly = 2) { mediaRepository.getAlbumTracks("album1", any()) }

        // The next re-entry fires the quiet forced regeneration the skip
        // promised — still silent: no loading state.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 3) { mediaRepository.getAlbumTracks("album1", any()) }
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
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
