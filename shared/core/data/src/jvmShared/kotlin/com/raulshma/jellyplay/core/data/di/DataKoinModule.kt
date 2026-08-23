package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl
import com.raulshma.jellyplay.core.data.network.OkHttpConfigProviderImpl
import com.raulshma.jellyplay.core.data.network.ServerHealthMonitor
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.playback.AudioCachePolicyGuard
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.remote.UiRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueueCoordinator
import com.raulshma.jellyplay.core.data.repository.DownloadProgressNotifier
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayoutContract
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryAccess
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MoodPlaylistRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
import com.raulshma.jellyplay.core.data.repository.OfflineImagePreloader
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SmartPlaylistRepository
import com.raulshma.jellyplay.core.data.repository.StoragePolicy
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.data.streaming.BandwidthMonitor
import com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayController
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEventHandler
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayPlaybackCore
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayQueueCore
import com.raulshma.jellyplay.core.data.syncplay.TimeSyncManager
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.data.util.SystemTimeSource
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.worker.DownloadTransferClient
import com.raulshma.jellyplay.core.data.worker.OkHttpDownloadTransferClient
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.core.network.subtitle.SubtitleProvider
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the platform-independent data layer
 * (docs/kmp-migration-plan.md §Phase C4 part 2). Batch 1 moved the portable
 * leaf types; batch 2 moved the Koin-constructible repository layer; batch 3
 * the session / playback / sync / syncplay / worker cluster. Each definition
 * is explicit (no reflection) and matches the constructor verbatim.
 *
 * Construction-owner rule: Koin owns every type defined here — one framework
 * per type. Hilt reaches these singles only through the legacy DataModule's
 * `koin().get()` bridges; the impl classes' `@Inject`/`@Singleton` annotations
 * were stripped at the move, so Hilt cannot build a parallel instance.
 *
 * Deliberately still Hilt-owned (no Koin definition): the MediaRepository
 * cluster — `MediaRepositoryImpl` plus its Hilt-coupled dependents
 * `PlayedStateSyncImpl` (the `dagger.Lazy<DownloadRepository>` edge, now
 * bridged back to the Koin single), `OfflineFirstItemResolverImpl`,
 * `UserDataMutatorImpl`, `MediaSearchEngineImpl`,
 * `UnifiedMediaDetailProviderImpl` and `OfflinePlaybackFacade`. The legacy
 * DataModule constructs these directly; they reach the Koin-owned
 * DownloadRepository below through `koin().get()` bridges. The V3 downloads
 * conveyor (C4-part-2 notes, fifth conveyor item) moved the download engine
 * itself here — `DownloadRepositoryImpl`, `DownloadDelegate` and the transfer
 * machinery — with its Android-only surfaces (WorkManager enqueue/cancel,
 * notification summary, Coil preloading, storage layout) behind the
 * `DownloadEnqueueCoordinator` / `DownloadProgressNotifier` /
 * `OfflineImagePreloader` / `DownloadStorageLayoutContract` seams, whose
 * Android actuals the app composition root registers
 * (androidDownloadSeamsModule) and desktop actuals live in
 * [desktopDataModule].
 *
 * Interim DataModule direct-construction providers (types moved here but
 * still Hilt-built pending the flips above): `AudioLyricsManager`
 * (LyricsRepository view of MediaRepository) and `DefaultAudioQueueFacade`
 * (AudioQueueManager impl is the media3 AudioPlaybackManager). The
 * `OfflineSyncManager` provider flipped to the standard `koin().get()`
 * bridge with the V3 downloads conveyor — see its definition below.
 *
 * Platform-bound definitions (Context / dataDir-shaped picks) live in
 * [androidDataModule] / [desktopDataModule].
 */
val dataJvmModule: Module = module {
    // Relocated from the app composition root's appNetworkConfigModule (C4
    // part 2, DI-finalize): networkJvmModule's base OkHttpClient resolves
    // this via cross-module get() — same ctor wiring the app module had.
    single<OkHttpConfigProvider> {
        OkHttpConfigProviderImpl(
            networkOfflineStore = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single { BandwidthMonitor() }

    // BandwidthInterceptor resolves from :shared:core:network's networkJvmModule.
    single { AdaptiveBitrateSelector(get(), get()) }

    single { OrderHomeSectionsUseCase() }

    // V3 library conveyor: Koin owns construction; legacy Hilt injectors
    // (HomeViewModel, PhotoFolderChildUrlsStore) reach this single through the
    // DataModule koin().get() bridge. MediaRepository resolves via the app's
    // Hilt interop on Android.
    single { PhotoFolderPrefetcher(get()) }

    // JellyfinApiClient resolves from :shared:core:network's networkJvmModule.
    single { ServerHealthMonitor(get()) }

    single { RemoteNavigationBridge() }

    single { UiRemoteControlDispatcher() }

    // NotificationStore resolves from :shared:core:datastore's Koin modules.
    single { NewsletterTriggerManager(get()) }

    // ── Repository layer (C4 part 2, batch 2) ─────────────────────────────
    // DAOs resolve from :shared:core:database's databaseDaosModule, stores from
    // :shared:core:datastore's modules, API clients from networkJvmModule.
    // Constructors are mirrored verbatim from the moved impls.

    single {
        AuthRepositoryImpl(
            apiClient = get(),
            webSocketClient = get<JellyfinWebSocketClient>(),
            database = get(),
            serverDao = get(),
            userDao = get(),
            serverIdentityStore = get(),
            tokenCipher = get(),
            json = get(),
            externalScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single<AuthRepository> { get<AuthRepositoryImpl>() }
    // The realtime-socket view of the same AuthRepositoryImpl singleton (the
    // legacy bindRealtimeConnection @Binds, one instance — not a second socket).
    single<RealtimeConnection> { get<AuthRepositoryImpl>() }

    single { ServerDiscoveryRepositoryImpl(get()) }
    single<ServerDiscoveryRepository> { get<ServerDiscoveryRepositoryImpl>() }

    single { SearchHistoryRepositoryImpl(get()) }
    single<SearchHistoryRepository> { get<SearchHistoryRepositoryImpl>() }

    single { ItemPlaybackPreferenceRepositoryImpl(get(), get()) }
    single<ItemPlaybackPreferenceRepository> { get<ItemPlaybackPreferenceRepositoryImpl>() }

    single { MetadataEditorRepositoryImpl(get()) }
    single<MetadataEditorRepository> { get<MetadataEditorRepositoryImpl>() }

    single { SeenMediaRepositoryImpl(get()) }
    single<SeenMediaRepository> { get<SeenMediaRepositoryImpl>() }

    single { WatchHistoryRepositoryImpl(get()) }
    single<WatchHistoryRepository> { get<WatchHistoryRepositoryImpl>() }

    single { OfflineRepositoryImpl(get(), get(), get(), get(), get()) }
    single<OfflineRepository> { get<OfflineRepositoryImpl>() }

    single { PlaybackOutboxRepositoryImpl(get()) }
    single<PlaybackOutboxRepository> { get<PlaybackOutboxRepositoryImpl>() }

    single { SmartPlaylistRepository(get(), get()) }

    single { MoodPlaylistRepository(get(), get()) }

    // The byte-cap rule previously built by DataModule.provideStoragePolicy —
    // same stores, same DAO-backed suspend aggregate.
    single {
        StoragePolicy(
            networkOfflineStore = get(),
            downloadsStore = get(),
            currentBytesProvider = { get<DownloadDao>().getTotalDownloadedBytes() },
        )
    }

    // ── C4 part 2, batch 3: session / playback / sync / syncplay / worker ──
    // Constructors mirrored verbatim from the moved impls. The types whose
    // ctor deps are still Hilt-owned in the legacy shim (MediaRepository,
    // DownloadRepository, AudioPlaybackManager, LyricsRepository,
    // OfflinePlaybackFacade) are deliberately NOT defined here — the legacy
    // DataModule constructs them via interim @Provides until those flip:
    // AudioLyricsManager, DefaultAudioQueueFacade, PlaybackSourceResolverImpl
    // (platform). OfflineSyncManager moved off that list with the V3 downloads
    // conveyor (see its definition below — the Hilt-interop edges resolve).

    single<TimeSource> { SystemTimeSource() }

    single { HomeSession(get(), get(DatastoreQualifiers.applicationScope)) }

    single {
        SessionCacheRegistry(
            homeSession = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        EpisodeCatalogueImpl(
            apiClient = get(),
            offlineRepository = get(),
            homeSession = get(),
            sessionCacheRegistry = get(),
        )
    }
    single<EpisodeCatalogue> { get<EpisodeCatalogueImpl>() }

    single { DownloadConcurrencyLimiter() }

    single { PlayerLifecycleManager(get()) }

    single { QueuePersistenceHelper(get()) }

    // V3 livetv conveyor: the mini-player holder moved from the legacy
    // :core:data shim (one framework per type — @Singleton/@Inject stripped at
    // the move). Legacy Hilt injectors (app FloatingPlayerState,
    // feature:player:video VideoPlayerViewModel) reach this single through the
    // DataModule koin().get() bridge; livetv's ChannelsViewModel resolves it
    // directly from here.
    single { VideoMiniPlayerState() }

    single {
        AudioCachePolicyGuard(
            audioCacheStore = get(),
            networkMonitor = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single { OfflineSyncComparator() }

    // V3 downloads conveyor: OfflineSyncManager flipped from the interim
    // direct-construction DataModule provider to a Koin single (C4 flip
    // pattern) — every ctor dep is now Koin-resolvable: the DAOs via
    // databaseDaosModule, the comparator/PlaybackRepository via this module,
    // OfflineModeManager via the platform data modules, the application scope
    // via DatastoreQualifiers, MediaRepository through the app composition
    // root's Hilt interop on Android, and — since the downloads conveyor
    // moved the engine — DownloadRepository from this module's own single
    // (the legacy DataModule provider bridges back to it via koin().get(),
    // so Hilt injectors like feature:details' ResyncActions share the
    // instance).
    // `writer` reuses the DownloadRepository single: the interface extends
    // OfflineDownloadWriter, so no separate definition is needed. Desktop:
    // latent — MediaRepository has no desktop definition yet, but the
    // single is lazy, so nothing resolves (or fails) until the downloads
    // feature opens there.
    single {
        OfflineSyncManager(
            mediaRepository = get(),
            writer = get<DownloadRepository>(),
            downloadRepository = get(),
            offlineMediaDao = get(),
            syncBaselineDao = get(),
            comparator = get(),
            offlineModeManager = get(),
            playbackRepository = get(),
            appScope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single { TimeSyncManager(get()) }
    single { SyncPlayController(get()) }
    single { SyncPlayEventHandler() }
    single { SyncPlayQueueCore(get()) }
    single {
        SyncPlayPlaybackCore(
            timeSyncManager = get(),
            controller = get(),
            syncPlayCastStore = get(),
        )
    }
    single {
        SyncPlayManager(
            apiClient = get(),
            webSocketClient = get(),
            authRepository = get(),
            timeSyncManager = get(),
            serverIdentityStore = get(),
            eventHandler = get(),
            syncPlayController = get(),
            playbackCore = get(),
            queueCore = get(),
        )
    }

    single {
        OkHttpDownloadTransferClient(get(NetworkQualifiers.downloadHttpClient))
    }
    single<DownloadTransferClient> { get<OkHttpDownloadTransferClient>() }

    // ── V3 downloads conveyor: the portable download engine ────────────────
    // DownloadRepositoryImpl moved from the legacy :core:data shim with its
    // Android surfaces behind seams: enqueue/cancel → DownloadEnqueueCoordinator
    // (Android: WorkManager DownloadEnqueuer via the app's
    // androidDownloadSeamsModule; desktop: the in-process DesktopDownloadManager),
    // storage layout → DownloadStorageLayoutContract (Android: Context/StatFs
    // impl via the app module; desktop: appdata-based impl in desktopDataModule),
    // notification summary + Coil preloading → platform no-op-able fun
    // interfaces, and MediaRepository behind the deferred MediaRepositoryAccess
    // (Android def: androidDataModule → Hilt interop; desktop def: throws until
    // Phase X — every mediaRepository use sits on the series paths, which a
    // single-item startDownload never reaches). `downloadDelegate` keeps the
    // construction cycle broken via a memoizing kotlin Lazy (the daggerLazy
    // pattern). Legacy Hilt injectors (PlayedStateSyncImpl,
    // OfflinePlaybackFacade, AudioLibraryBrowser, workers, feature modules)
    // reach this single through the DataModule koin().get() bridges.
    single {
        DownloadRepositoryImpl(
            downloadDao = get(),
            offlineMediaDao = get(),
            playbackStateDao = get(),
            syncBaselineDao = get(),
            database = get(),
            mediaRepository = get<MediaRepositoryAccess>(),
            episodeCatalogue = get(),
            playbackRepository = get(),
            httpClient = get(),
            downloadsStore = get(),
            json = get(),
            downloadDelegate = lazy { get<DownloadDelegate>() },
            storagePolicy = get(),
            downloadEnqueuer = get<DownloadEnqueueCoordinator>(),
            storageLayout = get<DownloadStorageLayoutContract>(),
            syncComparator = get(),
            progressNotifier = get<DownloadProgressNotifier>(),
            imagePreloader = get<OfflineImagePreloader>(),
        )
    }
    single<DownloadRepository> { get<DownloadRepositoryImpl>() }

    // The narrow write surface the DownloadDelegate depends on — the former
    // bindOfflineDownloadWriter @Binds: same instance as the repository above
    // (the interface extends OfflineDownloadWriter), not a second repository.
    single<OfflineDownloadWriter> { get<DownloadRepository>() }

    // Per-item download recipe (prepare + execute + artifact bundle). The
    // writer edge resolves to the DownloadRepository single above; the Lazy in
    // the repository ctor defers this resolution, breaking the cycle.
    single {
        DownloadDelegate(
            writer = get<DownloadRepository>(),
            playbackRepository = get(),
        )
    }

    single {
        SeerrRepositoryImpl(
            seerrApiClient = get(),
            tmdbApiClient = get(),
            seerrPreferencesStore = get(),
            secureCredentialsStore = get(),
            homeSession = get(),
            sessionCacheRegistry = get(),
            cacheScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single<SeerrRepository> { get<SeerrRepositoryImpl>() }

    single { SeerrRequestDelegate(get()) }

    single {
        PlaybackRepositoryImpl(
            apiClient = get(),
            outbox = get(),
            offlineModeManager = get(),
            homeSession = get(),
            sessionCacheRegistry = get(),
        )
    }
    single<PlaybackRepository> { get<PlaybackRepositoryImpl>() }

    single {
        SubtitleProviderRepositoryImpl(
            preferencesStore = get(),
            externalProviders = get<Map<SubtitleProviderKind, SubtitleProvider>>(),
            playbackRepository = get(),
        )
    }
    single<SubtitleProviderRepository> { get<SubtitleProviderRepositoryImpl>() }

    single {
        ArrRepositoryImpl(
            radarrApiClient = get(),
            sonarrApiClient = get(),
            seerrRepository = get(),
            arrPreferencesStore = get(),
            cacheScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single<ArrRepository> { get<ArrRepositoryImpl>() }
}
