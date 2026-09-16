package com.raulshma.jellyplay.feature.home.di

import com.raulshma.jellyplay.feature.home.HomeClock
import com.raulshma.jellyplay.feature.home.HomeNewsletterGate
import com.raulshma.jellyplay.feature.home.HomeSyncStatusFactory
import com.raulshma.jellyplay.feature.home.WasmHomeClock
import com.raulshma.jellyplay.feature.home.WasmHomeNewsletterGate
import com.raulshma.jellyplay.feature.home.WasmHomeSyncStatusFactory
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformHomeModule(): Module = module {
    // The former HomeDownloadActions / SeriesEpisodeDownloads web bindings
    // moved out with the download-actions seam consolidation: core:data
    // declares, implements and binds QuickDownloadActions /
    // SeriesEpisodeDownloads itself on both platforms (their honest web
    // no-op actuals live in dataWasmModule).
    single<HomeClock> { WasmHomeClock }
    single<HomeSyncStatusFactory> { WasmHomeSyncStatusFactory() }
    single<HomeNewsletterGate> { WasmHomeNewsletterGate }
}
