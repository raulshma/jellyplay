package com.raulshma.jellyplay.di

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
 * feature's four seams (V3 settings conveyor Wave 2; wave 8B — Hilt removal:
 * the four legacy impls are Koin-owned now, so each seam resolves its
 * dependency straight from the container instead of through the former
 * SettingsSeamsEntryPoint).
 *
 * Each single is lazy — the target is only resolved when the corresponding
 * settings screen first resolves its ViewModel, so there is no cold-start
 * cost and resolution failure surfaces only on that screen.
 *  - [AutoDownloadSync] delegates to the legacy
 *    [AutoDownloadScheduler.sync] WorkManager enqueue/cancel;
 *  - [NotificationSync] delegates to the legacy [NotificationScheduler]'s
 *    suspend scheduleOrUpdate (signature-matched);
 *  - [WatchNextRefresher] delegates to the [TvWatchNextScheduler] binding;
 *  - [AudioCacheClearer] delegates to [AudioStreamCache.clear].
 */
fun androidSettingsSeamsModule(): Module = module {
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
