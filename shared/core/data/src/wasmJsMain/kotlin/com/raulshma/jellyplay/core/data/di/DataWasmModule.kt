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
 *    `networkWasmModule` — which (since wave 15A) registers all four
 *    Seerr/TMDB/Radarr/Sonarr wasm clients. The requests graph resolves at
 *    RUNTIME on web once apps/web's startKoin includes `networkWasmModule +
 *    datastoreCommonModule + webDatastoreModule + dataWasmModule +
 *    requestsModule` (wave 15C's wiring).
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
