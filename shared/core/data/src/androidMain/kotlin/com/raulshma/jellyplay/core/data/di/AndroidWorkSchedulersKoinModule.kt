package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
import com.raulshma.jellyplay.core.data.worker.UserDataSyncSchedulerImpl
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The WorkManager schedulers + reconnect-listeners family of the
 * androidCoreDataModule split (see [androidCoreDataModule] for the
 * construction-owner rules). Binding bodies moved verbatim from the
 * pre-split single-module layout.
 */
internal fun androidWorkSchedulersModule(context: Context): Module = module {
    // ── WorkManager schedulers + reconnect listeners ────────────────────
    single {
        AutoDownloadScheduler(
            context = context,
            downloadsStore = get(),
            applicationScope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single { TvWatchNextSchedulerImpl(context = context) }
    single<TvWatchNextScheduler> { get<TvWatchNextSchedulerImpl>() }
    single { UserDataSyncSchedulerImpl(context = context) }
    single<UserDataSyncScheduler> { get<UserDataSyncSchedulerImpl>() }
    single { PlaybackSyncSchedulerImpl(context = context) }
    single<PlaybackSyncScheduler> { get<PlaybackSyncSchedulerImpl>() }

    single {
        DownloadReconnectListener(
            networkMonitor = get(),
            offlineModeManager = get(),
            downloadRepository = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single {
        PlaybackSyncReconnectListener(
            networkMonitor = get(),
            offlineModeManager = get(),
            scheduler = get(),
            outbox = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
}
