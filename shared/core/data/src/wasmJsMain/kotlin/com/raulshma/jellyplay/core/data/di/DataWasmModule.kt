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
 * OPEN DEPENDENCY (15A follow-up): the wasm graph needs
 * `SeerrApiClient`/`TmdbApiClient`/`RadarrApiClient`/`SonarrApiClient`
 * registered somewhere reachable from the web shell's startKoin — today
 * network's wasm module registers only the auth/library/playback/user
 * clients; the Seerr/TMDB/Radarr/Sonarr client impls still live in network's
 * jvmShared. Until that lands, `requestsModule`'s ViewModels cannot resolve
 * at RUNTIME on web (everything here COMPILES and the wiring shape is final).
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
