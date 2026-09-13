package com.raulshma.jellyplay.feature.downloads.di

import com.raulshma.jellyplay.feature.downloads.DownloadQueue
import com.raulshma.jellyplay.feature.downloads.JvmDownloadQueue
import com.raulshma.jellyplay.feature.downloads.JvmOfflineResync
import com.raulshma.jellyplay.feature.downloads.OfflineResync
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformDownloadsModule(): Module = module {
    // The VM resolves both seams via get() — the adapters delegate to the
    // process-wide DownloadRepository / OfflineSyncManager singles.
    single<DownloadQueue> { JvmDownloadQueue(get()) }
    single<OfflineResync> { JvmOfflineResync(get()) }
}
