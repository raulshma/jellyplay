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
import com.raulshma.jellyplay.feature.settings.platform.DesktopImageCacheOps
import com.raulshma.jellyplay.feature.settings.platform.DesktopStorageAreas
import com.raulshma.jellyplay.feature.settings.platform.DesktopStorageMountsProvider
import java.io.File
import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop platform pick of the settings feature's seams (the
 * androidDataModule/desktopDataModule split pattern): the AWT-dialog backup
 * picker + JDK-stream backup IO (wave 20C), the no-op app-locale setter, the
 * REAL storage actuals (wave 21B — see below), the build-literal AppMeta /
 * null-log / classpath aboutlibraries actuals, and the desktop
 * notification-poke actuals. The desktop composition root loads this
 * alongside [settingsModule].
 *
 * Storage went real with wave 21B: the downloads and cache buckets walk the
 * roots the desktop data seams own — `<dataDir>/downloads`
 * (DesktopDownloadStorageLayout's root, nested music subtree included) and
 * `<configDir>/http-cache` (DesktopNetworkModule's OkHttp cache) — with the
 * same subtree literals as the owning definitions; the image-cache bucket
 * delegates to the shell-injected [imageCache] handle (Coil's disk cache
 * belongs to the app shell's image pipeline, not this module).
 *
 * The auto-download sync wraps the existing Koin-owned
 * [DesktopAutoDownloadScheduler] (desktopDataModule): a poke restarts the
 * idempotent 6 h loop with one immediate check, so a freshly toggled
 * auto-download preference is honoured right away. WatchNext / audio-cache /
 * notification-sync have no desktop backend and bind no-ops — the no-ops are
 * the BEHAVIOR half only; the rows themselves are hidden through
 * `SettingsCapabilities` (see its ownership rule), so no desktop user sees a
 * control whose poke lands nowhere.
 *
 * @param dataDir the appdata root (`<appdata>/data`, DesktopPaths.dataDirNio)
 *   the downloads subtree hangs under.
 * @param configDir the config root (DesktopPaths.configDirNio) the http-cache
 *   subtree hangs under.
 * @param imageCache the shell's Coil disk-cache handle (sized + cleared for
 *   the image-cache bucket; DesktopImageCacheOps KDoc has the layout).
 */
fun desktopSettingsPlatformModule(
    dataDir: Path,
    configDir: Path,
    imageCache: DesktopImageCacheOps,
): Module {
    // Same subtree literals as the owning definitions: DesktopDownload
    // StorageLayout resolves every download under `<dataDir>/downloads`
    // (`downloads/music` for audio, nested inside the walk); DesktopNetwork
    // Module builds the base OkHttp client's Cache against
    // `<configDir>/http-cache`.
    val downloadsRoot = File(dataDir.toFile(), "downloads")
    val httpCacheRoot = File(configDir.toFile(), "http-cache")

    return module {
        single<SettingsBackupIo> { DesktopSettingsBackupIo(httpCacheRoot) }
        single<AppLocaleSetter> { DesktopAppLocaleSetter() }
        single<StorageAreas> {
            DesktopStorageAreas(
                downloadsRoot = downloadsRoot,
                httpCacheRoot = httpCacheRoot,
                imageCache = imageCache,
            )
        }
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
}
