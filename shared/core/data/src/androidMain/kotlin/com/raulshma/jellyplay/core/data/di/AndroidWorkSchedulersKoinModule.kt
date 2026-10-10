package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.SettingsSyncBackgroundTrigger
import com.raulshma.jellyplay.core.data.worker.SettingsSyncScheduler
import com.raulshma.jellyplay.core.data.worker.SettingsSyncSchedulerImpl
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

    // ── The settings/profile sync engine's background flush family (ADR
    //    0011): the WorkManager scheduler (12h catch-up + KEEP one-shots) and
    //    the background trigger (app-background edge + reconnect edge +
    //    app-start periodic arm), started from AppStartupPrewarms' deferred
    //    background-scheduler group. The dirty-write signal (the search /
    //    reader repositories' onDirty seams) resolves THIS interface via
    //    getOrNull, so the jvmShared graph stays scheduler-blind. ──
    single {
        SettingsSyncSchedulerImpl(
            context = context,
            syncRepository = get<ProfileSyncRepository>(),
        )
    }
    single<SettingsSyncScheduler> { get<SettingsSyncSchedulerImpl>() }
    single {
        SettingsSyncBackgroundTrigger(
            networkMonitor = get(),
            offlineModeManager = get(),
            scheduler = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
}
