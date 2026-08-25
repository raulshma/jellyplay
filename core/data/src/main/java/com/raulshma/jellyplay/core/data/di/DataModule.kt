package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadIntakeImpl
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.playback.AudioCachePolicyGuard
import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.DefaultAudioQueueFacade
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.repository.LocalStreamProbe
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
import com.raulshma.jellyplay.core.data.worker.UserDataSyncSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.DownloadTransferClient
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    companion object {
        // V3 downloads conveyor: DownloadDelegate moved to :shared:core:data
        // jvmShared (Koin-owned, ctor sans Context); this provider bridges the
        // remaining Hilt injectors (DownloadIntakeImpl) to the Koin single.
        @dagger.Provides
        @Singleton
        fun provideDownloadDelegate(): DownloadDelegate = koin().get()

        // V3 downloads conveyor: DownloadRepositoryImpl moved to
        // :shared:core:data jvmShared (Koin-owned, platform surfaces behind
        // seams); the former bindDownloadRepository @Binds became this bridge.
        // Every Hilt injector of the download repository (PlayedStateSyncImpl's
        // dagger.Lazy edge, OfflinePlaybackFacade, AudioLibraryBrowser,
        // workers, feature modules) shares the Koin single.
        @dagger.Provides
        @Singleton
        fun provideDownloadRepository(): com.raulshma.jellyplay.core.data.repository.DownloadRepository = koin().get()

        // The narrow write surface shared by DownloadDelegate — the former
        // bindOfflineDownloadWriter @Binds, now bridged to the same Koin-owned
        // DownloadRepository single (a real seam in the type graph, not a
        // second instance).
        @dagger.Provides
        @Singleton
        fun provideOfflineDownloadWriter(): com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter =
            koin().get()

        // V3 downloads conveyor: DownloadEnqueuer stays in this legacy module
        // (Android WorkManager actual of the shared DownloadEnqueueCoordinator
        // seam) but is Koin-constructed via the app composition root's
        // androidDownloadSeamsModule; this bridge keeps the Hilt injector
        // (startup DownloadRecoveryInitializer) on the same instance.
        @dagger.Provides
        @Singleton
        fun provideDownloadEnqueuer(): com.raulshma.jellyplay.core.data.repository.DownloadEnqueuer = koin().get()

        // V3 downloads conveyor: DownloadStorageLayout stays in this legacy
        // module (Android Context/StatFs actual of the shared
        // DownloadStorageLayoutContract) but is Koin-constructed via the app
        // composition root's androidDownloadSeamsModule; this bridge keeps the
        // Hilt injector (feature:settings StorageSettingsViewModel) on the
        // same instance.
        @dagger.Provides
        @Singleton
        fun provideDownloadStorageLayout(): com.raulshma.jellyplay.core.data.repository.DownloadStorageLayout =
            koin().get()

        // StoragePolicy takes the download-byte aggregate as a suspend lambda so
        // it stays pure logic (testable without a DAO). Bound here so the cap
        // rule has a single owner instead of being duplicated across
        // DownloadRepositoryImpl.startDownload / downloadSeries.
        // C4p2: Koin (dataJvmModule) constructs it; this provider bridges the
        // Hilt graph to the same single.
        @dagger.Provides
        @Singleton
        fun provideStoragePolicy(): com.raulshma.jellyplay.core.data.repository.StoragePolicy = koin().get()

        // LyricsRepository is a narrow (ISP) view of the same MediaRepository singleton
        // (MediaRepository extends LyricsRepository). Providing it via delegation guarantees
        // a single shared instance — no duplicate caches.
        @dagger.Provides
        @Singleton
        fun provideLyricsRepository(mediaRepository: MediaRepository): LyricsRepository =
            mediaRepository

        // Koin-constructed since Phase C4 (the shared network module's base
        // OkHttpClient resolves it from the Koin container): this bridge keeps
        // the Hilt graph pointing at the same instance the Koin definitions
        // build. The app composition root owns the definition.
        @dagger.Provides
        @Singleton
        fun provideOkHttpConfigProvider(): OkHttpConfigProvider = koin().get()

        // ── Phase C4 part 2 bridges ─────────────────────────────────────────
        // The classes below moved into :shared:core:data; Koin
        // (dataJvmModule) is their construction owner. These @Provides keep
        // the legacy/app/feature Hilt injectors pointing at the Koin singles.
        // Their @Inject/@Singleton annotations were stripped at the move, so
        // Hilt cannot build a parallel instance.

        @dagger.Provides
        @Singleton
        fun provideBandwidthMonitor(): com.raulshma.jellyplay.core.data.streaming.BandwidthMonitor = koin().get()

        @dagger.Provides
        @Singleton
        fun provideAdaptiveBitrateSelector(): com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector =
            koin().get()

        // Home conveyor: the OrderHomeSectionsUseCase + PhotoFolderPrefetcher
        // koin().get() bridges died with :feature:home — their only Hilt
        // injectors (HomeViewModel, PhotoFolderChildUrlsStore) moved to
        // :shared:feature:home and are Koin-constructed now.

        @dagger.Provides
        @Singleton
        fun provideServerHealthMonitor(): com.raulshma.jellyplay.core.data.network.ServerHealthMonitor = koin().get()

        @dagger.Provides
        @Singleton
        fun provideRemoteNavigationBridge(): com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideUiRemoteControlDispatcher(): com.raulshma.jellyplay.core.data.remote.UiRemoteControlDispatcher =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideNewsletterTriggerManager(): com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager =
            koin().get()

        // ── C4 part 2, batch 2: repository bridges ──────────────────────────
        // The impls below moved into :shared:core:data; Koin (dataJvmModule)
        // is their construction owner. These @Provides keep the legacy/app/
        // feature Hilt injectors pointing at the Koin singles. Every bridged
        // impl had @Inject/@Singleton stripped at the move, so Hilt cannot
        // build a parallel instance.

        @dagger.Provides
        @Singleton
        fun provideAuthRepository(): com.raulshma.jellyplay.core.data.repository.AuthRepository = koin().get()

        // The realtime-socket view of the same AuthRepositoryImpl singleton.
        @dagger.Provides
        @Singleton
        fun provideRealtimeConnection(): com.raulshma.jellyplay.core.data.repository.RealtimeConnection = koin().get()

        // SSDP server discovery split out of AuthRepository so the auth seam
        // changes only for auth concerns; feature modules keep seeing a core:data
        // type, never core:network.
        @dagger.Provides
        @Singleton
        fun provideServerDiscoveryRepository(): com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideOfflineRepository(): com.raulshma.jellyplay.core.data.repository.OfflineRepository = koin().get()

        @dagger.Provides
        @Singleton
        fun providePlaybackOutboxRepository(): com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideMetadataEditorRepository(): com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideSearchHistoryRepository(): com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideWatchHistoryRepository(): com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideSeenMediaRepository(): com.raulshma.jellyplay.core.data.repository.SeenMediaRepository = koin().get()

        @dagger.Provides
        @Singleton
        fun provideItemPlaybackPreferenceRepository(): com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository =
            koin().get()

        // Direct-class injectors exist (feature/music ViewModels inject the
        // concrete repositories), so the concrete types get bridges too —
        // with @Inject stripped at the move, Hilt resolves them here instead
        // of constructing a parallel instance.
        @dagger.Provides
        @Singleton
        fun provideSmartPlaylistRepository(): com.raulshma.jellyplay.core.data.repository.SmartPlaylistRepository =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideMoodPlaylistRepository(): com.raulshma.jellyplay.core.data.repository.MoodPlaylistRepository =
            koin().get()

        // ── C4 part 2, batch 3: seams, session, playback, sync, syncplay ────
        // Same bridge pattern: Koin (dataJvmModule / the platform modules)
        // constructs; these @Provides keep Hilt injectors (app/, core/,
        // feature/) pointing at the Koin singles.

        @dagger.Provides
        @Singleton
        fun provideNetworkMonitor(): NetworkMonitor = koin().get()

        @dagger.Provides
        @Singleton
        fun provideOfflineModeManager(): OfflineModeManager = koin().get()

        @dagger.Provides
        @Singleton
        fun provideTimeSource(): TimeSource = koin().get()

        @dagger.Provides
        @Singleton
        fun provideImageUrlProvider(): ImageUrlProvider = koin().get()

        @dagger.Provides
        @Singleton
        fun provideLocalStreamProbe(): LocalStreamProbe = koin().get()

        // Home conveyor: the HomeSession koin().get() bridge died with
        // :feature:home (HomeViewModel was its only Hilt injector; the Koin
        // single lives in dataJvmModule).

        @dagger.Provides
        @Singleton
        fun provideSessionCacheRegistry(): SessionCacheRegistry = koin().get()

        // Former bindEpisodeCatalogue @Binds (impl moved to jvmShared).
        @dagger.Provides
        @Singleton
        fun provideEpisodeCatalogue(): EpisodeCatalogue = koin().get()

        @dagger.Provides
        @Singleton
        fun provideDownloadConcurrencyLimiter(): DownloadConcurrencyLimiter = koin().get()

        @dagger.Provides
        @Singleton
        fun providePlayerLifecycleManager(): PlayerLifecycleManager = koin().get()

        @dagger.Provides
        @Singleton
        fun provideQueuePersistenceHelper(): QueuePersistenceHelper = koin().get()

        // V3 livetv conveyor: VideoMiniPlayerState moved into :shared:core:data
        // (Koin-owned); app FloatingPlayerState and feature:player:video
        // VideoPlayerViewModel still Hilt-inject it.
        @dagger.Provides
        @Singleton
        fun provideVideoMiniPlayerState(): com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideAudioCachePolicyGuard(): AudioCachePolicyGuard = koin().get()

        @dagger.Provides
        @Singleton
        fun provideOfflineSyncComparator(): OfflineSyncComparator = koin().get()

        @dagger.Provides
        @Singleton
        fun provideSyncPlayManager(): SyncPlayManager = koin().get()

        // Former bindDownloadTransferClient @Binds (impl moved to jvmShared).
        @dagger.Provides
        @Singleton
        fun provideDownloadTransferClient(): DownloadTransferClient = koin().get()

        // Former bindPlaybackRepository @Binds (impl moved to jvmShared).
        @dagger.Provides
        @Singleton
        fun providePlaybackRepository(): PlaybackRepository = koin().get()

        // ── C4 part 2, batch 3: interim direct constructions ────────────────
        // DownloadRepository left this list with the V3 downloads conveyor and
        // AudioLyricsManager + OfflineSyncManager left it with the Phase X
        // MediaRepository cluster flip (Koin owns them; the bridges above/below
        // point Hilt at the singles). Remaining: DefaultAudioQueueFacade only —
        // its AudioQueueManager ctor dep is the media3 AudioPlaybackManager,
        // which stays Android/Hilt-owned (see bindAudioQueueManager).
        // @Inject was stripped at every move, so Hilt resolves the moved
        // classes through these providers/bridges (no parallel instances).

        @dagger.Provides
        @Singleton
        fun provideAudioQueueFacade(
            queueManager: com.raulshma.jellyplay.core.data.playback.AudioQueueManager,
            mediaRepository: MediaRepository,
            imageUrlProvider: ImageUrlProvider,
        ): com.raulshma.jellyplay.core.data.playback.AudioQueueFacade =
            DefaultAudioQueueFacade(queueManager, mediaRepository, imageUrlProvider)

        // V3 downloads conveyor: OfflineSyncManager flipped to a Koin single in
        // dataJvmModule (every ctor dep now resolves). This bridge keeps
        // the legacy Hilt injectors (feature:details ResyncActions) pointing at
        // the same instance the Koin graph builds; the direct-construction
        // provider above was the interim shape until this flip.
        @dagger.Provides
        @Singleton
        fun provideOfflineSyncManager(): com.raulshma.jellyplay.core.data.sync.OfflineSyncManager = koin().get()

        // ── Phase X MediaRepository cluster flip ────────────────────────────
        // The cluster's 7 impls (MediaRepositoryImpl, PlayedStateSyncImpl,
        // UserDataMutatorImpl, MediaSearchEngineImpl,
        // UnifiedMediaDetailProviderImpl, OfflineFirstItemResolverImpl,
        // OfflinePlaybackFacade) moved to :shared:core:data jvmShared;
        // Koin (dataJvmModule) constructs them. The former @Binds below became
        // these koin().get() bridges — every surviving Hilt injector (workers,
        // schedulers, legacy feature:home/details/player injectors, and the
        // since-moved PlaybackSourceResolverImpl / AdminRepositoryImpl graph)
        // shares the Koin singles.

        @dagger.Provides
        @Singleton
        fun provideMediaRepository(): MediaRepository = koin().get()

        // Concrete-class bridge: UserDataSyncWorker injects MediaRepositoryImpl
        // directly (it reaches the impl-only wholesale invalidateCaches, which
        // the move widened to public for exactly this cross-module edge).
        @dagger.Provides
        @Singleton
        fun provideMediaRepositoryImpl(): MediaRepositoryImpl = koin().get()

        @dagger.Provides
        @Singleton
        fun providePlayedStateSync(): PlayedStateSync = koin().get()

        @dagger.Provides
        @Singleton
        fun provideUserDataMutator(): com.raulshma.jellyplay.core.data.repository.UserDataMutator = koin().get()

        @dagger.Provides
        @Singleton
        fun provideMediaDetailProvider(): com.raulshma.jellyplay.core.data.repository.MediaDetailProvider =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideMediaSearchEngine(): com.raulshma.jellyplay.core.data.search.MediaSearchEngine = koin().get()

        @dagger.Provides
        @Singleton
        fun provideOfflineFirstItemResolver(): com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideOfflinePlaybackFacade(): com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade =
            koin().get()

        // AudioLyricsManager left the interim direct-construction list with
        // this flip: dataJvmModule owns it (its LyricsRepository dep is the
        // Koin-owned view of MediaRepository).
        @dagger.Provides
        @Singleton
        fun provideAudioLyricsManager(): AudioLyricsManager = koin().get()

        // Admin flip (Wave wB): both admin repositories moved to
        // :shared:core:data jvmShared and Koin owns construction on both
        // platforms (the app's Hilt interop singles for them were deleted —
        // one framework per type). These bridges carry the legacy Hilt
        // injectors over to the Koin singles.
        @dagger.Provides
        @Singleton
        fun provideAdminRepository(): AdminRepository = koin().get()

        @dagger.Provides
        @Singleton
        fun provideAdminStatisticsRepository(): AdminStatisticsRepository = koin().get()

        // ── Playback-flips wave ──────────────────────────────────────────────
        // PlaybackSourceResolverImpl, SleepTimerManager and
        // AdaptiveBitrateManager moved to :shared:core:data jvmShared (Koin
        // owns construction, @Inject/@Singleton stripped at the move —
        // android.net.Uri → File.toURI, SystemClock → TimeSource,
        // ConnectivityManager → NetworkMonitor). These bridges keep every
        // surviving Hilt injector pointing at the Koin singles; the former
        // bindPlaybackSourceResolver @Binds became the first bridge.

        @dagger.Provides
        @Singleton
        fun providePlaybackSourceResolver(): com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideSleepTimerManager(): com.raulshma.jellyplay.core.data.playback.SleepTimerManager = koin().get()

        @dagger.Provides
        @Singleton
        fun provideAdaptiveBitrateManager(): com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager =
            koin().get()

        // AppUpdate split (Wave xB): AppUpdateRepositoryImpl moved to
        // :shared:core:data jvmShared (interface reshaped — no supportedAbis
        // param, downloadUpdate/cleanupDownloadedUpdate renames, install
        // intent extracted); Koin constructs it in the platform data modules
        // (androidDataModule supplies filesDir/version/flavor/ABIs,
        // desktopDataModule the no-self-update desktop actual). The former
        // bindAppUpdateRepository @Binds became this bridge; the Android-only
        // ApkInstallBuilder seam (the old buildInstallIntent body) rides the
        // bridge below (UpdateCoordinator's Hilt ctor).
        @dagger.Provides
        @Singleton
        fun provideAppUpdateRepository(): com.raulshma.jellyplay.core.data.update.AppUpdateRepository =
            koin().get()

        @dagger.Provides
        @Singleton
        fun provideApkInstallBuilder(): com.raulshma.jellyplay.core.data.update.ApkInstallBuilder =
            koin().get()
    }

    // Phase X MediaRepository cluster flip: the former bindMediaRepository /
    // bindMediaRepositoryCacheInvalidation / bindPlayedStateSync /
    // bindUserDataMutator / bindMediaDetailProvider / bindMediaSearchEngine /
    // bindOfflineFirstItemResolver @Binds moved to Koin (dataJvmModule) with
    // their impls; the companion-object koin().get() bridges above replaced
    // them. MediaRepositoryCacheInvalidation needs no bridge — its sole Hilt
    // injector (UnifiedMediaDetailProviderImpl) moved with the cluster.

    @Binds
    @Singleton
    abstract fun bindDownloadIntake(impl: DownloadIntakeImpl): DownloadIntake

    // The narrow queue interface AudioPlaybackManager already implements
    // (playQueue/addToQueue/...). Bound so the audio queue facade (and its
    // tests) depend on the seam, not the 1642-line concrete manager graph.
    // The interface itself moved to :shared:core:data commonMain (C4 part 2);
    // the implementing AudioPlaybackManager stays Hilt-owned here (media3).
    @Binds
    @Singleton
    abstract fun bindAudioQueueManager(
        impl: com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager,
    ): com.raulshma.jellyplay.core.data.playback.AudioQueueManager

    // Admin flip (Wave wB): the former bindAdminRepository /
    // bindAdminStatisticsRepository @Binds moved to Koin (dataJvmModule) with
    // their impls; the companion-object koin().get() bridges above replaced
    // them.

    @Binds
    @Singleton
    abstract fun bindTvWatchNextScheduler(impl: TvWatchNextSchedulerImpl): TvWatchNextScheduler

    @Binds
    @Singleton
    abstract fun bindUserDataSyncScheduler(impl: UserDataSyncSchedulerImpl): UserDataSyncScheduler

    @Binds
    @Singleton
    abstract fun bindPlaybackSyncScheduler(impl: PlaybackSyncSchedulerImpl): PlaybackSyncScheduler
}
