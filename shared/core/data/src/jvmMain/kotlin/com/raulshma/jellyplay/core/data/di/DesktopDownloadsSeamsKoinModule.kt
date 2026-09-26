package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.download.DesktopDownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadOutcomeMessenger
import com.raulshma.jellyplay.core.data.repository.DesktopDownloadStorageLayout
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueueCoordinator
import com.raulshma.jellyplay.core.data.repository.DownloadProgressNotifier
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayoutContract
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryAccess
import com.raulshma.jellyplay.core.data.repository.OfflineImagePreloader
import com.raulshma.jellyplay.core.data.worker.DesktopAutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * V3 downloads conveyor family of the desktopDataModule split: the desktop
 * actuals of the portable download engine's seams — the appdata storage
 * layout, the in-process DesktopDownloadManager (the
 * DownloadEnqueueCoordinator actual: enqueue = transfer-loop kick,
 * cancelWork = cooperative stop), no-op notification / image-preload
 * surfaces, the desktop DownloadIntake, the quick-action outcome
 * messenger, and the 6 h auto-download loop. Since the MediaRepository
 * cluster flip the MediaRepositoryAccess actual is REAL (Koin owns
 * MediaRepositoryImpl on desktop too) — series downloads and
 * auto-download work end-to-end. Binding bodies moved verbatim from the
 * pre-split single-module layout — see [desktopDataModule] for the
 * aggregate and the family map.
 */
internal fun desktopDownloadsSeamsModule(dataDir: Path): Module = module {
    // ── V3 downloads conveyor: desktop actuals of the engine seams ──────

    single<DownloadStorageLayoutContract> { DesktopDownloadStorageLayout(dataDir) }

    single<DownloadProgressNotifier> { DownloadProgressNotifier { /* no summary surface on desktop */ } }

    single<OfflineImagePreloader> { OfflineImagePreloader { /* no shared preload cache on desktop */ } }

    //  MediaRepository cluster flip: MediaRepository is now
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

    single {
        DesktopDownloadIntake(
            delegate = get(),
            downloadRepository = get(),
            mediaRepository = get(),
            downloadsStore = get(),
        )
    }
    single<DownloadIntake> { get<DesktopDownloadIntake>() }

    // Desktop actual of the quick-action download-outcome seam
    // (MediaDownloadActions.downloadAndReport posts Started/Failed
    // through it): Android bridges this to core/ui's UserMessageBus
    // snackbar via androidAppInteropAdaptersModule, but desktop has no
    // global toast host in core/data's reach — and core/data must not
    // depend on core/ui. Console-logging keeps the outcome visible in the
    // desktop log without inventing UI plumbing here; the transfer itself
    // is observable through the downloads screen either way.
    single<DownloadOutcomeMessenger> {
        object : DownloadOutcomeMessenger {
            override fun downloadStarted() {
                println("[downloads] download started")
            }

            override fun downloadStartFailed() {
                println("[downloads] download failed to start")
            }
        }
    }

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
