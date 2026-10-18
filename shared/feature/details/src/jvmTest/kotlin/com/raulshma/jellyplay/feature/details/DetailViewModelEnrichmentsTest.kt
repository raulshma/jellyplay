package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DetailLoadState
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.JellyPlayEpisodeRatings
import com.raulshma.jellyplay.core.network.api.JellyPlayRatingsResult
import com.raulshma.jellyplay.core.network.api.JellyPlayRatingsRoutes
import com.raulshma.jellyplay.core.data.repository.MediaCatalogueEnrichments
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.RemoteConnectivity
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.testfixtures.FakeUserDataMutator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * The declared enrichment fan-out ([DetailViewModel.enrichments] evaluated by
 * [DetailViewModel.runEnrichments]) pinned end-to-end: given a resolved
 * snapshot of type X with capabilities Y, the gated enrichments fire — and
 * NOT the others. Observable firing is each declaration's uiState write
 * (relatedItems / specialFeatures / segment booleans / sonarrServersResolved /
 * localRelatedItems / collectionItems) plus the theme-music and Arr seams.
 *
 * The harness mirrors [DetailViewModelTest] (MockK collaborators, fake
 * provider per item, Main test dispatcher); bodies are the former
 * hand-launched blocks verbatim, so the assertions are the same observable
 * behavior the screen saw before the fan-out became data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelEnrichmentsTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val strings = fakeDetailStrings()

    private lateinit var mediaRepository: MediaRepository

    // The browse-catalogue enrichments fake (the franchise row's source read).
    // Defaults to success/empty; tests override via boxSetResults and assert
    // reachability via boxSetCalls.
    private val catalogueEnrichments = object : MediaCatalogueEnrichments {
        val boxSetResults = HashMap<String, Result<List<com.raulshma.jellyplay.core.model.CollectionSummary>>>()
        val boxSetCalls = mutableListOf<String>()
        override suspend fun getTags(parentId: String?): Result<List<String>> =
            Result.success(emptyList())
        override suspend fun getBoxSetsContaining(itemId: String): Result<List<com.raulshma.jellyplay.core.model.CollectionSummary>> {
            boxSetCalls += itemId
            return boxSetResults[itemId] ?: Result.success(emptyList())
        }
    }
    private lateinit var libraryApiClient: LibraryApiClient
    private lateinit var mediaDetailProvider: MediaDetailProvider
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var themeMusicPlayer: DetailThemeMusic
    private lateinit var projections: PreferenceProjections

    private val providerFlows = mutableMapOf<String, MutableStateFlow<DetailLoadState>>()
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 64)

    private lateinit var viewModel: DetailViewModel

    @BeforeTest
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        libraryApiClient = mockk(relaxed = true)
        mediaDetailProvider = mockk(relaxed = false)
        playbackRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)
        themeMusicPlayer = mockk(relaxed = true)
        // D8: the VM folds the seam's playing flow into uiState — a real
        // StateFlow, not a relaxed mock.
        every { themeMusicPlayer.isPlaying } returns MutableStateFlow(false)

        every { seerrRepository.preferences } returns MutableStateFlow(SeerrPreferences())
        val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
        every { offlineModeManager.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        coEvery { mediaRepository.getSimilarItems(any(), any()) } returns Result.success(emptyList())
        coEvery { libraryApiClient.getSpecialFeatures(any()) } returns Result.success(emptyList())
        // D6: the franchise rows' BoxSet-ancestor read — the injected
        // [catalogueEnrichments] fake defaults to success/empty (no ancestor
        // → no rows). Individual tests override via boxSetResults.
        // D7: the "Airing next" strip fetch — empty keeps the strip absent.
        // Individual tests override.
        coEvery { libraryApiClient.getUpcoming(any(), any(), any()) } returns Result.success(emptyList())
        // D3: the merged trailer list's local leg — empty keeps the list
        // remote-only (every pre-D3 shape). Individual tests override.
        coEvery { libraryApiClient.getLocalTrailers(any()) } returns Result.success(emptyList())
        // D22: the ambient ThemeVideo leg — empty keeps the hero on the
        // embed/static (every pre-D22 shape). Individual tests override.
        coEvery { libraryApiClient.getThemeVideos(any()) } returns Result.success(emptyList())
        coEvery { playbackRepository.getMediaSegments(any()) } returns Result.success(emptyList())
        coEvery { seerrRepository.getTmdbVideos(any(), any()) } returns Result.success(emptyList())
        coEvery { seerrRepository.getTmdbReviews(any(), any()) } returns Result.success(emptyList())
        // D4: the externalRatings enrichment's Seerr leg — an empty payload
        // keeps every pre-D4 behavior (the strip falls through to the TMDB
        // seam, unwired unless a test injects it, and stays absent).
        coEvery { seerrRepository.getRatings(any(), any()) } returns
            Result.success(com.raulshma.jellyplay.core.model.seerr.SeerrRatings())
        coEvery { mediaDetailProvider.refresh(any()) } returns Unit
        every { mediaRepository.userDataChanges } returns userDataEvents
        coEvery { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) } returns Unit
        every { mediaDetailProvider.invalidate(any()) } returns Unit
        coEvery { arrRepository.resolveServers() } returns Result.success(ArrServiceSummary())
        coEvery { offlineRepository.getLocalRelated(any(), any(), any(), any()) } returns emptyList()

        projections = mockk(relaxed = true)
        every { projections.detailPreferences } returns MutableStateFlow(DetailPreferences())
        val homeDiscoveryStore = mockk<HomeDiscoveryStore>(relaxed = true)
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
        val experimentalStore = mockk<ExperimentalStore>(relaxed = true)
        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        val engineStore = mockk<PlayerEngineStore>(relaxed = true)
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        val libraryStore = mockk<LibraryStore>(relaxed = true)
        every { libraryStore.library } returns MutableStateFlow(LibrarySlice())

        viewModel = buildViewModel()
    }

    /**
     * The setUp construction, extractable so a single test can flip a
     * pre-construction stub (the Seerr connection flags — the combine captures
     * the preferences flow at construction time) or inject the D4 TMDB
     * fallback seam before building.
     */
    private fun buildViewModel(
        tmdbApiClient: com.raulshma.jellyplay.core.network.api.TmdbApiClient? = null,
        pluginStatusStore: com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore? = null,
        jellyPlayFeatureGate: com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate? = null,
        pluginRatingsApi: com.raulshma.jellyplay.core.network.api.JellyPlayRatingsRoutes? = null,
    ): DetailViewModel = DetailViewModel(
        storageProbe = mockk<DetailStorageProbe>(relaxed = true),
        strings = strings,
        mediaRepository = mediaRepository,
        mediaCatalogueEnrichments = catalogueEnrichments,
        libraryApiClient = libraryApiClient,
        collectionApiClient = mockk<com.raulshma.jellyplay.core.network.api.CollectionApiClient>(relaxed = true),
        userDataMutator = FakeUserDataMutator(mediaDetailProvider),
        mediaDetailProvider = mediaDetailProvider,
        playbackRepository = playbackRepository,
        imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true),
        offlineRepository = offlineRepository,
        stores = DetailStores(
            projections = projections,
            libraryStore = mockk<LibraryStore>(relaxed = true).also {
                every { it.library } returns MutableStateFlow(LibrarySlice())
            },
            homeDiscoveryStore = mockk<HomeDiscoveryStore>(relaxed = true).also {
                every { it.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
            },
            experimentalStore = mockk<ExperimentalStore>(relaxed = true).also {
                every { it.experimental } returns MutableStateFlow(ExperimentalSlice())
            },
            engineStore = mockk<PlayerEngineStore>(relaxed = true).also {
                every { it.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
            },
            notificationStore = mockk<NotificationStore>(relaxed = true).also {
                every { it.notification } returns MutableStateFlow(NotificationSlice())
            },
        ),
        remoteDiscovery = RemoteDiscoveryClients(
            seerrRepository = seerrRepository,
            seerrRequestDelegate = mockk<SeerrRequestDelegate>(relaxed = true),
            arrRepository = arrRepository,
            offlineModeManager = mockk<OfflineModeManager>(relaxed = true).also {
                every { it.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
            },
        ),
        audioQueueFacade = mockk<AudioQueueFacade>(),
        themeMusicPlayer = themeMusicPlayer,
        actionFactories = DetailActionFactories(
            downloads = DownloadLifecycleActions.Factory(
                downloadIntake = mockk<DownloadIntake>(relaxed = true),
                downloadsStore = mockk<DownloadsStore>(relaxed = true),
                adaptiveBitrateManager = mockk(relaxed = true),
                downloadRepository = mockk(relaxed = true),
            ),
            resync = ResyncActions.Factory(
                offlineSyncManager = mockk<OfflineSyncManager>(relaxed = true),
                downloadIntake = mockk<DownloadIntake>(relaxed = true),
            ),
            playlists = PlaylistTargets.Factory(
                playlistRepository = mockk<PlaylistRepository>(relaxed = true),
                appRuntimeStateStore = mockk<AppRuntimeStateStore>(relaxed = true),
            ),
            watchParty = WatchPartyActions.Factory(
                syncPlayRepository = mockk<SyncPlayRepository>(relaxed = true),
                syncPlayManager = mockk<SyncPlayManager>(relaxed = true),
            ),
                metadataAdmin = MetadataAdminActions.Factory(
                    editorRepository = mockk<MetadataEditorRepository>(relaxed = true),
                    mediaRepository = mockk(relaxed = true),
                    authRepository = mockk<AuthRepository>(relaxed = true),
                ),
        ),
        mediaDownloadActions = mockk<MediaDownloadActions>(relaxed = true),
        smartPlayDispatcher = mainDispatcher,
        tmdbApiClient = tmdbApiClient,
        pluginStatusStore = pluginStatusStore,
        pluginRatingsApi = pluginRatingsApi,
        jellyPlayFeatureGate = jellyPlayFeatureGate,
    )

    private fun stubProvider(
        itemId: String,
        initial: DetailLoadState = DetailLoadState.Loading,
    ): MutableStateFlow<DetailLoadState> {
        val flow = MutableStateFlow(initial)
        providerFlows[itemId] = flow
        every { mediaDetailProvider.observe(itemId) } returns flow
        return flow
    }

    private fun snapshot(
        origin: DetailOrigin,
        detail: MediaDetail,
        remoteDiscoveryAllowed: Boolean,
        contentGeneration: Long = 0L,
        seasons: List<MediaItem> = emptyList(),
    ): DetailLoadState.Loaded = DetailLoadState.Loaded(
        MediaDetailSnapshot(
            detail = detail,
            context = DetailContext(
                origin = origin,
                connectivity = if (origin == DetailOrigin.REMOTE) {
                    RemoteConnectivity.AVAILABLE
                } else {
                    RemoteConnectivity.BLOCKED
                },
                download = null,
                syncState = null,
                seriesAggregate = null,
            ),
            capabilities = DetailCapabilities(
                remoteDiscovery = remoteDiscoveryAllowed,
                remoteStreamSelection = remoteDiscoveryAllowed,
                localSubtitleSelection = false,
                localStreamInfo = false,
                personNavigation = remoteDiscoveryAllowed,
                studioNavigation = remoteDiscoveryAllowed,
                smartPlay = remoteDiscoveryAllowed,
                remoteWorkAllowed = remoteDiscoveryAllowed,
                localDownloadManagement = false,
                tagNavigation = remoteDiscoveryAllowed,
                chapters = false,
            ),
            assets = DetailAssets(),
            seasons = seasons,
            episodesBySeason = emptyMap(),
            fetchedSeasonIds = emptySet(),
            sortedEpisodes = emptyList(),
            albumTracks = emptyList(),
            localSubtitles = emptyList(),
            contentGeneration = contentGeneration,
        ),
    )

    private fun loadAndSettle(itemId: String, loaded: DetailLoadState.Loaded) = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(itemId, loaded)
        viewModel.onEvent(DetailUiEvent.LoadItem(itemId))
        advanceUntilIdle()
    }

    // ── The gate table ───────────────────────────────────────────────────

    @Test
    fun remoteMovieWithDiscovery_firesAllRemoteEnrichments_andNoneOfTheLocalOnes() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getSimilarItems("m1", limit = 12) } returns
            Result.success(listOf(MediaItem(id = "other", name = "Other", mediaType = MediaType.MOVIE)))
        coEvery { libraryApiClient.getSpecialFeatures("m1") } returns
            Result.success(listOf(MediaItem(id = "extra1", name = "Extra", mediaType = MediaType.MOVIE)))
        coEvery { playbackRepository.getMediaSegments("m1") } returns Result.success(
            listOf(
                MediaSegment(id = "seg1", itemId = "m1", type = MediaSegmentType.INTRO, startTicks = 0L, endTicks = 10L),
                MediaSegment(id = "seg2", itemId = "m1", type = MediaSegmentType.OUTRO, startTicks = 20L, endTicks = 30L),
            ),
        )
        stubProvider(
            "m1",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("m1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // remote-only enrichments fired…
        assertTrue(state.relatedItems.map { it.id } == listOf("other"), "similarItems wrote relatedItems (self-filtered)")
        assertTrue(state.specialFeatures.map { it.id } == listOf("extra1"), "specialFeatures fired")
        assertTrue(state.hasIntroSegment && state.hasCreditSegment, "mediaSegments fired")
        verify { themeMusicPlayer.playThemeFor("m1") }
        // …and the local/collection/arr ones did NOT.
        assertTrue(state.localRelatedItems.isEmpty())
        assertTrue(state.collectionItems.isEmpty())
        assertFalse(state.sonarrServersResolved)
        coVerify(exactly = 0) { offlineRepository.getLocalRelated(any(), any(), any(), any()) }
        coVerify(exactly = 0) { mediaRepository.getCollectionItems(any(), any(), any(), any()) }
        coVerify(exactly = 0) { arrRepository.resolveServers() }
    }

    @Test
    fun remoteSeriesWithTvdb_resolvesArrServers_forCanManageSeries() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { arrRepository.resolveServers() } returns Result.success(
            ArrServiceSummary(sonarrServers = listOf(mockk(relaxed = true))),
        )
        stubProvider(
            "s1",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(
                    item = MediaItem(id = "s1", name = "Series", mediaType = MediaType.SERIES),
                    providerIds = mapOf("tvdb" to "12345"),
                ),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("s1"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.sonarrServersResolved, "arrServerResolve fired for SERIES with tvdb")
    }

    @Test
    fun remoteEpisodeWithoutTvdb_skipsArrResolve() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "e1",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(
                    item = MediaItem(
                        id = "e1",
                        name = "Episode",
                        mediaType = MediaType.EPISODE,
                        seriesId = "s1",
                    ),
                    providerIds = mapOf("tvdb" to "not-a-number"),
                ),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("e1"))
        advanceUntilIdle()

        coVerify(exactly = 0) { arrRepository.resolveServers() }
        assertFalse(viewModel.uiState.value.sonarrServersResolved)
    }

    @Test
    fun localSnapshot_firesOnlyLocalRelated_andNoRemoteEnrichment() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { offlineRepository.getLocalRelated(any(), any(), any(), any()) } returns
            listOf(MediaItem(id = "local1", name = "Local", mediaType = MediaType.MOVIE))
        stubProvider(
            "dl1",
            snapshot(
                origin = DetailOrigin.LOCAL_OFFLINE_MODE,
                detail = MediaDetail(
                    item = MediaItem(
                        id = "dl1",
                        name = "Downloaded",
                        mediaType = MediaType.MOVIE,
                        genres = listOf("Drama"),
                    ),
                ),
                remoteDiscoveryAllowed = false,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("dl1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.localRelatedItems.map { it.id } == listOf("local1"), "localRelatedItems fired")
        // Every remote-only enrichment stayed cold.
        assertTrue(state.relatedItems.isEmpty())
        assertTrue(state.specialFeatures.isEmpty())
        assertFalse(state.hasIntroSegment)
        assertFalse(state.hasCreditSegment)
        assertTrue(state.collectionItems.isEmpty())
        coVerify(exactly = 0) { mediaRepository.getSimilarItems(any(), any()) }
        coVerify(exactly = 0) { libraryApiClient.getSpecialFeatures(any()) }
        coVerify(exactly = 0) { playbackRepository.getMediaSegments(any()) }
        coVerify(exactly = 0) { mediaRepository.getCollectionItems(any(), any(), any(), any()) }
        coVerify(exactly = 0) { arrRepository.resolveServers() }
    }

    @Test
    fun localSnapshotWithoutGenresOrStudios_skipsEvenLocalRelated() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "dl2",
            snapshot(
                origin = DetailOrigin.LOCAL_OFFLINE_MODE,
                detail = MediaDetail(
                    item = MediaItem(id = "dl2", name = "Bare", mediaType = MediaType.MOVIE),
                ),
                remoteDiscoveryAllowed = false,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("dl2"))
        advanceUntilIdle()

        coVerify(exactly = 0) { offlineRepository.getLocalRelated(any(), any(), any(), any()) }
        assertTrue(viewModel.uiState.value.localRelatedItems.isEmpty())
    }

    @Test
    fun remoteCollection_firesCollectionItems_onTopOfTheRemoteSet() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns
            Result.success(
                SearchResult(
                    items = listOf(MediaItem(id = "ci1", name = "Member", mediaType = MediaType.MOVIE)),
                    totalRecordCount = 1,
                    startIndex = 0,
                ),
            )
        stubProvider(
            "c1",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("c1"))
        advanceUntilIdle()

        assertEquals(listOf("ci1"), viewModel.uiState.value.collectionItems.map { it.id })
    }

    @Test
    fun remoteSnapshotWithoutDiscoveryCapability_startsNoRemoteEnrichment() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        // Remote ORIGIN but the capability flip off: the single authority for
        // whether discovery may run.
        stubProvider(
            "m2",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "m2", name = "Movie", mediaType = MediaType.MOVIE)),
                remoteDiscoveryAllowed = false,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("m2"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.relatedItems.isEmpty())
        assertTrue(state.specialFeatures.isEmpty())
        assertFalse(state.hasIntroSegment)
        coVerify(exactly = 0) { mediaRepository.getSimilarItems(any(), any()) }
        coVerify(exactly = 0) { libraryApiClient.getSpecialFeatures(any()) }
        coVerify(exactly = 0) { playbackRepository.getMediaSegments(any()) }
        verify(exactly = 0) { themeMusicPlayer.playThemeFor(any()) }
    }

    @Test
    fun attachmentTick_doesNotRefireAnyEnrichment() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val flow = stubProvider(
            "m3",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "m3", name = "Movie", mediaType = MediaType.MOVIE)),
                remoteDiscoveryAllowed = true,
            ),
        )
        viewModel.onEvent(DetailUiEvent.LoadItem("m3"))
        advanceUntilIdle()

        // Same contentGeneration re-emission (a download-completion attachment
        // tick from the still-collecting provider stream — capabilities flip,
        // generation does not): the reducer adopts only
        // context/capabilities/assets and the enrichment fan-out must not
        // re-run.
        flow.value = (flow.value as DetailLoadState.Loaded).let { loaded ->
            val capabilities = loaded.snapshot.capabilities.copy(localDownloadManagement = true)
            loaded.copy(snapshot = loaded.snapshot.copy(capabilities = capabilities))
        }
        advanceUntilIdle()

        // The similar-items fetch ran exactly once across both emissions —
        // the tick adopted the same generation and skipped the fan-out.
        coVerify(exactly = 1) { mediaRepository.getSimilarItems("m3", limit = 12) }
        verify(exactly = 1) { themeMusicPlayer.playThemeFor(any()) }
    }

    // ── D8: the themeMusic enrichment gate — the pref/mute matrix ────────

    @Test
    fun themeMusic_gate_respectsGlobalPrefAndPerSeriesMute() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val prefs = MutableStateFlow(DetailPreferences())
        every { projections.detailPreferences } returns prefs
        viewModel = buildViewModel()

        fun stubSeries(id: String) {
            stubProvider(
                id,
                snapshot(
                    origin = DetailOrigin.REMOTE,
                    detail = MediaDetail(item = MediaItem(id = id, name = "Series", mediaType = MediaType.SERIES)),
                    remoteDiscoveryAllowed = true,
                ),
            )
        }

        // (a) defaults (pref ON, nothing muted) → plays.
        stubSeries("g1")
        viewModel.onEvent(DetailUiEvent.LoadItem("g1"))
        advanceUntilIdle()
        verify(exactly = 1) { themeMusicPlayer.playThemeFor("g1") }

        // (b) global pref OFF → no series plays at all.
        prefs.value = prefs.value.copy(playThemeMusic = false)
        stubSeries("g2")
        viewModel.onEvent(DetailUiEvent.LoadItem("g2"))
        advanceUntilIdle()
        verify(exactly = 0) { themeMusicPlayer.playThemeFor("g2") }

        // (c) pref back ON but THIS series muted → still no play.
        prefs.value = DetailPreferences(themeMusicMutedSeriesIds = setOf("g3"))
        stubSeries("g3")
        viewModel.onEvent(DetailUiEvent.LoadItem("g3"))
        advanceUntilIdle()
        verify(exactly = 0) { themeMusicPlayer.playThemeFor("g3") }

        // (d) pref ON and a DIFFERENT series muted → plays.
        stubSeries("g4")
        viewModel.onEvent(DetailUiEvent.LoadItem("g4"))
        advanceUntilIdle()
        verify(exactly = 1) { themeMusicPlayer.playThemeFor("g4") }
    }

    // ── D4: the externalRatings enrichment — source-fallback matrix ──────

    private fun tmdbRatedMovie(itemId: String = "er1") =
        MediaDetail(
            item = MediaItem(id = itemId, name = "Movie", mediaType = MediaType.MOVIE),
            providerIds = mapOf("tmdb" to "123"),
        )

    @Test
    fun externalRatings_seerrConnected_mapsTheSeerrPayload_andSkipsTmdb() = runTest(mainDispatcher) {
        // Flip the connection-flag stub BEFORE constructing the ViewModel:
        // the uiState combine captures the preferences flow at construction
        // time (the loadSeerrData regression test's convention).
        every { seerrRepository.preferences } returns MutableStateFlow(
            SeerrPreferences(serverUrl = "https://seerr.example.com"),
        )
        val tmdbApiClient = mockk<com.raulshma.jellyplay.core.network.api.TmdbApiClient>(relaxed = true)
        viewModel = buildViewModel(tmdbApiClient = tmdbApiClient)
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        // Settle the seerr-flags combine BEFORE the load: the enrichment reads
        // the published isSeerrConnected at fan-out time, so the connection
        // flag must already be folded into uiState (runTest's single
        // advanceUntilIdle at the end races it against the enrichment).
        advanceUntilIdle()
        coEvery { seerrRepository.getMovieDetails(123) } returns
            Result.success(com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails())
        coEvery { seerrRepository.getRatings(123, "movie") } returns Result.success(
            com.raulshma.jellyplay.core.model.seerr.SeerrRatings(
                rt = com.raulshma.jellyplay.core.model.seerr.SeerrRtRating(criticsScore = 96, audienceScore = 88),
                imdb = com.raulshma.jellyplay.core.model.seerr.SeerrImdbRating(rating = 8.4f),
                tmdb = com.raulshma.jellyplay.core.model.seerr.SeerrTmdbRating(rating = 7.7f),
            ),
        )
        stubProvider("er1", snapshot(DetailOrigin.REMOTE, tmdbRatedMovie(), remoteDiscoveryAllowed = true))

        viewModel.onEvent(DetailUiEvent.LoadItem("er1"))
        advanceUntilIdle()

        val ratings = viewModel.uiState.value.externalRatings
        assertEquals(96, ratings.critics)
        assertEquals(88, ratings.audience)
        assertEquals(8.4f, ratings.imdb)
        assertEquals(7.7f, ratings.tmdb)
        // Seerr answered — the TMDB fallback never ran.
        coVerify(exactly = 0) { tmdbApiClient.getDetails(any(), any()) }
    }

    @Test
    fun externalRatings_seerrDisconnected_fallsBackToTheTmdbVoteAggregate() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val tmdbApiClient = mockk<com.raulshma.jellyplay.core.network.api.TmdbApiClient>(relaxed = true)
        viewModel = buildViewModel(tmdbApiClient = tmdbApiClient)
        coEvery { tmdbApiClient.getDetails(123, MediaType.MOVIE) } returns Result.success(
            com.raulshma.jellyplay.core.model.seerr.TmdbDetails(voteAverage = 7.456, voteCount = 12),
        )
        stubProvider("er2", snapshot(DetailOrigin.REMOTE, tmdbRatedMovie("er2"), remoteDiscoveryAllowed = true))

        viewModel.onEvent(DetailUiEvent.LoadItem("er2"))
        advanceUntilIdle()

        val ratings = viewModel.uiState.value.externalRatings
        assertEquals(7.456f, ratings.tmdb)
        assertEquals("https://www.themoviedb.org/movie/123", ratings.tmdbUrl)
        // Disconnected — the Seerr ratings fetch never started.
        coVerify(exactly = 0) { seerrRepository.getRatings(any(), any()) }
    }

    @Test
    fun externalRatings_seerrFetchFailure_fallsBackToTmdb() = runTest(mainDispatcher) {
        every { seerrRepository.preferences } returns MutableStateFlow(
            SeerrPreferences(serverUrl = "https://seerr.example.com"),
        )
        val tmdbApiClient = mockk<com.raulshma.jellyplay.core.network.api.TmdbApiClient>(relaxed = true)
        viewModel = buildViewModel(tmdbApiClient = tmdbApiClient)
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { seerrRepository.getMovieDetails(123) } returns
            Result.success(com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails())
        coEvery { seerrRepository.getRatings(123, "movie") } returns Result.failure(RuntimeException("seerr down"))
        coEvery { tmdbApiClient.getDetails(123, MediaType.MOVIE) } returns Result.success(
            com.raulshma.jellyplay.core.model.seerr.TmdbDetails(voteAverage = 6.0),
        )
        stubProvider("er3", snapshot(DetailOrigin.REMOTE, tmdbRatedMovie("er3"), remoteDiscoveryAllowed = true))

        viewModel.onEvent(DetailUiEvent.LoadItem("er3"))
        advanceUntilIdle()

        val ratings = viewModel.uiState.value.externalRatings
        assertEquals(6.0f, ratings.tmdb)
        assertNull(ratings.critics)
        assertFalse(viewModel.uiState.value.externalRatingsSuppressedByPlugin)
    }

    @Test
    fun externalRatings_preferenceOff_startsNoFetch() = runTest(mainDispatcher) {
        every { projections.detailPreferences } returns
            MutableStateFlow(DetailPreferences(showExternalRatings = false))
        val tmdbApiClient = mockk<com.raulshma.jellyplay.core.network.api.TmdbApiClient>(relaxed = true)
        viewModel = buildViewModel(tmdbApiClient = tmdbApiClient)
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider("er4", snapshot(DetailOrigin.REMOTE, tmdbRatedMovie("er4"), remoteDiscoveryAllowed = true))

        viewModel.onEvent(DetailUiEvent.LoadItem("er4"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.externalRatings.isEmpty)
        coVerify(exactly = 0) { seerrRepository.getRatings(any(), any()) }
        coVerify(exactly = 0) { tmdbApiClient.getDetails(any(), any()) }
    }

    @Test
    fun externalRatings_pluginRatingsLive_setsTheDedupeSuppressionFlag() = runTest(mainDispatcher) {
        val tmdbApiClient = mockk<com.raulshma.jellyplay.core.network.api.TmdbApiClient>(relaxed = true)
        val pluginStatusStore =
            mockk<com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore>(relaxed = true)
        val featureGate = mockk<com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate>(relaxed = true)
        // The catch-all must be registered FIRST: a later matching stub wins,
        // and the Ratings feature must answer true.
        coEvery { featureGate.isAvailableNow(any()) } returns false
        coEvery { featureGate.isAvailableNow(JellyPlayPluginFeatures.Ratings) } returns true
        viewModel = buildViewModel(
            tmdbApiClient = tmdbApiClient,
            pluginStatusStore = pluginStatusStore,
            jellyPlayFeatureGate = featureGate,
        )
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { tmdbApiClient.getDetails(123, MediaType.MOVIE) } returns Result.success(
            com.raulshma.jellyplay.core.model.seerr.TmdbDetails(voteAverage = 7.0),
        )
        stubProvider("er5", snapshot(DetailOrigin.REMOTE, tmdbRatedMovie("er5"), remoteDiscoveryAllowed = true))

        viewModel.onEvent(DetailUiEvent.LoadItem("er5"))
        advanceUntilIdle()

        // The chips resolved (TMDB fallback — the fetch still runs so a live
        // probe against an EMPTY plugin row never loses the strip), and the
        // dedupe flag rode the same write.
        assertEquals(7.0f, viewModel.uiState.value.externalRatings.tmdb)
        assertTrue(viewModel.uiState.value.externalRatingsSuppressedByPlugin)
        // …which stands the chips down exactly while the plugin row renders:
        // merged into the plugin row when it has content...
        assertFalse(
            externalRatingsMergedIntoPluginRow(
                hasPluginRatings = true,
                external = viewModel.uiState.value.externalRatings,
                externalSuppressedByPlugin = viewModel.uiState.value.externalRatingsSuppressedByPlugin,
            ),
        )
        // ...and never lost when the plugin row is empty/absent — there the
        // strip stands alone in the header (the D9 placement contract: merge
        // only "while the plugin row renders").
        assertTrue(
            externalRatingsStandaloneInHeader(
                showExternalRatings = true,
                hasPluginRatings = false,
                external = viewModel.uiState.value.externalRatings,
            ),
        )
    }

    // ── D6: the franchise enrichment ─────────────────────────────────────

    private fun boxSet(id: String, name: String) =
        com.raulshma.jellyplay.core.model.CollectionSummary(id = id, name = name, itemCount = 2)

    @Test
    fun franchise_remoteMovie_mapsBoxSetAncestorsToRows_selfFiltered() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        catalogueEnrichments.boxSetResults["m1"] = Result.success(
            listOf(boxSet("box1", "Fast Saga"), boxSet("box2", "Spy Universe")),
        )
        coEvery { mediaRepository.getCollectionItems("box1", any(), any(), any()) } returns Result.success(
            SearchResult(
                items = listOf(
                    MediaItem(id = "m1", name = "This movie", mediaType = MediaType.MOVIE),
                    MediaItem(id = "f2", name = "Faster", mediaType = MediaType.MOVIE),
                ),
                totalRecordCount = 2,
                startIndex = 0,
            ),
        )
        coEvery { mediaRepository.getCollectionItems("box2", any(), any(), any()) } returns Result.success(
            SearchResult(
                items = listOf(MediaItem(id = "s2", name = "Other film", mediaType = MediaType.MOVIE)),
                totalRecordCount = 1,
                startIndex = 0,
            ),
        )
        stubProvider(
            "m1",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "m1", name = "Movie", mediaType = MediaType.MOVIE)),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("m1"))
        advanceUntilIdle()

        val rows = viewModel.uiState.value.franchiseCollections
        assertEquals(listOf("box1", "box2"), rows.map { it.id })
        // Members keep the server-default order and never contain the loaded
        // item itself.
        assertEquals(listOf("f2"), rows.first().members.map { it.id })
        assertEquals(listOf("s2"), rows.last().members.map { it.id })
    }

    @Test
    fun franchise_capsAtTwoBoxSets_andDropsEmptyRows() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        catalogueEnrichments.boxSetResults["m2"] = Result.success(
            listOf(boxSet("box1", "One"), boxSet("box2", "Two"), boxSet("box3", "Three")),
        )
        coEvery { mediaRepository.getCollectionItems("box1", any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
        coEvery { mediaRepository.getCollectionItems("box2", any(), any(), any()) } returns Result.success(
            SearchResult(
                items = listOf(MediaItem(id = "s1", name = "Member", mediaType = MediaType.MOVIE)),
                totalRecordCount = 1,
                startIndex = 0,
            ),
        )
        stubProvider(
            "m2",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "m2", name = "Movie", mediaType = MediaType.MOVIE)),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("m2"))
        advanceUntilIdle()

        // Two ancestors taken (the cap), the empty first row dropped.
        assertEquals(listOf("box2"), viewModel.uiState.value.franchiseCollections.map { it.id })
    }

    @Test
    fun franchise_ancestorsFetchFailure_leavesRowsEmpty() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        catalogueEnrichments.boxSetResults["m3"] = Result.failure(RuntimeException("down"))
        stubProvider(
            "m3",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "m3", name = "Movie", mediaType = MediaType.MOVIE)),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("m3"))
        advanceUntilIdle()

        // Silent absence: the failure never surfaces and the members read
        // never starts.
        assertTrue(viewModel.uiState.value.franchiseCollections.isEmpty())
        coVerify(exactly = 0) { mediaRepository.getCollectionItems(any(), any(), any(), any()) }
    }

    @Test
    fun franchise_preferenceOff_startsNoFetch() = runTest(mainDispatcher) {
        every { projections.detailPreferences } returns
            MutableStateFlow(DetailPreferences(showMoreInCollection = false))
        viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "m4",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "m4", name = "Movie", mediaType = MediaType.MOVIE)),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("m4"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.franchiseCollections.isEmpty())
        assertTrue(catalogueEnrichments.boxSetCalls.isEmpty())
    }

    @Test
    fun franchise_episodeDetail_neverStartsTheAncestorRead() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "e9",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(
                    item = MediaItem(id = "e9", name = "Episode", mediaType = MediaType.EPISODE, seriesId = "s9"),
                ),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("e9"))
        advanceUntilIdle()

        assertTrue(catalogueEnrichments.boxSetCalls.isEmpty())
        assertTrue(viewModel.uiState.value.franchiseCollections.isEmpty())
    }

    // ── pluginSeasonRatings: the bounded-parallel per-season TMDB fetch ──

    /**
     * The ratings role's minimal counting fake: per-season canned results
     * (missing seasons degrade to the plugin-404 null payload) plus a
     * per-season-number call log the merge assertions read — a re-run that
     * re-fetches a settled season shows up here immediately.
     */
    private class CountingSeasonRatingsApi(
        private val resultsBySeasonNumber: Map<Int, Result<Map<Int, JellyPlayEpisodeRatings>?>> = emptyMap(),
    ) : JellyPlayRatingsRoutes {
        val callsBySeasonNumber = mutableMapOf<Int, Int>()

        override suspend fun getMdbListRatings(imdbId: String): Result<JellyPlayRatingsResult?> =
            Result.success(null)

        override suspend fun getTmdbSeasonRatings(
            tmdbId: String,
            seasonNumber: Int,
        ): Result<Map<Int, JellyPlayEpisodeRatings>?> {
            callsBySeasonNumber[seasonNumber] = (callsBySeasonNumber[seasonNumber] ?: 0) + 1
            return resultsBySeasonNumber[seasonNumber] ?: Result.success(null)
        }
    }

    private fun tmdbRatedSeries(itemId: String) =
        MediaDetail(
            item = MediaItem(id = itemId, name = "Series", mediaType = MediaType.SERIES),
            providerIds = mapOf("tmdb" to "123"),
        )

    private fun season(id: String, indexNumber: Int?) =
        MediaItem(id = id, name = "Season", mediaType = MediaType.SEASON, indexNumber = indexNumber)

    /** The plugin wiring the season-ratings enrichment needs: the status
     *  store probe plus a feature gate whose catch-all answers first so only
     *  the `Ratings` feature is live. */
    private fun ratingsFeatureWiredViewModel(ratingsApi: JellyPlayRatingsRoutes): DetailViewModel {
        val pluginStatusStore =
            mockk<com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore>(relaxed = true)
        val featureGate = mockk<com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate>(relaxed = true)
        // The catch-all must be registered FIRST: a later matching stub wins,
        // and only Ratings may answer true (the externalRatings test's convention).
        coEvery { featureGate.isAvailableNow(any()) } returns false
        coEvery { featureGate.isAvailableNow(JellyPlayPluginFeatures.Ratings) } returns true
        return buildViewModel(
            pluginStatusStore = pluginStatusStore,
            jellyPlayFeatureGate = featureGate,
            pluginRatingsApi = ratingsApi,
        )
    }

    @Test
    fun seasonRatings_multipleSeasons_mapsEveryScoredSeasonById() = runTest(mainDispatcher) {
        val ratingsApi = CountingSeasonRatingsApi(
            mapOf(
                1 to Result.success(mapOf(1 to JellyPlayEpisodeRatings(tmdbScore = 7.5, tmdbVotes = 120))),
                2 to Result.success(
                    mapOf(
                        3 to JellyPlayEpisodeRatings(tmdbScore = 8.0, tmdbVotes = 90),
                        4 to JellyPlayEpisodeRatings(tmdbScore = 6.5, tmdbVotes = 70),
                    ),
                ),
                // An empty payload drops its season (the serial loop's
                // `isEmpty → continue` leg).
                3 to Result.success(emptyMap()),
            ),
        )
        viewModel = ratingsFeatureWiredViewModel(ratingsApi)
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "sr1",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = tmdbRatedSeries("sr1"),
                remoteDiscoveryAllowed = true,
                seasons = listOf(
                    season("season1", 1),
                    season("season2", 2),
                    season("season3", 3),
                    // Numberless seasons never fetch (the `?: continue` leg).
                    season("seasonUnnumbered", null),
                ),
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("sr1"))
        advanceUntilIdle()

        val seasonRatings = viewModel.uiState.value.seasonRatings
        assertEquals(setOf("season1", "season2"), seasonRatings.keys)
        assertEquals(7.5, seasonRatings["season1"]?.get(1)?.tmdbScore)
        assertEquals(120, seasonRatings["season1"]?.get(1)?.tmdbVotes)
        assertEquals(8.0, seasonRatings["season2"]?.get(3)?.tmdbScore)
        assertEquals(6.5, seasonRatings["season2"]?.get(4)?.tmdbScore)
    }

    @Test
    fun seasonRatings_perSeasonFailure_dropsOnlyTheFailedSeason() = runTest(mainDispatcher) {
        val ratingsApi = CountingSeasonRatingsApi(
            mapOf(
                1 to Result.success(mapOf(1 to JellyPlayEpisodeRatings(tmdbScore = 7.0))),
                2 to Result.failure(RuntimeException("season 2 down")),
            ),
        )
        viewModel = ratingsFeatureWiredViewModel(ratingsApi)
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "sr2",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = tmdbRatedSeries("sr2"),
                remoteDiscoveryAllowed = true,
                seasons = listOf(season("season1", 1), season("season2", 2)),
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("sr2"))
        advanceUntilIdle()

        // The failed season drops alone — the survivors keep their scores
        // and no error surfaces.
        val seasonRatings = viewModel.uiState.value.seasonRatings
        assertEquals(setOf("season1"), seasonRatings.keys)
        assertEquals(7.0, seasonRatings["season1"]?.get(1)?.tmdbScore)
    }

    @Test
    fun seasonRatings_lazySeasonExpansion_rerunFetchesOnlyTheNewSeason() = runTest(mainDispatcher) {
        val ratingsApi = CountingSeasonRatingsApi(
            mapOf(
                1 to Result.success(mapOf(1 to JellyPlayEpisodeRatings(tmdbScore = 7.5))),
                2 to Result.success(mapOf(2 to JellyPlayEpisodeRatings(tmdbScore = 8.0))),
            ),
        )
        viewModel = ratingsFeatureWiredViewModel(ratingsApi)
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val flow = stubProvider(
            "sr3",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = tmdbRatedSeries("sr3"),
                remoteDiscoveryAllowed = true,
                contentGeneration = 0L,
                seasons = listOf(season("season1", 1)),
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("sr3"))
        advanceUntilIdle()
        assertEquals(setOf("season1"), viewModel.uiState.value.seasonRatings.keys)

        // The lazily-expanded season re-emits a NEW contentGeneration, the
        // fan-out re-runs, and the merge must fetch (and change) only the
        // new arrival — season 1's entry survives from the previous
        // generation untouched.
        flow.value = (flow.value as DetailLoadState.Loaded).let { loaded ->
            loaded.copy(
                snapshot = loaded.snapshot.copy(
                    seasons = loaded.snapshot.seasons + season("season2", 2),
                    contentGeneration = 1L,
                ),
            )
        }
        advanceUntilIdle()

        assertEquals(1, ratingsApi.callsBySeasonNumber[1])
        assertEquals(1, ratingsApi.callsBySeasonNumber[2])
        val seasonRatings = viewModel.uiState.value.seasonRatings
        assertEquals(setOf("season1", "season2"), seasonRatings.keys)
        assertEquals(7.5, seasonRatings["season1"]?.get(1)?.tmdbScore)
        assertEquals(8.0, seasonRatings["season2"]?.get(2)?.tmdbScore)
    }

    @Test
    fun seasonRatings_gateOffOrWhollyFailingFetch_leavesTheFieldEmpty() = runTest(mainDispatcher) {
        // (a) The Ratings feature toggled off: the enrichment never reaches
        // the fetch (the franchise preferenceOff convention).
        val gatedApi = CountingSeasonRatingsApi(
            mapOf(1 to Result.success(mapOf(1 to JellyPlayEpisodeRatings(tmdbScore = 7.0)))),
        )
        val featureGate = mockk<com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate>(relaxed = true)
        coEvery { featureGate.isAvailableNow(any()) } returns false
        viewModel = buildViewModel(
            pluginStatusStore = mockk(relaxed = true),
            jellyPlayFeatureGate = featureGate,
            pluginRatingsApi = gatedApi,
        )
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "sr4",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = tmdbRatedSeries("sr4"),
                remoteDiscoveryAllowed = true,
                seasons = listOf(season("season1", 1)),
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("sr4"))
        advanceUntilIdle()

        assertTrue(gatedApi.callsBySeasonNumber.isEmpty())
        assertTrue(viewModel.uiState.value.seasonRatings.isEmpty())

        // (b) Default prefs again so the wholly-failing fetch exercises the
        // fetch, not the gate: every season drops, the fold comes up empty,
        // and the field stays empty — silent absence, never an error.
        val failingApi = CountingSeasonRatingsApi(
            mapOf(1 to Result.failure(RuntimeException("plugin down"))),
        )
        viewModel = ratingsFeatureWiredViewModel(failingApi)
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "sr5",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = tmdbRatedSeries("sr5"),
                remoteDiscoveryAllowed = true,
                seasons = listOf(season("season1", 1)),
            ),
        )
        viewModel.onEvent(DetailUiEvent.LoadItem("sr5"))
        advanceUntilIdle()

        assertEquals(1, failingApi.callsBySeasonNumber[1])
        assertTrue(viewModel.uiState.value.seasonRatings.isEmpty())
    }

    // ── D7: the upcoming enrichment ──────────────────────────────────────

    @Test
    fun upcoming_remoteSeries_fetchesKeyedOnTheSeriesId() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { libraryApiClient.getUpcoming(userId = null, parentId = "s1", limit = 12) } returns
            Result.success(
                listOf(
                    MediaItem(
                        id = "up1",
                        name = "Next week",
                        mediaType = MediaType.EPISODE,
                        seriesId = "s1",
                        premiereDate = "2026-10-20T00:00:00.000Z",
                    ),
                ),
            )
        stubProvider(
            "s1",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "s1", name = "Series", mediaType = MediaType.SERIES)),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("s1"))
        advanceUntilIdle()

        // The strip keyed on the SERIES id (parentId = s1, the loaded item).
        coVerify(exactly = 1) { libraryApiClient.getUpcoming(userId = null, parentId = "s1", limit = 12) }
        assertEquals(listOf("up1"), viewModel.uiState.value.upcomingEpisodes.map { it.id })
    }

    @Test
    fun upcoming_episodeDetail_neverStartsTheFetch() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        // The keying-rule test (the MediaDetailScreen seriesIdForSeasons
        // precedent): an EPISODE detail's series id is NOT its item id, and
        // the strip must not fire keyed on the wrong id — it must not fire
        // at all (the SERIES-only gate excludes re-keying entirely).
        stubProvider(
            "e1",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(
                    item = MediaItem(id = "e1", name = "Episode", mediaType = MediaType.EPISODE, seriesId = "s1"),
                ),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("e1"))
        advanceUntilIdle()

        coVerify(exactly = 0) { libraryApiClient.getUpcoming(any(), any(), any()) }
        assertTrue(viewModel.uiState.value.upcomingEpisodes.isEmpty())
    }

    @Test
    fun upcoming_preferenceOffOrFetchFailure_leavesTheStripAbsent() = runTest(mainDispatcher) {
        every { projections.detailPreferences } returns
            MutableStateFlow(DetailPreferences(showUpcomingStrip = false))
        viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubProvider(
            "s2",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "s2", name = "Series", mediaType = MediaType.SERIES)),
                remoteDiscoveryAllowed = true,
            ),
        )

        viewModel.onEvent(DetailUiEvent.LoadItem("s2"))
        advanceUntilIdle()

        coVerify(exactly = 0) { libraryApiClient.getUpcoming(any(), any(), any()) }
        assertTrue(viewModel.uiState.value.upcomingEpisodes.isEmpty())

        // Fetch failure — silent absence, never an error surface. Default
        // prefs again so the failure leg exercises the fetch, not the gate.
        every { projections.detailPreferences } returns MutableStateFlow(DetailPreferences())
        viewModel = buildViewModel()
        coEvery { libraryApiClient.getUpcoming(any(), any(), any()) } returns
            Result.failure(RuntimeException("server down"))
        stubProvider(
            "s3",
            snapshot(
                origin = DetailOrigin.REMOTE,
                detail = MediaDetail(item = MediaItem(id = "s3", name = "Series", mediaType = MediaType.SERIES)),
                remoteDiscoveryAllowed = true,
            ),
        )
        viewModel.onEvent(DetailUiEvent.LoadItem("s3"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.upcomingEpisodes.isEmpty())
    }
}
