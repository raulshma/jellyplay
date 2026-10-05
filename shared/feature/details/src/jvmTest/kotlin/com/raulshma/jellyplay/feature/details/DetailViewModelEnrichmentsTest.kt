package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DetailLoadState
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaExtrasReads
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
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
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
    private lateinit var mediaExtrasReads: MediaExtrasReads
    private lateinit var mediaDetailProvider: MediaDetailProvider
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var arrRepository: ArrRepository
    private lateinit var themeMusicPlayer: DetailThemeMusic

    private val providerFlows = mutableMapOf<String, MutableStateFlow<DetailLoadState>>()
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 64)

    private lateinit var viewModel: DetailViewModel

    @BeforeTest
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        mediaExtrasReads = mockk(relaxed = true)
        mediaDetailProvider = mockk(relaxed = false)
        playbackRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        arrRepository = mockk(relaxed = true)
        themeMusicPlayer = mockk(relaxed = true)

        every { seerrRepository.preferences } returns MutableStateFlow(SeerrPreferences())
        val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
        every { offlineModeManager.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        coEvery { mediaRepository.getSimilarItems(any(), any()) } returns Result.success(emptyList())
        coEvery { mediaExtrasReads.getSpecialFeatures(any()) } returns Result.success(emptyList())
        coEvery { playbackRepository.getMediaSegments(any()) } returns Result.success(emptyList())
        coEvery { seerrRepository.getTmdbVideos(any(), any()) } returns Result.success(emptyList())
        coEvery { seerrRepository.getTmdbReviews(any(), any()) } returns Result.success(emptyList())
        coEvery { mediaDetailProvider.refresh(any()) } returns Unit
        every { mediaRepository.userDataChanges } returns userDataEvents
        coEvery { mediaDetailProvider.applyOptimisticItemState(any(), any(), any()) } returns Unit
        every { mediaDetailProvider.invalidate(any()) } returns Unit
        coEvery { arrRepository.resolveServers() } returns Result.success(ArrServiceSummary())
        coEvery { offlineRepository.getLocalRelated(any(), any(), any(), any()) } returns emptyList()

        val projections = mockk<PreferenceProjections>(relaxed = true)
        every { projections.detailPreferences } returns MutableStateFlow(DetailPreferences())
        val homeDiscoveryStore = mockk<HomeDiscoveryStore>(relaxed = true)
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
        val experimentalStore = mockk<ExperimentalStore>(relaxed = true)
        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        val engineStore = mockk<PlayerEngineStore>(relaxed = true)
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        val libraryStore = mockk<LibraryStore>(relaxed = true)
        every { libraryStore.library } returns MutableStateFlow(LibrarySlice())

        viewModel = DetailViewModel(
            storageProbe = mockk<DetailStorageProbe>(relaxed = true),
            strings = strings,
            mediaRepository = mediaRepository,
            mediaExtrasReads = mediaExtrasReads,
            userDataMutator = FakeUserDataMutator(mediaDetailProvider),
            mediaDetailProvider = mediaDetailProvider,
            playbackRepository = playbackRepository,
            imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true),
            offlineRepository = offlineRepository,
            stores = DetailStores(
                projections = projections,
                libraryStore = libraryStore,
                homeDiscoveryStore = homeDiscoveryStore,
                experimentalStore = experimentalStore,
                engineStore = engineStore,
            ),
            remoteDiscovery = RemoteDiscoveryClients(
                seerrRepository = seerrRepository,
                seerrRequestDelegate = mockk<SeerrRequestDelegate>(relaxed = true),
                arrRepository = arrRepository,
                offlineModeManager = offlineModeManager,
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
        )
    }

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
            seasons = emptyList(),
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
        coEvery { mediaExtrasReads.getSpecialFeatures("m1") } returns
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
        coVerify(exactly = 0) { mediaExtrasReads.getSpecialFeatures(any()) }
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
        coVerify(exactly = 0) { mediaExtrasReads.getSpecialFeatures(any()) }
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
}
