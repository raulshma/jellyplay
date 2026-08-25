package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.download.DesktopDownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.network.DesktopNetworkMonitor
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.DesktopOfflineModeManager
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DesktopDownloadStorageLayout
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueueCoordinator
import com.raulshma.jellyplay.core.data.repository.DownloadProgressNotifier
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayoutContract
import com.raulshma.jellyplay.core.data.repository.LocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.DesktopLocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryAccess
import com.raulshma.jellyplay.core.data.repository.OfflineImagePreloader
import com.raulshma.jellyplay.core.data.util.DesktopImageUrlProvider
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.DataBuildFlags
import com.raulshma.jellyplay.core.data.worker.DesktopAutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop platform pick of the Koin-owned data layer (Phase C4 part 2).
 * Holds the always-connected connectivity seams, the LinkedHashMap-based
 * image-URL memoiser, and the (unsupported, badge-less) desktop stream
 * probe; everything else resolves from [dataJvmModule].
 *
 * V3 downloads conveyor: also holds the desktop actuals of the portable
 * download engine's seams — the appdata storage layout, the in-process
 * DesktopDownloadManager (the DownloadEnqueueCoordinator actual: enqueue =
 * transfer-loop kick, cancelWork = cooperative stop), no-op notification /
 * image-preload surfaces, the desktop DownloadIntake, and the 6 h
 * auto-download loop. Since the Phase X MediaRepository cluster flip the
 * MediaRepositoryAccess actual is REAL (Koin owns MediaRepositoryImpl on
 * desktop too) — series downloads and auto-download work end-to-end.
 */
fun desktopDataModule(dataDir: Path): Module {
    // Side effect, deliberately before the module definition: common code
    // reads [DataBuildFlags.debugBuild] (the moved BuildConfig.DEBUG seam)
    // possibly as early as single construction, so the flag must be set when
    // the module function runs. Desktop defaults to debug logging on unless
    // `jellyplay.debug=false` is set on the JVM command line (desktop app
    // builds arrive at Phase V1; jvmTest smoke tests get verbose logs).
    DataBuildFlags.debugBuild = System.getProperty("jellyplay.debug")?.toBoolean() ?: true

    return module {
        single<NetworkMonitor> { DesktopNetworkMonitor() }

        single<OfflineModeManager> {
            DesktopOfflineModeManager(
                networkMonitor = get(),
                networkOfflineStore = get(),
            )
        }

        single<ImageUrlProvider> {
            DesktopImageUrlProvider(
                playbackRepository = get(),
                appearanceStore = get(),
            )
        }

        single<LocalStreamProbe> { DesktopLocalStreamProbe() }

        // ── V3 downloads conveyor: desktop actuals of the engine seams ──────

        single<DownloadStorageLayoutContract> { DesktopDownloadStorageLayout(dataDir) }

        single<DownloadProgressNotifier> { DownloadProgressNotifier { /* no summary surface on desktop */ } }

        single<OfflineImagePreloader> { OfflineImagePreloader { /* no shared preload cache on desktop */ } }

        // Phase X MediaRepository cluster flip: MediaRepository is now
        // Koin-owned on desktop too (dataJvmModule's MediaRepositoryImpl
        // single), so this accessor is real — desktop SERIES downloads and
        // the auto-download scheduler went live with the flip. Previously the
        // documented throwing-lazy (no desktop definition): downloadSeries
        // failed loudly and episode series-seeding degraded to the minimal
        // parent-row fallback.
        single<MediaRepositoryAccess> { MediaRepositoryAccess { get<MediaRepository>() } }

        // The in-process download manager: construction is side-effect free;
        // the composition root resolves + start()s it after startKoin.
        single {
            DesktopDownloadManager(
                downloadDao = get(),
                userDao = get(),
                downloadsStore = get(),
                serverIdentityStore = get(),
                tokenCipher = get(),
                concurrencyLimiter = get(),
                transferClient = get(),
                downloadHttpClient = get(NetworkQualifiers.downloadHttpClient),
                // Lazy: the manager is the repository's coordinator actual, so
                // an eager resolution here would re-enter the repository
                // single's construction (see the manager ctor kdoc).
                downloadRepository = lazy { get<DownloadRepository>() },
                networkMonitor = get(),
                offlineModeManager = get(),
                scope = get(DatastoreQualifiers.applicationScope),
            )
        }
        single<DownloadEnqueueCoordinator> { get<DesktopDownloadManager>() }

        single { DesktopDownloadIntake(delegate = get(), downloadRepository = get()) }
        single<DownloadIntake> { get<DesktopDownloadIntake>() }

        single {
            DesktopAutoDownloadScheduler(
                downloadsStore = get(),
                downloadRepository = get(),
                downloadIntake = get(),
                episodeCatalogue = get(),
                scope = get(DatastoreQualifiers.applicationScope),
            )
        }
    }
}
