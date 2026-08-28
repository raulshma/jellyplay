package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.download.DesktopDownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.network.DesktopNetworkMonitor
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.DesktopOfflineModeManager
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsLabelProvider
import com.raulshma.jellyplay.core.data.repository.DesktopAdminStatisticsLabels
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
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStoreImpl
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.AppUpdateRepositoryImpl
import com.raulshma.jellyplay.core.data.util.DesktopImageUrlProvider
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.DataBuildFlags
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.DesktopAutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.network.di.NetworkQualifiers
import java.io.File
import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop platform pick of the Koin-owned data layer (Phase C4 part 2).
 * Holds the always-connected connectivity seams, the LinkedHashMap-based
 * image-URL memoiser, the (unsupported, badge-less) desktop stream
 * probe, and the file-backed StreamingSubtitleStore (wave 18B); everything
 * else resolves from [dataJvmModule].
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

        // Phase X admin flip: desktop actual of the admin-statistics label
        // seam — base-locale English literals (see the object's kdoc for the
        // accepted locale delta). The Android actual lives in the app
        // composition root (androidAdminSeamsModule) over legacy core:data
        // R.string.
        single<AdminStatisticsLabelProvider> { DesktopAdminStatisticsLabels }

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

        // ── Home conveyor desktop actuals (wave 8B): the four WorkManager/ ──
        // widget-backed HomeViewModel ctor deps have no desktop machinery
        // behind them, so these are honest no-ops mirroring the Android impls'
        // shapes (Android: PlaybackSyncScheduler/TvWatchNextScheduler live in
        // androidCoreDataModule, ContinueWatchingBroadcaster/LibrarySyncHook
        // in the app's androidAppModule).
        //  - PlaybackSyncScheduler: Android drains the playback-progress
        //    offline outbox via WorkManager; desktop stages rows into the
        //    same outbox (manual offline toggle or a transient HTTP
        //    failure) but ships no drain machinery — the WorkManager
        //    worker is Android-side per plan §Phase C4 — so staged rows
        //    sit. 17C's real network probe changes nothing here: staging
        //    keys off the manual offline mode, never the network seam.
        //  - TvWatchNextScheduler: the Android TV "Watch Next" OS row has no
        //    desktop equivalent.
        //  - ContinueWatchingBroadcaster: refreshes the Android app widget's
        //    RemoteViews service; no widgets on desktop.
        //  - LibrarySyncHook: fans a library scan out to Android's
        //    auto-download drain + widget refresh; both are no-ops here.
        single<PlaybackSyncScheduler> {
            object : PlaybackSyncScheduler {
                override fun enqueuePeriodic() {}
                override fun enqueueNow() {}
            }
        }
        single<TvWatchNextScheduler> {
            object : TvWatchNextScheduler {
                override fun scheduleRefresh() {}
            }
        }
        single<ContinueWatchingBroadcaster> {
            object : ContinueWatchingBroadcaster {
                override fun refreshContinueWatching() {}
            }
        }
        single<LibrarySyncHook> {
            object : LibrarySyncHook {
                override suspend fun onLibraryScanComplete() {}
            }
        }

        // ── Streaming-subtitle store (wave 18B promotion) ────────────────────
        // The impl moved out of the legacy Android-Hilt-owned :core:data shim
        // into jvmShared, so desktop gets the real file-backed store, not a
        // stub. baseDir is the appdata dir — the desktop twin of Android's
        // `filesDir`; the impl appends its own "streaming-subtitles" root
        // (same subtree name on both platforms). Backs the metadata editor's
        // external-provider subtitle downloads AND the player's
        // SubtitleManager provider-download path on desktop.
        single<StreamingSubtitleStore> {
            StreamingSubtitleStoreImpl(
                baseDir = dataDir.toFile(),
                json = get(),
            )
        }

        // ── AppUpdate split (Wave xB): the desktop update-check actual ──────
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
            )
        }
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
