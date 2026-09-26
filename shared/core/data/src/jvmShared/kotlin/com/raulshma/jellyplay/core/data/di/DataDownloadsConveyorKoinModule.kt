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
import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriterCore
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
    // desktop in desktopDataModule). Consumers (PlayedStateSyncImpl,
    // OfflinePlaybackFacade, AudioLibraryBrowser, workers, feature modules)
    // resolve this single from Koin directly.
    //
    // D6: the artifact-write half lives in the OfflineDownloadWriterCore
    // single below — over its own DAOs and seams with NO back-reference to
    // the repository — so the graph is a straight line (writer → delegate →
    // repo, writer → repo) and the repository's former Lazy<DownloadDelegate>
    // deferral is gone.
    single {
        OfflineDownloadWriterCore(
            downloadDao = get(),
            offlineMediaDao = get(),
            playbackStateDao = get(),
            syncBaselineDao = get(),
            database = get(),
            downloadsStore = get(),
            storagePolicy = get(),
            storageLayout = get<DownloadStorageLayoutContract>(),
            syncComparator = get(),
            downloadEnqueuer = get<DownloadEnqueueCoordinator>(),
            imagePreloader = get<OfflineImagePreloader>(),
            playbackRepository = get(),
            playbackIdentity = get(),
            httpClient = get(),
            json = get(),
            timeSource = get(),
            mediaRepository = get<MediaRepositoryAccess>(),
        )
    }

    // The narrow write surface DownloadDelegate depends on — the former
    // bindOfflineDownloadWriter @Binds. No longer an alias of the repository:
    // the standalone writer core IS the implementation (the repository still
    // carries the interface — DownloadRepository extends it — but forwards
    // those members to this same core).
    single<OfflineDownloadWriter> { get<OfflineDownloadWriterCore>() }

    // Per-item download recipe (prepare + execute + artifact bundle). The
    // writer edge resolves to the writer-core single above; no construction
    // cycle remains, so the repository receives the delegate eagerly.
    single {
        DownloadDelegate(
            writer = get<OfflineDownloadWriterCore>(),
            playbackRepository = get(),
        )
    }

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
            downloadsStore = get(),
            storagePolicy = get(),
            downloadEnqueuer = get<DownloadEnqueueCoordinator>(),
            progressNotifier = get<DownloadProgressNotifier>(),
            writer = get(),
            downloadDelegate = get(),
        )
    }
    single<DownloadRepository> { get<DownloadRepositoryImpl>() }

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
