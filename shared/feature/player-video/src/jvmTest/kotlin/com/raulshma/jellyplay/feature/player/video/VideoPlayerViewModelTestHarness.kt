package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.SleepCountdownClock
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileSlice
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/**
 * The shared construction harness for the reinstated ViewModel suites
 * (VideoPlayerViewModelTest / VideoPlayerResetEquivalenceTest — the
 * pre-KMP `feature/player/video` tree's suites, ported to the KMP VM's
 * seam-shaped constructor): relaxed mocks at every repository/store seam
 * with REAL StateFlows wherever the collaborator graph collects one, the
 * EXTERNAL preferred-player short-circuit as the engine-free load path,
 * and a [FakePlatform] whose factory members return the jvmMain no-ops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class VideoPlayerViewModelHarness {

    val testDispatcher = UnconfinedTestDispatcher()

    lateinit var mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository
    lateinit var playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository
    lateinit var aggregateStore: VideoPlayerAggregateStore
    lateinit var aggregateFlow: MutableStateFlow<VideoPlayerAggregate>
    lateinit var jellyfinRemotePlayCastStrategy: JellyfinRemotePlayCastStrategy
    lateinit var userDataMutator: com.raulshma.jellyplay.core.data.repository.UserDataMutator
    lateinit var episodeCatalogue: com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
    lateinit var playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
    lateinit var syncPlayManager: SyncPlayManager
    lateinit var viewModel: VideoPlayerViewModel

    @Before
    fun setUpHarness() {
        Dispatchers.setMain(testDispatcher)

        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        val itemPlaybackPreferenceRepository =
            mockk<com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository>(relaxed = true)
        // Resolve to "no stored preference": a relaxed mock would return a
        // mocked ItemPlaybackPreference whose enum props clobber the dialogue
        // boost under test.
        coEvery { itemPlaybackPreferenceRepository.get(any(), any()) } returns null
        userDataMutator = mockk(relaxed = true)
        episodeCatalogue = mockk(relaxed = true)
        coEvery { episodeCatalogue.loadSeasonEpisodes(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { episodeCatalogue.loadSeriesEpisodes(any(), any()) } returns
            Result.success(com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueSnapshot.empty("series-1"))
        playbackSourceResolver = mockk(relaxed = true)
        // A relaxed mock would hand back a fake DownloadItem and route every
        // load into the offline path (whose vanished-file gate then fail-
        // loads the session before the duration seed). Resolve to "no local
        // download" so loads take the online path.
        coEvery { playbackSourceResolver.resolveUsableDownload(any()) } returns null
        // The load-sidecar refresh uses the TWO-ARG overload; left to the
        // relaxed mock it returns a mocked MediaDetail whose flows explode
        // inside a fire-and-forget launch — an uncaught exception that
        // poisons whichever sibling test class runs next (the
        // SyncPlayTestSupport precedent). Resolve to failure: the sidecar's
        // null guard skips the work entirely.
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns
            Result.failure(RuntimeException("no sidecar refresh in the harness"))

        // Every store: relaxed mock over REAL slice flows — the collaborator
        // graph collects these at arm time, so a mocked flow would poison the
        // Main dispatcher with cast exceptions.
        val engineStore = mockk<PlayerEngineStore>(relaxed = true)
        val subtitleStore = mockk<SubtitleLanguageStore>(relaxed = true)
        val securityStore = mockk<SecurityStore>(relaxed = true)
        val syncPlayCastStore = mockk<SyncPlayCastStore>(relaxed = true)
        val playbackStore = mockk<PlaybackStore>(relaxed = true)
        val audioStore = mockk<AudioStore>(relaxed = true)
        val audioEffectsStore = mockk<AudioEffectsStore>(relaxed = true)
        val videoPlayerStore = mockk<VideoPlayerStore>(relaxed = true)
        val downloadsStore = mockk<DownloadsStore>(relaxed = true)
        val appearanceStore = mockk<AppearanceStore>(relaxed = true)
        val networkOfflineStore = mockk<NetworkOfflineStore>(relaxed = true)
        val volumeProfileStore = mockk<VolumeProfileStore>(relaxed = true)

        aggregateFlow = MutableStateFlow(VideoPlayerAggregate())
        aggregateStore = mockk(relaxed = true)
        every { aggregateStore.aggregate } returns aggregateFlow
        // aggregateRaw must track the SAME flow: the session's load spine
        // reads `aggregateRaw.first()` to resolve the preferred player — a
        // stale flowOf() snapshot would defeat the EXTERNAL short-circuit.
        every { aggregateStore.aggregateRaw } returns aggregateFlow
        every { engineStore.playerEngine } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice())
        every { subtitleStore.subtitle } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice())
        every { securityStore.security } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.security.SecuritySlice())
        every { syncPlayCastStore.syncPlayCast } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice())
        every { playbackStore.playback } returns MutableStateFlow(PlaybackSlice())
        every { audioStore.audio } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.audio.AudioSlice())
        every { audioEffectsStore.audioEffects } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice())
        every { videoPlayerStore.videoPlayer } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice())
        every { downloadsStore.downloads } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice())
        every { appearanceStore.appearance } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice())
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice())
        every { volumeProfileStore.volumeProfile } returns MutableStateFlow(VolumeProfileSlice())

        // Relaxed mock's StateFlow<Boolean>.value returns an Object that
        // won't cast to Boolean — stub the "Play On" routing check away.
        jellyfinRemotePlayCastStrategy = mockk(relaxed = true)
        every { jellyfinRemotePlayCastStrategy.isConnected } returns MutableStateFlow<Boolean>(false)
        syncPlayManager = mockk(relaxed = true)
        every { syncPlayManager.isInSyncPlaySession } returns false
        every { syncPlayManager.currentGroup } returns null
        syncPlayManager.stubEmptyEvents()

        val networkMonitor = mockk<com.raulshma.jellyplay.core.data.network.NetworkMonitor>(relaxed = true)
        every { networkMonitor.isMetered } returns MutableStateFlow(false)
        val activePlayerController = mockk<ActivePlayerController>(relaxed = true)
        every { activePlayerController.engine } returns null
        every { activePlayerController.screenshotRequests } returns MutableSharedFlow()
        val videoMiniPlayerState = mockk<com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState>(relaxed = true)
        every { videoMiniPlayerState.tryReclaimMediaEngine(any()) } returns null
        val pipController = mockk<com.raulshma.jellyplay.core.data.playback.PipController>(relaxed = true)
        every { pipController.isInPipMode } returns MutableStateFlow(false)
        every { pipController.pipDismissed } returns MutableStateFlow(false)

        val offlineModeManager = mockk<com.raulshma.jellyplay.core.data.offline.OfflineModeManager>(relaxed = true)
        every { offlineModeManager.isOffline } returns false
        every { offlineModeManager.networkStatus } returns
            MutableStateFlow(com.raulshma.jellyplay.core.model.NetworkStatus.Online)

        viewModel = VideoPlayerViewModel(
            platform = FakePlatform(),
            mediaRepository = mediaRepository,
            mediaExtrasReads = mockk(relaxed = true),
            lyricsRepository = mockk(relaxed = true),
            playbackRepository = playbackRepository,
            playbackIdentity = mockk<PlaybackIdentity>(relaxed = true).apply {
                every { serverUrl() } returns "https://jellyfin.test"
                every { accessToken() } returns "token"
            },
            subtitleProviderRepository = mockk(relaxed = true),
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            imageUrlProvider = mockk(relaxed = true),
            downloadRepository = mockk(relaxed = true),
            offlineRepository = mockk(relaxed = true),
            offlinePlaybackFacade = mockk(relaxed = true),
            playbackSourceResolver = playbackSourceResolver,
            episodeCatalogue = episodeCatalogue,
            itemPlaybackPreferenceRepository = itemPlaybackPreferenceRepository,
            stores = PlayerStores(
                aggregateStore = aggregateStore,
                engine = engineStore,
                subtitleLanguage = subtitleStore,
                playback = playbackStore,
                audio = audioStore,
                audioEffects = audioEffectsStore,
                videoPlayer = videoPlayerStore,
                security = securityStore,
                syncPlayCast = syncPlayCastStore,
                downloads = downloadsStore,
                appearance = appearanceStore,
                networkOffline = networkOfflineStore,
                volumeProfile = volumeProfileStore,
            ),
            mediaSessionFactory = NoOpMediaSessionFactory,
            castManager = mockk(relaxed = true),
            jellyfinRemotePlayCastStrategy = jellyfinRemotePlayCastStrategy,
            syncPlayManager = syncPlayManager,
            adaptiveBitrateManager = mockk(relaxed = true),
            networkMonitor = networkMonitor,
            activePlayerController = activePlayerController,
            playerLifecycleManager = PlayerLifecycleManager(playbackStore),
            pipController = pipController,
            videoMiniPlayerState = videoMiniPlayerState,
            sleepCountdown = SleepCountdown(SleepCountdownClock.perTick(1_000L)),
            userMessageBus = NoOpPlayerVideoMessageBus,
            playerEngineFactory = object : com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory {
                override suspend fun create(playerType: PlayerType): MediaEngine =
                    com.raulshma.jellyplay.feature.player.video.JvmNoOpEngine()
            },
            fontProvider = NoOpFontProvider,
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            subtitlePreviewRepository = mockk(relaxed = true),
            userDataMutator = userDataMutator,
            offlineModeManager = offlineModeManager,
            nowPlayingReporter = mockk(relaxed = true),
        )
    }

    @After
    fun tearDownHarness() {
        // Full teardown before resetMain: cancels the session's load task and
        // stops the sleep-timer/countdown ticker (its own Main scope would
        // otherwise resume after resetMain and poison a sibling test class
        // with an uncaught exception).
        runCatching { viewModel.release() }
        Dispatchers.resetMain()
    }

    /** EXTERNAL short-circuits engine creation — the engine-free load path. */
    fun setExternalPlayer() {
        aggregateFlow.value = VideoPlayerAggregate(
            playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL),
        )
    }

    fun itemDetail(itemId: String, name: String = "Item", runTimeTicks: Long = 0L): MediaDetail =
        MediaDetail(item = MediaItem(id = itemId, name = name, mediaType = MediaType.MOVIE, runTimeTicks = runTimeTicks))

    /**
     * The harness's [VideoPlayerPlatform]: every factory member returns the
     * jvmMain no-ops the desktop target ships; the content-URI gateway reads
     * nothing (no subtitle-upload IO in these suites).
     */
    private class FakePlatform : VideoPlayerPlatform {
        override fun isLowRamDevice(): Boolean = false
        override fun queryFileSizeBytes(uri: String): Long = 0L
        override fun readBytes(uri: String): ByteArray = ByteArray(0)
        override val offlineMediaProbe: OfflineMediaProbe = object : OfflineMediaProbe {
            override fun extractDurationMs(path: String): Long? = null
            override fun mapContainerToMime(container: String?): String? = null
        }
        override fun createTrickplayController(playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository): TrickplayController =
            NoOpTrickplayController
        override fun createCastController(
            playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
            imageUrlProvider: com.raulshma.jellyplay.core.data.util.ImageUrlProvider,
            adaptiveBitrateManager: com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager,
            syncPlayCastStore: com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore,
            getEngine: () -> MediaEngine?,
            getCurrentPlaybackMode: () -> com.raulshma.jellyplay.core.model.PlaybackMode,
            getSessionState: () -> PlayerSessionState,
        ): PlayerCastController = NoOpPlayerCastController()
        override fun createBecomingNoisy(getEngine: () -> MediaEngine?, isResumeOnPlugEnabled: () -> Boolean): VideoPlayerAudio = NoOpVideoPlayerAudio
    }
}
