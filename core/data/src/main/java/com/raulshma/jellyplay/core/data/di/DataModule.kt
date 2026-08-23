package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AdminRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepositoryImpl
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadIntakeImpl
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlayedStateSyncImpl
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
import com.raulshma.jellyplay.core.data.session.HomeSession
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
        @dagger.Provides
        @Singleton
        fun provideDownloadDelegate(
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
            writer: com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter,
            playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
        ): DownloadDelegate = DownloadDelegate(context, writer, playbackRepository)

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

        @dagger.Provides
        @Singleton
        fun provideOrderHomeSectionsUseCase(): com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase =
            koin().get()

        // V3 library conveyor: moved into :shared:core:data (Koin-owned);
        // HomeViewModel + PhotoFolderChildUrlsStore still Hilt-inject it.
        @dagger.Provides
        @Singleton
        fun providePhotoFolderPrefetcher(): com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher =
            koin().get()

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

        @dagger.Provides
        @Singleton
        fun provideHomeSession(): HomeSession = koin().get()

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
        // These impls MOVED to :shared:core:data but Koin cannot own them
        // yet — a ctor dep is still Hilt-owned legacy (MediaRepository /
        // DownloadRepository / AudioPlaybackManager / LyricsRepository /
        // OfflinePlaybackFacade). Until those flip, Hilt constructs the moved
        // classes here directly. @Inject was stripped at the move, so Hilt
        // resolves them through these providers (no parallel instances); the
        // Koin definitions + koin().get() bridges land with the remaining
        // repository flips.

        @dagger.Provides
        @Singleton
        fun provideAudioLyricsManager(lyricsRepository: LyricsRepository): AudioLyricsManager =
            AudioLyricsManager(lyricsRepository)

        @dagger.Provides
        @Singleton
        fun provideAudioQueueFacade(
            queueManager: com.raulshma.jellyplay.core.data.playback.AudioQueueManager,
            mediaRepository: MediaRepository,
            imageUrlProvider: ImageUrlProvider,
        ): com.raulshma.jellyplay.core.data.playback.AudioQueueFacade =
            DefaultAudioQueueFacade(queueManager, mediaRepository, imageUrlProvider)

        // The deep "Playback Source Resolver" seam: the interface moved to
        // :shared:core:data commonMain, but the impl stays Hilt-owned legacy
        // (C4 part 2 bounce: its ctor takes OfflinePlaybackFacade, itself
        // Hilt-coupled to DownloadRepository). See bindPlaybackSourceResolver.

        @dagger.Provides
        @Singleton
        fun provideOfflineSyncManager(
            mediaRepository: MediaRepository,
            writer: com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter,
            downloadRepository: DownloadRepository,
            offlineMediaDao: com.raulshma.jellyplay.core.database.dao.OfflineMediaDao,
            syncBaselineDao: com.raulshma.jellyplay.core.database.dao.SyncBaselineDao,
            comparator: OfflineSyncComparator,
            offlineModeManager: OfflineModeManager,
            playbackRepository: PlaybackRepository,
            @com.raulshma.jellyplay.core.datastore.di.ApplicationScope appScope: kotlinx.coroutines.CoroutineScope,
        ): com.raulshma.jellyplay.core.data.sync.OfflineSyncManager =
            com.raulshma.jellyplay.core.data.sync.OfflineSyncManager(
                mediaRepository,
                writer,
                downloadRepository,
                offlineMediaDao,
                syncBaselineDao,
                comparator,
                offlineModeManager,
                playbackRepository,
                appScope,
            )
    }

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    // Plan 08: module-internal cache-maintenance view of the same
    // MediaRepositoryImpl singleton. The per-type invalidation dispatch
    // (MediaRepositoryCacheInvalidation.invalidateFor) lives behind this
    // narrow seam so the detail provider never sees the repository's cache
    // machinery — same instance, not a second set of caches.
    @Binds
    @Singleton
    internal abstract fun bindMediaRepositoryCacheInvalidation(
        impl: MediaRepositoryImpl,
    ): com.raulshma.jellyplay.core.data.repository.MediaRepositoryCacheInvalidation

    @Binds
    @Singleton
    abstract fun bindPlayedStateSync(impl: PlayedStateSyncImpl): PlayedStateSync

    // The single seam for user-data mutations (watched / favorite): owns the
    // serialize → write → optimistic-rewrite protocol so feature screens only
    // supply a container adapter. Adapter over MediaRepository (the write) and
    // MediaDetailProvider (the active-session rewrite), both Lazy — see
    // UserDataMutatorImpl.
    @Binds
    @Singleton
    abstract fun bindUserDataMutator(
        impl: com.raulshma.jellyplay.core.data.repository.UserDataMutatorImpl,
    ): com.raulshma.jellyplay.core.data.repository.UserDataMutator

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    // The narrow write surface shared by DownloadDelegate. Bound to the same
    // DownloadRepositoryImpl singleton — a real seam in the type graph, not a
    // second instance. Keeps DownloadDelegate off the 25-method god-interface.
    @Binds
    @Singleton
    abstract fun bindOfflineDownloadWriter(impl: DownloadRepositoryImpl): com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter

    @Binds
    @Singleton
    abstract fun bindDownloadIntake(impl: DownloadIntakeImpl): DownloadIntake

    // The single external seam for media-detail resolution. Owns the remote/local
    // source policy, the source-dependent read graph, and capability derivation.
    // See the MediaDetailProvider kdoc for the source policy. Internal impl:
    // it takes the module-internal cache-maintenance seam (plan 08), so only
    // the MediaDetailProvider surface is public.
    @Binds
    @Singleton
    internal abstract fun bindMediaDetailProvider(
        impl: com.raulshma.jellyplay.core.data.repository.UnifiedMediaDetailProviderImpl,
    ): com.raulshma.jellyplay.core.data.repository.MediaDetailProvider

    // The deep "Playback Source Resolver" seam: the single owner of the
    // download-vs-stream fork. The interface lives in :shared:core:data
    // commonMain (C4 part 2); the impl stays here — its ctor takes the
    // Hilt-coupled OfflinePlaybackFacade (bounced; re-attempt when
    // DownloadRepository flips to Koin).
    @Binds
    @Singleton
    abstract fun bindPlaybackSourceResolver(
        impl: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolverImpl,
    ): com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver

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

    // The administration seam for Jellyfin-native admin screens: absorbs each
    // screen's fan-out, lookup, and fallback policy behind screen operations
    // so features never see the raw transport client.
    @Binds
    @Singleton
    abstract fun bindAdminRepository(impl: AdminRepositoryImpl): AdminRepository

    @Binds
    @Singleton
    abstract fun bindAdminStatisticsRepository(impl: AdminStatisticsRepositoryImpl): AdminStatisticsRepository

    @Binds
    @Singleton
    abstract fun bindMediaSearchEngine(
        impl: com.raulshma.jellyplay.core.data.search.MediaSearchEngineImpl,
    ): com.raulshma.jellyplay.core.data.search.MediaSearchEngine

    // The narrow offline-first title+poster lookup seam. Leaf
    // adapter over existing singletons — no scope or cycle risk.
    @Binds
    @Singleton
    abstract fun bindOfflineFirstItemResolver(
        impl: com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolverImpl,
    ): com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver

    @Binds
    @Singleton
    abstract fun bindTvWatchNextScheduler(impl: TvWatchNextSchedulerImpl): TvWatchNextScheduler

    @Binds
    @Singleton
    abstract fun bindUserDataSyncScheduler(impl: UserDataSyncSchedulerImpl): UserDataSyncScheduler

    @Binds
    @Singleton
    abstract fun bindPlaybackSyncScheduler(impl: PlaybackSyncSchedulerImpl): PlaybackSyncScheduler

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(impl: com.raulshma.jellyplay.core.data.update.AppUpdateRepositoryImpl): com.raulshma.jellyplay.core.data.update.AppUpdateRepository
}
