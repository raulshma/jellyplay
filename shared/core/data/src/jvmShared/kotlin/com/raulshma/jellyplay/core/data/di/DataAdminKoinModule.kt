package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AdminRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsLabelProvider
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PluginAdminRepository
import com.raulshma.jellyplay.core.data.repository.PluginAdminRepositoryImpl
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The admin-flip family of the dataJvmModule split (see [dataJvmModule] for
 * the construction-owner rules): AdminRepositoryImpl,
 * AdminStatisticsRepositoryImpl and the plugin facade split. Binding bodies
 * moved verbatim from the pre-split single-module layout.
 */
internal val dataAdminModule: Module = module {
    // ──  admin flip ──────────────────────────────────────
    // AdminRepositoryImpl + AdminStatisticsRepositoryImpl moved from the
    // legacy :core:data shim (Hilt @Binds -> koin().get() bridges there, the
    // app's Hilt interop singles deleted). Every ctor dep resolves natively
    // here: the API client + engine + the two realtime channels from
    // networkJvmModule, the DAOs from databaseDaosModule, Json from
    // networkJvmModule, the application scope from DatastoreQualifiers, and
    // the label seam from the platform data modules (Android: the app's
    // androidAdminSeamsModule over legacy core:data R.string, desktop:
    // English literals in desktopDataModule).

    single {
        AdminRepositoryImpl(
            apiClient = get(),
            engine = get(),
            realtimeTasks = get(),
            activityLogRealtimeChannel = get(),
        )
    }
    single<AdminRepository> { get<AdminRepositoryImpl>() }
    // Admin facade split (the LiveTvRepositoryImpl pattern): the plugin
    // family's own single over the PluginApiClient family client, with the
    // WebView bridge session read through narrow seams — the engine's
    // failover-correct active address + atomic session value, and the
    // constructor-injected unqualified OkHttpClient single (the same client
    // the engine exposes lazily; the DlnaCastStrategy wiring precedent).
    // Single-family plugin consumers inject this seam, not the admin union.
    single {
        PluginAdminRepositoryImpl(
            pluginApiClient = get(),
            activeServerAddress = { get<JellyfinApiEngine>().activeServerAddress },
            session = { get<JellyfinApiEngine>().session.value },
            okHttpClient = get<OkHttpClient>(),
        )
    }
    single<PluginAdminRepository> { get<PluginAdminRepositoryImpl>() }

    single {
        AdminStatisticsRepositoryImpl(
            apiClient = get(),
            auditLogDao = get(),
            scanStateDao = get(),
            json = get(),
            scope = get(DatastoreQualifiers.applicationScope),
            labels = get<AdminStatisticsLabelProvider>(),
            timeSource = get(),
            playbackReportingStatusStore = get(),
        )
    }
    single<AdminStatisticsRepository> { get<AdminStatisticsRepositoryImpl>() }
}
