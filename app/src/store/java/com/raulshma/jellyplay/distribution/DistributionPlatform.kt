package com.raulshma.jellyplay.distribution

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.raulshma.jellyplay.core.data.cast.GoogleCastIntegration
import com.raulshma.jellyplay.core.data.cast.GoogleCastIntegrationImpl
import com.raulshma.jellyplay.core.data.cast.GoogleCastStrategy
import com.raulshma.jellyplay.core.data.update.ApkInstallBuilder
import com.raulshma.jellyplay.core.data.update.ApkInstallBuilderImpl
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepositoryImpl
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import com.raulshma.jellyplay.feature.book.JunrarRarEntryReader
import com.raulshma.jellyplay.feature.book.RarEntryReaderFactory
import com.raulshma.jellyplay.shell.AppUpdateCoordinator
import com.raulshma.jellyplay.shell.UpdateCoordinator
import com.raulshma.jellyplay.update.AppUpdateSheet
import com.raulshma.jellyplay.update.UpdateState
import java.io.File
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/**
 * The distribution-flavor linchpin. Exactly one implementation of this
 * object is on each variant's classpath (src/store vs src/fdroid); src/main
 * references the FQCN only. Members are grouped by seam: cast, fonts,
 * self-update.
 *
 * Store line (Play / GMS on the classpath):
 *  - cast: Google Play services Cast is a store-only dependency; [preinitCast]
 *    warms the CastContext on the main thread and reports whether the hidden
 *    media-route button may be composed (fdroid: always false). The Koin
 *    module below binds the GMS cast family (GoogleCastStrategy +
 *    GoogleCastIntegrationImpl) so core:data's CastManager resolves a real
 *    integration on this line only.
 *  - books: the RAR (CBR) back-end ([RarEntryReaderFactory] over junrar) is
 *    bound here too — the UnRAR license is non-free for F-Droid, so fdroid
 *    leaves RAR comics unsupported by construction.
 *  - fonts: no bundled families — theming rides the GMS downloadable-fonts
 *    provider, so [installBundledFonts] stays a no-op here.
 *  - self-update: the GitHub-release updater (REQUEST_INSTALL_PACKAGES stays
 *    in the merged manifest on this line only) — [UpdateSheetOverlay] hosts
 *    the sheet and [selfUpdateSupported] gates the Settings → About update
 *    rows.
 */
object DistributionPlatform {

    /** Koin modules contributed by this distribution flavor. */
    fun koinModules(app: Application): List<Module> = castAndBookModules(app) + updateModules(app)

    /**
     * Cast pre-init, run on Dispatchers.Main after onCreate (store: the GMS
     * CastContext warm; fdroid: always false — no Play services on that
     * classpath). Resolves the flavor-bound [GoogleCastIntegration] straight
     * from the Koin container (same KoinPlatform idiom the shell entry points
     * use): startKoin runs in Application.onCreate, strictly before any
     * activity reaches this.
     */
    fun preinitCast(activity: ComponentActivity): Boolean =
        KoinPlatform.getKoin()?.getOrNull<GoogleCastIntegration>()?.preinit(activity) ?: false

    /**
     * Hidden 1dp media-route button; composed only when [preinitCast] returned
     * true (the composition gate lives at the MainActivity call site so its
     * recomposition still subscribes to the castPreinitialized field). The
     * 1dp size is deliberate: the button must exist so
     * CastButtonFactory.setUpMediaRouteButton has a View to bind the session
     * listener to (it keeps the framework's route discovery warm for
     * CastManager), but must never render visibly. The factory's try/catch
     * collapses any failure into a plain 1dp View — composition can never
     * crash on Cast init. Timing: composed only after the onCreate pre-init
     * coroutine succeeded, so setUpMediaRouteButton resolves the cached
     * CastContext and does no Dynamite dex I/O on the composition thread.
     */
    @Composable
    fun HiddenCastRouteButton() {
        Box(
            modifier = Modifier
                .size(1.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    try {
                        androidx.mediarouter.app.MediaRouteButton(ctx).also {
                            it.visibility = View.INVISIBLE
                            com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(ctx, it)
                        }
                    } catch (_: Exception) {
                        View(ctx)
                    }
                },
                modifier = Modifier.size(1.dp),
            )
        }
    }

    /** Installs bundled TTF font families for the theme; no-op on store (GMS provider). */
    fun installBundledFonts() {
    }

    /** Self-update IS supported on this line: the About update rows stay visible. */
    val selfUpdateSupported: Boolean = true

    /**
     * Root overlay for the self-update sheet. Collects the flavor-bound
     * [AppUpdateCoordinator]'s state and renders the [AppUpdateSheet] when an
     * update flow is active. Centralized here so the launch-time auto-check
     * and any manual check (Settings) drive the same single sheet.
     */
    @Composable
    fun UpdateSheetOverlay(update: AppUpdateCoordinator) {
        val state by update.updateState.collectAsStateWithLifecycle()
        val autoDownloadEnabled by update.selfUpdateDownloadEnabled.collectAsStateWithLifecycle()
        if (state !is UpdateState.Idle) {
            val context = LocalContext.current
            AppUpdateSheet(
                state = state,
                autoDownloadEnabled = autoDownloadEnabled,
                onAutoDownloadToggle = { enabled ->
                    update.setSelfUpdateDownloadEnabled(enabled)
                    // Turning it ON while an update is already available should also
                    // start downloading the shown update immediately.
                    val available = state as? UpdateState.UpdateAvailable
                    if (enabled && available != null) {
                        update.startUpdateDownload(available.info)
                    }
                },
                onDownload = { info -> update.startUpdateDownload(info) },
                onInstall = { intent ->
                    runCatching { context.startActivity(intent) }
                },
                onRedownload = { update.redownloadUpdate() },
                onCancel = { update.cancelDownload() },
                onDismiss = { update.dismissUpdate() },
                buildInstallIntent = { update.buildInstallIntent() },
            )
        }
    }
}

/**
 * The store line's cast + book Koin bindings — the seams whose implementation
 * needs a non-free classpath: the GMS cast family (GoogleCastStrategy +
 * GoogleCastIntegrationImpl; core:data's CastManager resolves the integration
 * via getOrNull ?: Unavailable, so fdroid's graph — which binds neither of
 * these — degrades cleanly) and the junrar-backed RAR entry-reader factory
 * (the UnRAR license is non-free for F-Droid, so the fdroid line binds no
 * RarEntryReaderFactory and ComicArchiveDocument.open treats RAR as
 * unsupported there).
 */
private fun castAndBookModules(app: Application): List<Module> = listOf(
    module {
        single { GoogleCastStrategy(appContext = app) }
        single<GoogleCastIntegration> {
            GoogleCastIntegrationImpl(context = app, googleCastStrategy = get<GoogleCastStrategy>())
        }
        single<RarEntryReaderFactory> { JunrarRarEntryReader.FACTORY }
    },
)

/**
 * The store line's self-update Koin bindings: the shell's GitHub-release
 * coordinator plus the Android actuals of the update seams (moved here from
 * core:data's AndroidDataModule so the fdroid line binds none of them and R8
 * strips their impls from fdroid APKs — no download, no installer intent).
 */
private fun updateModules(app: Application): List<Module> = listOf(
    module {
        single<AppUpdateCoordinator> {
            UpdateCoordinator(
                appUpdateRepository = get(),
                apkInstallBuilder = get(),
                experimentalStore = get(),
            )
        }

        // The system-installer intent seam (android.content.Intent +
        // FileProvider cannot live in the shared interface). The shell resolves
        // this single straight from Koin.
        single<ApkInstallBuilder> { ApkInstallBuilderImpl(app) }

        single<AppUpdateRepository> {
            AppUpdateRepositoryImpl(
                gitHubReleasesApi = get(),
                // The Koin twin of the legacy @Named("download") Hilt qualifier.
                downloadClient = get<OkHttpClient>(NetworkQualifiers.downloadHttpClient),
                // Same layout the legacy impl derived: <filesDir>/updates.
                updatesDir = File(app.filesDir, UPDATES_DIR),
                // Lazy, like the legacy currentVersionName(): the PackageManager
                // read happens per check/sweep, not at single construction.
                currentVersionName = { currentVersionName(app) },
                // The running product flavor is derived from the package name.
                // Library modules cannot read `Build.FLAVOR` (it is empty
                // outside the :app module's flavor), so this is the single
                // source of truth shared by every caller. TV builds carry a
                // `.tv` applicationId suffix (see app/build.gradle.kts).
                flavor = if (app.packageName.endsWith(".tv")) "tv" else "phone",
                supportedAbis = android.os.Build.SUPPORTED_ABIS,
                timeSource = get(),
            )
        }
    },
)

/** Same directory name the legacy impl used under filesDir. */
private const val UPDATES_DIR = "updates"

/**
 * The installed version name — replicates the legacy
 * AppUpdateRepositoryImpl.currentVersionName() verbatim (the TIRAMISU
 * PackageInfoFlags branch included).
 */
private fun currentVersionName(context: android.content.Context): String {
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
