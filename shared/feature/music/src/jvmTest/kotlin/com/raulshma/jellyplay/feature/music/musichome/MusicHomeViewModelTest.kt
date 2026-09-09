package com.raulshma.jellyplay.feature.music.musichome

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.AudioQueueOutcome
import com.raulshma.jellyplay.core.data.playback.TrackWithAlbumFallback
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertTrue

/**
 * Pins the music home contract: section building in canonical
 * [MusicHomeSectionType] order (empty sections dropped), offline-mode gating
 * (non-ONLINE clears sections and blocks loads), the failure split (cold error
 * state with nothing cached vs transient bus toast with cached sections), and
 * the play/shuffle delegation overloads — the multi-album batch path travels
 * as [TrackWithAlbumFallback] pairs with per-album fallbacks (plan 04 risk 2).
 *
 * viewModelScope runs on `Dispatchers.Main.immediate`, which under the test
 * Main dispatcher executes launches INLINE — so every stub is recorded BEFORE
 * [createViewModel] and the init collectors' first load happens during
 * construction, not at [advanceUntilIdle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MusicHomeViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private val mediaRepository: MediaRepository = mockk()
    private val imageUrlProvider: ImageUrlProvider = mockk(relaxed = true)
    private val audioQueueFacade: AudioQueueFacade = mockk()
    private val downloadRepository: DownloadRepository = mockk()
    private val homeDiscoveryStore: HomeDiscoveryStore = mockk()
    private val offlineModeManager: OfflineModeManager = mockk()
    private val userMessageBus: MusicMessageBus = mockk()

    private val homeModeFlow = MutableStateFlow(HomeDiscoverySlice())
    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)

    /** Driven by the deferred-refresh tests; collected by the VM for its lifetime. */
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)

    private lateinit var viewModel: MusicHomeViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        every { homeDiscoveryStore.homeDiscovery } returns homeModeFlow
        every { offlineModeManager.offlineMode } returns offlineModeFlow
        every { downloadRepository.getActiveDownloadCount() } returns flowOf(0)
        every { userMessageBus.error(any()) } just Runs
        every { offlineModeManager.toggleManualOffline() } just Runs
        // The deferred refresher collects this for the whole VM lifetime.
        every { mediaRepository.userDataChanges } returns userDataEvents
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(id: String, name: String = id) =
        MediaItem(id = id, name = name, mediaType = MediaType.AUDIO)

    /**
     * Empty passthrough for every home query, optionally seeding the
     * favorite-artists section. Exact per-callsite matchers so each query
     * shape has exactly ONE active answer (no stub-precedence ambiguity).
     */
    private fun stubHomeQueries(favoriteArtists: List<MediaItem> = emptyList()) {
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } returns
            Result.success(SearchResult(favoriteArtists, favoriteArtists.size, 0))
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.AUDIO), limit = 20) } returns
            Result.success(SearchResult(emptyList(), 0, 0))
        coEvery {
            mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.success(SearchResult(emptyList(), 0, 0))
    }

    /** Constructs the VM — the init collectors run their first load inline. */
    private fun createViewModel() {
        viewModel = MusicHomeViewModel(
            mediaRepository = mediaRepository,
            imageUrlProvider = imageUrlProvider,
            audioQueueFacade = audioQueueFacade,
            downloadRepository = downloadRepository,
            homeDiscoveryStore = homeDiscoveryStore,
            offlineModeManager = offlineModeManager,
            userMessageBus = userMessageBus,
        )
    }

    @Test
    fun init_reflectsHomeModeFromDiscoverySlice() = runTest(mainDispatcher) {
        stubHomeQueries()
        homeModeFlow.value = HomeDiscoverySlice(homeMode = HomeMode.MUSIC)
        createViewModel()

        advanceUntilIdle()

        assertEquals(HomeMode.MUSIC, viewModel.uiState.value.homeMode)
    }

    @Test
    fun loadSections_buildsSectionsInCanonicalOrderSkippingEmpty() = runTest(mainDispatcher) {
        val artists = listOf(MediaItem(id = "a1", name = "Artist", mediaType = MediaType.ARTIST))
        val latest = listOf(item("l1", "Latest Album"))
        val recent = listOf(item("r1", "Recent Track"))
        val top = listOf(item("t1", "Top Album"))
        val favTracks = listOf(item("f1", "Fav Track"))
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } returns
            Result.success(SearchResult(artists, 1, 0))
        coEvery {
            mediaRepository.getMediaItems(
                parentId = null,
                filters = LibraryFilters(mediaTypes = listOf(MediaType.ALBUM), sortBy = SortOption.DATE_ADDED),
                studioIds = null,
                startIndex = 0,
                limit = 20,
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        } returns Result.success(SearchResult(latest, 1, 0))
        coEvery {
            mediaRepository.getMediaItems(
                parentId = null,
                filters = LibraryFilters(mediaTypes = listOf(MediaType.AUDIO), sortBy = SortOption.DATE_PLAYED),
                studioIds = null,
                startIndex = 0,
                limit = 20,
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        } returns Result.success(SearchResult(recent, 1, 0))
        coEvery {
            mediaRepository.getMediaItems(
                parentId = null,
                filters = LibraryFilters(mediaTypes = listOf(MediaType.ALBUM), sortBy = SortOption.RATING),
                studioIds = null,
                startIndex = 0,
                limit = 20,
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        } returns Result.success(SearchResult(top, 1, 0))
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.AUDIO), limit = 20) } returns
            Result.success(SearchResult(favTracks, 1, 0))
        createViewModel()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            listOf(
                MusicHomeSectionType.FAVORITE_ARTISTS to artists,
                MusicHomeSectionType.LATEST_ALBUMS to latest,
                MusicHomeSectionType.RECENTLY_PLAYED to recent,
                MusicHomeSectionType.TOP_RATED_ALBUMS to top,
                MusicHomeSectionType.FAVORITE_TRACKS to favTracks,
            ),
            state.sections.map { it.type to it.items },
        )
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun loadSections_allQueriesEmpty_yieldsNoSections() = runTest(mainDispatcher) {
        stubHomeQueries()
        createViewModel()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.sections.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun offlineMode_clearsSectionsAndUpdatesState() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        advanceUntilIdle()

        assertEquals(OfflineMode.OFFLINE_MANUAL, viewModel.uiState.value.offlineMode)
        assertTrue(viewModel.uiState.value.sections.isEmpty())
    }

    @Test
    fun loadSections_whileOffline_neverQueriesRepository() = runTest(mainDispatcher) {
        stubHomeQueries()
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        createViewModel()

        advanceUntilIdle()

        coVerify(exactly = 0) { mediaRepository.getFavorites(any(), any(), any()) }
        coVerify(exactly = 0) { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) }
        assertTrue(viewModel.uiState.value.sections.isEmpty())
    }

    @Test
    fun loadSections_failureWithNoSections_setsErrorStateOnly() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getFavorites(any(), any(), any()) } throws RuntimeException("boom")
        coEvery {
            mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.success(SearchResult(emptyList(), 0, 0))
        createViewModel()

        advanceUntilIdle()

        assertEquals("boom", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        verify(exactly = 0) { userMessageBus.error(any()) }
    }

    @Test
    fun refresh_failureWithCachedSections_surfacesBusToastAndKeepsSections() = runTest(mainDispatcher) {
        stubHomeQueries()
        // One answer per matcher; the artist query succeeds on the first load
        // and throws on the refresh — no stub re-recording.
        var artistQueries = 0
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } answers {
            if (++artistQueries == 1) {
                Result.success(SearchResult(listOf(item("a1", "Artist")), 1, 0))
            } else {
                throw RuntimeException("refresh boom")
            }
        }
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        viewModel.refresh()
        advanceUntilIdle()

        verify(exactly = 1) { userMessageBus.error("refresh boom") }
        assertNull(viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.sections.size)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun toggleOfflineMode_delegatesToManager() = runTest(mainDispatcher) {
        stubHomeQueries()
        createViewModel()

        viewModel.toggleOfflineMode()
        advanceUntilIdle()

        verify(exactly = 1) { offlineModeManager.toggleManualOffline() }
    }

    // ── Deferred refresh (user-data changes while off-screen) ───────────────

    @Test
    fun deferredRefresh_reloadsSectionsSilently() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)
        coVerify(exactly = 1) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }

        // A write confirmed while the screen is off-screen marks stale only.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()
        coVerify(exactly = 1) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }

        // Re-entry regenerates — silently: no isLoading flip (the
        // pull-to-refresh spinner keys off it), no error reset, no toast.
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        coVerify(exactly = 2) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }
        assertEquals(1, viewModel.uiState.value.sections.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
        verify(exactly = 0) { userMessageBus.error(any()) }
    }

    @Test
    fun deferredRefresh_failureKeepsSectionsAndStaysQuiet() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        // One answer per matcher: the artist query succeeds on the initial
        // load and fails on the silent regeneration (no stub re-recording).
        var artistQueries = 0
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } answers {
            if (++artistQueries == 1) {
                Result.success(SearchResult(listOf(item("a1", "Artist")), 1, 0))
            } else {
                Result.failure(RuntimeException("refresh boom"))
            }
        }
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // The regeneration ran, its favorite-artists read failed, and nothing
        // was published: the stale sections stay (serve-stale-while-revalidate)
        // — no dropped rows, no toast, no error, no spinner.
        coVerify(exactly = 2) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }
        assertEquals(1, viewModel.uiState.value.sections.size)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        verify(exactly = 0) { userMessageBus.error(any()) }

        // The failed silent regeneration re-arms the deferred refresh: the
        // next re-entry retries instead of trusting the consumed flag (which
        // would pin the pre-change rows until the next user-data event).
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 3) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }
        assertEquals(1, viewModel.uiState.value.sections.size)
    }

    @Test
    fun deferredRefresh_doesNotStackSilentLoadOnAnInFlightLoudLoad() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        // Armed while off-screen...
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()

        // ...then a loud refresh parks mid-fetch when the deferred effect
        // consumes the flag: the loud load is the regeneration, so no silent
        // twin may stack on top of it (last-writer-wins would let the slower
        // fetch overwrite fresher results, and its bare isLoading write would
        // kill the loud load's pull-to-refresh spinner).
        val gate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } coAnswers {
            gate.await()
            Result.success(SearchResult(listOf(item("a2", "Artist 2")), 1, 0))
        }
        viewModel.refresh()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        coVerify(exactly = 2) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }
        assertTrue(viewModel.uiState.value.isLoading, "the loud load's spinner survives the deferred activation")

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.sections.size)
        assertEquals("Artist 2", viewModel.uiState.value.sections.single().items.single().name)
    }

    @Test
    fun refresh_cancelledByALaterLoudLoad_doesNotMaskAsFailureOrStrandSpinner() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        val gate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } coAnswers {
            gate.await()
            Result.success(SearchResult(listOf(item("a2", "Artist 2")), 1, 0))
        }
        // The second loud load cancels the first mid-fetch.
        viewModel.refresh()
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading, "the surviving load owns the spinner")

        gate.complete(Unit)
        advanceUntilIdle()

        // The cancelled load rethrew CancellationException instead of falling
        // into the fetch-failure handler: no toast, no error state — and the
        // surviving load resolves the spinner when it publishes.
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
        verify(exactly = 0) { userMessageBus.error(any()) }
        assertEquals("Artist 2", viewModel.uiState.value.sections.single().items.single().name)
    }

    @Test
    fun deferredRefresh_cancelledByALoudLoad_doesNotRearm() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        val gate = CompletableDeferred<Unit>()
        var artistQueries = 0
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } coAnswers {
            if (++artistQueries == 1) {
                Result.success(SearchResult(listOf(item("a1", "Artist")), 1, 0))
            } else {
                gate.await()
                Result.success(SearchResult(listOf(item("a2", "Artist 2")), 1, 0))
            }
        }

        // Armed while off-screen; re-entry starts the silent regeneration,
        // which parks mid-fetch. A loud load then cancels it — cancellation
        // must NOT re-arm (the loud load IS the regeneration), or the next
        // re-entry would fire a pointless second silent regeneration.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Artist 2", viewModel.uiState.value.sections.single().items.single().name)

        // The next re-entry fires nothing: the pending flag stayed consumed.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 3) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }
        assertFalse(viewModel.uiState.value.isLoading)
        verify(exactly = 0) { userMessageBus.error(any()) }
    }

    @Test
    fun deferredRefresh_skippedByAnInFlightLoudLoad_rearmsForTheNextReentry() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        // Armed while off-screen; the activation's silent regeneration then
        // skips itself because a loud load (dispatched BEFORE the change
        // landed) is in flight — the skip must re-arm, or the loud load's
        // pre-change result strands the consumed flag until the next WS event.
        val gate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } coAnswers {
            gate.await()
            Result.success(SearchResult(listOf(item("a2", "Artist 2")), 1, 0))
        }
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        // The loud load is now PARKED mid-fetch (its artist query awaits the
        // gate) when the deferred effect consumes the flag: the silent twin
        // skips itself, and the skip must re-arm — the parked load dispatched
        // before the change landed, so its result is pre-change data.
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        coVerify(exactly = 2) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Artist 2", viewModel.uiState.value.sections.single().items.single().name)

        // The next re-entry fires the quiet regeneration the skip promised —
        // silently: no spinner, no toast.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 3) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }
        assertFalse(viewModel.uiState.value.isLoading)
        verify(exactly = 0) { userMessageBus.error(any()) }
    }

    @Test
    fun deferredRefresh_whileOffline_rearmsInsteadOfStrandingThePendingFlag() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        advanceUntilIdle()

        // Armed while off-screen AND offline: the activation's silent
        // regeneration cannot fetch, so it must report failure (re-arm)
        // rather than strand the consumed flag — while the app stays
        // offline, no later re-entry would otherwise retry.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("t1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }

        // Returning online regenerates loudly via the offline-mode
        // collector — the change heals even though the silent path could
        // not run while offline.
        offlineModeFlow.value = OfflineMode.ONLINE
        advanceUntilIdle()
        coVerify(exactly = 2) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }
    }

    @Test
    fun refresh_loudFailureOverArmsTheDeferredRefreshForTheNextReentry() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        // One answer per matcher: the artist query succeeds on the initial
        // load and throws on every later one (the loud refresh and the
        // over-armed silent retry it promises).
        var artistQueries = 0
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } answers {
            if (++artistQueries == 1) {
                Result.success(SearchResult(listOf(item("a1", "Artist")), 1, 0))
            } else {
                throw RuntimeException("refresh boom")
            }
        }
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        // A loud failure with nothing pending just over-arms one quiet
        // refetch; the toast fires for the loud failure only.
        viewModel.refresh()
        advanceUntilIdle()
        verify(exactly = 1) { userMessageBus.error("refresh boom") }
        assertEquals(1, viewModel.uiState.value.sections.size)

        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // The over-armed regeneration ran and stayed silent: the retry's own
        // failure keeps the stale sections and raises no second toast.
        coVerify(exactly = 3) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }
        assertEquals(1, viewModel.uiState.value.sections.size)
        assertFalse(viewModel.uiState.value.isLoading)
        verify(exactly = 1) { userMessageBus.error(any()) }
    }

    @Test
    fun refresh_partialLoudFailurePublishesWhatItGotAndRearmsToHeal() = runTest(mainDispatcher) {
        stubHomeQueries(favoriteArtists = listOf(item("a1", "Artist")))
        // Query 1 (initial load) succeeds; query 2 (loud refresh) returns a
        // failure RESULT — swallowed by getOrNull, so the loud load finishes
        // "successfully" minus the artist row; query 3 (the re-armed silent
        // twin) heals it.
        var artistQueries = 0
        coEvery { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) } answers {
            if (++artistQueries == 2) {
                Result.failure(RuntimeException("artists blip"))
            } else {
                Result.success(SearchResult(listOf(item("a1", "Artist")), 1, 0))
            }
        }
        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.sections.size)

        viewModel.refresh()
        advanceUntilIdle()

        // The loud partial failure publishes what it got (here: nothing —
        // every other section is empty) and stays quiet: the sub-fetch
        // failures were swallowed, so no toast fires.
        assertTrue(viewModel.uiState.value.sections.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
        verify(exactly = 0) { userMessageBus.error(any()) }

        // It reported failure, so the coordinator re-armed: the next re-entry
        // silently regenerates and the dropped row heals.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        coVerify(exactly = 3) { mediaRepository.getFavorites(mediaTypes = listOf(MediaType.ARTIST), limit = 20) }
        assertEquals(1, viewModel.uiState.value.sections.size)
    }

    @Test
    fun surpriseMe_invokesCallbackWithRandomTrackId() = runTest(mainDispatcher) {
        stubHomeQueries()
        coEvery {
            mediaRepository.getMediaItems(
                parentId = null,
                filters = LibraryFilters(mediaTypes = listOf(MediaType.AUDIO), sortBy = SortOption.RANDOM),
                studioIds = null,
                startIndex = 0,
                limit = 1,
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        } returns Result.success(SearchResult(listOf(item("lucky")), 1, 0))
        createViewModel()

        val clicked = mutableListOf<String>()
        viewModel.surpriseMe { clicked += it }
        advanceUntilIdle()

        assertEquals(listOf("lucky"), clicked)
    }

    @Test
    fun getImageUrl_andBackdrop_delegateToProvider() = runTest(mainDispatcher) {
        stubHomeQueries()
        createViewModel()
        every { imageUrlProvider.getImageUrl("i1") } returns "img"
        every { imageUrlProvider.getBackdropUrl("i1") } returns "bd"

        assertEquals("img", viewModel.getImageUrl("i1"))
        assertEquals("bd", viewModel.getBackdropUrl("i1"))
    }

    @Test
    fun playAll_delegatesWithDefaultsAndStartIndex() = runTest(mainDispatcher) {
        stubHomeQueries()
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), 0)
        createViewModel()
        val tracks = listOf(item("t1"))

        viewModel.playAll(tracks, startIndex = 2)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(tracks, 2, false, null, ImageUrlProvider.DEFAULT_MAX_WIDTH)
        }
    }

    @Test
    fun shufflePlay_delegatesWithShuffledFlag() = runTest(mainDispatcher) {
        stubHomeQueries()
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), 0)
        createViewModel()
        val tracks = listOf(item("t1"))

        viewModel.shufflePlay(tracks)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(tracks, 0, true, null, ImageUrlProvider.DEFAULT_MAX_WIDTH)
        }
    }

    @Test
    fun playAlbum_fetchesTracksAndPlays() = runTest(mainDispatcher) {
        stubHomeQueries()
        val tracks = listOf(item("t1"))
        coEvery { mediaRepository.getAlbumTracks("al1") } returns Result.success(tracks)
        coEvery { audioQueueFacade.playTracks(any(), any(), any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), 0)
        createViewModel()

        viewModel.playAlbum("al1")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(tracks, 0, false, null, ImageUrlProvider.DEFAULT_MAX_WIDTH)
        }
    }

    @Test
    fun playAlbums_playsConcatenatedPairsWithPerAlbumFallback() = runTest(mainDispatcher) {
        stubHomeQueries()
        val albumA = MediaItem(id = "a1", name = "Album A", mediaType = MediaType.ALBUM)
        val albumB = MediaItem(id = "a2", name = "Album B", mediaType = MediaType.ALBUM)
        coEvery { mediaRepository.getAlbumTracks("a1") } returns Result.success(listOf(item("t1")))
        coEvery { mediaRepository.getAlbumTracks("a2") } returns Result.success(listOf(item("t2")))
        coEvery { audioQueueFacade.playTracks(any<List<TrackWithAlbumFallback>>(), any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), 0)
        createViewModel()

        viewModel.playAlbums(listOf(albumA, albumB))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(
                listOf(
                    TrackWithAlbumFallback(item("t1"), "Album A"),
                    TrackWithAlbumFallback(item("t2"), "Album B"),
                ),
                0,
                false,
                ImageUrlProvider.DEFAULT_MAX_WIDTH,
            )
        }
    }

    @Test
    fun playArtist_expandsAlbumsIntoPairedQueue() = runTest(mainDispatcher) {
        stubHomeQueries()
        val albumA = MediaItem(id = "a1", name = "Album A", mediaType = MediaType.ALBUM)
        coEvery { mediaRepository.getArtistAlbums("ar1") } returns Result.success(listOf(albumA))
        coEvery { mediaRepository.getAlbumTracks("a1") } returns Result.success(listOf(item("t1")))
        coEvery { audioQueueFacade.playTracks(any<List<TrackWithAlbumFallback>>(), any(), any(), any()) } returns
            AudioQueueOutcome.Started(emptyList(), 0)
        createViewModel()

        viewModel.playArtist("ar1")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            audioQueueFacade.playTracks(
                listOf(TrackWithAlbumFallback(item("t1"), "Album A")),
                0,
                false,
                ImageUrlProvider.DEFAULT_MAX_WIDTH,
            )
        }
    }
}
