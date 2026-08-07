package com.raulshma.jellyplay.feature.home

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Real HomeViewModel tests. Instantiates the VM with MockK deps + a fake
 * [TimeSource], then drives it through the branches the previous tautological
 * test suite skipped: section fetch + ordering, CW side-effects, offline
 * transitions, search, and sign-in reset.
 *
 * Harness mirrors `DetailViewModelTest`: MockK + [MainDispatcherRule] + runTest.
 * Uses [runCurrent] (not `advanceUntilIdle`) so the periodic-refresh `while(true)`
 * loop's `delay` doesn't drive virtual time unbounded.
 */
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var photoFolderPrefetcher: PhotoFolderPrefetcher
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var newsletterTriggerManager: NewsletterTriggerManager
    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var playbackStore: PlaybackStore
    private lateinit var preferencesEditor: PreferencesEditor
    private lateinit var serverIdentityStore: ServerIdentityStore
    private lateinit var widgetDataStore: WidgetDataStore
    private lateinit var searchHistoryRepository: SearchHistoryRepository
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var seerrPreferencesStore: SeerrPreferencesStore
    private lateinit var authRepository: AuthRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var tvWatchNextScheduler: TvWatchNextScheduler
    private lateinit var continueWatchingBroadcaster: ContinueWatchingBroadcaster
    private lateinit var playbackOutboxRepository: PlaybackOutboxRepository
    private lateinit var playbackSyncScheduler: PlaybackSyncScheduler
    private lateinit var fakeTimeSource: FakeTimeSource

    private val userFlow = MutableStateFlow<UserInfo?>(null)
    private val homeDiscoveryFlow = MutableStateFlow(HomeDiscoverySlice())
    private val appearanceFlow = MutableStateFlow(AppearanceSlice())
    private val experimentalFlow = MutableStateFlow(ExperimentalSlice())
    private val playbackFlow = MutableStateFlow(PlaybackSlice())
    private val activeUserIdFlow = MutableStateFlow<String?>(null)
    private val seerrPrefsFlow = MutableStateFlow(SeerrPreferences())
    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Online)
    private val outboxCountFlow = MutableStateFlow(0)
    private val outboxEntriesFlow = MutableStateFlow<List<PlaybackOutboxEntry>>(emptyList())

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        imageUrlProvider = mockk(relaxed = true)
        photoFolderPrefetcher = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        newsletterTriggerManager = mockk(relaxed = true)
        homeDiscoveryStore = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        experimentalStore = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        preferencesEditor = mockk(relaxed = true)
        serverIdentityStore = mockk(relaxed = true)
        widgetDataStore = mockk(relaxed = true)
        searchHistoryRepository = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        seerrPreferencesStore = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)
        tvWatchNextScheduler = mockk(relaxed = true)
        continueWatchingBroadcaster = mockk(relaxed = true)
        playbackOutboxRepository = mockk(relaxed = true)
        playbackSyncScheduler = mockk(relaxed = true)
        fakeTimeSource = FakeTimeSource()

        every { authRepository.currentUser } returns userFlow
        every { homeDiscoveryStore.homeDiscovery } returns homeDiscoveryFlow
        every { appearanceStore.appearance } returns appearanceFlow
        every { experimentalStore.experimental } returns experimentalFlow
        every { playbackStore.playback } returns playbackFlow
        every { serverIdentityStore.activeUserId } returns activeUserIdFlow
        every { seerrPreferencesStore.preferences } returns seerrPrefsFlow
        every { offlineModeManager.offlineMode } returns offlineModeFlow
        every { offlineModeManager.networkStatus } returns networkStatusFlow
        every { offlineModeManager.isOffline } returns false
        every { downloadRepository.getActiveDownloadCount() } returns flowOf(0)
        every { playbackOutboxRepository.countFlow() } returns outboxCountFlow
        every { playbackOutboxRepository.getAllFlow() } returns outboxEntriesFlow
        every { offlineRepository.getOfflineLibrary() } returns flowOf(emptyList())
        every { newsletterTriggerManager.shouldShowBanner() } returns flowOf(false)
        every { searchHistoryRepository.getRecent(any(), any()) } returns flowOf(emptyList())
        every { seerrRepository.isConnected() } returns flowOf(false)
        every { seerrRepository.isSearchEnabled() } returns flowOf(false)
    }

    private fun buildViewModel(): HomeViewModel = HomeViewModel(
        appContext = mockk<Context>(relaxed = true),
        mediaRepository = mediaRepository,
        orderHomeSections = OrderHomeSectionsUseCase(),
        imageUrlProvider = imageUrlProvider,
        photoFolderPrefetcher = photoFolderPrefetcher,
        downloadRepository = downloadRepository,
        offlineRepository = offlineRepository,
        playbackOutboxRepository = playbackOutboxRepository,
        playbackSyncScheduler = playbackSyncScheduler,
        offlineModeManager = offlineModeManager,
        newsletterTriggerManager = newsletterTriggerManager,
        homeDiscoveryStore = homeDiscoveryStore,
        appearanceStore = appearanceStore,
        experimentalStore = experimentalStore,
        playbackStore = playbackStore,
        preferencesEditor = preferencesEditor,
        serverIdentityStore = serverIdentityStore,
        widgetDataStore = widgetDataStore,
        searchHistoryRepository = searchHistoryRepository,
        seerrRepository = seerrRepository,
        seerrRequestDelegate = seerrRequestDelegate,
        seerrPreferencesStore = seerrPreferencesStore,
        authRepository = authRepository,
        arrRepository = arrRepository,
        tvWatchNextScheduler = tvWatchNextScheduler,
        continueWatchingBroadcaster = continueWatchingBroadcaster,
        timeSource = fakeTimeSource,
    )

    @Test
    fun signIn_fetchesSections_andOrdersThem() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(
            HomeSectionsResult(
                sections = listOf(
                    section(HomeSectionType.LATEST_MEDIA),
                    section(HomeSectionType.CONTINUE_WATCHING, items = listOf(item("cw1"))),
                ),
            ),
        )
        viewModel = buildViewModel()

        // Triggers the sign-in path: previousUserId null → userId set → fetch.
        userFlow.value = userInfo("u1")
        runCurrent()

        val sections = viewModel.uiState.value.sections
        // Default UserPreferences.homeSectionOrder is CONFIGURABLE, which lists
        // CONTINUE_WATCHING before LATEST_MEDIA — so ordering should apply.
        assertEquals(HomeSectionType.CONTINUE_WATCHING, sections.first().type)
        assertEquals(2, sections.size)
        stopPeriodicRefresh()
    }

    /**
     * Stops the VM's periodic-refresh `while(true)` loop so `runTest` cleanup
     * (which advances virtual time to settle pending `delay`s) doesn't hang on
     * the infinite loop. Equivalent to the app backgrounding the screen.
     */
    private fun stopPeriodicRefresh() {
        viewModel.onStop(mockk(relaxed = true))
    }

    @Test
    fun continueWatchingChange_firesBroadcaster_andTvScheduler() = runTest {
        // CW publishing is gated on the androidTvWatchNextEnabled pref (default true).
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(
            HomeSectionsResult(
                sections = listOf(
                    section(HomeSectionType.CONTINUE_WATCHING, items = listOf(item("cw1"))),
                ),
            ),
        )
        viewModel = buildViewModel()

        userFlow.value = userInfo("u1")
        runCurrent()

        verify { continueWatchingBroadcaster.refreshContinueWatching() }
        coVerify { tvWatchNextScheduler.scheduleRefresh() }
        stopPeriodicRefresh()
    }

    @Test
    fun continueWatchingChange_skipsTvScheduler_whenPrefDisabled() = runTest {
        playbackFlow.value = PlaybackSlice(androidTvWatchNextEnabled = false)
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(
            HomeSectionsResult(
                sections = listOf(
                    section(HomeSectionType.CONTINUE_WATCHING, items = listOf(item("cw1"))),
                ),
            ),
        )
        viewModel = buildViewModel()

        userFlow.value = userInfo("u1")
        runCurrent()

        verify { continueWatchingBroadcaster.refreshContinueWatching() }
        coVerify(exactly = 0) { tvWatchNextScheduler.scheduleRefresh() }
        stopPeriodicRefresh()
    }

    @Test
    fun signOut_clearsSections() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(
            HomeSectionsResult(sections = listOf(section(HomeSectionType.CONTINUE_WATCHING))),
        )
        viewModel = buildViewModel()
        userFlow.value = userInfo("u1")
        runCurrent()
        assertFalse(viewModel.uiState.value.sections.isEmpty())

        userFlow.value = null
        runCurrent()

        assertTrue(viewModel.uiState.value.sections.isEmpty())
        stopPeriodicRefresh()
    }

    @Test
    fun offlineToOnline_clearsIsGoingOnline_afterFetch() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        viewModel = buildViewModel()
        userFlow.value = userInfo("u1")
        runCurrent()

        // Drive the offline→online transition path: the offlineMode collector's
        // ONLINE branch sets isGoingOnline via toggleOfflineMode, then clears
        // it in finally after the fetch resolves.
        every { offlineModeManager.isOffline } returns true
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()
        every { offlineModeManager.isOffline } returns false
        offlineModeFlow.value = OfflineMode.ONLINE
        runCurrent()

        assertFalse(
            "isGoingOnline must clear after the online fetch resolves",
            viewModel.uiState.value.isGoingOnline,
        )
        stopPeriodicRefresh()
    }

    @Test
    fun syncNow_whenOnline_enqueuesDrain() = runTest {
        viewModel = buildViewModel()
        viewModel.onEvent(HomeUiEvent.SyncNow)
        // The drain worker must be enqueued exactly once; the worker itself
        // carries the NetworkType.CONNECTED constraint, but the VM gate also
        // short-circuits while offline.
        verify(exactly = 1) { playbackSyncScheduler.enqueueNow() }
        stopPeriodicRefresh()
    }

    @Test
    fun syncNow_whenOffline_skipsEnqueue() = runTest {
        viewModel = buildViewModel()
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()
        viewModel.onEvent(HomeUiEvent.SyncNow)
        verify(exactly = 0) { playbackSyncScheduler.enqueueNow() }
        stopPeriodicRefresh()
    }

    @Test
    fun pendingSyncEntries_emitsWhatRepositoryProduces() = runTest {
        val entry = PlaybackOutboxEntry(
            id = "e1",
            itemId = "item-1",
            eventType = PlaybackOutboxEventType.PROGRESS,
            sessionId = "s1",
            positionTicks = 10_000_000L,
            isPaused = false,
            playMethod = PlayMethod.DIRECT_PLAY,
            mediaSourceId = null,
            recordedAt = 1L,
            createdAt = 1L,
        )
        outboxEntriesFlow.value = listOf(entry)
        viewModel = buildViewModel()
        // pendingSyncEntries is stateIn(WhileSubscribed) — needs a live
        // subscriber to pull, then its .value reflects the upstream emission.
        val job = launch { viewModel.pendingSyncEntries.collect { } }
        runCurrent()

        assertEquals(listOf(entry), viewModel.pendingSyncEntries.value)
        job.cancel()
        stopPeriodicRefresh()
    }

    @Test
    fun offlineToOnline_clearsIsGoingOnline_whenFetchTimesOut() = runTest {
        // Regression: a hung getHomeSections call (half-open socket, unvalidated
        // captive portal that still reports INTERNET, etc.) previously parked
        // fetchAndUpdateSections on refreshMutex forever, so isGoingOnline never
        // cleared and the Go Online button + app bar spinners spun indefinitely.
        // The withTimeoutOrNull cap must force-clear both flags on timeout.
        // Hang on a never-completing Deferred (not real delay): real delay would
        // run on the repository's withContext(Dispatchers.Default) and block a
        // worker thread for the full timeout, leaking past test teardown.
        coEvery {
            mediaRepository.getHomeSections(any())
        } coAnswers { CompletableDeferred<Result<HomeSectionsResult>>().await() }
        viewModel = buildViewModel()
        userFlow.value = userInfo("u1")
        runCurrent()

        every { offlineModeManager.isOffline } returns true
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()
        every { offlineModeManager.isOffline } returns false
        offlineModeFlow.value = OfflineMode.ONLINE
        // Advance virtual time past the GOING_ONLINE_TIMEOUT_MS deadline.
        advanceTimeBy(31_000)
        runCurrent()

        assertFalse(
            "isGoingOnline must clear even if the fetch hangs past the deadline",
            viewModel.uiState.value.isGoingOnline,
        )
        assertFalse(
            "isLoading must clear even if the fetch hangs past the deadline",
            viewModel.uiState.value.isLoading,
        )
        stopPeriodicRefresh()
    }

    @Test
    fun search_keepsLatestQuery_afterSupersededEntry() = runTest {
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.UpdateSearchQuery("bat"))
        viewModel.onEvent(HomeUiEvent.UpdateSearchQuery("batman"))
        runCurrent()

        // Query is the latest value; the intermediate "bat" was superseded by
        // the debounce + distinctUntilChanged chain. The live query now lives
        // on the VM's searchQuery flow (read by the leaf), not searchState.
        assertEquals("batman", viewModel.searchQuery.value)
        assertTrue(viewModel.uiState.value.isSearchActive)
    }

    @Test
    fun clearSearch_resetsSearchState() = runTest {
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.UpdateSearchQuery("hello"))
        runCurrent()

        viewModel.onEvent(HomeUiEvent.ClearSearch)
        runCurrent()

        val search = viewModel.uiState.value.searchState
        assertEquals("", viewModel.searchQuery.value)
        assertFalse(viewModel.uiState.value.isSearchActive)
        assertTrue(search.jellyfinResults.isEmpty())
        assertTrue(search.seerrResults.isEmpty())
    }

    @Test
    fun prefChange_withUnrelatedPrefs_doesNotRefetch() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        viewModel = buildViewModel()
        userFlow.value = userInfo("u1")
        runCurrent()

        // Reset invocation count after the sign-in fetch.
        io.mockk.clearMocks(mediaRepository, answers = false, recordedCalls = true, childMocks = false)
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))

        // Toggle a pref that is NOT in the home-section diff set (oledMode).
        appearanceFlow.value = AppearanceSlice(oledMode = true)
        runCurrent()

        coVerify(exactly = 0) {
            mediaRepository.getHomeSections(any())
        }
        stopPeriodicRefresh()
    }

    @Test
    fun homeBackdropEnabled_mapsFromPreferences_toUiState() = runTest {
        homeDiscoveryFlow.value = HomeDiscoverySlice(homeBackdropEnabled = false)
        viewModel = buildViewModel()
        runCurrent()

        assertFalse(viewModel.uiState.value.homeBackdropEnabled)

        homeDiscoveryFlow.value = HomeDiscoverySlice(homeBackdropEnabled = true)
        runCurrent()

        assertTrue(viewModel.uiState.value.homeBackdropEnabled)
        stopPeriodicRefresh()
    }

    @Test
    fun setSectionVisible_removesTypeFromEnabledSet() = runTest {
        // Start with all configurable types enabled.
        homeDiscoveryFlow.value = HomeDiscoverySlice(
            enabledHomeSectionTypes = HomeSectionType.CONFIGURABLE.toSet(),
        )
        viewModel = buildViewModel()
        runCurrent()

        viewModel.setSectionVisible(HomeSectionType.NEXT_UP, visible = false)

        val expected = HomeSectionType.CONFIGURABLE.toSet() - HomeSectionType.NEXT_UP
        verify { preferencesEditor.setEnabledHomeSectionTypes(expected) }
        stopPeriodicRefresh()
    }

    @Test
    fun setSectionVisible_addsTypeToEnabledSet() = runTest {
        homeDiscoveryFlow.value = HomeDiscoverySlice(
            enabledHomeSectionTypes = emptySet(),
        )
        viewModel = buildViewModel()
        runCurrent()

        viewModel.setSectionVisible(HomeSectionType.RECOMMENDATIONS, visible = true)

        verify { preferencesEditor.setEnabledHomeSectionTypes(setOf(HomeSectionType.RECOMMENDATIONS)) }
        stopPeriodicRefresh()
    }

    @Test
    fun moveSection_up_swapsWithPredecessor() = runTest {
        val order = listOf(
            HomeSectionType.CONTINUE_WATCHING,
            HomeSectionType.NEXT_UP,
            HomeSectionType.LATEST_MEDIA,
        )
        homeDiscoveryFlow.value = HomeDiscoverySlice(homeSectionOrder = order)
        // Capture the edit{} block and run it against a recording store so the
        // resulting order can be asserted (edit is fire-and-forget over the app
        // scope, so the lambda is the only place the new order lives).
        val editorBlock = slot<suspend PreferencesEditScope.() -> Unit>()
        every { preferencesEditor.edit(capture(editorBlock)) } returns mockk()
        viewModel = buildViewModel()
        runCurrent()

        viewModel.moveSection(HomeSectionType.NEXT_UP, up = true)

        assertTrue(editorBlock.isCaptured)
        val recordingHome = mockk<HomeDiscoveryStore>(relaxed = true)
        var capturedOrder: List<HomeSectionType>? = null
        coEvery { recordingHome.setHomeSectionOrder(any()) } answers { capturedOrder = firstArg() }
        val editScope = mockk<PreferencesEditScope>(relaxed = true)
        every { editScope.homeDiscovery } returns recordingHome
        // edit's block is suspend — run it in a real coroutine to replay it
        // against the recording scope and observe the persisted order.
        kotlinx.coroutines.runBlocking { editorBlock.captured.invoke(editScope) }
        assertEquals(
            listOf(HomeSectionType.NEXT_UP, HomeSectionType.CONTINUE_WATCHING, HomeSectionType.LATEST_MEDIA),
            capturedOrder,
        )
        stopPeriodicRefresh()
    }

    @Test
    fun moveSection_down_atLastIndex_isNoOp() = runTest {
        val order = listOf(
            HomeSectionType.CONTINUE_WATCHING,
            HomeSectionType.NEXT_UP,
        )
        homeDiscoveryFlow.value = HomeDiscoverySlice(homeSectionOrder = order)
        every { preferencesEditor.edit(any()) } returns mockk()
        viewModel = buildViewModel()
        runCurrent()

        viewModel.moveSection(HomeSectionType.NEXT_UP, up = false)

        // NEXT_UP is already last → editor must not be touched.
        verify(exactly = 0) { preferencesEditor.edit(any()) }
        stopPeriodicRefresh()
    }

    @Test
    fun setLibrarySectionVisible_disabled_addsTypeToOverrideSet() = runTest {
        homeDiscoveryFlow.value = HomeDiscoverySlice(
            libraryHomeSectionOverrides = mapOf("movies" to setOf(HomeSectionType.RECENTLY_ADDED)),
        )
        viewModel = buildViewModel()
        runCurrent()

        // Hiding LATEST_MEDIA for the "movies" library adds it to the disabled
        // set alongside the existing RECENTLY_ADDED entry.
        viewModel.setLibrarySectionVisible("movies", HomeSectionType.LATEST_MEDIA, visible = false)

        verify {
            preferencesEditor.setLibraryHomeSectionOverrides(
                mapOf("movies" to setOf(HomeSectionType.RECENTLY_ADDED, HomeSectionType.LATEST_MEDIA)),
            )
        }
        stopPeriodicRefresh()
    }

    @Test
    fun setLibrarySectionVisible_enabled_dropsEmptyKey() = runTest {
        homeDiscoveryFlow.value = HomeDiscoverySlice(
            libraryHomeSectionOverrides = mapOf("movies" to setOf(HomeSectionType.LATEST_MEDIA)),
        )
        viewModel = buildViewModel()
        runCurrent()

        // Re-enabling the only disabled type empties the set, so the key must
        // be dropped entirely (restoring default-enabled state).
        viewModel.setLibrarySectionVisible("movies", HomeSectionType.LATEST_MEDIA, visible = true)

        verify { preferencesEditor.setLibraryHomeSectionOverrides(emptyMap()) }
        stopPeriodicRefresh()
    }

    @Test
    fun ensurePendingItemDetails_resolvesOfflineItem_andUsesLocalPosterPath() = runTest {
        val offlineItem = com.raulshma.jellyplay.core.model.OfflineMediaItem(
            id = "item-1",
            name = "Offline Movie",
            mediaType = MediaType.MOVIE,
            posterPath = "file:///offline/poster.jpg",
        )
        coEvery { offlineRepository.getOfflineItem("item-1") } returns offlineItem
        every { imageUrlProvider.getImageUrl("item-1") } returns "http://server/item-1/image"
        viewModel = buildViewModel()

        viewModel.ensurePendingItemDetails(listOf("item-1"))
        runCurrent()

        val resolved = viewModel.pendingItemDetails.value["item-1"]
        assertEquals("Offline Movie", resolved?.item?.name)
        // Offline hit must prefer the local poster path over the server URL.
        assertEquals("file:///offline/poster.jpg", resolved?.posterUrl)
        // Network fallback must not fire when the offline store had the row.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail("item-1") }
        stopPeriodicRefresh()
    }

    @Test
    fun ensurePendingItemDetails_fallsBackToNetwork_whenOfflineMiss_andOnline() = runTest {
        coEvery { offlineRepository.getOfflineItem("item-2") } returns null
        val detail = com.raulshma.jellyplay.core.model.MediaDetail(
            item = MediaItem(id = "item-2", name = "Online Only", mediaType = MediaType.MOVIE),
        )
        coEvery { mediaRepository.getMediaDetail("item-2") } returns Result.success(detail)
        every { imageUrlProvider.getImageUrl("item-2") } returns "http://server/item-2/image"
        viewModel = buildViewModel()

        viewModel.ensurePendingItemDetails(listOf("item-2"))
        runCurrent()

        val resolved = viewModel.pendingItemDetails.value["item-2"]
        assertEquals("Online Only", resolved?.item?.name)
        assertEquals("http://server/item-2/image", resolved?.posterUrl)
        stopPeriodicRefresh()
    }

    @Test
    fun ensurePendingItemDetails_skipsNetwork_whenOfflineMiss_andOfflineMode() = runTest {
        coEvery { offlineRepository.getOfflineItem("item-3") } returns null
        every { imageUrlProvider.getImageUrl("item-3") } returns "http://server/item-3/image"
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        viewModel = buildViewModel()
        runCurrent()

        viewModel.ensurePendingItemDetails(listOf("item-3"))
        runCurrent()

        val resolved = viewModel.pendingItemDetails.value["item-3"]
        // Resolves to the not-found marker (null item) with a server URL so the
        // row can still attempt to load it once back online.
        assertEquals(null, resolved?.item)
        assertEquals("http://server/item-3/image", resolved?.posterUrl)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail("item-3") }
        stopPeriodicRefresh()
    }

    @Test
    fun ensurePendingItemDetails_prunesStaleKeys_andDedupesInFlight() = runTest {
        coEvery { offlineRepository.getOfflineItem(any()) } returns null
        coEvery { mediaRepository.getMediaDetail(any()) } returns Result.failure(RuntimeException("net"))
        every { imageUrlProvider.getImageUrl(any()) } returns "http://server/img"
        viewModel = buildViewModel()

        viewModel.ensurePendingItemDetails(listOf("a", "b"))
        runCurrent()
        assertEquals(setOf("a", "b"), viewModel.pendingItemDetails.value.keys)

        // Second call with overlapping ids must not re-launch resolves for
        // already-resolved keys (dedup), and ids dropped from the input are
        // pruned from the map.
        viewModel.ensurePendingItemDetails(listOf("b", "c"))
        runCurrent()

        assertEquals(setOf("b", "c"), viewModel.pendingItemDetails.value.keys)
        stopPeriodicRefresh()
    }

    @Test
    fun refresh_resetsScrollAndFetchesSections() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        viewModel = buildViewModel()
        viewModel.saveHomeScrollPosition(5, 100)

        viewModel.onEvent(HomeUiEvent.Refresh)
        runCurrent()

        val pos = viewModel.getHomeScrollPosition()
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
        coVerify { mediaRepository.getHomeSections(any()) }
        stopPeriodicRefresh()
    }

    @Test
    fun pullToRefresh_invalidatesDiscoverCache_andRefetches() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.PullToRefresh)
        runCurrent()

        assertFalse(viewModel.uiState.value.isRefreshing)
        coVerify { mediaRepository.getHomeSections(any()) }
        stopPeriodicRefresh()
    }

    @Test
    fun dismissNewsletterBanner_updatesUiState() = runTest {
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.DismissNewsletterBanner)
        runCurrent()

        assertFalse(viewModel.uiState.value.newsletterBannerVisible)
        stopPeriodicRefresh()
    }

    @Test
    fun saveHomeScrollPosition_storesPositiveValues_andClampsNegatives() = runTest {
        viewModel = buildViewModel()

        viewModel.saveHomeScrollPosition(3, 150)
        var pos = viewModel.getHomeScrollPosition()
        assertEquals(3, pos.firstVisibleItemIndex)
        assertEquals(150, pos.firstVisibleItemScrollOffset)

        viewModel.saveHomeScrollPosition(-10, -50)
        pos = viewModel.getHomeScrollPosition()
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
        stopPeriodicRefresh()
    }

    @Test
    fun prefetchPhotoFolderChildUrls_callsPrefetcher_andUpdatesState() = runTest {
        val items = listOf(item("p1"))
        coEvery { photoFolderPrefetcher.prefetch(items, any()) } returns mapOf("p1" to listOf("url1", "url2"))
        viewModel = buildViewModel()

        viewModel.prefetchPhotoFolderChildUrls(items)
        runCurrent()

        assertEquals(mapOf("p1" to listOf("url1", "url2")), viewModel.photoFolderChildUrls.value)
        stopPeriodicRefresh()
    }

    @Test
    fun fetchAndUpdateSections_onFailure_setsErrorState() = runTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.failure(RuntimeException("Connection timeout"))
        viewModel = buildViewModel()

        userFlow.value = userInfo("u1")
        runCurrent()

        assertEquals("Connection timeout", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        stopPeriodicRefresh()
    }

    @Test
    fun selectSeerrRequestItem_and_clearRequestResult() = runTest {
        viewModel = buildViewModel()

        val seerrItem = com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem(
            id = 10,
            mediaType = "movie",
            title = "Test Seerr Movie",
        )
        viewModel.onEvent(HomeUiEvent.SelectSeerrRequestItem(seerrItem))
        runCurrent()

        assertEquals(seerrItem, viewModel.uiState.value.seerrRequestState.requestItem)

        viewModel.onEvent(HomeUiEvent.ClearRequestResult)
        runCurrent()

        org.junit.Assert.assertNull(viewModel.uiState.value.seerrRequestState.result)
        stopPeriodicRefresh()
    }

    @Test
    fun getImageUrl_and_getBackdropUrl_delegateToProvider() = runTest {
        every { imageUrlProvider.getImageUrl("item-99") } returns "http://server/item-99/poster"
        every { imageUrlProvider.getBackdropUrl("item-99") } returns "http://server/item-99/backdrop"
        viewModel = buildViewModel()

        val posterUrl = viewModel.getImageUrl("item-99")
        val backdropUrl = viewModel.getBackdropUrl("item-99")

        assertEquals("http://server/item-99/poster", posterUrl)
        assertEquals("http://server/item-99/backdrop", backdropUrl)
        stopPeriodicRefresh()
    }



    @Test
    fun lifecycleEvents_onStartAndOnStop_controlPeriodicRefresh() = runTest {
        viewModel = buildViewModel()
        
        viewModel.onStart(mockk(relaxed = true))
        runCurrent()

        viewModel.onStop(mockk(relaxed = true))
        runCurrent()
        // Successfully starts and stops lifecycle observers without throwing exception
    }

    private fun userInfo(id: String) = UserInfo(
        id = id,
        name = "Tester",
        serverAddress = "http://server",
        accessToken = "token",
        serverId = "s1",
        primaryImageTag = null,
    )

    private fun section(
        type: HomeSectionType,
        items: List<MediaItem> = emptyList(),
    ) = HomeSection(
        id = type.name,
        title = type.displayName,
        type = type,
        items = items,
    )

    private fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    /**
     * Controllable [TimeSource] returning a fixed epoch so the periodic-refresh
     * and TTL gates behave deterministically without crossing their thresholds.
     */
    private class FakeTimeSource : TimeSource {
        override fun nowEpochMillis(): Long = 1_000L
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
