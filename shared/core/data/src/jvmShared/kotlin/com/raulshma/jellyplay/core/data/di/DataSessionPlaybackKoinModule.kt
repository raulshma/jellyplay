package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.AudioCachePolicyGuard
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.SleepCountdownClock
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionIdentityProvider
import com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.model.EpochMillisSource
import com.raulshma.jellyplay.core.model.SystemTimeSource
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import kotlinx.coroutines.flow.first
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The session / playback / sync / syncplay / worker family of the
 * dataJvmModule split (C4 part 2, batch 3 — see [dataJvmModule] for the
 * construction-owner rules): the session-identity spine, the portable
 * playback helpers, and the offline-sync manager. Binding bodies moved
 * verbatim from the pre-split single-module layout.
 */
internal val dataSessionPlaybackModule: Module = module {
    // ── C4 part 2, batch 3: session / playback / sync / syncplay / worker ──
    // Constructors mirrored verbatim from the moved impls. The types whose
    // ctor deps live in the Android-only legacy core:data remainder
    // (AudioPlaybackManager + the media3 audio graph, owned by
    // androidCoreDataModule there) are deliberately NOT defined here:
    // DefaultAudioQueueFacade only. OfflineSyncManager, AudioLyricsManager and
    // (playback flips) PlaybackSourceResolverImpl all moved off that
    // list as their ctor deps became Koin-resolvable.

    // D3: the seam types moved to :shared:core:model (so core:network below
    // this module can adopt them); this module stays their Koin owner —
    // core:model deliberately carries no Koin module.
    single<TimeSource> { SystemTimeSource() }

    // the commonMain-promoted repository impls (SearchHistory /
    // ItemPlaybackPreference / PlaybackOutbox / MoodPlaylist) take the
    // commonMain EpochMillisSource clock seam — bind it to the SAME
    // SystemTimeSource single above (one framework per clock; the fakes in
    // jvmTest satisfy the seam through the TimeSource supertype).
    single<EpochMillisSource> { get<TimeSource>() }

    single { HomeSession(get(), get(DatastoreQualifiers.applicationScope)) }

    // The identity seam the promoted commonMain graph consumes
    // (SeerrRepositoryImpl's cache keys + SessionCacheRegistry's transition
    // subscription). Binds the SAME HomeSession singleton — android/desktop
    // behavior unchanged.
    single<SessionIdentityProvider> { get<HomeSession>() }

    single {
        SessionCacheRegistry(
            sessionIdentity = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        // The show-missing-episodes preference read feeds the catalogue's
        // online `isMissing` episodes filter (hide is the default; offline
        // snapshots never consult it).
        val libraryStore: LibraryStore = get()
        EpisodeCatalogueImpl(
            libraryApiClient = get(),
            offlineRepository = get(),
            homeSession = get(),
            sessionCacheRegistry = get(),
            showMissingEpisodes = { libraryStore.library.first().showMissingEpisodes },
        )
    }
    single<EpisodeCatalogue> { get<EpisodeCatalogueImpl>() }

    single { DownloadConcurrencyLimiter() }

    single { PlayerLifecycleManager(get()) }

    single { QueuePersistenceHelper(get()) }

    // Playback flips: the sleep-timer countdown (then SleepTimerManager) moved
    // from the legacy :core:data shim — SystemClock.elapsedRealtime became the
    // TimeSource seam above, and the sleep-timer fold later collapsed that
    // manager into the commonMain [SleepCountdown] core (the alias
    // AudioSleepTimerManager interface died with it: every host — video VM,
    // audio VM, both queue managers, the reader — resolves THIS single, whose
    // clock binds the same TimeSource single, so the wall-clock timing model
    // is unchanged). The audio/live player VMs resolve this single through
    // Koin directly since their migrations; legacy core:data's
    // AudioPlaybackManager resolves this single from androidCoreDataModule.
    single {
        SleepCountdown(
            clock = SleepCountdownClock { get<TimeSource>().nowElapsedRealtimeMillis() },
        )
    }

    // Playback flips: AdaptiveBitrateManager moved from the legacy
    // core:data shim — ConnectivityManager became the NetworkMonitor seam
    // (null-network/metered parity documented on the class). Its consumers
    // (feature:details DownloadLifecycleActions, feature:player:video
    // PlayerCastController / PlaybackSession / PlayerSessionManager /
    // VideoPlayerViewModel) resolve this single from Koin directly.
    single { AdaptiveBitrateManager(get(), get(), get()) }

    // V3 livetv conveyor: the mini-player holder moved from the legacy
    // core:data shim (one framework per type — @Singleton/@Inject stripped at
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
    // Hilt→Koin→Hilt edge (interop MediaRepository) died with the
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
}
