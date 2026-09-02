package com.raulshma.jellyplay.feature.settings.di

import android.content.Context
import com.raulshma.jellyplay.feature.settings.AndroidAboutLibrariesJsonSource
import com.raulshma.jellyplay.feature.settings.AndroidAppLocaleSetter
import com.raulshma.jellyplay.feature.settings.AndroidAppMetaProvider
import com.raulshma.jellyplay.feature.settings.AndroidLogCollector
import com.raulshma.jellyplay.feature.settings.AndroidSettingsBackupIo
import com.raulshma.jellyplay.feature.settings.AppLocaleSetter
import com.raulshma.jellyplay.feature.settings.AppMetaProvider
import com.raulshma.jellyplay.feature.settings.AboutLibrariesJsonSource
import com.raulshma.jellyplay.feature.settings.LogCollector
import com.raulshma.jellyplay.feature.settings.SettingsBackupIo
import com.raulshma.jellyplay.feature.settings.StorageAreas
import com.raulshma.jellyplay.feature.settings.StorageMountsProvider
import com.raulshma.jellyplay.feature.settings.platform.AndroidStorageAreas
import com.raulshma.jellyplay.feature.settings.platform.AndroidStorageMountsProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform pick of the settings feature's Context-shaped seams (the
 * androidDataModule/desktopDataModule split pattern): the SAF backup IO, the
 * LocaleManager-based app-locale setter, the Context-walking storage
 * size/clear and mount-enumeration bodies, and the package-manager / logcat /
 * asset-reader seams behind the About and Licenses screens. The app
 * composition root loads this alongside [settingsModule] during startKoin.
 *
 * Deliberately NOT provided here (see the settingsModule kdoc): AutoDownloadSync,
 * NotificationSync, WatchNextRefresher, AudioCacheClearer — their Android impls
 * wrap Hilt-owned legacy :core:data/:core:notification types this module
 * cannot reach, so the composition root registers them app-side (downloads
 * phase's AndroidDownloadSeamsModule / EntryPointAccessors interop pattern).
 */
fun androidSettingsPlatformModule(context: Context): Module = module {
    single<SettingsBackupIo> { AndroidSettingsBackupIo(context) }
    single<AppLocaleSetter> { AndroidAppLocaleSetter(context) }
    single<StorageAreas> { AndroidStorageAreas(context) }
    single<StorageMountsProvider> { AndroidStorageMountsProvider(context) }
    single<AppMetaProvider> { AndroidAppMetaProvider(context) }
    single<LogCollector> { AndroidLogCollector(context) }
    single<AboutLibrariesJsonSource> { AndroidAboutLibrariesJsonSource(context) }
}
