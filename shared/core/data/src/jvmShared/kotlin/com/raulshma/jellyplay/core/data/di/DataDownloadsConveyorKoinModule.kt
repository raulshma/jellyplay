package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.download.QuickDownloadActions
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueueCoordinator
import com.raulshma.jellyplay.core.data.repository.DownloadProgressNotifier
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayoutContract
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryAccess
import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
import com.raulshma.jellyplay.core.data.repository.OfflineImagePreloader
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The V3 downloads-conveyor family of the dataJvmModule split (C4-part-2
 * notes, fifth conveyor item — see [dataJvmModule] for the
 * construction-owner rules): the portable download engine and its
 * quick-action delegates. Binding bodies moved verbatim from the pre-split
 * single-module layout.
 */
internal val dataDownloadsConveyorModule: Module = module {
    // ── V3 downloads conveyor: the portable download engine ────────────────
    // DownloadRepositoryImpl moved from the legacy :core:data shim with its
    // Android surfaces behind seams: enqueue/cancel → DownloadEnqueueCoordinator
    // (Android: WorkManager DownloadEnqueuer via the app's
    // androidDownloadSeamsModule; desktop: the in-process DesktopDownloadManager),
    // storage layout → DownloadStorageLayoutContract (Android: Context/StatFs
    // impl via the app module; desktop: appdata-based impl in desktopDataModule),
    // notification summary + Coil preloading → platform no-op-able fun
    // interfaces, and MediaRepository behind the deferred MediaRepositoryAccess
    // (both platform defs forward to this module's own MediaRepositoryImpl
    // single since the cluster flip — Android in androidDataModule,
    // desktop in desktopDataModule). `downloadDelegate` keeps the
    // construction cycle broken via a memoizing kotlin Lazy (the Lazy-deferred
    // pattern). Consumers (PlayedStateSyncImpl,
    // OfflinePlaybackFacade, AudioLibraryBrowser, workers, feature modules)
    // resolve this single from Koin directly.
    single {
        DownloadRepositoryImpl(
            downloadDao = get(),
            offlineMediaDao = get(),
            playbackStateDao = get(),
            syncBaselineDao = get(),
            database = get(),
            mediaRepository = get<MediaRepositoryAccess>(),
            episodeCatalogue = get(),
            playbackRepository = get(),
            playbackIdentity = get(),
            httpClient = get(),
            downloadsStore = get(),
            json = get(),
            downloadDelegate = lazy { get<DownloadDelegate>() },
            storagePolicy = get(),
            downloadEnqueuer = get<DownloadEnqueueCoordinator>(),
            storageLayout = get<DownloadStorageLayoutContract>(),
            syncComparator = get(),
            progressNotifier = get<DownloadProgressNotifier>(),
            imagePreloader = get<OfflineImagePreloader>(),
            timeSource = get(),
        )
    }
    single<DownloadRepository> { get<DownloadRepositoryImpl>() }

    // The narrow write surface the DownloadDelegate depends on — the former
    // bindOfflineDownloadWriter @Binds: same instance as the repository above
    // (the interface extends OfflineDownloadWriter), not a second repository.
    single<OfflineDownloadWriter> { get<DownloadRepository>() }

    // Per-item download recipe (prepare + execute + artifact bundle). The
    // writer edge resolves to the DownloadRepository single above; the Lazy in
    // the repository ctor defers this resolution, breaking the cycle.
    single {
        DownloadDelegate(
            writer = get<DownloadRepository>(),
            playbackRepository = get(),
        )
    }

    // The unified quick-action download/remove delegate every host surface
    // shares (library, favorites, search, detail rows). Koin-owned
    // construction per the jvmShared convention (see MediaDownloadActions'
    // kdoc): the scope is the DatastoreQualifiers application scope,
    // DownloadRepository/OfflineRepository are this module's own singles, and
    // DownloadIntake resolves from the platform data modules (Android:
    // AndroidCoreDataKoinModule's DownloadIntakeImpl; desktop:
    // desktopDataModule's DesktopDownloadIntake). The DownloadOutcomeMessenger
    // binding is platform-owned too — androidAppInteropAdaptersModule bridges
    // it to core/ui's UserMessageBus on Android, desktopDataModule provides a
    // desktop definition — because core/data must not depend on core/ui.
    single {
        MediaDownloadActions(
            scope = get(DatastoreQualifiers.applicationScope),
            downloadRepository = get<DownloadRepository>(),
            downloadIntake = get<DownloadIntake>(),
            offlineRepository = get(),
            messenger = get(),
        )
    }

    // The quick-download seam the library/favorites/studio/search hosts
    // inject. Since the promoted-interface pass its JVM actual IS the
    // MediaDownloadActions single (the class implements QuickDownloadActions
    // directly — the DownloadIntake precedent, no verbatim-forward adapter).
    single<QuickDownloadActions> { get<MediaDownloadActions>() }
}
