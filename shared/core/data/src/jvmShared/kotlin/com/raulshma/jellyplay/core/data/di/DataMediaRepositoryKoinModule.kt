package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.playback.AudioLyricsManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolverImpl
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.LyricsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MediaCacheInvalidator
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.CollectionOperations
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.HomeFeed
import com.raulshma.jellyplay.core.data.repository.LibraryFeed
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryCacheInvalidation
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryInternals
import com.raulshma.jellyplay.core.data.repository.MediaCatalogueEnrichments
import com.raulshma.jellyplay.core.data.repository.MusicCatalogue
import com.raulshma.jellyplay.core.data.repository.PhotoFolderReads
import com.raulshma.jellyplay.core.data.repository.SeriesNotificationReads
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolverImpl
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlayedStateSyncImpl
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.RecentLocalUserDataWrites
import com.raulshma.jellyplay.core.data.repository.RecentLocalUserDataWritesImpl
import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
import com.raulshma.jellyplay.core.data.repository.SyncPlayRepositoryImpl
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.data.repository.UnifiedMediaDetailProviderImpl
import com.raulshma.jellyplay.core.data.repository.UserDataAnnouncer
import com.raulshma.jellyplay.core.data.repository.UserDataAnnouncerImpl
import com.raulshma.jellyplay.core.data.repository.UserDataChanges
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.repository.UserDataMutatorImpl
import com.raulshma.jellyplay.core.data.repository.UserDataWriteOperations
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
import com.raulshma.jellyplay.core.data.repository.HomeSectionsSnapshotStore
import com.raulshma.jellyplay.core.data.repository.MediaFreshnessLedger
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

    // The synthetic-push fan-out (see [UserDataAnnouncer]): the leaf single
    // that owns the shared flow the media repo's userDataChanges merges and
    // the local-write record the refresher's echo demotion consults. Its
    // leafness is the point — PlayedStateSyncImpl / PlaybackRepositoryImpl /
    // MediaRepositoryImpl all announce through it EAGERLY, which is what let
    // the former `lazy { get<MediaRepository>() }` cycle-breakers die.
    single {
        UserDataAnnouncerImpl(
            homeSession = get(),
            recentLocalWrites = get(),
            // The confirmed-write arming (#157) resolves the repository at
            // ANNOUNCE time — lazy inside the lambda, so the announcer's
            // construction never touches the repository's graph.
            onConfirmedWrite = { get<MediaRepositoryImpl>().armStaleReadGroupsForConfirmedWrite() },
        )
    }
    single<UserDataAnnouncer> { get<UserDataAnnouncerImpl>() }

    single {
        PlayedStateSyncImpl(
            // Reconcile's fresh-detail read rides the wide client; the flip /
            // toggleFavorite writes ride the UserDataApiClient family single
            // (the former LibraryApiClient.writeUserData forward is gone).
            apiClient = get(),
            userDataApiClient = get(),
            offlineRepository = get(),
            playbackOutboxRepository = get(),
            offlineModeManager = get(),
            // The confirmed-write announcements ride the announcer leaf —
            // no media-repository edge, no deferral (see the binding above).
            userDataAnnouncer = get(),
            // The download stack params stay Lazy (defensive, per the ctor
            // kdocs — those edges are already acyclic).
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
            // The detail cluster's four member caches each register their
            // identity action (epoch bump + wholesale clear) through their
            // SessionScopedCache chassis — the registry reaches the group
            // here, at construction.
            sessionCacheRegistry = get(),
        )
    }
    // The home-sections snapshot store: the deep owner of the PERSISTED half
    // of the home pipeline (the Room SWR snapshot — dedup-window persist
    // choreography, identity-scoped privacy clear, the two cold-open reads).
    // Declared BEFORE its one consumer below: MediaRepositoryImpl
    // ctor-injects this single (the EpisodeCatalogueImpl precedent), keeping
    // the construction edge acyclic and one-instance across the graph.
    single {
        HomeSectionsSnapshotStore(
            homeSectionCacheDao = get(),
            homeSession = get(),
            timeSource = get(),
        )
    }
    // The local-write log the user-data echo demotion consults — recorded
    // by MediaRepositoryImpl's local-notify path, consulted by the home
    // refresher's user-data chain. ONE single so an echo is matched against
    // the writes that caused it; declared before its two consumers.
    single { RecentLocalUserDataWritesImpl() }
    single<RecentLocalUserDataWrites> { get<RecentLocalUserDataWritesImpl>() }
    // The media freshness ledger: the ONE owner of the freshness/cache
    // chassis (spec registry, #157 stale-read ladder, home write-generation
    // funnel, identity-transition clear, eviction ladders) — extracted from
    // MediaRepositoryImpl. Declared BEFORE its one consumer below (the
    // HomeSectionsSnapshotStore precedent), so its registry registrations
    // happen at the same graph point the impl's construction used to run
    // them.
    single {
        MediaFreshnessLedger(
            homeSession = get(),
            sessionCacheRegistry = get(),
            homeSnapshotStore = get(),
            episodeCatalogue = get(),
            internals = get(),
            homeSectionsCachePort = get(),
            timeSource = get(),
        )
    }
    single {
        MediaRepositoryImpl(
            libraryApiClient = get(),
            collectionApiClient = get(),
            // The image-URL family single (the collectionApiClient precedent):
            // the photo-folder cover probe's child-image row read.
            imageUrlApiClient = get(),
            homeSectionsCachePort = get(),
            homeSnapshotStore = get(),
            playedStateSync = get(),
            userDataRealtimeChannel = get(),
            internals = get(),
            userDataAnnouncer = get(),
            ledger = get(),
        )
    }
    // Family seams over the same single (the SonarrSeriesOperations
    // over-the-impl pattern in dataSeerrArrModule): every family the former
    // MediaRepository union carried, aliased onto this one impl so
    // single-family consumers inject the seam alone and mixed consumers
    // compose the families they read (e.g. the detail provider's session
    // resolves DetailFeed + MusicCatalogue together).
    single<MusicCatalogue> { get<MediaRepositoryImpl>() }
    // The browse-catalogue enrichments (filter-sheet tags, the detail
    // franchise row) — same over-the-impl aliasing for their two screens.
    single<MediaCatalogueEnrichments> { get<MediaRepositoryImpl>() }
    // The series-notification family (the per-series Next Up diff read).
    single<SeriesNotificationReads> { get<MediaRepositoryImpl>() }
    single<UserDataWriteOperations> { get<MediaRepositoryImpl>() }
    // The photo-folder family (the cached child-image-URL read the prefetcher
    // fans out over): the SeriesNotificationReads one-member pattern, aliased
    // over the same impl single.
    single<PhotoFolderReads> { get<MediaRepositoryImpl>() }
    // The detail-read family (the detail fetch + the similar-items row) and
    // the collection-operations family (the collection page read + the two
    // collection edits): the same over-the-impl aliasing, so a detail-read-
    // only or collections-only consumer injects the seam alone.
    single<DetailFeed> { get<MediaRepositoryImpl>() }
    single<CollectionOperations> { get<MediaRepositoryImpl>() }
    // The home-feed family (the sections payload, the single-row edge-pull
    // refetch, the SWR snapshot reads, the discover-row verbs) and the
    // user-data change feed: the same over-the-impl aliasing, so the home
    // feature's refresh stack injects the seam alone and the background
    // refetchers take it beside the surfaces they are mixed consumers of.
    single<HomeFeed> { get<MediaRepositoryImpl>() }
    single<UserDataChanges> { get<MediaRepositoryImpl>() }
    // The library/browse family (folders, latest, paged browse, search,
    // genres, studios): the same over-the-impl aliasing, so browse-only
    // consumers (library screens, search, the music collection hosts, photo
    // albums, the settings library-views) inject the seam alone.
    single<LibraryFeed> { get<MediaRepositoryImpl>() }
    // Module-internal cache-maintenance view (the former DataModule
    // bindMediaRepositoryCacheInvalidation @Binds): same single, narrow seam.
    single<MediaRepositoryCacheInvalidation> { get<MediaRepositoryImpl>() }
    // Worker port of the wholesale cache drop (same single, narrow seam — the
    // MediaRepositoryCacheInvalidation pattern): keeps the background sync
    // workers in legacy :core:data off the concrete MediaRepositoryImpl type.
    single<MediaCacheInvalidator> { get<MediaRepositoryImpl>() }
    // Family-repository split: the SyncPlay family is 4 stateless members on
    // its own impl (contract: see SyncPlayRepositoryImpl). Single-family
    // consumers (SyncPlayViewModel, WatchPartyActions) inject the family
    // type, not a wide media surface.
    single {
        SyncPlayRepositoryImpl(
            syncPlayApiClient = get(),
            syncPlayManager = get(),
        )
    }
    single<SyncPlayRepository> { get<SyncPlayRepositoryImpl>() }
    // The newsletter family seam of the MediaInfoApiClient single (the
    // one-impl-many-seams idiom the network module uses for the other client
    // families): the newsletter feature's view model injects the family type.
    single<com.raulshma.jellyplay.core.network.api.NewsletterApiClient> {
        get<com.raulshma.jellyplay.core.network.api.MediaInfoApiClient>()
    }
    single {
        PlaylistRepositoryImpl(
            playlistApiClient = get(),
            internals = get(),
        )
    }
    single<PlaylistRepository> { get<PlaylistRepositoryImpl>() }
    // Lyrics engine: its own impl (the LRC/LRCLIB fetch-parse-cache chain),
    // independent of the media single.
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
            // Both eager now: with the media-repository edge out of
            // PlayedStateSyncImpl / PlaybackRepositoryImpl (the
            // UserDataAnnouncer leaf above), neither the write family's impl
            // nor the detail provider's graph cycles back into this one.
            // The write dep is the family seam, not the union — the mutator
            // is the user-data-write family's clean sole consumer.
            userDataWrites = get(),
            mediaDetailProvider = get(),
        )
    }
    single<UserDataMutator> { get<UserDataMutatorImpl>() }

    single {
        MediaSearchEngineImpl(
            libraryFeed = get(),
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
            detailFeed = get(),
            playbackRepository = get(),
            offlineRepository = get(),
            offlinePlaybackFacade = get(),
        )
    }
    single<PlaybackSourceResolver> { get<PlaybackSourceResolverImpl>() }

    single {
        UnifiedMediaDetailProviderImpl(
            detailFeed = get(),
            musicCatalogue = get(),
            cacheInvalidation = get(),
            offlineRepository = get(),
            downloadRepository = get(),
            episodeCatalogue = get(),
            playbackSourceResolver = get(),
            offlineModeManager = get(),
            localStreamProbe = get(),
            appScope = get(DatastoreQualifiers.applicationScope),
            timeSource = get<TimeSource>(),
        )
    }
    single<MediaDetailProvider> { get<UnifiedMediaDetailProviderImpl>() }

    single {
        OfflineFirstItemResolverImpl(
            offlineRepository = get(),
            detailFeed = get(),
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

    single { TimeSyncManager(get(), applicationScope = get(DatastoreQualifiers.applicationScope)) }
    single { SyncPlayController(get()) }
    single { SyncPlayEventHandler() }
    single { SyncPlayQueueCore(get()) }
    single {
        SyncPlayPlaybackCore(
            timeSyncManager = get(),
            controller = get(),
            syncPlayCastStore = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single {
        SyncPlayManager(
            // Narrow family seams (the PlaybackRepositoryImpl ctor
            // precedent): the manager only touches SyncPlay group
            // lifecycle/ping + Auth's capability broadcast.
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
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        OkHttpDownloadTransferClient(get(NetworkQualifiers.downloadHttpClient))
    }
    single<DownloadTransferClient> { get<OkHttpDownloadTransferClient>() }
}
