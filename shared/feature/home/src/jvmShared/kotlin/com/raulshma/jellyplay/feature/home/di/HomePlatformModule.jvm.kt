package com.raulshma.jellyplay.feature.home.di

import com.raulshma.jellyplay.core.data.sync.SyncStatusStateHolderFactory
import com.raulshma.jellyplay.feature.home.HomeClock
import com.raulshma.jellyplay.feature.home.HomeDownloadActions
import com.raulshma.jellyplay.feature.home.HomeNewsletterGate
import com.raulshma.jellyplay.feature.home.HomeSyncStatusFactory
import com.raulshma.jellyplay.feature.home.JvmHomeClock
import com.raulshma.jellyplay.feature.home.JvmHomeDownloadActions
import com.raulshma.jellyplay.feature.home.JvmHomeNewsletterGate
import com.raulshma.jellyplay.feature.home.JvmHomeSyncStatusFactory
import com.raulshma.jellyplay.feature.home.JvmSeriesEpisodeDownloads
import com.raulshma.jellyplay.feature.home.SeriesEpisodeDownloads
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformHomeModule(): Module = module {
    // The VM/refresher resolve the seams via get() — the adapters delegate to
    // the process-wide core:data jvmShared singles.
    single<HomeClock> { JvmHomeClock(get()) }
    single<HomeDownloadActions> { JvmHomeDownloadActions(get()) }
    single<SeriesEpisodeDownloads> { JvmSeriesEpisodeDownloads(get()) }
    single<HomeNewsletterGate> { JvmHomeNewsletterGate(get()) }
    // The holder factory's collaborator single, moved here from the common
    // home module (its deps are jvmShared: WorkManager scheduler). The
    // wrapping seam adapter rides it.
    single {
        SyncStatusStateHolderFactory(
            playbackOutboxRepository = get(),
            playbackSyncScheduler = get(),
            offlineFirstItemResolver = get(),
        )
    }
    single<HomeSyncStatusFactory> { JvmHomeSyncStatusFactory(get()) }
}
