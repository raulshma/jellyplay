package com.raulshma.jellyplay.di

import android.app.Application
import com.raulshma.jellyplay.core.data.playback.AudioStreamCache
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.feature.settings.AudioCacheClearer
import com.raulshma.jellyplay.feature.settings.AutoDownloadSync
import com.raulshma.jellyplay.feature.settings.NotificationSync
import com.raulshma.jellyplay.feature.settings.WatchNextRefresher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-authored Koin definitions for the Android actuals of the settings
 * feature's four Hilt-backed seams (V3 settings conveyor Wave 2).
 *
 * Wave 8A: the four legacy impls (AutoDownloadScheduler,
 * NotificationScheduler, TvWatchNextScheduler binding, AudioStreamCache)
 * are Koin-owned (:core:data androidCoreDataModule / :core:notification
 * androidNotificationModule), so the seams resolve them straight from the
 * container — the SettingsSeamsEntryPoint/Hilt reach-through died with the
 * core-side Hilt extinction. Each single stays lazy: the impls are only
 * touched when the corresponding settings screen resolves its ViewModel.
 *  - [AutoDownloadSync] delegates to [AutoDownloadScheduler.sync]'s
 *    WorkManager enqueue/cancel;
 *  - [NotificationSync] delegates to [NotificationScheduler]'s suspend
 *    scheduleOrUpdate (signature-matched);
 *  - [WatchNextRefresher] delegates to the [TvWatchNextScheduler] binding
 *    (androidCoreDataModule → TvWatchNextSchedulerImpl);
 *  - [AudioCacheClearer] delegates to [AudioStreamCache.clear].
 */
fun androidSettingsSeamsModule(application: Application): Module = module {
    single<AutoDownloadSync> {
        AutoDownloadSync { get<AutoDownloadScheduler>().sync() }
    }
    single<NotificationSync> {
        NotificationSync { get<NotificationScheduler>().scheduleOrUpdate() }
    }
    single<WatchNextRefresher> {
        WatchNextRefresher { get<TvWatchNextScheduler>().scheduleRefresh() }
    }
    single<AudioCacheClearer> {
        AudioCacheClearer { get<AudioStreamCache>().clear() }
    }
}
