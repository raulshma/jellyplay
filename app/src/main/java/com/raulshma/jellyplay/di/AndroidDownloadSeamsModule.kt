package com.raulshma.jellyplay.di

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueuer
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueueCoordinator
import com.raulshma.jellyplay.core.data.repository.DownloadProgressNotifier
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayout
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayoutContract
import com.raulshma.jellyplay.core.data.repository.DownloadSummaryRefresher
import com.raulshma.jellyplay.core.data.repository.CoilOfflineImagePreloader
import com.raulshma.jellyplay.core.data.repository.OfflineImagePreloader
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-authored Koin definitions for the Android actuals of the portable
 * download engine's seams (V3 downloads conveyor — the C4 OkHttpConfigProvider
 * precedent: legacy-side impls that the shared module cannot see get their Koin
 * defs in the composition root that sees both).
 *
 * DownloadRepositoryImpl moved to :shared:core:data jvmShared and Koin
 * (dataJvmModule) owns it; its platform surfaces resolve through these defs:
 *  - [DownloadEnqueuer] (stays in the legacy :core:data shim) is the WorkManager
 *    actual of [DownloadEnqueueCoordinator];
 *  - [DownloadStorageLayout] (also staying-legacy) is the Context/StatFs actual
 *    of [DownloadStorageLayoutContract];
 *  - [DownloadSummaryRefresher] / [CoilOfflineImagePreloader] (legacy shim)
 *    adapt the notification-summary and image-cache seams.
 *
 * Hilt no longer constructs any of these — the legacy DataModule bridges its
 * remaining injectors (DownloadRecoveryInitializer, StorageSettingsViewModel,
 * DownloadIntakeImpl) to these singles via koin().get().
 */
fun androidDownloadSeamsModule(context: Context): Module = module {
    single { DownloadEnqueuer(context, get()) }
    single<DownloadEnqueueCoordinator> { get<DownloadEnqueuer>() }

    single { DownloadStorageLayout(context) }
    single<DownloadStorageLayoutContract> { get<DownloadStorageLayout>() }

    single<DownloadProgressNotifier> { DownloadSummaryRefresher(context) }

    single<OfflineImagePreloader> { CoilOfflineImagePreloader(context) }
}
