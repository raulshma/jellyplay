package com.raulshma.jellyplay.feature.settings.di

import android.content.Context
import com.raulshma.jellyplay.core.data.playback.AudioStreamCache
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.feature.settings.AndroidAboutLibrariesJsonSource
import com.raulshma.jellyplay.feature.settings.AndroidAppLocaleSetter
import com.raulshma.jellyplay.feature.settings.AndroidAppMetaProvider
import com.raulshma.jellyplay.feature.settings.AndroidLogCollector
import com.raulshma.jellyplay.feature.settings.AndroidSettingsBackupIo
import com.raulshma.jellyplay.feature.settings.AppLocaleSetter
import com.raulshma.jellyplay.feature.settings.AppMetaProvider
import com.raulshma.jellyplay.feature.settings.AboutLibrariesJsonSource
import com.raulshma.jellyplay.feature.settings.AudioCacheClearer
import com.raulshma.jellyplay.feature.settings.AutoDownloadCleanup
import com.raulshma.jellyplay.feature.settings.AutoDownloadCleanupSummary
import com.raulshma.jellyplay.feature.settings.AutoDownloadSync
import com.raulshma.jellyplay.feature.settings.LogCollector
import com.raulshma.jellyplay.feature.settings.NotificationSync
import com.raulshma.jellyplay.feature.settings.SettingsBackupIo
import com.raulshma.jellyplay.feature.settings.StorageAreas
import com.raulshma.jellyplay.feature.settings.StorageMountsProvider
import com.raulshma.jellyplay.feature.settings.WatchNextRefresher
import com.raulshma.jellyplay.feature.settings.platform.AndroidStorageAreas
import com.raulshma.jellyplay.feature.settings.platform.AndroidStorageMountsProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform pick of the settings feature's seams (the
 * androidDataModule/desktopDataModule split pattern — desktop mirror:
 * desktopSettingsPlatformModule): the Context-shaped actuals (the SAF backup
 * IO, the LocaleManager-based app-locale setter, the Context-walking storage
 * size/clear and mount-enumeration bodies, and the package-manager / logcat /
 * asset-reader seams behind the About and Licenses screens) plus the five
 * ViewModel seam adapters whose Android halves wrap core:data's Android-only
 * types. The wrapped types are all visible from this source set —
 * feature/settings depends on core:data, and the schedulers, the
 * split-package NotificationScheduler and [AudioStreamCache] all live in
 * core:data's androidMain — so the seams + adapters + bindings read in one
 * place here (the app composition root's former AndroidSettingsSeamsModule is
 * gone). The app composition root loads this alongside [settingsModule] during
 * startKoin.
 *
 * Each single is lazy — the target is only resolved when the corresponding
 * settings screen first resolves its ViewModel, so there is no cold-start
 * cost and resolution failure surfaces only on that screen.
 *  - [AutoDownloadSync] delegates to [AutoDownloadScheduler]:
 *    `sync` wraps the WorkManager enqueue/cancel pair, `checkNow` fires the
 *    scheduler's distinct one-shot check (the storage screen's
 *    "Check for new episodes now" action);
 *  - [AutoDownloadCleanup] delegates to the download repository's
 *    keep-days retention sweep (the "Clean up now" action);
 *  - [NotificationSync] delegates to [NotificationScheduler]'s
 *    suspend scheduleOrUpdate (signature-matched);
 *  - [WatchNextRefresher] delegates to the [TvWatchNextScheduler] binding;
 *  - [AudioCacheClearer] delegates to [AudioStreamCache.clear].
 */
fun androidSettingsPlatformModule(context: Context): Module = module {
    single<SettingsBackupIo> { AndroidSettingsBackupIo(context) }
    single<AppLocaleSetter> { AndroidAppLocaleSetter(context) }
    single<StorageAreas> { AndroidStorageAreas(context) }
    single<StorageMountsProvider> { AndroidStorageMountsProvider(context) }
    single<AppMetaProvider> { AndroidAppMetaProvider(context) }
    single<LogCollector> { AndroidLogCollector(context) }
    single<AboutLibrariesJsonSource> { AndroidAboutLibrariesJsonSource(context) }

    single<AutoDownloadSync> {
        val scheduler = get<AutoDownloadScheduler>()
        object : AutoDownloadSync {
            override fun sync() = scheduler.sync()
            override fun checkNow() = scheduler.checkNow()
        }
    }
    single<AutoDownloadCleanup> {
        AutoDownloadCleanup {
            val result = get<DownloadRepository>().sweepExpiredAutoDownloads()
            AutoDownloadCleanupSummary(result.deletedCount, result.bytesReclaimed)
        }
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
