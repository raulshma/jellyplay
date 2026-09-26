package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.download.sniffContainerFile
import com.raulshma.jellyplay.core.data.network.OkHttpConfigProviderImpl
import com.raulshma.jellyplay.core.data.network.ServerHealthMonitor
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.remote.RemoteNavigationBridge
import com.raulshma.jellyplay.core.data.remote.UiRemoteControlDispatcher
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.data.streaming.BandwidthMonitor
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.database.migration.ContainerProbe
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The core/leaf half of the dataJvmModule family split (see [dataJvmModule]
 * for the construction-owner rules these definitions live under): the
 * preamble singles that own no repository — the migration glue, the network
 * config seam, and the standalone leaf helpers. Binding bodies moved
 * verbatim from the pre-split single-module layout.
 */
internal val dataCoreLeafModule: Module = module {
    // ContainerProbe for Migration53To54 (the v53→v54 one-time backfill of
    // legacy-NULL `downloads.container` rows): combines this module's
    // `sniffContainerFile` java.io glue with the pure byte-level
    // ContainerSniffer from commonMain. Registered HERE because
    // shared:core:database can see neither java.io in commonMain nor this
    // module (core:data is downstream — its repositories consume the
    // database DAOs); its platform database modules resolve the probe via
    // Koin get() when assembling the migration chain (TokenCipher seam,
    // inverted).
    single<ContainerProbe> { ContainerProbe(::sniffContainerFile) }

    // Relocated from the app composition root's appNetworkConfigModule (C4
    // part 2, DI-finalize): networkJvmModule's base OkHttpClient resolves
    // this via cross-module get() — same ctor wiring the app module had.
    single<OkHttpConfigProvider> {
        OkHttpConfigProviderImpl(
            networkOfflineStore = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single { BandwidthMonitor() }

    // BandwidthInterceptor resolves from :shared:core:network's networkJvmModule.
    single { AdaptiveBitrateSelector(get(), get()) }

    single { OrderHomeSectionsUseCase() }

    // V3 library conveyor: Koin owns construction; its consumers
    // (HomeViewModel, PhotoFolderChildUrlsStore) resolve this single
    // straight from Koin.
    single { PhotoFolderPrefetcher(get()) }

    // JellyfinApiClient resolves from :shared:core:network's networkJvmModule.
    single { ServerHealthMonitor(get(), get()) }

    single { RemoteNavigationBridge() }

    single { UiRemoteControlDispatcher() }

    // The active-engine registry moved to commonMain with the desktop
    // receiver port — both JVM shells bind the one shared single (the video
    // player's platform adapters wrap it; the remote-control dispatchers and
    // the receiver's screenshot/idle gates read it).
    single { com.raulshma.jellyplay.core.data.remote.ActivePlayerController() }

    // NotificationStore resolves from :shared:core:datastore's Koin modules;
    // the TimeSource single from dataSessionPlaybackModule.
    single { NewsletterTriggerManager(get(), get()) }
}
