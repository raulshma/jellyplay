package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MoodPlaylistRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepositoryImpl
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SmartPlaylistRepository
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
 * SmartPlaylistRepository, MoodPlaylistRepository, QueuePersistenceHelper)
 * are wired here over the OPFS Room database — `webDatabaseModule()` (also
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
 * graph grows with each wave.
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
