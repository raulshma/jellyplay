package com.raulshma.jellyplay.feature.music.artistdetail

import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.feature.music.MixErrorMessage
import com.raulshma.jellyplay.feature.music.MusicQueueOutcome
import com.raulshma.jellyplay.feature.music.MusicQueuePlayer
import com.raulshma.jellyplay.feature.music.generated.resources.Res
import com.raulshma.jellyplay.feature.music.generated.resources.music_mix_unavailable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * Pins the artist detail contract on the [com.raulshma.jellyplay.core.ui.viewmodel.DeferredFetchCoordinator]
 * chassis (the album host's suite shape): all-or-nothing load (name from
 * detail + albums row published as one pair), the re-entry guard and its
 * reload-after-failure re-arm, the deferred silent regeneration (serve-stale,
 * heal, no flash) — plus instant-mix outcome mapping through the shared
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
    private val audioQueueFacade: MusicQueuePlayer = mockk()

    private lateinit var viewModel: ArtistDetailViewModel

    /** Driven by the deferred-refresh tests; collected by the VM for its lifetime. */
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)

    private val albums = listOf(
        MediaItem(id = "al1", name = "Album 1", mediaType = MediaType.ALBUM),
        MediaItem(id = "al2", name = "Album 2", mediaType = MediaType.ALBUM),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        // The deferred refresher collects this for the whole VM lifetime.
        every { mediaRepository.userDataChanges } returns userDataEvents
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
    fun loadArtist_detailFailure_setsRawErrorOverNoContent() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getMediaDetail("ar1", any()) } returns
            Result.failure(RuntimeException("no artist"))
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns Result.success(albums)

        viewModel.loadArtist("ar1")
        advanceUntilIdle()

        // All-or-nothing on the loud path too: the failed detail half fails
        // the whole fetch, so the error owns the screen (the screen renders
        // ErrorScreen whenever error != null) and no half-pair is published.
        assertEquals("no artist", (viewModel.error as MixErrorMessage.Raw).message)
        assertEquals("", viewModel.artistName)
        assertEquals(emptyList(), viewModel.albums)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun loadArtist_albumsFailure_setsRawErrorOverNoContent() = runTest(mainDispatcher) {
        // The albums half now fails the whole fetch (all-or-nothing): the old
        // ladder left it silently empty — an artist that looked to have no
        // albums, with no error and no retry path.
        coEvery { mediaRepository.getMediaDetail("ar1", any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist", mediaType = MediaType.ARTIST)),
        )
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns
            Result.failure(RuntimeException("albums gone"))

        viewModel.loadArtist("ar1")
        advanceUntilIdle()

        assertEquals("albums gone", (viewModel.error as MixErrorMessage.Raw).message)
        assertEquals("", viewModel.artistName)
        assertEquals(emptyList(), viewModel.albums)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun startInstantMix_started_setsMixFirstTrackIdFromOutcomeQueue() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns MusicQueueOutcome.Started(
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
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns MusicQueueOutcome.Empty

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
            MusicQueueOutcome.Failed(RuntimeException("boom"))

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
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns MusicQueueOutcome.Suppressed

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
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns MusicQueueOutcome.Started(
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

    // ── Re-entry guard + deferred refresh (the album host's fixture shape) ───

    @Test
    fun loadArtist_onAnAlreadyLoadedArtistIsANoOp() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()

        // Back-stack re-entry re-runs the screen's LaunchedEffect; a second
        // loud load must not refetch on top of the deferred refresh's silent
        // regeneration.
        viewModel.loadArtist("ar1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("ar1") }
        coVerify(exactly = 1) { mediaRepository.getArtistAlbums("ar1") }
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun loadArtist_afterAFailedInstantMixIsStillANoOp() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()
        // A failed mix shares the screen's `error` field; the re-entry guard
        // must key on LOAD failures only, or every re-entry after a failed
        // mix would flash a loud reload over loaded content.
        coEvery { audioQueueFacade.startInstantMix(any(), any(), any()) } returns
            MusicQueueOutcome.Failed(RuntimeException("mix boom"))
        viewModel.startInstantMix("ar1")
        advanceUntilIdle()
        assertEquals("mix boom", (viewModel.error as MixErrorMessage.Raw).message)

        viewModel.loadArtist("ar1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("ar1") }
        coVerify(exactly = 1) { mediaRepository.getArtistAlbums("ar1") }
        assertFalse(viewModel.isLoading)
        // The mix error survives the skipped re-entry.
        assertEquals("mix boom", (viewModel.error as MixErrorMessage.Raw).message)
    }

    @Test
    fun loadArtist_thrownRepoFailure_clearsSpinnerSetsErrorAndReloadsOnReEntry() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getMediaDetail("ar1", any()) } throws IllegalStateException("engine blew up")
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns Result.success(albums)

        viewModel.loadArtist("ar1")
        advanceUntilIdle()

        // The coordinator swallows the throw (re-arm + no uncaught handler);
        // without the error hook the spinner would stay up with no error UI.
        assertEquals("engine blew up", (viewModel.error as MixErrorMessage.Raw).message)
        assertFalse(viewModel.isLoading)

        // The throw must count as a failed loud load: re-entry reloads
        // (no no-op guard) and succeeds.
        coEvery { mediaRepository.getMediaDetail("ar1", any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist", mediaType = MediaType.ARTIST)),
        )
        viewModel.loadArtist("ar1")
        advanceUntilIdle()

        assertEquals("Artist", viewModel.artistName)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
    }

    @Test
    fun loadArtist_afterAFailedLoudLoadReloads() = runTest(mainDispatcher) {
        // Detail half succeeds, albums half fails: the loud load FAILED (the
        // all-or-nothing publish leaves nothing on screen behind the error),
        // so the re-entry guard must not skip — this is the flag's positive
        // case beyond the null-content guard.
        coEvery { mediaRepository.getMediaDetail("ar1", any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist", mediaType = MediaType.ARTIST)),
        )
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns
            Result.failure(RuntimeException("albums gone"))
        viewModel.loadArtist("ar1")
        advanceUntilIdle()
        assertEquals("albums gone", (viewModel.error as MixErrorMessage.Raw).message)

        // Re-entry retries the loud load and heals the failed half.
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns Result.success(albums)
        viewModel.loadArtist("ar1")
        advanceUntilIdle()

        coVerify(exactly = 2) { mediaRepository.getMediaDetail("ar1") }
        coVerify(exactly = 2) { mediaRepository.getArtistAlbums("ar1") }
        assertNull(viewModel.error)
        assertEquals(albums, viewModel.albums)
    }

    @Test
    fun deferredRefresh_successHealsAFailedLoudLoadForReEntry() = runTest(mainDispatcher) {
        // Loud failure: nothing on screen behind the error, and the failed
        // loud load armed the re-entry guard.
        coEvery { mediaRepository.getMediaDetail("ar1", any()) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist", mediaType = MediaType.ARTIST)),
        )
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns
            Result.failure(RuntimeException("albums gone"))
        viewModel.loadArtist("ar1")
        advanceUntilIdle()
        assertEquals("albums gone", (viewModel.error as MixErrorMessage.Raw).message)

        // The deferred silent regeneration succeeds and heals the screen.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        coEvery { mediaRepository.getMediaDetail("ar1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist", mediaType = MediaType.ARTIST)),
        )
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns Result.success(albums)
        userDataEvents.emit(UserDataChange("user-1", listOf("al1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // Healed: the load error cleared with the fresh pair published —
        // a stranded error would pin the error screen over fresh content.
        assertNull(viewModel.error)
        assertEquals(albums, viewModel.albums)

        // ...and the healed artist is an already-loaded artist again: re-entry
        // no-ops instead of flash-reloading over the healed content.
        viewModel.loadArtist("ar1")
        advanceUntilIdle()
        coVerify(exactly = 1) { mediaRepository.getMediaDetail("ar1") }
        coVerify(exactly = 1) { mediaRepository.getMediaDetail("ar1", true) }
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun deferredRefresh_rerunsSilentlyWithoutBlankingContent() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()
        assertFalse(viewModel.isLoading)

        // A write confirmed while the artist screen is NOT on screen only
        // marks the artist stale.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        coEvery { mediaRepository.getMediaDetail("ar1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist 2", mediaType = MediaType.ARTIST)),
        )
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns Result.success(albums)
        userDataEvents.emit(UserDataChange("user-1", listOf("al1")))
        advanceUntilIdle()

        // Re-entry fires the single deferred regeneration — force + silent:
        // the fetch runs but never drops the content into a loading state.
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("ar1", true) }
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
        assertEquals("Artist 2", viewModel.artistName)
    }

    @Test
    fun deferredRefresh_failureKeepsLastContentInsteadOfFlashingError() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()

        viewModel.deferredRefresher.onScreenActiveChanged(false)
        coEvery { mediaRepository.getMediaDetail("ar1", true) } returns Result.failure(RuntimeException("offline blip"))
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns Result.success(albums)
        userDataEvents.emit(UserDataChange("user-1", listOf("al1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // The silent refetch failed — serve-stale-while-revalidate keeps the
        // last name/albums on screen instead of flashing an error.
        coVerify(exactly = 1) { mediaRepository.getMediaDetail("ar1", true) }
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.error)
        assertEquals("Artist", viewModel.artistName)
        assertEquals(albums, viewModel.albums)

        // The failed silent half re-arms the deferred refresh: the next
        // re-entry retries the regeneration instead of trusting the consumed
        // flag (which would pin the pre-change data forever).
        coEvery { mediaRepository.getMediaDetail("ar1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist 2", mediaType = MediaType.ARTIST)),
        )
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        assertEquals("Artist 2", viewModel.artistName)
    }

    @Test
    fun deferredRefresh_failedHalfKeepsTheWholeStalePairInsteadOfMixing() = runTest(mainDispatcher) {
        loadArtist()
        advanceUntilIdle()

        // The name re-fetches fine, the albums row fails: publishing only the
        // fresh half would show a new name beside the pre-change albums.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        coEvery { mediaRepository.getMediaDetail("ar1", true) } returns Result.success(
            MediaDetail(item = MediaItem(id = "ar1", name = "Artist 2", mediaType = MediaType.ARTIST)),
        )
        coEvery { mediaRepository.getArtistAlbums("ar1", any()) } returns
            Result.failure(RuntimeException("albums blip"))
        userDataEvents.emit(UserDataChange("user-1", listOf("al1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        assertNull(viewModel.error)
        assertEquals("Artist", viewModel.artistName, "the stale pair stays whole — no fresh/stale mix")
        assertEquals(albums, viewModel.albums)
    }

    // The no-stack/skip-re-arm choreography pair this suite used to re-pin is
    // module behaviour now — DeferredFetchCoordinatorTest owns that table;
    // this suite pins the host adapter's own surfaces (pair publish, heal,
    // guard, spinner).

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
