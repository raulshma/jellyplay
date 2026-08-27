package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepositoryImpl
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.session.SessionIdentityProvider
import com.raulshma.jellyplay.core.data.session.WasmSessionIdentityProvider
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wave 15B: the wasmJs slice of the data graph — exactly the repos the
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
 * DEPENDENCY CLOSURE (wave 15C update — the follow-up this module's first
 * revision documented is DONE): the wasm clients this module's repos need —
 * `SeerrApiClient`/`TmdbApiClient`/`RadarrApiClient`/`SonarrApiClient` — are
 * registered by `networkWasmModule` (15A's KtorWasm* client bindings), and
 * the web shell's startKoin (apps/web Main.kt) lists BOTH modules plus
 * `requestsModule`, so the requests ViewModels resolve end-to-end at
 * runtime on web (browser-verified by tools/e2e/web-verify.mjs, which also
 * documents the remaining honesty cut: no Seerr credentials UI on web, and
 * session-cookie auth is browser-impossible — only API-key creds can ever
 * function there).
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
