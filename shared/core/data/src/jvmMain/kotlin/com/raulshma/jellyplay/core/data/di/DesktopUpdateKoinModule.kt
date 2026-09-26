package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepositoryImpl
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import java.io.File
import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Update family of the desktopDataModule split: the desktop update-check
 * actual of AppUpdateRepository — the version-SENTINEL binding below.
 *
 * OVERRIDE COUPLING (the ONE deliberate desktop override — read before
 * touching): apps/desktop's desktopAppUpdateModule
 * (com.raulshma.jellyplay.desktop.update, DesktopAppUpdate.kt) REPLACES
 * the single below when the desktop shell boots — it is loaded LAST in
 * desktopKoinModules' startKoin list, which runs with allowOverride(true)
 * (Koin 4 dropped the per-definition override flag), so the later
 * real-version actual wins the mapping (docs/adr/desktop-auto-update.md).
 * Graphs that load desktopDataModule WITHOUT that override (the core:data
 * jvmTest smoke graph, DataKoinModulesTest) resolve THIS sentinel — it
 * must keep binding AppUpdateRepository and keep never reporting an
 * update.
 */
internal fun desktopUpdateModule(dataDir: Path): Module = module {
    // ── AppUpdate split: the desktop update-check actual ──────
    // OVERRIDE POINTER: this sentinel single is REPLACED by apps/desktop's
    // desktopAppUpdateModule — loaded last in desktopKoinModules' startKoin
    // list, which runs with allowOverride(true) (see DesktopKoinModules.kt
    // and this file's KDoc).
    // The repository resolves (the About screen's "Check for updates" row
    // calls it through DesktopAppRoot), but desktop has NO self-update: the
    // version sentinel below beats every real release tag, so
    // GitHubReleasesApiImpl.fetchLatestUpdate's
    // compareVersions(tag, currentVersionName) can never report an update.
    // ("dev" would FALSE-positive here: compareVersions reads non-numeric
    // segments as 0, and selectAsset's last-resort branch — any asset
    // ending in "-universal.apk" — would then attach an Android universal
    // APK to the result.) downloadUpdate is unreachable (the UI gates on
    // isUpdateAvailable), so the appdata updates dir stays empty and
    // getPendingUpdate / cleanupDownloadedUpdate are no-ops.
    single<AppUpdateRepository> {
        AppUpdateRepositoryImpl(
            gitHubReleasesApi = get(),
            downloadClient = get(NetworkQualifiers.downloadHttpClient),
            // Same "updates" subtree name as the Android filesDir layout.
            updatesDir = File(dataDir.toFile(), UPDATES_DIR),
            currentVersionName = { DESKTOP_SELF_UPDATE_VERSION },
            flavor = "desktop",
            supportedAbis = arrayOf("desktop"),
            timeSource = get(),
        )
    }
}

/** Same directory name as the Android filesDir layout ("updates"). */
private const val UPDATES_DIR = "updates"

/**
 * Sentinel current version, deliberately not the About screen's "dev": a
 * "dev" that compareVersions folds to 0 would make every GitHub release look
 * like an available desktop update (see the definition comment above). No
 * realistic release tag beats 999999.
 */
private const val DESKTOP_SELF_UPDATE_VERSION = "999999.0.0"
