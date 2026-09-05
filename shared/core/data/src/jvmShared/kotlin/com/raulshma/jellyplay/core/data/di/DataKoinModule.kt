package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.network.OkHttpConfigProviderImpl
import com.raulshma.jellyplay.core.data.network.ServerHealthMonitor
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.AudioCachePolicyGuard
import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.AudioSleepTimerManager
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolverImpl
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.remote.UiRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AdminRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsLabelProvider
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueueCoordinator
import com.raulshma.jellyplay.core.data.repository.DownloadProgressNotifier
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayoutContract
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.LyricsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryAccess
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryCacheInvalidation
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MoodPlaylistRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.NewsletterRepository
import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolverImpl
import com.raulshma.jellyplay.core.data.repository.OfflineImagePreloader
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlayedStateSyncImpl
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
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
import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
import com.raulshma.jellyplay.core.data.repository.UnifiedMediaDetailProviderImpl
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.repository.UserDataMutatorImpl
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.search.MediaSearchEngineImpl
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionIdentityProvider
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
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
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
 * per type. During the Hilt era these singles were reached from Hilt through
 * the legacy DataModule's `koin().get()` bridges; the impl classes'
 * `@Inject`/`@Singleton` annotations were stripped at the move, and the whole
 * bridge layer left with the wave-8 Hilt extinction — Koin only.
 *
 * Phase X MediaRepository cluster flip: the last Hilt-owned data-layer
 * cluster moved here — `MediaRepositoryImpl` (+ its PlayedStateSync /
 * MediaRepositoryCacheInvalidation / LyricsRepository views),
 * `PlayedStateSyncImpl`, `UserDataMutatorImpl`, `MediaSearchEngineImpl`,
 * `UnifiedMediaDetailProviderImpl`, `OfflineFirstItemResolverImpl` and
 * `OfflinePlaybackFacade` (see the definitions below). The legacy DataModule
 * constructs nothing from the cluster anymore (the whole legacy DataModule
 * left with the wave-8 Hilt extinction); Koin builds the cluster natively. `PlaybackSourceResolver` left
 * that latent-on-desktop state with the playback-flips wave: its impl moved
 * here Uri-free (`File.toURI()` instead of `android.net.Uri.fromFile`), so
 * UnifiedMediaDetailProviderImpl's ctor dep resolves from this module on
 * BOTH platforms and MediaDetailProvider is live on desktop too. The app's
 * HiltInteropModule reverse single for it was deleted (one framework per
 * type).
 *
 * The V3 downloads conveyor (C4-part-2 notes, fifth conveyor item) moved the
 * download engine here — `DownloadRepositoryImpl`, `DownloadDelegate` and the
 * transfer machinery — with its Android-only surfaces (WorkManager
 * enqueue/cancel, notification summary, Coil preloading, storage layout)
 * behind the `DownloadEnqueueCoordinator` / `DownloadProgressNotifier` /
 * `OfflineImagePreloader` / `DownloadStorageLayoutContract` seams, whose
 * Android actuals the app composition root registers
 * (androidDownloadSeamsModule) and desktop actuals live in
 * [desktopDataModule]. With the cluster flip above, the desktop
 * `MediaRepositoryAccess` actual became real — desktop series downloads and
 * the auto-download scheduler are live.
 *
 * `DefaultAudioQueueFacade` is the one playback-graph type NOT defined here:
 * its AudioQueueManager ctor dep is the media3 AudioPlaybackManager, so its
 * Koin single lives in the legacy core:data androidCoreDataModule (owned
 * there since wave 8A; desktopPlayerModule binds the desktop twin).
 * `AudioLyricsManager` left that Android-only set when its sole dep (the
 * LyricsRepository view of MediaRepository) became the single below;
 * `OfflineSyncManager` flipped into this module with the V3 downloads
 * conveyor.
 *
 * The admin flip (Wave wB) moved the last two Hilt-owned repositories here:
 * `AdminRepositoryImpl` (verbatim — no platform surface) and
 * `AdminStatisticsRepositoryImpl`, whose Android surfaces became seams: the
 * former `context.getString(R.string.data_*)` labels now flow through
 * [AdminStatisticsLabelProvider] (Android def in the app composition root
 * over legacy core:data resources; desktop def = base-locale English
 * literals), `android.util.Log` became the core.data.log facade, and the
 * `@ApplicationScope` scope is the DatastoreQualifiers single. The desktop
 * settings + admin sections went live with this flip.
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

    // V3 library conveyor: Koin owns construction; its consumers
    // (HomeViewModel, PhotoFolderChildUrlsStore) resolve this single
    // straight from Koin.
    single { PhotoFolderPrefetcher(get()) }

    // JellyfinApiClient resolves from :shared:core:network's networkJvmModule.
    single { ServerHealthMonitor(get(), get()) }

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
            timeSource = get(),
        )
    }
    single<AuthRepository> { get<AuthRepositoryImpl>() }
    // The realtime-socket view of the same AuthRepositoryImpl singleton (the
    // legacy bindRealtimeConnection @Binds, one instance — not a second socket).
    single<RealtimeConnection> { get<AuthRepositoryImpl>() }

    single { ServerDiscoveryRepositoryImpl(get()) }
    single<ServerDiscoveryRepository> { get<ServerDiscoveryRepositoryImpl>() }

    single { SearchHistoryRepositoryImpl(get(), get()) }
    single<SearchHistoryRepository> { get<SearchHistoryRepositoryImpl>() }

    single { ItemPlaybackPreferenceRepositoryImpl(get(), get(), get()) }
    single<ItemPlaybackPreferenceRepository> { get<ItemPlaybackPreferenceRepositoryImpl>() }

    single { MetadataEditorRepositoryImpl(get()) }
    single<MetadataEditorRepository> { get<MetadataEditorRepositoryImpl>() }

    single { SeenMediaRepositoryImpl(get()) }
    single<SeenMediaRepository> { get<SeenMediaRepositoryImpl>() }

    single { WatchHistoryRepositoryImpl(get()) }
    single<WatchHistoryRepository> { get<WatchHistoryRepositoryImpl>() }

    single { OfflineRepositoryImpl(get(), get(), get(), get(), get()) }
    single<OfflineRepository> { get<OfflineRepositoryImpl>() }

    single { PlaybackOutboxRepositoryImpl(get(), get()) }
    single<PlaybackOutboxRepository> { get<PlaybackOutboxRepositoryImpl>() }

    single { SmartPlaylistRepository(get(), get()) }

    single { MoodPlaylistRepository(get(), get(), get()) }

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
    // ctor deps live in the Android-only legacy core:data remainder
    // (AudioPlaybackManager + the media3 audio graph, owned by
    // androidCoreDataModule there) are deliberately NOT defined here:
    // DefaultAudioQueueFacade only. OfflineSyncManager, AudioLyricsManager and
    // (playback-flips wave) PlaybackSourceResolverImpl all moved off that
    // list as their ctor deps became Koin-resolvable.

    single<TimeSource> { SystemTimeSource() }

    single { HomeSession(get(), get(DatastoreQualifiers.applicationScope)) }

    // Wave 15B: the identity seam the promoted commonMain graph consumes
    // (SeerrRepositoryImpl's cache keys + SessionCacheRegistry's transition
    // subscription). Binds the SAME HomeSession singleton — android/desktop
    // behavior unchanged; wasmJs binds the AtomicSessionState-backed provider
    // in dataWasmModule instead.
    single<SessionIdentityProvider> { get<HomeSession>() }

    single {
        SessionCacheRegistry(
            sessionIdentity = get(),
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

    // Playback-flips wave: SleepTimerManager moved from the legacy :core:data
    // shim — SystemClock.elapsedRealtime became the TimeSource seam above
    // (the Android actual IS SystemClock.elapsedRealtime, so the countdown is
    // unchanged). The audio/live player VMs resolve this single through Koin
    // directly since their wave-7 migrations; legacy core:data's
    // AudioPlaybackManager resolves this single from androidCoreDataModule.
    // No other consumer needs the AudioSleepTimerManager interface, so only
    // the Koin alias exists here.
    single { SleepTimerManager(get()) }
    single<AudioSleepTimerManager> { get<SleepTimerManager>() }

    // Playback-flips wave: AdaptiveBitrateManager moved from the legacy
    // :core:data shim — ConnectivityManager became the NetworkMonitor seam
    // (null-network/metered parity documented on the class). Its consumers
    // (feature:details DownloadLifecycleActions, feature:player:video
    // PlayerCastController / PlaybackSession / PlayerSessionManager /
    // VideoPlayerViewModel) resolve this single from Koin directly.
    single { AdaptiveBitrateManager(get(), get(), get()) }

    // V3 livetv conveyor: the mini-player holder moved from the legacy
    // :core:data shim (one framework per type — @Singleton/@Inject stripped at
    // the move). Consumers (app FloatingPlayerState, feature:player:video
    // VideoPlayerViewModel, livetv's ChannelsViewModel) resolve this single
    // directly from Koin; ChannelsViewModel resolves it
    // directly from here.
    single { VideoMiniPlayerState() }

    single {
        AudioCachePolicyGuard(
            audioCacheStore = get(),
            networkMonitor = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single { OfflineSyncComparator(get()) }

    // V3 downloads conveyor: OfflineSyncManager flipped from the interim
    // direct-construction DataModule provider to a Koin single (C4 flip
    // pattern) — every ctor dep is now Koin-resolvable: the DAOs via
    // databaseDaosModule, the comparator/PlaybackRepository via this module,
    // OfflineModeManager via the platform data modules, the application scope
    // via DatastoreQualifiers, and — since the downloads conveyor moved the
    // engine — DownloadRepository from this module's own single (feature:
    // details' ResyncActions shares the same instance through Koin).
    // `writer` reuses the DownloadRepository single: the interface extends
    // OfflineDownloadWriter, so no separate definition is needed. The former
    // Hilt→Koin→Hilt edge (interop MediaRepository) died with the Phase X
    // MediaRepository cluster flip below — the mediaRepository dep is now
    // this module's own MediaRepositoryImpl single on both platforms, so the
    // graph is pure Koin from OfflineSyncManager down.
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
            timeSource = get(),
        )
    }

    // ── Phase X MediaRepository cluster flip ───────────────────────────────
    // The last Hilt-owned data-layer cluster (C4 part 2's "deliberately
    // Hilt-retained" list, unblocked by the downloads seams). The impls moved
    // here verbatim (see each file's move note); definitions mirror the
    // constructors. The former Hilt-interop singles for MediaRepository /
    // UserDataMutator / MediaSearchEngine were deleted from the app module —
    // Koin builds them natively now, on both platforms.

    single {
        PlayedStateSyncImpl(
            apiClient = get(),
            offlineRepository = get(),
            playbackOutboxRepository = get(),
            offlineModeManager = get(),
            // Lazy breaks the MediaRepositoryImpl ↔ PlayedStateSync
            // construction cycle (the downloadDelegate pattern):
            // MediaRepositoryImpl ctor-injects PlayedStateSync eagerly, this
            // side defers MediaRepository until first use. The download
            // stack params stay Lazy too (defensive, per the ctor kdoc).
            mediaRepository = lazy { get<MediaRepository>() },
            downloadsStore = lazy { get<DownloadsStore>() },
            downloadRepository = lazy { get<DownloadRepository>() },
            timeSource = get(),
        )
    }
    single<PlayedStateSync> { get<PlayedStateSyncImpl>() }

    single {
        MediaRepositoryImpl(
            apiClient = get(),
            homeSectionCacheDao = get(),
            playedStateSync = get(),
            episodeCatalogue = get(),
            userDataRealtimeChannel = get(),
            timeSource = get(),
            homeSession = get(),
            sessionCacheRegistry = get(),
        )
    }
    single<MediaRepository> { get<MediaRepositoryImpl>() }
    // Plan 08's module-internal cache-maintenance view (the former DataModule
    // bindMediaRepositoryCacheInvalidation @Binds): same single, narrow seam.
    single<MediaRepositoryCacheInvalidation> { get<MediaRepositoryImpl>() }
    // Family-repository views (same single, narrow seam — same pattern as the
    // MediaRepositoryCacheInvalidation binding above): MediaRepositoryImpl
    // implements each family directly, and single-family consumers now inject
    // the family type instead of the 86-member MediaRepository union.
    single<LiveTvRepository> { get<MediaRepositoryImpl>() }
    single<SyncPlayRepository> { get<MediaRepositoryImpl>() }
    single<NewsletterRepository> { get<MediaRepositoryImpl>() }
    single<PlaylistRepository> { get<MediaRepositoryImpl>() }
    // Lyrics engine: its own impl (the LRC/LRCLIB fetch-parse-cache chain)
    // since the extraction from MediaRepositoryImpl — no longer a view of the
    // media single.
    single {
        LyricsRepositoryImpl(
            apiClient = get(),
            lrcLibApi = get(),
            lyricsCacheDao = get(),
            networkMonitor = get(),
            timeSource = get(),
        )
    }
    single<LyricsRepository> { get<LyricsRepositoryImpl>() }

    single {
        UserDataMutatorImpl(
            // Both deferred: the provider and repository reference each
            // other's graphs (UnifiedMediaDetailProviderImpl ctor-injects
            // MediaRepository; UserDataMutator reaches MediaDetailProvider),
            // and deferring construction keeps this module out of any cycle.
            mediaRepository = lazy { get<MediaRepository>() },
            mediaDetailProvider = lazy { get<MediaDetailProvider>() },
        )
    }
    single<UserDataMutator> { get<UserDataMutatorImpl>() }

    single {
        MediaSearchEngineImpl(
            mediaRepository = get(),
            seerrRepository = get(),
            searchHistoryRepository = get(),
            serverIdentityStore = get(),
            experimentalStore = get(),
            offlineModeManager = get(),
            offlineRepository = get(),
        )
    }
    single<MediaSearchEngine> { get<MediaSearchEngineImpl>() }

    // Playback-flips wave: PlaybackSourceResolverImpl moved from the legacy
    // :core:data shim (Uri.fromFile → File.toURI, see the impl's URI-shape
    // note) — UnifiedMediaDetailProviderImpl's ctor dep below now resolves
    // from this module on BOTH platforms, and the app's HiltInterop reverse
    // single for the interface was deleted with the wave-8 Hilt extinction.
    // Every consumer of the interface (app MainViewModel,
    // feature:player:video PlayerSessionManager, the core:data audio trio)
    // resolves this single from Koin directly.
    single {
        PlaybackSourceResolverImpl(
            downloadRepository = get(),
            mediaRepository = get(),
            playbackRepository = get(),
            offlineRepository = get(),
            offlinePlaybackFacade = get(),
        )
    }
    single<PlaybackSourceResolver> { get<PlaybackSourceResolverImpl>() }

    single {
        UnifiedMediaDetailProviderImpl(
            mediaRepository = get(),
            cacheInvalidation = get(),
            offlineRepository = get(),
            downloadRepository = get(),
            episodeCatalogue = get(),
            playbackSourceResolver = get(),
            offlineModeManager = get(),
            localStreamProbe = get(),
            appScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single<MediaDetailProvider> { get<UnifiedMediaDetailProviderImpl>() }

    single {
        OfflineFirstItemResolverImpl(
            offlineRepository = get(),
            mediaRepository = get(),
            offlineModeManager = get(),
            imageUrlProvider = get(),
        )
    }
    single<OfflineFirstItemResolver> { get<OfflineFirstItemResolverImpl>() }

    // Concrete class (no interface). Playback-flips wave: its one former
    // Hilt injector (PlaybackSourceResolverImpl) moved into this module too,
    // so construction AND consumption are all-Koin here.
    single { OfflinePlaybackFacade(get(), get()) }

    // AudioLyricsManager left the DataModule interim direct-construction
    // list with this flip: its sole ctor dep (the LyricsRepository view of
    // MediaRepository) is Koin-owned above.
    single { AudioLyricsManager(get()) }

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
    // (both platform defs forward to this module's own MediaRepositoryImpl
    // single since the Phase X cluster flip — Android in androidDataModule,
    // desktop in desktopDataModule). `downloadDelegate` keeps the
    // construction cycle broken via a memoizing kotlin Lazy (the Lazy-deferred
    // pattern). Consumers (PlayedStateSyncImpl,
    // OfflinePlaybackFacade, AudioLibraryBrowser, workers, feature modules)
    // resolve this single from Koin directly.
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
            timeSource = get(),
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

    // The unified quick-action download/remove delegate every host surface
    // shares (library, favorites, search, detail rows). Koin-owned
    // construction per the jvmShared convention (see MediaDownloadActions'
    // kdoc): the scope is the DatastoreQualifiers application scope,
    // DownloadRepository/OfflineRepository are this module's own singles, and
    // DownloadIntake resolves from the platform data modules (Android:
    // AndroidCoreDataKoinModule's DownloadIntakeImpl; desktop:
    // desktopDataModule's DesktopDownloadIntake). The DownloadOutcomeMessenger
    // binding is platform-owned too — androidAppInteropAdaptersModule bridges
    // it to core/ui's UserMessageBus on Android, desktopDataModule provides a
    // desktop definition — because core/data must not depend on core/ui.
    single {
        MediaDownloadActions(
            scope = get(DatastoreQualifiers.applicationScope),
            downloadRepository = get<DownloadRepository>(),
            downloadIntake = get<DownloadIntake>(),
            offlineRepository = get(),
            messenger = get(),
        )
    }

    single {
        SeerrRepositoryImpl(
            seerrApiClient = get(),
            tmdbApiClient = get(),
            seerrPreferencesStore = get(),
            secureCredentialsStore = get(),
            sessionIdentity = get(),
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

    // ── Phase X admin flip (Wave wB) ──────────────────────────────────────
    // AdminRepositoryImpl + AdminStatisticsRepositoryImpl moved from the
    // legacy :core:data shim (Hilt @Binds -> koin().get() bridges there, the
    // app's Hilt interop singles deleted). Every ctor dep resolves natively
    // here: the API client + engine + the two realtime channels from
    // networkJvmModule, the DAOs from databaseDaosModule, Json from
    // networkJvmModule, the application scope from DatastoreQualifiers, and
    // the label seam from the platform data modules (Android: the app's
    // androidAdminSeamsModule over legacy core:data R.string, desktop:
    // English literals in desktopDataModule).

    single {
        AdminRepositoryImpl(
            apiClient = get(),
            engine = get(),
            realtimeTasks = get(),
            activityLogRealtimeChannel = get(),
        )
    }
    single<AdminRepository> { get<AdminRepositoryImpl>() }

    single {
        AdminStatisticsRepositoryImpl(
            apiClient = get(),
            auditLogDao = get(),
            scanStateDao = get(),
            json = get(),
            scope = get(DatastoreQualifiers.applicationScope),
            labels = get<AdminStatisticsLabelProvider>(),
            timeSource = get(),
        )
    }
    single<AdminStatisticsRepository> { get<AdminStatisticsRepositoryImpl>() }
}
