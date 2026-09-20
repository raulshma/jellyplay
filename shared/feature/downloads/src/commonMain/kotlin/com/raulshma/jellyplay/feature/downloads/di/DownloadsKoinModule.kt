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
 * single constructor owner (one framework per type). Ctor deps:
 *  - DownloadQueue / OfflineResync resolve from core:data's graph (the
 *    promoted commonMain interfaces — implemented DIRECTLY by the jvmShared
 *    DownloadRepositoryImpl / OfflineSyncManager singles and bound in
 *    dataJvmModule).
 *    The former platformDownloadsModule fragment + JvmDownloadQueue /
 *    JvmOfflineResync adapters died with the promoted-interface pass.
 *  - OfflineRepository resolves from dataJvmModule and UserDataMutator from
 *    the shared-module graph.
 *  - OfflineSyncManager is a Koin single in dataJvmModule (the V3 downloads
 *    conveyor flip).
 *
 * DownloadsViewModel's delete feedback no longer goes through the Android-only
 * UserMessageBus: it emits DownloadsUserMessage values on a messages Flow that
 * DownloadsScreen renders, posting the resolved text to the shared
 * UserMessageBus.
 */
val downloadsModule: Module = module {
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
