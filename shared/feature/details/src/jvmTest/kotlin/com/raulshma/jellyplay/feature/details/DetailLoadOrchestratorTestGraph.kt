package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.DetailLoadState
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaCatalogueEnrichments
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.data.repository.UserDataChanges
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.DownloadAttachment
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.RemoteConnectivity
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * The jvmTest-local construction graph for the [DetailLoadOrchestrator] ALONE
 * (the [PlaybackSessionTestGraph] precedent: construction graphs over
 * internal constructors stay jvmTest-local per ADR-0006 rule 4 / glossary
 * "Test fixtures home" — no doubles leave this module). The orchestrator is
 * the details feature's HomeRefresher-pattern deep module; its concurrency
 * choreography (the debounced user-data burst drain, the staleness epoch, the
 * content-generation clobber latch) is only reachable by driving the
 * orchestrator directly — [DetailViewModelTest] goes through the VM's fold
 * and never emits into the user-data feed itself.
 *
 * Mirrors the collaborator set DetailViewModelTest's `buildViewModel` hands
 * the VM for the orchestrator's slice (copy-into-harness, no churn to that
 * suite): relaxed mocks at every repository/store seam with REAL StateFlows
 * wherever a construction-site or body lambda reads one
 * (`seerrRepository.preferences`, `offlineModeManager.networkStatus`,
 * `engineStore.playerEngine`, `preferences`, the session flow), and the
 * default-empty fetch stubs so a REMOTE resolution's enrichment fan-out
 * resolves silently. The strict-mock [mediaDetailProvider] stays recording —
 * the pins verify `refresh` call counts on it.
 *
 * Virtual time: the orchestrator runs on a scheduler-linked scope (the
 * HomeRefresherTest `buildRefresher` hand-off idiom — SupervisorJob over the
 * caller's TestScope scheduler, NOT a child of the TestScope, so the parked
 * collectors never block runTest's completion wait), and the debounce is a
 * REAL operator over that scheduler — the HomeUserDataReactorTest idiom:
 * runCurrent / advanceTimeBy, never advanceUntilIdle-then-emit. Cancel via
 * [cancel] in @AfterTest so no collector or debounce chain survives the body.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DetailLoadOrchestratorTestGraph(testScope: TestScope) {

    val strings: DetailStrings = fakeDetailStrings()

    /** Strict recording double — the pins verify refresh counts on it. */
    val mediaDetailProvider: MediaDetailProvider = mockk(relaxed = false)

    val detailFeed: DetailFeed = mockk(relaxed = true)
    val libraryApiClient: LibraryApiClient = mockk(relaxed = true)
    val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    val offlineRepository: OfflineRepository = mockk(relaxed = true)
    val seerrRepository: SeerrRepository = mockk(relaxed = true)
    val offlineModeManager: OfflineModeManager = mockk(relaxed = true)

    /** The orchestrator's scope, scheduler-linked like the production viewModelScope hand-off. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScope.testScheduler))

    /** Hot flow behind the [UserDataChanges] seam; tests emit into it. */
    val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 64)

    private val userDataChanges = object : UserDataChanges {
        override val userDataChanges: Flow<UserDataChange> = userDataEvents
    }

    /** Per-item provider flow, so tests can emit Loaded/Error/attachment ticks. */
    private val providerFlows = mutableMapOf<String, MutableStateFlow<DetailLoadState>>()

    /** The helpers' session — reset by [DetailLoadOrchestrator.load] at each load start. */
    val session = MutableStateFlow<DetailSession?>(null)

    private val messages = MutableSharedFlow<DetailMessage>(replay = 0, extraBufferCapacity = 8)

    /**
     * The orchestrator under test. [DetailLoadOrchestrator.publishedState]
     * reads it back (the VM's `publishedState = { uiState.value }` shape with
     * the fold degenerated — the harness reads the raw content state, whose
     * Seerr flags default to disconnected).
     */
    val orchestrator: DetailLoadOrchestrator

    init {
        // ── Default stubs copied from DetailViewModelTest's setUp ──────────
        val themeMusicPlayer = mockk<DetailThemeMusic>(relaxed = true)
        every { themeMusicPlayer.isPlaying } returns MutableStateFlow(false)
        every { seerrRepository.preferences } returns MutableStateFlow(SeerrPreferences())
        // Default: online. Prevents the Seerr Local-skip from falsely firing
        // on a REMOTE snapshot's discovery launch.
        every { offlineModeManager.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        coEvery { detailFeed.getSimilarItems(any(), any()) } returns Result.success(emptyList())
        coEvery { libraryApiClient.getSpecialFeatures(any()) } returns Result.success(emptyList())
        coEvery { libraryApiClient.getUpcoming(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { libraryApiClient.getLocalTrailers(any()) } returns Result.success(emptyList())
        coEvery { libraryApiClient.getThemeVideos(any()) } returns Result.success(emptyList())
        coEvery { playbackRepository.getMediaSegments(any()) } returns Result.success(emptyList())
        // The disconnected-Seerr TMDB fallback legs — empty keeps every row
        // silent; the stale-flight pin overrides the videos leg with a parked
        // gate.
        coEvery { seerrRepository.getTmdbVideos(any(), any()) } returns Result.success(emptyList())
        coEvery { seerrRepository.getTmdbReviews(any(), any()) } returns Result.success(emptyList())
        coEvery { seerrRepository.getRatings(any(), any()) } returns Result.success(SeerrRatings())
        coEvery { mediaDetailProvider.refresh(any()) } returns Unit
        // Same-item re-entry invalidates the previous series through the
        // provider; the strict mock needs the no-op stub.
        every { mediaDetailProvider.invalidate(any()) } just Runs

        val downloadActions = DownloadLifecycleActions.Factory(
            downloadIntake = mockk<DownloadIntake>(relaxed = true),
            downloadsStore = mockk<DownloadsStore>(relaxed = true),
            adaptiveBitrateManager = mockk<AdaptiveBitrateManager>(relaxed = true),
            downloadRepository = mockk<DownloadRepository>(relaxed = true),
            policyStore = mockk<SeriesDownloadPolicyStore>(relaxed = true),
            autoDownloadScheduler = mockk(relaxed = true),
        ).create(
            scope = scope,
            session = session,
            messages = messages,
            strings = strings,
            mediaDetailProvider = mediaDetailProvider,
        )

        val engineStore = mockk<PlayerEngineStore>(relaxed = true)
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())

        lateinit var built: DetailLoadOrchestrator
        built = DetailLoadOrchestrator(
            scope = scope,
            strings = strings,
            detailFeed = detailFeed,
            collectionOperations = mockk(relaxed = true),
            userDataChanges = userDataChanges,
            mediaDetailProvider = mediaDetailProvider,
            libraryApiClient = libraryApiClient,
            mediaCatalogueEnrichments = object : MediaCatalogueEnrichments {
                override suspend fun getTags(parentId: String?) = Result.success(emptyList<String>())
                override suspend fun getBoxSetsContaining(itemId: String) =
                    Result.success(emptyList<CollectionSummary>())
            },
            playbackRepository = playbackRepository,
            offlineRepository = offlineRepository,
            remoteDiscovery = RemoteDiscoveryClients(
                seerrRepository = seerrRepository,
                seerrRequestDelegate = mockk<SeerrRequestDelegate>(relaxed = true),
                arrRepository = mockk<ArrRepository>(relaxed = true),
                offlineModeManager = offlineModeManager,
            ),
            pluginArms = DetailPluginArms(),
            tmdbApiClient = null,
            appearanceStore = null,
            themeMusicPlayer = themeMusicPlayer,
            bookArms = DetailBookArms(),
            // Smart-play resolution rides the test scheduler so the
            // post-resolution write is ordered with the rest of the graph.
            smartPlayDispatcher = StandardTestDispatcher(testScope.testScheduler),
            preferences = MutableStateFlow(DetailPreferences()),
            session = session,
            downloadActions = downloadActions,
            engineStore = engineStore,
            artworkUrls = ArtworkUrls.EMPTY,
            publishedState = { built.state.value },
        )
        orchestrator = built
    }

    /** Cancels the orchestrator's scope (the @AfterTest teardown half). */
    fun cancel() {
        scope.cancel()
    }

    /**
     * Creates (or fetches) the controllable [MutableStateFlow] the fake
     * provider emits for [itemId], and wires `mediaDetailProvider.observe(itemId)`
     * to it. Default initial: [DetailLoadState.Loading] — an item whose
     * snapshot never resolves (the stale-flight pin's superseding item).
     */
    fun stubProvider(
        itemId: String,
        initial: DetailLoadState = DetailLoadState.Loading,
    ): MutableStateFlow<DetailLoadState> {
        val flow = MutableStateFlow(initial)
        providerFlows[itemId] = flow
        every { mediaDetailProvider.observe(itemId) } returns flow
        return flow
    }

    /**
     * Parks `mediaDetailProvider.refresh(itemId)` on [gate] — the silent
     * refresh / pull-to-refresh in-flight state becomes observable.
     */
    fun parkRefresh(itemId: String, gate: CompletableDeferred<Unit>) {
        coEvery { mediaDetailProvider.refresh(itemId) } coAnswers { gate.await() }
    }

    // ── Snapshot builders (copied from DetailViewModelTest's builders) ────

    /** A REMOTE-origin Loaded snapshot with discovery/stream capabilities on. */
    fun remoteSnapshot(
        detail: MediaDetail,
        seasons: List<MediaItem> = emptyList(),
        episodesBySeason: Map<String, List<MediaItem>> = emptyMap(),
        fetchedSeasonIds: Set<String> = emptySet(),
        sortedEpisodes: List<MediaItem> = episodesBySeason.values.flatten(),
        albumTracks: List<MediaItem> = emptyList(),
        contentGeneration: Long = 0L,
        download: DownloadAttachment? = null,
        capabilities: DetailCapabilities = DetailCapabilities(
            remoteDiscovery = true,
            remoteStreamSelection = true,
            localSubtitleSelection = false,
            localStreamInfo = false,
            personNavigation = true,
            studioNavigation = detail.studios.isNotEmpty(),
            smartPlay = true,
            remoteWorkAllowed = true,
            localDownloadManagement = download?.isCompleted == true,
            tagNavigation = true,
            chapters = detail.chapters.isNotEmpty(),
        ),
    ): DetailLoadState.Loaded = DetailLoadState.Loaded(
        MediaDetailSnapshot(
            detail = detail,
            context = DetailContext(
                origin = DetailOrigin.REMOTE,
                connectivity = RemoteConnectivity.AVAILABLE,
                download = download,
                syncState = null,
                seriesAggregate = null,
            ),
            capabilities = capabilities,
            assets = DetailAssets(),
            seasons = seasons,
            episodesBySeason = episodesBySeason,
            fetchedSeasonIds = fetchedSeasonIds,
            sortedEpisodes = sortedEpisodes,
            albumTracks = albumTracks,
            localSubtitles = emptyList(),
            contentGeneration = contentGeneration,
        ),
    )

    /** A minimal REMOTE movie [MediaDetail] with an optional tmdb provider id. */
    fun movieDetail(id: String, tmdbId: Int? = null): MediaDetail = MediaDetail(
        item = MediaItem(id = id, name = "Movie", mediaType = MediaType.MOVIE),
        providerIds = tmdbId?.let { mapOf("tmdb" to it.toString()) } ?: emptyMap(),
    )
}
