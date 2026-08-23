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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-authored Koin definitions for the Android actuals of the settings
 * feature's four Hilt-backed seams (V3 settings conveyor Wave 2 — the
 * HiltInteropModule EntryPointAccessors pattern: the shared module cannot
 * reach the Hilt-owned legacy :core:data/:core:notification impls, so the
 * composition root that sees both pulls them from the application graph).
 *
 * Each single is lazy — the entry point (and the Hilt singleton behind it)
 * is only touched when the corresponding settings screen first resolves its
 * ViewModel, so there is no cold-start cost and resolution failure surfaces
 * only on that screen.
 *  - [AutoDownloadSync] delegates to the legacy
 *    [AutoDownloadScheduler.sync] WorkManager enqueue/cancel;
 *  - [NotificationSync] delegates to the legacy [NotificationScheduler]'s
 *    suspend scheduleOrUpdate (signature-matched);
 *  - [WatchNextRefresher] delegates to the [TvWatchNextScheduler] binding
 *    (DataModule @Binds → TvWatchNextSchedulerImpl);
 *  - [AudioCacheClearer] delegates to [AudioStreamCache.clear].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsSeamsEntryPoint {
    fun autoDownloadScheduler(): AutoDownloadScheduler
    fun notificationScheduler(): NotificationScheduler
    fun tvWatchNextScheduler(): TvWatchNextScheduler
    fun audioStreamCache(): AudioStreamCache
}

private fun settingsSeamsEntryPoint(application: Application): SettingsSeamsEntryPoint =
    EntryPointAccessors.fromApplication(application, SettingsSeamsEntryPoint::class.java)

fun androidSettingsSeamsModule(application: Application): Module = module {
    single<AutoDownloadSync> {
        AutoDownloadSync { settingsSeamsEntryPoint(application).autoDownloadScheduler().sync() }
    }
    single<NotificationSync> {
        NotificationSync { settingsSeamsEntryPoint(application).notificationScheduler().scheduleOrUpdate() }
    }
    single<WatchNextRefresher> {
        WatchNextRefresher { settingsSeamsEntryPoint(application).tvWatchNextScheduler().scheduleRefresh() }
    }
    single<AudioCacheClearer> {
        AudioCacheClearer { settingsSeamsEntryPoint(application).audioStreamCache().clear() }
    }
}
