package com.raulshma.jellyplay.feature.downloads.di

import com.raulshma.jellyplay.feature.downloads.DownloadQueue
import com.raulshma.jellyplay.feature.downloads.OfflineResync
import com.raulshma.jellyplay.feature.downloads.WasmDownloadQueue
import com.raulshma.jellyplay.feature.downloads.WasmOfflineResync
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformDownloadsModule(): Module = module {
    single<DownloadQueue> { WasmDownloadQueue }
    single<OfflineResync> { WasmOfflineResync }
}
