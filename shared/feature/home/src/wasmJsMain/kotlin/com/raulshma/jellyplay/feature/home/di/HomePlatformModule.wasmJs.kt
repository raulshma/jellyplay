package com.raulshma.jellyplay.feature.home.di

import com.raulshma.jellyplay.feature.home.HomeClock
import com.raulshma.jellyplay.feature.home.HomeDownloadActions
import com.raulshma.jellyplay.feature.home.HomeNewsletterGate
import com.raulshma.jellyplay.feature.home.HomeSyncStatusFactory
import com.raulshma.jellyplay.feature.home.SeriesEpisodeDownloads
import com.raulshma.jellyplay.feature.home.WasmHomeClock
import com.raulshma.jellyplay.feature.home.WasmHomeDownloadActions
import com.raulshma.jellyplay.feature.home.WasmHomeNewsletterGate
import com.raulshma.jellyplay.feature.home.WasmHomeSyncStatusFactory
import com.raulshma.jellyplay.feature.home.WasmSeriesEpisodeDownloads
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformHomeModule(): Module = module {
    single<HomeClock> { WasmHomeClock }
    single<HomeDownloadActions> { WasmHomeDownloadActions }
    single<SeriesEpisodeDownloads> { WasmSeriesEpisodeDownloads }
    single<HomeSyncStatusFactory> { WasmHomeSyncStatusFactory() }
    single<HomeNewsletterGate> { WasmHomeNewsletterGate }
}
