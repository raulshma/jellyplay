package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository
import com.raulshma.jellyplay.core.data.repository.MetadataEditorRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MoodPlaylistRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SelfSignedTrustRepository
import com.raulshma.jellyplay.core.data.repository.SelfSignedTrustRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepository
import com.raulshma.jellyplay.core.data.repository.ServerDiscoveryRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SmartPlaylistRepository
import com.raulshma.jellyplay.core.data.repository.StoragePolicy
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.session.PlaybackReportingStatusStore
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The repository-layer family of the dataJvmModule split (C4 part 2, batch
 * 2 — see [dataJvmModule] for the construction-owner rules): the
 * early-moved single-purpose repositories plus the StoragePolicy byte-cap
 * rule. Binding bodies moved verbatim from the pre-split single-module
 * layout.
 */
internal val dataRepositoriesModule: Module = module {
    // ── Repository layer (C4 part 2, batch 2) ─────────────────────────────
    // DAOs resolve from :shared:core:database's databaseDaosModule, stores from
    // shared:core:datastore's modules, API clients from networkJvmModule.
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

    // Auth-cluster narrow seam (the AuthRepositorySurfaceTest ratchet's named
    // escape hatch for a genuinely new auth capability): the self-signed
    // trust DECISION the Server Management screen renders, delegating to
    // core:network's matcher behind this module boundary. Stateless — the
    // granted set arrives per call — so the impl single takes no deps.
    single { SelfSignedTrustRepositoryImpl() }
    single<SelfSignedTrustRepository> { get<SelfSignedTrustRepositoryImpl>() }

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

    single {
        ReaderAnnotationsRepositoryImpl(
            bookmarkDao = get(),
            annotationDao = get(),
            timeSource = get(),
        )
    }
    single<ReaderAnnotationsRepository> { get<ReaderAnnotationsRepositoryImpl>() }

    single { BookTocCacheRepositoryImpl(dao = get(), timeSource = get()) }
    single<BookTocCacheRepository> { get<BookTocCacheRepositoryImpl>() }

    // ONE owner of the Playback Reporting plugin status — the admin
    // statistics half and the watch-history (insights heatmap) half used to
    // each hold their own StateFlow + refresh of the same server check (two
    // independently stale answers to one question). Both repositories now
    // inject this single. Registered with SessionCacheRegistry inside the
    // store (owner "playback-reporting-status") so identity transitions
    // reset it. Declared BEFORE both consumer singles below.
    single { PlaybackReportingStatusStore(get(), get()) }

    single { WatchHistoryRepositoryImpl(get(), get(), get()) }
    single<WatchHistoryRepository> { get<WatchHistoryRepositoryImpl>() }

    single { OfflineRepositoryImpl(get(), get(), get(), get(), get(), timeSource = get()) }
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
}
