package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.ArrReleaseOperations
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeerrAuthenticator
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeerrRequestLifecycle
import com.raulshma.jellyplay.core.data.repository.SeerrServiceDirectory
import com.raulshma.jellyplay.core.data.repository.SonarrSeriesOperations
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
            // The bridge arm's per-feature gate (probe AND the user's
            // `seerr-bridge` toggle — JellyPlayFeatureGate).
            featureGate = get(),
        )
    }
    single<SeerrRepository> { get<SeerrRepositoryImpl>() }
    // Family seams over the same impl single (the SonarrSeriesOperations
    // over-the-impl pattern): the *arr service-discovery family (consumed by
    // ArrRepositoryImpl below — its only pair of calls into Seerr), the
    // request-lifecycle family (the requests feature's moderation commands;
    // still mixed with badge/read members there, so nothing injects it YET —
    // the seam is the declared migration target), and the auth family (the
    // Seerr settings screen's connection probe, its clean sole consumer).
    single<SeerrServiceDirectory> { get<SeerrRepositoryImpl>() }
    single<SeerrRequestLifecycle> { get<SeerrRepositoryImpl>() }
    single<SeerrAuthenticator> { get<SeerrRepositoryImpl>() }

    single { SeerrRequestDelegate(get()) }

    single {
        ArrRepositoryImpl(
            radarrApiClient = get(),
            sonarrApiClient = get(),
            seerrServiceDirectory = get(),
            arrPreferencesStore = get(),
            cacheScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single<ArrRepository> { get<ArrRepositoryImpl>() }
    // The Manage-Series screen's Sonarr series-management seam — the same
    // ArrRepositoryImpl single as the aggregate above implements it (the
    // family moved off ArrRepository because this one feature is its only
    // consumer), so the binding follows the exact over-the-impl pattern.
    single<SonarrSeriesOperations> { get<ArrRepositoryImpl>() }
    // The release sheet's search & grab seam — the same one-consumer
    // over-the-impl pattern (the arrqueue release sheet is its only consumer,
    // and the aggregate's surface ratchet pins ArrRepository's member count).
    single<ArrReleaseOperations> { get<ArrRepositoryImpl>() }
}
