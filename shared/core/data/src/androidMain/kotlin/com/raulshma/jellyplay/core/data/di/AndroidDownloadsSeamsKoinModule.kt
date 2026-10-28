package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.AndroidSuggestionNotifier
import com.raulshma.jellyplay.core.data.repository.CoilOfflineImagePreloader
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueuer
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueueCoordinator
import com.raulshma.jellyplay.core.data.repository.DownloadProgressNotifier
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayout
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayoutContract
import com.raulshma.jellyplay.core.data.repository.DownloadSummaryRefresher
import com.raulshma.jellyplay.core.data.repository.OfflineImagePreloader
import com.raulshma.jellyplay.core.data.worker.AutoDownloadSuggestionNotifier
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * V3 downloads conveyor family of the androidCoreDataModule split: the
 * Android actuals of the portable download engine's seams, bound beside the
 * impls that live in this module's androidMain — the WorkManager enqueuer
 * (the [DownloadEnqueueCoordinator] actual), the Context/StatFs storage
 * layout, the notification-summary refresher, the gh-181 suggestion
 * notifier and the Coil image preloader (AndroidDownloadSeamAdapters.kt).
 * Desktop mirror: DesktopDownloadsSeamsKoinModule (jvmMain).
 *
 * This replaces the app composition root's AndroidDownloadSeamsModule, whose
 * KDoc still claimed the impls were a legacy :core:data shim the shared
 * module cannot see — they migrated into androidMain with the V3 conveyor,
 * so the seam + adapters + bindings now read in one place here. The
 * consumers are unchanged: DownloadRepositoryImpl (dataDownloadsConveyorModule)
 * resolves the coordinator/storage/notifier seams, and the cold-start
 * recovery single (DownloadRecoveryPort) reaches the [DownloadEnqueuer]
 * actual through the coordinator seam. Hilt constructs none of these.
 */
internal fun androidDownloadsSeamsModule(context: Context): Module = module {
    // ── V3 downloads conveyor: Android actuals of the engine seams ──────

    single { DownloadEnqueuer(context, get()) }
    single<DownloadEnqueueCoordinator> { get<DownloadEnqueuer>() }

    single { DownloadStorageLayout(context) }
    single<DownloadStorageLayoutContract> { get<DownloadStorageLayout>() }

    single<DownloadProgressNotifier> { DownloadSummaryRefresher(context) }

    single<AutoDownloadSuggestionNotifier> { AndroidSuggestionNotifier(context) }

    single<OfflineImagePreloader> { CoilOfflineImagePreloader(context) }
}
