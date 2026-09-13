package com.raulshma.jellyplay.feature.downloads.di

import com.raulshma.jellyplay.feature.downloads.DownloadsViewModel
import com.raulshma.jellyplay.feature.downloads.OfflineLibraryViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin construction owner for the Downloads feature (docs/kmp-migration-plan.md
 *,  fifth conveyor item after search, library, music and livetv). The
 * HiltViewModel/@Inject annotations were stripped at the move — Koin is the
 * single constructor owner (one framework per type). Ctor deps split three
 * ways:
 *  - DownloadQueue / OfflineResync resolve from
 *    [platformDownloadsModule]: the jvmShared fragment delegates to the
 *    process-wide DownloadRepository / OfflineSyncManager singles (the
 *    repository is still Hilt-owned in the legacy data shim,
 *    WorkManager-coupled, reaching Koin through the app composition root's
 *    Hilt interop module); the wasmJs fragment binds the honest empty
 *    queue/idle resync.
 *  - OfflineRepository resolves from dataJvmModule and UserDataMutator from
 *    the Hilt interop bridge (the C4 shared-module graph);
 *  - OfflineSyncManager was flipped to a Koin single in dataJvmModule by this
 *    same conveyor item (its MediaRepository/DownloadRepository edges resolve
 *    through the Hilt interop on Android) — the legacy DataModule provider now
 *    bridges to it via koin().get().
 *
 * DownloadsViewModel's delete feedback no longer goes through the Android-only
 * UserMessageBus: it emits DownloadsUserMessage values on a messages Flow that
 * DownloadsScreen renders via the DownloadsMessenger actual (bus→flow seam,
 * same shape as the livetv conveyor's LiveTvMessenger).
 */
val downloadsModule: Module = module {
    includes(platformDownloadsModule())

    viewModel {
        DownloadsViewModel(
            queue = get(),
            offlineRepository = get(),
            resync = get(),
        )
    }
    viewModel {
        OfflineLibraryViewModel(
            offlineRepository = get(),
            userDataMutator = get(),
        )
    }
}
