package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepositoryImpl
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The seerr/arr family of the dataJvmModule split — the discovery-stack half
 * of the former mixed download-actions section (see [dataJvmModule] for the
 * construction-owner rules). Binding bodies moved verbatim from the
 * pre-split single-module layout.
 */
internal val dataSeerrArrModule: Module = module {
    // ── seerr / arr family ───────────────────────────────────────────────
    single {
        SeerrRepositoryImpl(
            seerrApiClient = get(),
            tmdbApiClient = get(),
            seerrPreferencesStore = get(),
            secureCredentialsStore = get(),
            sessionIdentity = get(),
            sessionCacheRegistry = get(),
            cacheScope = get(DatastoreQualifiers.applicationScope),
            // The poll loop's offline gate — the same platform
            // OfflineModeManager binding every other jvmShared consumer
            // (PlaybackRepositoryImpl, OfflineSyncManager, …) resolves.
            offlineModeManager = get(),
        )
    }
    single<SeerrRepository> { get<SeerrRepositoryImpl>() }

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
