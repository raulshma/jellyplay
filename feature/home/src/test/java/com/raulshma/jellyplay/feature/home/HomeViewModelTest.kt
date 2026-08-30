package com.raulshma.jellyplay.feature.home

import androidx.lifecycle.LifecycleOwner
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.AppliedMutation
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.search.MediaSearchPreviewState
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.sync.SyncStatusStateHolderFactory
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
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
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/**
 * Real HomeViewModel tests. Instantiates the VM with MockK deps + a fake
 * [TimeSource], then drives it through the branches the previous tautological
 * test suite skipped: section fetch + ordering, offline transitions, and
 * sign-in reset.
 *
 * Harness mirrors `DetailViewModelTest`: MockK + [MainDispatcherRule] + runTest.
 * Uses [runCurrent] (not `advanceUntilIdle`) so the refresher's periodic
 * `while(true)` loop's `delay` doesn't drive virtual time unbounded. The
 * concern-owned extractions from this VM each have their own JVM suite —
 * [HomeRefresherTest] (refresh policy), `ScrollPositionStoreTest`,
 * `PhotoFolderChildUrlsStoreTest`, `HomeSearchStateHolderTest`,
 * `SeriesDeleteStateHolderTest` (all in this package) and
 * `SyncStatusStateHolderTest` in :core:data — so this suite keeps only
 * VM-level UiState/collector policy and the pass-through seams.
 */
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var episodeCatalogue: EpisodeCatalogue
    private lateinit var userDataMutator: FakeUserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var photoFolderPrefetcher: PhotoFolderPrefetcher
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var downloadIntake: com.raulshma.jellyplay.core.data.download.DownloadIntake
    private lateinit var userMessageBus: com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var newsletterTriggerManager: NewsletterTriggerManager
    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var playbackStore: PlaybackStore
    private lateinit var preferencesEditor: PreferencesEditor
    private lateinit var widgetDataStore: WidgetDataStore

    /**
     * Inline-search kernel — targeted fake instead of five collaborators.
     * Passed through to the VM's HomeSearchStateHolder, whose init collects
     * `preview` from construction, so the stub must stay.
     */
    private val mediaSearchEngine: MediaSearchEngine = mockk(relaxed = true)

    /**
     * Offline-first title+poster resolution — passed through to the VM's
     * SyncStatusStateHolder; no remaining test here resolves ids (the
     * resolution policy is pinned by SyncStatusStateHolderTest).
     */
    private val offlineFirstItemResolver: OfflineFirstItemResolver = mockk(relaxed = true)

    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var seerrPreferencesStore: SeerrPreferencesStore
    private lateinit var authRepository: AuthRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var tvWatchNextScheduler: TvWatchNextScheduler
    private lateinit var continueWatchingBroadcaster: ContinueWatchingBroadcaster
    private lateinit var librarySyncHook: LibrarySyncHook

    /**
     * Outbox/sync collaborators — passed through to the VM's
     * SyncStatusStateHolder. Relaxed mocks are enough here: the remaining
     * offline→online tests only hit `count()` (relaxed → 0, drain short-
     * circuits); the sync surface itself is pinned by
     * SyncStatusStateHolderTest.
     */
    private val playbackOutboxRepository: PlaybackOutboxRepository = mockk(relaxed = true)
    private val playbackSyncScheduler: PlaybackSyncScheduler = mockk(relaxed = true)
    private lateinit var fakeTimeSource: FakeTimeSource

    /**
     * The settings-search seam injected into the VM. In production the Hilt
     * binding (feature/settings' SettingsSearchModule) supplies the real
     * catalog; here a small fake with synthetic items keeps the VM's
     * settingsSearchResults flow exercisable without depending on
     * feature/settings from this suite.
     */
    private val fakeSettingsSearchProvider = object : SettingsSearchProvider {
        override val items = listOf(
            SettingsSearchItem(
                id = "test_setting",
                titleRes = 0,
                subtitleRes = 0,
                categoryRes = 0,
                keywords = listOf("test"),
                route = Route.Settings,
                icon = mockk(relaxed = true),
            ),
        )
    }

    private val userFlow = MutableStateFlow<UserInfo?>(null)

    /**
     * Identity plumbing for the VM's HomeSession collector (the single
     * identity detector): a mock JellyfinApiClient exposes a real atomic
     * session flow, and the shared real [HomeSession] runs on the rule's test
     * dispatcher so `runCurrent()` drives classification → transition → VM
     * routing deterministically. [userFlow] above still feeds the separate
     * uiState.currentUser mirror collector.
     */
    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val sessionApiClient: JellyfinApiClient = mockk(relaxed = true)
    private val homeSession: HomeSession by lazy {
        HomeSession(sessionApiClient, CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher))
    }

    /**
     * Backing flow for mediaRepository.userDataChanges — the refresher inside
     * the VM collects it from init, so it must be a real flow, not a relaxed
     * mock. Emission behaviour itself is covered by HomeRefresherTest.
     */
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 64)
    private val homeDiscoveryFlow = MutableStateFlow(HomeDiscoverySlice())
    private val appearanceFlow = MutableStateFlow(AppearanceSlice())
    private val experimentalFlow = MutableStateFlow(ExperimentalSlice())
    private val playbackFlow = MutableStateFlow(PlaybackSlice())
    private val seerrPrefsFlow = MutableStateFlow(SeerrPreferences())
    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Online)

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        episodeCatalogue = mockk(relaxed = true)
        userDataMutator = FakeUserDataMutator()
        imageUrlProvider = mockk(relaxed = true)
        photoFolderPrefetcher = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        downloadIntake = mockk(relaxed = true)
        userMessageBus = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)
        newsletterTriggerManager = mockk(relaxed = true)
        homeDiscoveryStore = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        experimentalStore = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        preferencesEditor = mockk(relaxed = true)
        widgetDataStore = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        seerrRequestDelegate = mockk(relaxed = true)
        seerrPreferencesStore = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)
        tvWatchNextScheduler = mockk(relaxed = true)
        continueWatchingBroadcaster = mockk(relaxed = true)
        librarySyncHook = mockk(relaxed = true)
        fakeTimeSource = FakeTimeSource()

        every { mediaSearchEngine.recentHistory() } returns flowOf(emptyList())
        every { mediaSearchEngine.preview(any()) } returns flowOf(
            MediaSearchPreviewState(query = "", jellyfin = emptyList(), seerr = emptyList(), isSearching = false)
        )

        every { authRepository.currentUser } returns userFlow
        every { sessionApiClient.session } returns sessionFlow
        every { mediaRepository.userDataChanges } returns userDataEvents
        every { homeDiscoveryStore.homeDiscovery } returns homeDiscoveryFlow
        every { appearanceStore.appearance } returns appearanceFlow
        every { experimentalStore.experimental } returns experimentalFlow
        every { playbackStore.playback } returns playbackFlow
        every { seerrPreferencesStore.preferences } returns seerrPrefsFlow
        every { offlineModeManager.offlineMode } returns offlineModeFlow
        every { offlineModeManager.networkStatus } returns networkStatusFlow
        every { offlineModeManager.isOffline } returns false
        every { downloadRepository.getActiveDownloadCount() } returns flowOf(0)
        every { downloadRepository.observeCompletedDownloadedIds() } returns flowOf(emptySet())
        every { downloadRepository.observeDownloadedIdsIncludingSeries() } returns flowOf(emptySet())
        every { offlineRepository.getOfflineLibrary() } returns flowOf(emptyList())
        every { offlineRepository.getOfflineEpisodes() } returns flowOf(emptyList())
        every { newsletterTriggerManager.shouldShowBanner() } returns flowOf(false)
    }

    private fun buildViewModel(): HomeViewModel = HomeViewModel(
        episodeCatalogue = episodeCatalogue,
        userDataMutator = userDataMutator,
        mediaSearchEngine = mediaSearchEngine,
        imageUrlProvider = imageUrlProvider,
        photoFolderPrefetcher = photoFolderPrefetcher,
        downloadRepository = downloadRepository,
        downloadIntake = downloadIntake,
        offlineRepository = offlineRepository,
        offlineModeManager = offlineModeManager,
        newsletterTriggerManager = newsletterTriggerManager,
        homeDiscoveryStore = homeDiscoveryStore,
        appearanceStore = appearanceStore,
        experimentalStore = experimentalStore,
        playbackStore = playbackStore,
        preferencesEditor = preferencesEditor,
        seerrRequestDelegate = seerrRequestDelegate,
        seerrPreferencesStore = seerrPreferencesStore,
        authRepository = authRepository,
        homeSession = homeSession,
        userMessageBus = userMessageBus,
        settingsSearchProvider = fakeSettingsSearchProvider,
        homeRefresherFactory = HomeRefresherFactory(
            timeSource = fakeTimeSource,
            mediaRepository = mediaRepository,
            seerrRepository = seerrRepository,
            arrRepository = arrRepository,
            orderHomeSections = OrderHomeSectionsUseCase(),
            widgetDataStore = widgetDataStore,
            continueWatchingBroadcaster = continueWatchingBroadcaster,
            tvWatchNextScheduler = tvWatchNextScheduler,
            librarySyncHook = librarySyncHook,
        ),
        syncStatusStateHolderFactory = SyncStatusStateHolderFactory(
            playbackOutboxRepository = playbackOutboxRepository,
            playbackSyncScheduler = playbackSyncScheduler,
            offlineFirstItemResolver = offlineFirstItemResolver,
        ),
    )

    /**
     * runTest wrapper that stops the VM's periodic-refresh `while(true)`
     * loop in a `finally` INSIDE the coroutine — before runTest's completion
     * advanceUntilIdle. An @After rule is too late: it only runs once runTest
     * returns, and runTest never returns while its scheduler is driving the
     * infinite loop's delay (the 600s-hang lesson). Equivalent to the old
     * manual last-line `stopPeriodicRefresh()` calls, but exception- and
     * early-return-proof.
     */
    private fun vmTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit): Unit =
        runTest {
            try {
                block()
            } finally {
                if (::viewModel.isInitialized) {
                    viewModel.onStop(mockk(relaxed = true))
                }
            }
        }

    @Test
    fun signIn_fetchesSections_andOrdersThem() = vmTest {
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
        signIn("u1")
        runCurrent()

        val sections = viewModel.uiState.value.sections
        // Default UserPreferences.homeSectionOrder is CONFIGURABLE, which lists
        // CONTINUE_WATCHING before LATEST_MEDIA — so ordering should apply.
        assertEquals(HomeSectionType.CONTINUE_WATCHING, sections.first().type)
        assertEquals(2, sections.size)
    }

    /**
     * The plan-03 two-sections test: the same item can appear in several home
     * sections, and the container adapter must flip EVERY occurrence (plus
     * zero the resume position) — while non-matching items keep their exact
     * instances. Mutation is optimistic through the fake mutator, mirroring
     * the real module's success path.
     */
    @Test
    fun markItemPlayed_flipsItemInEverySectionWhereItAppears() = vmTest {
        val shared = item("cw1").copy(playbackPositionTicks = 5_000_000_000L)
        val other = item("other")
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(
            HomeSectionsResult(
                sections = listOf(
                    section(HomeSectionType.CONTINUE_WATCHING, items = listOf(shared, other)),
                    section(HomeSectionType.LATEST_MEDIA, items = listOf(item("cw1"))),
                ),
            ),
        )
        viewModel = buildViewModel()
        signIn("u1")
        runCurrent()

        viewModel.onEvent(HomeUiEvent.MarkItemPlayed(item("cw1")))
        runCurrent()

        assertEquals(listOf(Triple("cw1", true, null)), userDataMutator.playedCalls)
        val sections = viewModel.uiState.value.sections
        val firstOccurrence = sections[0].items.first { it.id == "cw1" }
        val secondOccurrence = sections[1].items.single { it.id == "cw1" }
        assertTrue(firstOccurrence.isPlayed)
        assertTrue(secondOccurrence.isPlayed)
        // Server clears resume on manual mark; the patch mirrors it.
        assertEquals(0L, firstOccurrence.playbackPositionTicks)
        assertEquals(0L, secondOccurrence.playbackPositionTicks)
        // The sibling card in the first section is untouched.
        assertSame(other, sections[0].items.last())
    }

    @Test
    fun signOut_clearsSections() = vmTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(
            HomeSectionsResult(sections = listOf(section(HomeSectionType.CONTINUE_WATCHING))),
        )
        viewModel = buildViewModel()
        signIn("u1")
        runCurrent()
        assertFalse(viewModel.uiState.value.sections.isEmpty())

        signOut()
        runCurrent()

        assertTrue(viewModel.uiState.value.sections.isEmpty())
    }

    @Test
    fun offlineToOnline_clearsIsGoingOnline_afterFetch() = vmTest {
        // The sign-in fetch resolves immediately; the handshake's fetch parks
        // on the gate so the busy flag is observable mid-transition via the
        // uiState fold (the refresher owns the flag, the VM only folds it).
        val fetchGate = CompletableDeferred<Unit>()
        var fetchCalls = 0
        coEvery {
            mediaRepository.getHomeSections(any())
        } coAnswers {
            fetchCalls++
            if (fetchCalls == 1) Result.success(HomeSectionsResult(sections = emptyList()))
            else {
                fetchGate.await()
                Result.success(HomeSectionsResult(sections = emptyList()))
            }
        }
        viewModel = buildViewModel()
        signIn("u1")
        runCurrent()

        // Go offline first (the dock's offline toggle), like the real path.
        every { offlineModeManager.isOffline } returns true
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()

        // Go online through the real entry: the event forwards the
        // GoingOnline trigger to the refresher, which raises the busy flag
        // and toggles the manager; the mocked manager flips the mode flow
        // like the real one, and the refresher's own observer runs the
        // drain + capped fetch handshake.
        every { offlineModeManager.isOffline } returns false
        every { offlineModeManager.toggleManualOffline() } answers { offlineModeFlow.value = OfflineMode.ONLINE }
        viewModel.onEvent(HomeUiEvent.ToggleOfflineMode)
        runCurrent()

        assertTrue(
            "isGoingOnline must be observable (via the uiState fold) while the fetch runs",
            viewModel.uiState.value.isGoingOnline,
        )
        fetchGate.complete(Unit)
        runCurrent()

        assertFalse(
            "isGoingOnline must clear after the online fetch resolves",
            viewModel.uiState.value.isGoingOnline,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun offlineToOnline_clearsIsGoingOnline_whenFetchTimesOut() = vmTest {
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
        signIn("u1")
        runCurrent()

        every { offlineModeManager.isOffline } returns true
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()
        every { offlineModeManager.isOffline } returns false
        every { offlineModeManager.toggleManualOffline() } answers { offlineModeFlow.value = OfflineMode.ONLINE }
        viewModel.onEvent(HomeUiEvent.ToggleOfflineMode)
        runCurrent()
        assertTrue(
            "isGoingOnline must be observable (via the uiState fold) while the fetch hangs",
            viewModel.uiState.value.isGoingOnline,
        )
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
    }

    @Test
    fun prefChange_withUnrelatedPrefs_doesNotRefetch() = vmTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        viewModel = buildViewModel()
        signIn("u1")
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
    }

    @Test
    fun homeBackdropEnabled_mapsFromPreferences_toUiState() = vmTest {
        homeDiscoveryFlow.value = HomeDiscoverySlice(homeBackdropEnabled = false)
        viewModel = buildViewModel()
        runCurrent()

        assertFalse(viewModel.uiState.value.homeBackdropEnabled)

        homeDiscoveryFlow.value = HomeDiscoverySlice(homeBackdropEnabled = true)
        runCurrent()

        assertTrue(viewModel.uiState.value.homeBackdropEnabled)
    }

    @Test
    fun setSectionVisible_routesToStoreCommand() = vmTest {
        viewModel = buildViewModel()
        runCurrent()

        viewModel.onEvent(HomeUiEvent.SetSectionVisible(HomeSectionType.NEXT_UP, visible = false))
        runCurrent()

        coVerify { homeDiscoveryStore.setSectionVisible(HomeSectionType.NEXT_UP, visible = false) }
    }

    @Test
    fun moveSection_routesToStoreCommand() = vmTest {
        viewModel = buildViewModel()
        runCurrent()

        viewModel.onEvent(HomeUiEvent.MoveSection(HomeSectionType.NEXT_UP, up = false))
        runCurrent()

        coVerify { homeDiscoveryStore.moveSection(HomeSectionType.NEXT_UP, up = false) }
    }

    @Test
    fun setLibrarySectionVisible_routesToStoreCommand() = vmTest {
        viewModel = buildViewModel()
        runCurrent()

        viewModel.onEvent(HomeUiEvent.SetLibrarySectionVisible("movies", HomeSectionType.LATEST_MEDIA, visible = false))
        runCurrent()

        coVerify {
            homeDiscoveryStore.setLibrarySectionVisible("movies", HomeSectionType.LATEST_MEDIA, visible = false)
        }
    }

    @Test
    fun refresh_resetsScrollAndFetchesSections() = vmTest {
        coEvery {
            mediaRepository.getHomeSections(any(), any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        viewModel = buildViewModel()
        viewModel.saveHomeScrollPosition(5, 100)

        viewModel.onEvent(HomeUiEvent.Refresh)
        runCurrent()

        val pos = viewModel.getHomeScrollPosition()
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
        // Manual refresh bypasses the home-sections cache (force read).
        coVerify { mediaRepository.getHomeSections(any(), force = true) }
    }

    @Test
    fun pullToRefresh_invalidatesDiscoverCache_andRefetches() = vmTest {
        coEvery {
            mediaRepository.getHomeSections(any(), any())
        } returns Result.success(HomeSectionsResult(sections = emptyList()))
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.PullToRefresh)
        runCurrent()

        assertFalse(viewModel.uiState.value.isRefreshing)
        // Pull-to-refresh bypasses the home-sections cache (force read).
        coVerify { mediaRepository.getHomeSections(any(), force = true) }
    }

    @Test
    fun dismissNewsletterBanner_updatesUiState() = vmTest {
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.DismissNewsletterBanner)
        runCurrent()

        assertFalse(viewModel.uiState.value.newsletterBannerVisible)
    }

    @Test
    fun fetchAndUpdateSections_onFailure_setsErrorState() = vmTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.failure(RuntimeException("Connection timeout"))
        viewModel = buildViewModel()

        signIn("u1")
        runCurrent()

        assertEquals("Connection timeout", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ── Offline-library collection gate (offline modes + implicit offline) ──

    @Test
    fun offlineLibraryCollection_onlineFetchSuccess_neverCollected() = vmTest {
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.success(
            HomeSectionsResult(sections = listOf(section(HomeSectionType.LATEST_MEDIA, listOf(item("m1"))))),
        )
        viewModel = buildViewModel()

        signIn("u1")
        runCurrent()

        // The gate stays closed while online with content: no collection, so
        // download-progress writes can't re-invalidated the home tree.
        assertTrue(viewModel.uiState.value.offlineLibrary.isEmpty())
        assertEquals(HomeRenderSource.Online, viewModel.uiState.value.renderSource)
        verify(exactly = 0) { offlineRepository.getOfflineLibrary() }
        verify(exactly = 0) { offlineRepository.getOfflineEpisodes() }
    }

    @Test
    fun offlineLibraryCollection_onlineFetchFailure_collectsLibraryAndClearsPending() = vmTest {
        val libraryFlow = MutableSharedFlow<List<OfflineMediaItem>>(extraBufferCapacity = 8)
        // A completing cold flow (not a hot SharedFlow): combine waits for both
        // upstreams, and a never-emitting hot episodes flow would stall it.
        val episodes = listOf(OfflineMediaItem(id = "e1", name = "Ep", mediaType = MediaType.EPISODE))
        every { offlineRepository.getOfflineLibrary() } returns libraryFlow
        every { offlineRepository.getOfflineEpisodes() } returns flowOf(episodes)
        coEvery {
            mediaRepository.getHomeSections(any())
        } returns Result.failure(RuntimeException("Connection timeout"))
        viewModel = buildViewModel()

        signIn("u1")
        runCurrent()

        // The failed fetch opened the gate (implicit offline): the
        // pre-emission window carries the pending render source so the home
        // shows a loading state instead of flashing the hard error screen.
        assertEquals(HomeRenderSource.FallbackPending, viewModel.uiState.value.renderSource)

        val downloaded = listOf(OfflineMediaItem(id = "d1", name = "Downloaded", mediaType = MediaType.MOVIE))
        libraryFlow.tryEmit(downloaded)
        runCurrent()

        assertEquals(downloaded, viewModel.uiState.value.offlineLibrary)
        // Downloaded episodes land in their own state slice for the offline
        // CW / Next Up rows (the top-level library excludes episodes).
        assertEquals(episodes, viewModel.uiState.value.offlineEpisodes)
        // Downloads confirmed present: the implicit-offline fallback renders.
        assertEquals(HomeRenderSource.Offline.Implicit, viewModel.uiState.value.renderSource)
    }

    @Test
    fun selectSeerrRequestItem_and_clearRequestResult() = vmTest {
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

        org.junit.Assert.assertNull(viewModel.uiState.value.seerrRequestState.snapshot.requestResult)
    }

    /**
     * Signs in on BOTH surfaces, like the real login path: the atomic session
     * pair (drives HomeSession → the VM's refresh routing) and the plain
     * currentUser flow (drives the uiState.currentUser mirror).
     */
    private fun signIn(userId: String) {
        userFlow.value = userInfo(userId)
        sessionFlow.value = ActiveSession(serverInfo(), userInfo(userId))
    }

    /** Signs out on both surfaces — see [signIn]. */
    private fun signOut() {
        userFlow.value = null
        sessionFlow.value = null
    }

    private fun serverInfo() = ServerInfo(
        id = "s1",
        name = "Test Server",
        address = "http://server",
    )

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

    private fun episode(id: String, played: Boolean = false, ticks: Long? = null) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        parentId = "season-1",
        playbackPositionTicks = ticks,
        isPlayed = played,
    )

    // ── PlaySeries (series card smart-play resolution) ──────────────────────

    private fun snapshot(seriesId: String, episodes: List<MediaItem>) = EpisodeCatalogueSnapshot(
        seriesId = seriesId,
        seasons = emptyList(),
        episodesBySeason = mapOf("season-1" to episodes),
        fetchedSeasonIds = setOf("season-1"),
        sortedEpisodes = episodes,
        epoch = 1L,
    )

    private fun series(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.SERIES)

    @Test
    fun playSeries_picksResumeEpisode_fromCatalogueDecision() = vmTest {
        val episodes = listOf(
            episode("ep-1", played = true),
            episode("ep-2", ticks = 600_000_000L),
            episode("ep-3"),
        )
        coEvery {
            episodeCatalogue.loadSeriesEpisodes("series-1", offline = false)
        } returns Result.success(snapshot("series-1", episodes))
        viewModel = buildViewModel()

        var resolved: SeriesPlayResolution? = null
        viewModel.onEvent(HomeUiEvent.PlaySeries(series("series-1")) { resolved = it })
        runCurrent()

        val target = resolved as SeriesPlayResolution.Episode
        assertEquals("ep-2", target.item.id)
        assertEquals(600_000_000L, target.startPositionTicks)
    }

    @Test
    fun playSeries_catalogueFailure_fallsBackToDetails() = vmTest {
        coEvery {
            episodeCatalogue.loadSeriesEpisodes("series-x", offline = false)
        } returns Result.failure(IllegalStateException("server unreachable"))
        viewModel = buildViewModel()

        var resolved: SeriesPlayResolution? = null
        viewModel.onEvent(HomeUiEvent.PlaySeries(series("series-x")) { resolved = it })
        runCurrent()

        assertEquals("series-x", (resolved as SeriesPlayResolution.Details).series.id)
    }

    @Test
    fun playSeries_offlineHome_readsDownloadedEpisodes() = vmTest {
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        viewModel = buildViewModel()
        runCurrent()
        assertEquals(OfflineMode.OFFLINE_MANUAL, viewModel.uiState.value.offlineMode)

        coEvery {
            episodeCatalogue.loadSeriesEpisodes("series-1", offline = true)
        } returns Result.success(snapshot("series-1", listOf(episode("dl-ep-1"))))

        var resolved: SeriesPlayResolution? = null
        viewModel.onEvent(HomeUiEvent.PlaySeries(series("series-1")) { resolved = it })
        runCurrent()

        assertEquals("dl-ep-1", (resolved as SeriesPlayResolution.Episode).item.id)
    }

    @Test
    fun playSeries_failedFetchWithDownloads_isImplicitOffline() = vmTest {
        // The screen's implicit-offline render branch: online mode, but the
        // fetch failed leaving only downloads. Resolution must read local
        // episodes, not poke the server that just failed. The downloads must
        // actually exist — the render-source fold treats a failed fetch over a
        // confirmed-empty offline library as the hard-error screen (Online),
        // where no card can fire a play at all.
        coEvery { mediaRepository.getHomeSections(any()) } returns
            Result.failure(IOException("server down"))
        every { offlineRepository.getOfflineLibrary() } returns flowOf(
            listOf(OfflineMediaItem(id = "dl-1", name = "Downloaded", mediaType = MediaType.MOVIE)),
        )
        viewModel = buildViewModel()
        signIn("u1")
        runCurrent()
        assertEquals(OfflineMode.ONLINE, viewModel.uiState.value.offlineMode)
        assertNotNull(viewModel.uiState.value.error)
        assertEquals(HomeRenderSource.Offline.Implicit, viewModel.uiState.value.renderSource)

        coEvery {
            episodeCatalogue.loadSeriesEpisodes("series-1", offline = true)
        } returns Result.success(snapshot("series-1", listOf(episode("dl-ep-1"))))

        var resolved: SeriesPlayResolution? = null
        viewModel.onEvent(HomeUiEvent.PlaySeries(series("series-1")) { resolved = it })
        runCurrent()

        assertEquals("dl-ep-1", (resolved as SeriesPlayResolution.Episode).item.id)
    }

    @Test
    fun playSeries_rapidSecondTap_whileResolveInFlight_isDropped() = vmTest {
        val gate = CompletableDeferred<Result<EpisodeCatalogueSnapshot>>()
        coEvery {
            episodeCatalogue.loadSeriesEpisodes(any(), any())
        } coAnswers { gate.await() }
        viewModel = buildViewModel()

        var firstResolved: SeriesPlayResolution? = null
        var secondResolved: SeriesPlayResolution? = null
        viewModel.onEvent(HomeUiEvent.PlaySeries(series("s-a")) { firstResolved = it })
        viewModel.onEvent(HomeUiEvent.PlaySeries(series("s-b")) { secondResolved = it })

        gate.complete(Result.success(snapshot("s-a", listOf(episode("a-ep-1")))))
        runCurrent()

        assertEquals("a-ep-1", (firstResolved as SeriesPlayResolution.Episode).item.id)
        assertNull(secondResolved)
    }

    /**
     * Controllable [TimeSource] whose clock defaults to a fixed epoch so the
     * periodic-refresh and TTL gates stay on one side of their thresholds;
     * tests move [nowMs] to deliberately cross one.
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }

    /**
     * Behavior fake for [UserDataMutator]: records calls and mimics the real
     * module's success path (container rewrite via the resolved
     * [AppliedMutation] patch) so the home container adapter's flip is driven
     * exactly as in production. The protocol itself is pinned by
     * UserDataMutatorTest in :core:data.
     */
    private class FakeUserDataMutator : UserDataMutator {
        val playedCalls = mutableListOf<Triple<String, Boolean, String?>>()

        override suspend fun setPlayed(
            itemId: String,
            played: Boolean,
            mode: UserDataMutator.FlipMode,
            containers: List<UserDataContainer>,
            seriesId: String?,
        ): Result<AppliedMutation> {
            playedCalls += Triple(itemId, played, seriesId)
            val applied = AppliedMutation(itemId = itemId, played = played)
            if (mode == UserDataMutator.FlipMode.Optimistic) {
                containers.forEach { it.rewrite(itemId, applied::patch) }
            }
            return Result.success(applied)
        }

        override suspend fun setFavorite(
            itemId: String,
            mode: UserDataMutator.FlipMode,
            containers: List<UserDataContainer>,
            seriesId: String?,
        ): Result<AppliedMutation> = Result.success(AppliedMutation(itemId = itemId, favorite = true))

        override suspend fun setSeasonPlayed(
            seriesId: String,
            seasonId: String,
            played: Boolean,
        ): Result<AppliedMutation> = Result.success(AppliedMutation(itemId = seasonId, played = played))
    }
}
