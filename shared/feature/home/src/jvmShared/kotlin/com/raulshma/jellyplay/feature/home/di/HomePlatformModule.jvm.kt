package com.raulshma.jellyplay.feature.home.di

import com.raulshma.jellyplay.core.data.sync.SyncStatusStateHolderFactory
import com.raulshma.jellyplay.feature.home.HomeClock
import com.raulshma.jellyplay.feature.home.HomeNewsletterGate
import com.raulshma.jellyplay.feature.home.HomeSyncStatusFactory
import com.raulshma.jellyplay.feature.home.JvmHomeClock
import com.raulshma.jellyplay.feature.home.JvmHomeNewsletterGate
import com.raulshma.jellyplay.feature.home.JvmHomeSyncStatusFactory
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformHomeModule(): Module = module {
    // The VM/refresher resolve the seams via get() — the adapters delegate to
    // the process-wide core:data jvmShared singles. The former
    // HomeDownloadActions / SeriesEpisodeDownloads bindings moved out with
    // the download-actions seam consolidation: core:data declares, implements
    // and binds QuickDownloadActions / SeriesEpisodeDownloads itself on both
    // platforms (dataJvmModule here, dataWasmModule on web).
    single<HomeClock> { JvmHomeClock(get()) }
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
