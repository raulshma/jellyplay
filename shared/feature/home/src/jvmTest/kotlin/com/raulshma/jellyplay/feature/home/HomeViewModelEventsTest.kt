package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadRequestResult
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.AppliedMutation
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.UserDataContainer
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
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
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_loading
import com.raulshma.jellyplay.core.ui.generated.resources.core_search
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_playback
import com.raulshma.jellyplay.feature.home.generated.resources.home_download_start_failed
import com.raulshma.jellyplay.feature.home.generated.resources.home_download_started
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.viewModelScope
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Invariants pinned (the onEvent branches [HomeViewModelTest] does not route):
 *  - [HomeUiEvent.MarkItemUnplayed] flips the card back in every section and
 *    carries played=false to the mutator.
 *  - [HomeUiEvent.DownloadItem] routes each [DownloadRequestResult]: Started →
 *    info toast, Failed → error toast, NeedsDetailScreen → onOpenDetail(itemId,
 *    openDownloadSheet=false), SeriesSelectionRequired → silent no-op.
 *  - [HomeUiEvent.SettingsResultClicked] enables advanced settings first ONLY
 *    for an advanced target; a non-advanced target writes nothing.
 *  - [HomeUiEvent.SyncNow] enqueues the playback sync only while online.
 *  - [HomeUiEvent.SwitchUser] delegates to AuthRepository.
 *  - [HomeUiEvent.ToggleOfflineMode] while ONLINE toggles the manager directly
 *    (no going-online handshake, isGoingOnline stays down).
 *  - [HomeUiEvent.PlaySeries] with a resolved-but-empty catalogue falls back to
 *    Details; the single-flight latch re-arms after a resolve settles.
 *
 * Harness mirrors [HomeViewModelTest] (MockK + inlined StandardTestDispatcher +
 * runCurrent, never advanceUntilIdle; the periodic loop dies via
 * viewModelScope.cancel in a finally INSIDE the coroutine).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelEventsTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var episodeCatalogue: EpisodeCatalogue
    private lateinit var userDataMutator: RecordingUserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var photoFolderPrefetcher: PhotoFolderPrefetcher
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var downloadIntake: DownloadIntake
    private lateinit var mediaDownloadActions: MediaDownloadActions
    private lateinit var userMessageBus: UserMessageBus
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var newsletterTriggerManager: NewsletterTriggerManager
    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var playbackStore: PlaybackStore
    private lateinit var preferencesEditor: PreferencesEditor
    private lateinit var widgetDataStore: WidgetDataStore
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var seerrRequestDelegate: SeerrRequestDelegate
    private lateinit var seerrPreferencesStore: SeerrPreferencesStore
    private lateinit var authRepository: AuthRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var tvWatchNextScheduler: TvWatchNextScheduler
    private lateinit var continueWatchingBroadcaster: ContinueWatchingBroadcaster
    private lateinit var librarySyncHook: LibrarySyncHook
    private lateinit var playbackSyncScheduler: PlaybackSyncScheduler
    private lateinit var fakeTimeSource: FakeTimeSource

    private val mediaSearchEngine: MediaSearchEngine = mockk(relaxed = true)
    private val offlineFirstItemResolver: OfflineFirstItemResolver = mockk(relaxed = true)
    private val playbackOutboxRepository: PlaybackOutboxRepository = mockk(relaxed = true)

    private val fakeSettingsSearchProvider = object : SettingsSearchProvider {
        override val items = listOf(
            SettingsSearchItem(
                id = "test_setting",
                titleRes = Res.string.core_search,
                subtitleRes = Res.string.core_loading,
                categoryRes = Res.string.ss_cat_playback,
                keywords = listOf("test"),
                route = Route.Settings,
                icon = mockk(relaxed = true),
            ),
        )
    }

    private val userFlow = MutableStateFlow<UserInfo?>(null)
    private val sessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val sessionApiClient: JellyfinApiClient = mockk(relaxed = true)
    private val homeSession: HomeSession by lazy {
        HomeSession(sessionApiClient, CoroutineScope(SupervisorJob() + mainDispatcher))
    }

    private val userDataEvents = MutableSharedFlow<com.raulshma.jellyplay.core.model.UserDataChange>(extraBufferCapacity = 64)
    private val homeDiscoveryFlow = MutableStateFlow(HomeDiscoverySlice())
    private val appearanceFlow = MutableStateFlow(AppearanceSlice())
    private val experimentalFlow = MutableStateFlow(com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice())
    private val playbackFlow = MutableStateFlow(PlaybackSlice())
    private val seerrPrefsFlow = MutableStateFlow(SeerrPreferences())
    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Online)

    private lateinit var viewModel: HomeViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        episodeCatalogue = mockk(relaxed = true)
        userDataMutator = RecordingUserDataMutator()
        imageUrlProvider = mockk(relaxed = true)
        photoFolderPrefetcher = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        downloadIntake = mockk(relaxed = true)
        mediaDownloadActions = mockk(relaxed = true)
        every { mediaDownloadActions.downloadedIds } returns MutableStateFlow(emptySet())
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
        playbackSyncScheduler = mockk(relaxed = true)
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
        coEvery { mediaRepository.getOfflineHomeLayout() } returns null
        every { newsletterTriggerManager.shouldShowBanner() } returns flowOf(false)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): HomeViewModel = HomeViewModel(
        episodeCatalogue = episodeCatalogue,
        userDataMutator = userDataMutator,
        mediaSearchEngine = mediaSearchEngine,
        mediaRepository = mediaRepository,
        imageUrlProvider = imageUrlProvider,
        photoFolderPrefetcher = photoFolderPrefetcher,
        downloadRepository = downloadRepository,
        downloadIntake = downloadIntake,
        mediaDownloadActions = mediaDownloadActions,
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

    private fun vmTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit): Unit =
        runTest {
            try {
                block()
            } finally {
                if (::viewModel.isInitialized) {
                    viewModel.viewModelScope.cancel()
                }
            }
        }

    // ── MarkItemUnplayed ─────────────────────────────────────────────────────

    @Test
    fun markItemUnplayed_carriesPlayedFalse_andFlipsEverySectionOccurrence() = vmTest {
        val shared = item("cw1").copy(isPlayed = true)
        coEvery { mediaRepository.getHomeSections(any()) } returns Result.success(
            HomeSectionsResult(
                sections = listOf(
                    section(HomeSectionType.CONTINUE_WATCHING, listOf(shared)),
                    section(HomeSectionType.LATEST_MEDIA, listOf(item("cw1").copy(isPlayed = true))),
                ),
            ),
        )
        viewModel = buildViewModel()
        signIn("u1")
        runCurrent()

        viewModel.onEvent(HomeUiEvent.MarkItemUnplayed(item("cw1")))
        runCurrent()

        assertEquals(Triple("cw1", false, null as Any?), userDataMutator.playedCalls.single())
        viewModel.uiState.value.sections.forEach { sec ->
            assertFalse(sec.items.single { it.id == "cw1" }.isPlayed)
        }
    }

    // ── DownloadItem routing ────────────────────────────────────────────────

    @Test
    fun downloadItem_started_postsInfoMessage() = vmTest {
        coEvery { downloadIntake.startFromItem(any()) } returns DownloadRequestResult.Started
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.DownloadItem(item("m1")))
        runCurrent()

        val infoSlot = slot<UiText>()
        verify { userMessageBus.info(capture(infoSlot)) }
        assertTrue(infoSlot.captured is UiText.Resource, "expected a resource-backed info message")
        verify(exactly = 0) { userMessageBus.error(any<UiText>()) }
    }

    @Test
    fun downloadItem_failed_postsErrorMessage() = vmTest {
        coEvery { downloadIntake.startFromItem(any()) } returns DownloadRequestResult.Failed("disk full")
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.DownloadItem(item("m1")))
        runCurrent()

        val errorSlot = slot<UiText>()
        verify { userMessageBus.error(capture(errorSlot)) }
        assertTrue(errorSlot.captured is UiText.Resource, "expected a resource-backed error message")
        verify(exactly = 0) { userMessageBus.info(any<UiText>()) }
    }

    @Test
    fun downloadItem_needsDetailScreen_opensDetail_withoutTheDownloadSheet() = vmTest {
        coEvery { downloadIntake.startFromItem(any()) } returns
            DownloadRequestResult.NeedsDetailScreen("season-9")
        viewModel = buildViewModel()

        var opened: Pair<String, Boolean>? = null
        viewModel.onEvent(HomeUiEvent.DownloadItem(item("season-9")) { id, sheet -> opened = id to sheet })
        runCurrent()

        assertEquals("season-9" to false, opened)
        verify(exactly = 0) { userMessageBus.info(any<UiText>()) }
        verify(exactly = 0) { userMessageBus.error(any<UiText>()) }
    }

    @Test
    fun downloadItem_seriesSelectionRequired_isSilentlyIgnored() = vmTest {
        coEvery { downloadIntake.startFromItem(any()) } returns
            DownloadRequestResult.SeriesSelectionRequired("series-1")
        viewModel = buildViewModel()

        var opened: Pair<String, Boolean>? = null
        viewModel.onEvent(HomeUiEvent.DownloadItem(item("series-1")) { id, sheet -> opened = id to sheet })
        runCurrent()

        assertNull(opened)
        verify(exactly = 0) { userMessageBus.info(any<UiText>()) }
        verify(exactly = 0) { userMessageBus.error(any<UiText>()) }
    }

    // ── SettingsResultClicked ───────────────────────────────────────────────

    @Test
    fun settingsResultClicked_advancedTarget_enablesAdvancedSettingsFirst() = vmTest {
        viewModel = buildViewModel()

        val blockSlot = slot<suspend PreferencesEditScope.() -> Unit>()
        every { preferencesEditor.edit(capture(blockSlot)) } returns mockk()

        viewModel.onEvent(HomeUiEvent.SettingsResultClicked(resolvedItem(isAdvanced = true)))
        runCurrent()

        val editScope = mockk<PreferencesEditScope>(relaxed = true)
        val appearance = mockk<AppearanceStore>(relaxed = true)
        every { editScope.appearance } returns appearance
        blockSlot.captured(editScope)
        coVerify(exactly = 1) { appearance.setShowAdvancedSettings(true) }
    }

    @Test
    fun settingsResultClicked_nonAdvancedTarget_writesNothing() = vmTest {
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.SettingsResultClicked(resolvedItem(isAdvanced = false)))
        runCurrent()

        verify(exactly = 0) { preferencesEditor.edit(any()) }
    }

    // ── SyncNow / SwitchUser / ToggleOfflineMode(online) ────────────────────

    @Test
    fun syncNow_whileOnline_enqueuesPlaybackSync() = vmTest {
        viewModel = buildViewModel()
        runCurrent()

        viewModel.onEvent(HomeUiEvent.SyncNow)
        runCurrent()

        verify(exactly = 1) { playbackSyncScheduler.enqueueNow() }
    }

    @Test
    fun syncNow_whileOffline_isNoOp() = vmTest {
        viewModel = buildViewModel()
        runCurrent()
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()

        viewModel.onEvent(HomeUiEvent.SyncNow)
        runCurrent()

        verify(exactly = 0) { playbackSyncScheduler.enqueueNow() }
    }

    @Test
    fun switchUser_delegatesToAuthRepository() = vmTest {
        coEvery { authRepository.switchUser(any()) } returns Result.success(Unit)
        viewModel = buildViewModel()

        viewModel.onEvent(HomeUiEvent.SwitchUser("u2"))
        runCurrent()

        coVerify(exactly = 1) { authRepository.switchUser("u2") }
    }

    @Test
    fun toggleOfflineMode_whileOnline_togglesManagerDirectly_withoutGoingOnlineHandshake() = vmTest {
        viewModel = buildViewModel()
        runCurrent()

        viewModel.onEvent(HomeUiEvent.ToggleOfflineMode)
        runCurrent()

        verify(exactly = 1) { offlineModeManager.toggleManualOffline() }
        assertFalse(
            viewModel.uiState.value.isGoingOnline,
            "going offline is instantaneous — no busy flag",
        )
    }

    // ── PlaySeries: empty catalogue + latch re-arm ──────────────────────────

    @Test
    fun playSeries_resolvedButEmptyCatalogue_fallsBackToDetails() = vmTest {
        coEvery { episodeCatalogue.loadSeriesEpisodes("series-empty", offline = false) } returns
            Result.success(
                EpisodeCatalogueSnapshot(
                    seriesId = "series-empty",
                    seasons = emptyList(),
                    episodesBySeason = emptyMap(),
                    fetchedSeasonIds = emptySet(),
                    sortedEpisodes = emptyList(),
                    epoch = 1L,
                ),
            )
        viewModel = buildViewModel()

        var resolved: SeriesPlayResolution? = null
        viewModel.onEvent(HomeUiEvent.PlaySeries(series("series-empty")) { resolved = it })
        runCurrent()

        assertEquals("series-empty", (resolved as SeriesPlayResolution.Details).series.id)
    }

    @Test
    fun playSeries_secondTapAccepted_afterFirstResolveSettles() = vmTest {
        val gate = CompletableDeferred<Result<EpisodeCatalogueSnapshot>>()
        coEvery { episodeCatalogue.loadSeriesEpisodes("s-a", offline = false) } coAnswers { gate.await() }
        coEvery {
            episodeCatalogue.loadSeriesEpisodes("s-b", offline = false)
        } returns Result.success(snapshot("s-b", listOf(episode("b-ep-1"))))
        viewModel = buildViewModel()

        var first: SeriesPlayResolution? = null
        var second: SeriesPlayResolution? = null
        viewModel.onEvent(HomeUiEvent.PlaySeries(series("s-a")) { first = it })
        runCurrent()
        assertNull(first, "resolve still parked on the gate")

        gate.complete(Result.success(snapshot("s-a", listOf(episode("a-ep-1")))))
        runCurrent()
        assertEquals("a-ep-1", (first as SeriesPlayResolution.Episode).item.id)

        // The latch must re-arm once the first resolve settles — a fresh tap
        // after the first navigation must be accepted.
        viewModel.onEvent(HomeUiEvent.PlaySeries(series("s-b")) { second = it })
        runCurrent()
        assertEquals("b-ep-1", (second as SeriesPlayResolution.Episode).item.id)
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private fun signIn(userId: String) {
        userFlow.value = userInfo(userId)
        sessionFlow.value = ActiveSession(serverInfo(), userInfo(userId))
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

    private fun resolvedItem(isAdvanced: Boolean) = ResolvedSettingsItem(
        item = SettingsSearchItem(
            id = "test_setting",
            titleRes = Res.string.core_search,
            subtitleRes = Res.string.core_loading,
            categoryRes = Res.string.ss_cat_playback,
            keywords = listOf("test"),
            route = Route.Settings,
            icon = mockk(relaxed = true),
            isAdvanced = isAdvanced,
        ),
        title = "Test",
        subtitle = "Test",
        category = "Playback",
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

    private fun episode(id: String) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        parentId = "season-1",
    )

    private fun series(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.SERIES)

    private fun snapshot(seriesId: String, episodes: List<MediaItem>) = EpisodeCatalogueSnapshot(
        seriesId = seriesId,
        seasons = emptyList(),
        episodesBySeason = mapOf("season-1" to episodes),
        fetchedSeasonIds = setOf("season-1"),
        sortedEpisodes = episodes,
        epoch = 1L,
    )

    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: java.time.ZoneId): java.time.LocalDate = java.time.LocalDate.of(2026, 1, 1)
    }

    /**
     * Behavior fake for [UserDataMutator]: records mark-played/unplayed calls
     * and mirrors the real module's optimistic success path (same shape as the
     * fake in [HomeViewModelTest]).
     */
    private class RecordingUserDataMutator : UserDataMutator {
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
