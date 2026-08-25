package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.network.AndroidNetworkMonitor
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.AndroidOfflineModeManager
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.LocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.MediaExtractorLocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryAccess
import com.raulshma.jellyplay.core.data.update.ApkInstallBuilder
import com.raulshma.jellyplay.core.data.update.ApkInstallBuilderImpl
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepositoryImpl
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.ImageUrlProviderImpl
import com.raulshma.jellyplay.core.data.util.DataBuildFlags
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import java.io.File
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform pick of the Koin-owned data layer (Phase C4 part 2).
 * Holds the Context-shaped definitions: the connectivity/offline seams
 * (NetworkMonitor / OfflineModeManager), the LruCache-based image-URL
 * memoiser, and the MediaExtractor metadata probe; everything else resolves
 * from [dataJvmModule].
 */
fun androidDataModule(context: Context): Module {
    // Side effect, deliberately before the module definition: common code
    // reads [DataBuildFlags.debugBuild] (the moved BuildConfig.DEBUG seam)
    // possibly as early as single construction, so the flag must be set when
    // the module function runs — the app composition root calls this during
    // startKoin, long before any definition resolves.
    DataBuildFlags.debugBuild =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    return module {
        single<NetworkMonitor> { AndroidNetworkMonitor(context) }

        single<OfflineModeManager> {
            AndroidOfflineModeManager(
                context = context,
                networkMonitor = get(),
                networkOfflineStore = get(),
            )
        }

        single<ImageUrlProvider> {
            ImageUrlProviderImpl(
                playbackRepository = get(),
                appearanceStore = get(),
            )
        }

        single<LocalStreamProbe> { MediaExtractorLocalStreamProbe() }

        // V3 downloads conveyor: the deferred MediaRepository edge of the
        // Koin-owned DownloadRepositoryImpl. Since the Phase X MediaRepository
        // cluster flip this resolves Koin's own MediaRepositoryImpl single
        // (dataJvmModule) — the former Hilt-interop hop is gone, and the
        // desktop counterpart in desktopDataModule is now real too.
        single<MediaRepositoryAccess> { MediaRepositoryAccess { get<MediaRepository>() } }

        // ── AppUpdate split (Wave xB): Android actuals of the update seams ──
        // AppUpdateRepositoryImpl moved to jvmShared as a plain class whose
        // Context-shaped inputs became ctor params; these definitions supply
        // the Android ones. The desktop twins live in desktopDataModule.

        // The system-installer intent seam (android.content.Intent +
        // FileProvider cannot live in the shared interface). Registered here
        // so the shell's Hilt-side injection rides the DataModule
        // koin().get() bridge onto this single.
        single<ApkInstallBuilder> { ApkInstallBuilderImpl(context) }

        single<AppUpdateRepository> {
            AppUpdateRepositoryImpl(
                gitHubReleasesApi = get(),
                // The Koin twin of the legacy @Named("download") Hilt qualifier.
                downloadClient = get<OkHttpClient>(NetworkQualifiers.downloadHttpClient),
                // Same layout the legacy impl derived: <filesDir>/updates.
                updatesDir = File(context.filesDir, UPDATES_DIR),
                // Lazy, like the legacy currentVersionName(): the PackageManager
                // read happens per check/sweep, not at single construction.
                currentVersionName = { currentVersionName(context) },
                // The running product flavor is derived from the package name.
                // Library modules cannot read `Build.FLAVOR` (it is empty
                // outside the :app module's flavor), so this is the single
                // source of truth shared by every caller. TV builds carry a
                // `.tv` applicationId suffix (see app/build.gradle.kts).
                flavor = if (context.packageName.endsWith(".tv")) "tv" else "phone",
                supportedAbis = android.os.Build.SUPPORTED_ABIS,
            )
        }
    }
}

/** Same directory name the legacy impl used under filesDir. */
private const val UPDATES_DIR = "updates"

/**
 * The installed version name — replicates the legacy
 * AppUpdateRepositoryImpl.currentVersionName() verbatim (the TIRAMISU
 * PackageInfoFlags branch included).
 */
private fun currentVersionName(context: Context): String {
    val pm = context.packageManager
    val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    // Prefer the long version code as the source of truth; fall back to
    // versionName for the human-readable dotted string used by the
    // comparator. versionName is what the build injects via -PversionName.
    @Suppress("DEPRECATION")
    return info.versionName ?: androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toString()
}
