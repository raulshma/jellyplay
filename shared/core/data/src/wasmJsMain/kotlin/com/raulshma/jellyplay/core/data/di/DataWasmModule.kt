package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.download.ActiveDownloadCount
import com.raulshma.jellyplay.core.data.download.DownloadQueue
import com.raulshma.jellyplay.core.data.download.OfflineResync
import com.raulshma.jellyplay.core.data.download.QuickDownloadActions
import com.raulshma.jellyplay.core.data.download.SeriesEpisodeDownloads
import com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow
import com.raulshma.jellyplay.core.data.download.WasmActiveDownloadCount
import com.raulshma.jellyplay.core.data.download.WasmDownloadQueue
import com.raulshma.jellyplay.core.data.download.WasmOfflineResync
import com.raulshma.jellyplay.core.data.download.WasmQuickDownloadActions
import com.raulshma.jellyplay.core.data.download.WasmSeriesEpisodeDownloads
import com.raulshma.jellyplay.core.data.download.WasmTrackDownloadStatusWindow
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MoodPlaylistRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepositoryImpl
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SmartPlaylistRepository
import com.raulshma.jellyplay.core.data.repository.WasmAuthRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionIdentityProvider
import com.raulshma.jellyplay.core.data.session.WasmSessionIdentityProvider
import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.model.wallNowMillis
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * the wasmJs slice of the data graph — exactly the repos the
 * requests feature consumes, wired the same way `DataKoinModule` wires them
 * on the JVM:
 *  - [SessionIdentityProvider] is the [WasmSessionIdentityProvider] over the
 *    ONE shared [AtomicSessionState] that `networkWasmModule` owns (do NOT
 *    construct a second one here — the wasm auth/library/playback clients
 *    publish through that instance).
 *  - [SessionCacheRegistry], [SeerrRepository] and [ArrRepository] are the
 *    promoted commonMain singletons; ctor deps resolve from
 *    `datastoreCommonModule` (stores + application scope) and
 *    `networkWasmModule`.
 *
 * DEPENDENCY CLOSURE (update — the follow-up this module's first
 * revision documented is DONE): the wasm clients this module's repos need —
 * `SeerrApiClient`/`TmdbApiClient`/`RadarrApiClient`/`SonarrApiClient` — are
 * registered by `networkWasmModule` (15A's KtorWasm* client bindings), and
 * the web shell's startKoin (apps/web Main.kt) lists BOTH modules plus
 * `requestsModule`, so the requests ViewModels resolve end-to-end at
 * runtime on web (browser-verified by tools/e2e/web-verify.mjs). The
 * credentials-UI cut this note used to carry is closed by: the
 * shell's Seerr pane (WebSeerrPane) saves and persists the API key
 * (localStorage carve-out — the other web credential stores stay
 * session-memory only). Still true: session-cookie Seerr auth is
 * browser-impossible (forbidden `Cookie` header), so only API-key creds
 * can ever function on web.
 *
 * ROOM-ON-WEB SLICE: the promoted commonMain repository impls
 * (SearchHistoryRepositoryImpl, ItemPlaybackPreferenceRepositoryImpl,
 * SeenMediaRepositoryImpl, PlaybackOutboxRepositoryImpl,
 * SmartPlaylistRepository, MoodPlaylistRepository, QueuePersistenceHelper,
 * ReaderAnnotationsRepositoryImpl) are wired here over the OPFS Room
 * database — `webDatabaseModule()` (also
 * in the web startKoin list) provides the JellyPlayDatabase single and
 * `databaseDaosModule` (database commonMain since, also in the web
 * startKoin list) the DAO bindings. The clock seam is [EpochMillisSource]
 * over core:model's commonMain `wallNowMillis()` (the same
 * System.currentTimeMillis read dataJvmModule's SystemTimeSource binding
 * performs on android/desktop), and `Json` is bound here with the same
 * config networkJvmModule uses on the JVM (networkWasmModule binds no Json,
 * so the promoted playlist repositories resolve it from this module).
 * Nothing else on web resolves these yet — the bindings make the Room-backed
 * graph available to the web modules; unresolved-consumer parity with the JVM
 * graph grows with each module.
 *
 * DOWNLOAD-ACTIONS SEAMS: the feature-facing download reads the features
 * consume (QuickDownloadActions, TrackDownloadStatusWindow,
 * ActiveDownloadCount, SeriesEpisodeDownloads, DownloadQueue,
 * OfflineResync) are declared in core:data commonMain, implemented AND
 * bound by core:data on both platforms — the honest web no-op stubs here
 * (WasmQuickDownloadActions & co., see their KDocs), the real JVM actuals
 * bound in dataJvmModule directly over the engine singles
 * (DownloadRepositoryImpl / OfflineSyncManager / MediaDownloadActions —
 * no adapter files since the promoted-interface pass). The feature platform
 * fragments that used to bind these on web (search/library's
 * platformSearchModule/platformLibraryModule) were deleted with the seam
 * consolidation — features never grow their own wall-crossing template.
 *
 * AUTH SLICE: [WasmAuthRepository] — the web shell's [AuthRepository]
 * binding, a faithful port of the jvmShared AuthRepositoryImpl establishment
 * choreography over the same OPFS Room database + ServerIdentityStore the
 * android/desktop graph uses (every declared divergence lives on the class's
 * KDoc; the ONE structural rule: it implements AuthRepository only — no
 * RealtimeConnection, the websocket client stays jvmShared). Its api-client
 * deps resolve from `networkWasmModule` (the AuthApiClient + UserApiClient
 * Ktor singles share one AtomicSessionState), the TokenCipher from
 * `webDatabaseModule` (the web pass-through), and the clock from the
 * EpochMillisSource binding below. The web landing flow
 * (apps/web WebConnectController) drives this repository instead of
 * hand-mirroring the choreography over the raw client.
 */
val dataWasmModule: Module = module {

    single {
        WasmSessionIdentityProvider(
            sessionState = get(),
            collectorScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single<SessionIdentityProvider> { get<WasmSessionIdentityProvider>() }

    single {
        SessionCacheRegistry(
            sessionIdentity = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }

    // ── auth slice (the web shell's session seam) ──────────────────────
    // The establishment choreography port — see WasmAuthRepository's KDoc
    // for the declared divergences vs the jvmShared AuthRepositoryImpl. The
    // two API-client singles (AuthApiClient for connect/authenticate/
    // quick-connect/revocation, UserApiClient for the getCurrentUser token
    // check behind restore/refresh) both publish into the ONE shared
    // AtomicSessionState networkWasmModule owns. DAOs/database resolve from
    // databaseDaosModule/webDatabaseModule, ServerIdentityStore from
    // datastoreCommonModule over the web "user_prefs" store, TokenCipher
    // from webDatabaseModule (the pass-through), Json + EpochMillisSource
    // from this module's bindings below.
    single {
        WasmAuthRepository(
            apiClient = get(),
            userApiClient = get(),
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
    single<AuthRepository> { get<WasmAuthRepository>() }

    // ──: the commonMain-promoted Room-backed slice ─────────────────────

    // Same Json config as networkJvmModule's `single { Json { ignoreUnknownKeys = true } }`.
    // INVARIANT: exactly ONE Json binding per web graph — Koin permits silent
    // override (last registration wins, log-only), so a second module binding
    // Json would drift config invisibly. Qualify by name when a competitor
    // binding ever appears.
    single { Json { ignoreUnknownKeys = true } }

    // The epoch-millis clock seam the promoted impls take; on android/desktop
    // dataJvmModule binds it to the SystemTimeSource single — same read here
    // over the core:model platform seam.
    single<EpochMillisSource> { EpochMillisSource { wallNowMillis() } }

    single {
        SearchHistoryRepositoryImpl(
            dao = get(),
            timeSource = get(),
        )
    }
    single<SearchHistoryRepository> { get<SearchHistoryRepositoryImpl>() }

    single {
        ItemPlaybackPreferenceRepositoryImpl(
            dao = get(),
            database = get(),
            timeSource = get(),
        )
    }
    single<ItemPlaybackPreferenceRepository> { get<ItemPlaybackPreferenceRepositoryImpl>() }

    single { SeenMediaRepositoryImpl(seenMediaDao = get()) }
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

    single {
        PlaybackOutboxRepositoryImpl(
            dao = get(),
            timeSource = get(),
        )
    }
    single<PlaybackOutboxRepository> { get<PlaybackOutboxRepositoryImpl>() }

    single {
        SmartPlaylistRepository(
            smartPlaylistDao = get(),
            json = get(),
        )
    }

    single {
        MoodPlaylistRepository(
            moodPlaylistDao = get(),
            json = get(),
            timeSource = get(),
        )
    }

    single { QueuePersistenceHelper(audioQueueDao = get()) }

    // ── download-actions seams (web no-op actuals) ───────────────────────
    // The honest web stubs for the feature-facing download reads — the same
    // bindings the deleted search/library platform fragments used to make on
    // web, plus the window/count/series-episode/queue/resync reads the
    // player, music, home and downloads features resolve. No download
    // pipeline exists on web, so every actual is a no-op gated by
    // isSupported = false (see each stub's KDoc). Since the promoted-
    // interface pass the interfaces live in core:data commonMain and the JVM
    // actuals are the engine singles themselves (dataJvmModule binds them
    // over DownloadRepositoryImpl / OfflineSyncManager / MediaDownloadActions
    // — no adapter files).

    single<QuickDownloadActions> { WasmQuickDownloadActions }
    single<TrackDownloadStatusWindow> { WasmTrackDownloadStatusWindow }
    single<ActiveDownloadCount> { WasmActiveDownloadCount }
    single<SeriesEpisodeDownloads> { WasmSeriesEpisodeDownloads }
    single<DownloadQueue> { WasmDownloadQueue }
    single<OfflineResync> { WasmOfflineResync }

    // ── requests slice (pre-) ───────────────────────────────────────────

    single {
        SeerrRepositoryImpl(
            seerrApiClient = get(),
            tmdbApiClient = get(),
            seerrPreferencesStore = get(),
            secureCredentialsStore = get(),
            sessionIdentity = get(),
            sessionCacheRegistry = get(),
            cacheScope = get(DatastoreQualifiers.applicationScope),
            // offlineModeManager stays at its ctor default (null): the wasm
            // graph binds no OfflineModeManager, so the poll loop's offline
            // skip is JVM-only — web leans on browser background-timer
            // throttling instead (see the ctor param's kdoc).
        )
    }
    single<SeerrRepository> { get<SeerrRepositoryImpl>() }

    // SeerrRequestDelegate moved to commonMain (zero JVM imports —
    // pure kotlinx.coroutines + core:model), so the web graph can serve the
    // SeerrDetailViewModel ctor the same way DataKoinModule does on the JVM
    // (single { SeerrRequestDelegate(get()) } over the SeerrRepository above).
    single { SeerrRequestDelegate(get()) }

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
