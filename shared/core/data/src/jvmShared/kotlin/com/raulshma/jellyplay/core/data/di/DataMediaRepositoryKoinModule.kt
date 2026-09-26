package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolverImpl
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.repository.LiveTvRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.LyricsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MediaCacheInvalidator
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryCacheInvalidation
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryInternals
import com.raulshma.jellyplay.core.data.repository.NewsletterRepository
import com.raulshma.jellyplay.core.data.repository.NewsletterRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolverImpl
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlayedStateSyncImpl
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
import com.raulshma.jellyplay.core.data.repository.UnifiedMediaDetailProviderImpl
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.repository.UserDataMutatorImpl
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.search.MediaSearchEngineImpl
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayController
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayEventHandler
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayPlaybackCore
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayQueueCore
import com.raulshma.jellyplay.core.data.syncplay.TimeSyncManager
import com.raulshma.jellyplay.core.data.worker.DownloadTransferClient
import com.raulshma.jellyplay.core.data.worker.OkHttpDownloadTransferClient
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.SyncPlayApiClient
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The MediaRepository-cluster family of the dataJvmModule split (the last
 * Hilt-owned data-layer cluster's flip — see [dataJvmModule] for the
 * construction-owner rules): the media repository + its views and
 * collaborators, the family-repository splits, and the SyncPlay graph that
 * rides at the section's tail. Binding bodies moved verbatim from the
 * pre-split single-module layout.
 */
internal val dataMediaRepositoryModule: Module = module {
    // ──  MediaRepository cluster flip ───────────────────────────────
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

    // Facade split: the ONE shared-state holder for the media repository
    // family — a Koin single ctor-injected into MediaRepositoryImpl AND
    // PlaylistRepositoryImpl below, so the detail-cache cluster stays ONE
    // instance across the split (a playlist edit self-invalidates through
    // the same epoch-guarded group the media repo's detail reads go
    // through). Scope rule on the holder: only what an extracted surface
    // actually observes — today the detail cluster only.
    single {
        MediaRepositoryInternals(
            apiClient = get(),
            homeSession = get(),
        )
    }
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
            internals = get(),
        )
    }
    single<MediaRepository> { get<MediaRepositoryImpl>() }
    // Plan 08's module-internal cache-maintenance view (the former DataModule
    // bindMediaRepositoryCacheInvalidation @Binds): same single, narrow seam.
    single<MediaRepositoryCacheInvalidation> { get<MediaRepositoryImpl>() }
    // Worker port of the wholesale cache drop (same single, narrow seam — the
    // MediaRepositoryCacheInvalidation pattern): keeps the background sync
    // workers in legacy :core:data off the concrete MediaRepositoryImpl type.
    single<MediaCacheInvalidator> { get<MediaRepositoryImpl>() }
    // Family-repository split: SyncPlay stays a VIEW of the media single by
    // decision (its 11 members interleave with the user-data channel's
    // invalidation choreography). LiveTv / Newsletter / Playlist moved to
    // their own impls over the narrow API family clients (the
    // PlaybackRepositoryImpl ctor precedent — the family singles compose the
    // same impls the JellyfinApiClient union delegates to, so the wire
    // behavior is unchanged); single-family consumers keep injecting the
    // family type instead of the MediaRepository union.
    single<SyncPlayRepository> { get<MediaRepositoryImpl>() }
    single {
        LiveTvRepositoryImpl(
            liveTvApiClient = get(),
            // deleteRecording goes through the generic item delete — the
            // one route this family uses that MediaInfoApiClient owns.
            mediaInfoApiClient = get(),
        )
    }
    single<LiveTvRepository> { get<LiveTvRepositoryImpl>() }
    single { NewsletterRepositoryImpl(apiClient = get()) }
    single<NewsletterRepository> { get<NewsletterRepositoryImpl>() }
    single {
        PlaylistRepositoryImpl(
            libraryApiClient = get(),
            internals = get(),
        )
    }
    single<PlaylistRepository> { get<PlaylistRepositoryImpl>() }
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

    // Playback flips: PlaybackSourceResolverImpl moved from the legacy
    // core:data shim (Uri.fromFile → File.toURI, see the impl's URI-shape
    // note) — UnifiedMediaDetailProviderImpl's ctor dep below now resolves
    // from this module on BOTH platforms, and the app's HiltInterop reverse
    // single for the interface was deleted with the Hilt extinction.
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

    // Concrete class (no interface). Playback flips: its one former
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
            // Narrow family seams, not the JellyfinApiClient union (the
            // PlaybackRepositoryImpl ctor precedent): the manager only
            // touches SyncPlay group lifecycle/ping + Auth's capability
            // broadcast, and the family singles compose the same impls
            // JellyfinApiClientImpl does.
            syncPlayApiClient = get<SyncPlayApiClient>(),
            authApiClient = get<AuthApiClient>(),
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
}
