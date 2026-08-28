package com.raulshma.jellyplay.feature.settings.di

import com.raulshma.jellyplay.core.data.worker.DesktopAutoDownloadScheduler
import com.raulshma.jellyplay.feature.settings.AppLocaleSetter
import com.raulshma.jellyplay.feature.settings.AppMetaProvider
import com.raulshma.jellyplay.feature.settings.AboutLibrariesJsonSource
import com.raulshma.jellyplay.feature.settings.AudioCacheClearer
import com.raulshma.jellyplay.feature.settings.AutoDownloadSync
import com.raulshma.jellyplay.feature.settings.DesktopAboutLibrariesJsonSource
import com.raulshma.jellyplay.feature.settings.DesktopAppLocaleSetter
import com.raulshma.jellyplay.feature.settings.DesktopAppMetaProvider
import com.raulshma.jellyplay.feature.settings.DesktopLogCollector
import com.raulshma.jellyplay.feature.settings.DesktopSettingsBackupIo
import com.raulshma.jellyplay.feature.settings.LogCollector
import com.raulshma.jellyplay.feature.settings.NotificationSync
import com.raulshma.jellyplay.feature.settings.SettingsBackupIo
import com.raulshma.jellyplay.feature.settings.StorageAreas
import com.raulshma.jellyplay.feature.settings.StorageMountsProvider
import com.raulshma.jellyplay.feature.settings.WatchNextRefresher
import com.raulshma.jellyplay.feature.settings.platform.DesktopStorageAreas
import com.raulshma.jellyplay.feature.settings.platform.DesktopStorageMountsProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop platform pick of the settings feature's seams (the
 * androidDataModule/desktopDataModule split pattern): the AWT-dialog backup
 * picker + JDK-stream backup IO (wave 20C), the no-op app-locale setter, the
 * zero/no-op storage actuals, the
 * build-literal AppMeta / null-log / classpath aboutlibraries actuals, and
 * the desktop notification-poke actuals. The desktop composition root loads
 * this alongside [settingsModule].
 *
 * The auto-download sync wraps the existing Koin-owned
 * [DesktopAutoDownloadScheduler] (desktopDataModule): a poke restarts the
 * idempotent 6 h loop with one immediate check, so a freshly toggled
 * auto-download preference is honoured right away. WatchNext / audio-cache /
 * notification-sync have no desktop surface and bind no-ops.
 */
fun desktopSettingsPlatformModule(): Module = module {
    single<SettingsBackupIo> { DesktopSettingsBackupIo() }
    single<AppLocaleSetter> { DesktopAppLocaleSetter() }
    single<StorageAreas> { DesktopStorageAreas() }
    single<StorageMountsProvider> { DesktopStorageMountsProvider() }
    single<AppMetaProvider> { DesktopAppMetaProvider() }
    single<LogCollector> { DesktopLogCollector() }
    single<AboutLibrariesJsonSource> { DesktopAboutLibrariesJsonSource() }
    single<AutoDownloadSync> {
        AutoDownloadSync { get<DesktopAutoDownloadScheduler>().start() }
    }
    single<WatchNextRefresher> {
        WatchNextRefresher { /* no Android TV Watch Next row on desktop */ }
    }
    single<AudioCacheClearer> {
        AudioCacheClearer { /* no desktop audio cache to clear yet */ }
    }
    single<NotificationSync> {
        NotificationSync { /* no notification worker on desktop */ }
    }
}
