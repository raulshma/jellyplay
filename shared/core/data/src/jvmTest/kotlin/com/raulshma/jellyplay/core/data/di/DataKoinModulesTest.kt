package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.network.OkHttpConfigProviderImpl
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueueCoordinator
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadStorageLayoutContract
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.LocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.MediaDetailProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryAccess
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryCacheInvalidation
import com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.StoragePolicy
import com.raulshma.jellyplay.core.data.repository.UnifiedMediaDetailProviderImpl
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.worker.DesktopAutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.di.databaseDaosModule
import com.raulshma.jellyplay.core.database.di.desktopDatabaseModule
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.desktopDatastoreModule
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import com.raulshma.jellyplay.core.network.di.desktopNetworkModule
import com.raulshma.jellyplay.core.network.di.networkJvmModule
import com.raulshma.jellyplay.core.network.subtitle.SubtitleProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Smoke test for the C4 part-2 data construction owner: loads the full
 * desktop graph (datastore + database + network + data, common plus desktop
 * platform picks) and resolves the shapes the Hilt bridges depend on — the
 * repository facades, the OkHttpConfigProvider whose definition moved here
 * from the app composition root (DI-finalize stage), the shared-single alias
 * contracts, the session / syncplay cluster, and the subtitle-provider map
 * flipped from @IntoMap.
 *
 * DownloadRepository (and with the V3 downloads conveyor, the whole download
 * engine — delegate, transfer machinery, in-process manager, auto-download
 * loop) IS resolved since the conveyor moved it into this module; the
 * MediaRepository edge was a documented throwing-lazy on desktop until the
 * Phase X cluster flip made it real.
 *
 * The Phase X MediaRepository cluster flip moved the last Hilt-owned cluster
 * here — MediaRepository(+ its PlayedStateSync / cache-invalidation /
 * LyricsRepository views), UserDataMutator, MediaSearchEngine,
 * OfflineFirstItemResolver and OfflinePlaybackFacade all resolve on desktop.
 * MediaDetailProvider was the one holdout until the playback-flips wave moved
 * PlaybackSourceResolverImpl into this module Uri-free (`File.toURI()`), so
 * the provider and its concrete impl resolve on desktop too.
 *
 * The admin flip (Wave wB) followed: AdminRepository and
 * AdminStatisticsRepository (label seam satisfied by the desktop
 * English-literals actual) resolve on desktop too — the desktop settings +
 * admin nav sections ride on that.
 */
class DataKoinModulesTest {

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun fullDesktopGraphResolvesDataSingles() {
        // SyncPlayPlaybackCore constructs its scope on Dispatchers.Main.immediate;
        // plain JVM has no Main dispatcher module, so stand one up for the
        // test (nothing launches on it — construction only).
        Dispatchers.setMain(StandardTestDispatcher())
        val dataDir = createTempDirectory("jellyplay-data-test-data").toString().toPath()
        val dbDir = createTempDirectory("jellyplay-data-test-db")
        val dbPath = dbDir.resolve("jellyplay.db").toString().toPath()
        val configDir = createTempDirectory("jellyplay-data-test-config").toString().toPath()
        val app = startKoin {
            modules(
                datastoreCommonModule,
                desktopDatastoreModule(dataDir),
                databaseDaosModule,
                desktopDatabaseModule(dbPath),
                networkJvmModule,
                desktopNetworkModule(configDir),
                dataJvmModule,
                desktopDataModule(dbDir),
            )
        }
        val database = app.koin.get<JellyPlayDatabase>()
        try {
            val koin = app.koin

            // The definition relocated from the app's appNetworkConfigModule:
            // networkJvmModule's base OkHttpClient resolves it cross-module.
            val configProvider = koin.get<OkHttpConfigProvider>()
            assertTrue(configProvider is OkHttpConfigProviderImpl)

            // Repository facades resolve to the shared AuthRepositoryImpl
            // single: the RealtimeConnection alias is the same socket, not a
            // second connection (the legacy bindRealtimeConnection @Binds).
            val auth = koin.get<AuthRepository>()
            assertTrue(
                koin.get<RealtimeConnection>() === auth,
                "RealtimeConnection must alias the AuthRepository single (one socket, not two)",
            )

            // Representative set across the moved cluster — each resolution
            // walks the full ctor graph (DAOs, stores, API clients, scope).
            assertResolves<PlaybackRepository>(koin)
            assertResolves<OfflineRepository>(koin)
            assertResolves<SeerrRepository>(koin)
            assertResolves<ArrRepository>(koin)
            assertResolves<StoragePolicy>(koin)
            assertResolves<TimeSource>(koin)
            assertResolves<HomeSession>(koin)
            assertResolves<SessionCacheRegistry>(koin)
            assertResolves<SyncPlayManager>(koin)
            assertResolves<EpisodeCatalogue>(koin)

            // The @IntoMap subtitle fan-out flipped to Koin: same two keys the
            // legacy SubtitleProviderModule built, values wrapped resilient.
            val subtitleMap = koin.get<Map<SubtitleProviderKind, SubtitleProvider>>()
            assertEquals(
                setOf(SubtitleProviderKind.WYZIE, SubtitleProviderKind.OPENSUBTITLES),
                subtitleMap.keys,
            )

            // Platform picks exist on desktop too — every type dataJvmModule
            // consumes via get() has a desktopDataModule counterpart.
            assertResolves<NetworkMonitor>(koin)
            assertResolves<OfflineModeManager>(koin)
            assertResolves<ImageUrlProvider>(koin)
            assertResolves<LocalStreamProbe>(koin)

            // V3 downloads conveyor: the download engine resolves on desktop —
            // the repository single (seams satisfied by desktopDataModule's
            // actuals), its OfflineDownloadWriter view (same instance), the
            // per-item delegate, and the in-process manager/scheduler behind
            // the enqueue-coordinator seam. The MediaRepositoryAccess desktop
            // def is the documented throwing-lazy — constructed eagerly here
            // (cheap), only invoked on the series paths.
            val repository = koin.get<DownloadRepository>()
            assertTrue(
                koin.get<OfflineDownloadWriter>() === repository,
                "OfflineDownloadWriter must alias the DownloadRepository single (one instance, not two)",
            )
            assertResolves<DownloadDelegate>(koin)
            assertResolves<DownloadEnqueueCoordinator>(koin)
            assertResolves<DownloadIntake>(koin)
            assertResolves<DownloadStorageLayoutContract>(koin)
            assertResolves<MediaRepositoryAccess>(koin)
            assertResolves<DesktopDownloadManager>(koin)
            assertResolves<DesktopAutoDownloadScheduler>(koin)

            // ── Phase X MediaRepository cluster flip ─────────────────────────
            // The last Hilt-owned data cluster, now Koin-owned on BOTH
            // platforms. MediaDetailProvider and its concrete impl resolve
            // since the playback-flips wave moved PlaybackSourceResolverImpl
            // into dataJvmModule (Uri-free) — no longer latent on desktop.
            val mediaRepository = koin.get<MediaRepository>()
            assertTrue(
                koin.get<MediaRepositoryCacheInvalidation>() === mediaRepository,
                "MediaRepositoryCacheInvalidation must alias the MediaRepository single (one cache set, not two)",
            )
            assertTrue(
                koin.get<LyricsRepository>() === mediaRepository,
                "LyricsRepository must alias the MediaRepository single (MediaRepository extends it)",
            )
            assertResolves<PlayedStateSync>(koin)
            assertResolves<UserDataMutator>(koin)
            assertResolves<MediaSearchEngine>(koin)
            assertResolves<OfflineFirstItemResolver>(koin)
            assertResolves<OfflinePlaybackFacade>(koin)
            assertResolves<com.raulshma.jellyplay.core.data.playback.AudioLyricsManager>(koin)
            assertResolves<MediaDetailProvider>(koin)
            assertResolves<UnifiedMediaDetailProviderImpl>(koin)

            // ── Admin flip (Wave wB) ──────────────────────────────────────
            // Both admin repositories resolve on desktop: every ctor dep is
            // Koin-native (API client/engine + realtime channels from
            // networkJvmModule, DAOs from databaseDaosModule, Json from
            // networkJvmModule, application scope from DatastoreQualifiers,
            // and the label seam's desktop actual — English literals — from
            // desktopDataModule).
            assertResolves<AdminRepository>(koin)
            assertResolves<AdminStatisticsRepository>(koin)

            // ── AppUpdate split (Wave xB) ──────────────────────────────────
            // The update repository resolves on desktop (About's update-check
            // row): GitHubReleasesApi from networkJvmModule, the download
            // client from desktopNetworkModule's qualified single, and the
            // no-self-update platform inputs (appdata updates dir, sentinel
            // version, "desktop" flavor) from desktopDataModule.
            assertResolves<AppUpdateRepository>(koin)
        } finally {
            database.close()
            stopKoin()
            Dispatchers.resetMain()
            dataDir.toFile().deleteRecursively()
            dbDir.toFile().deleteRecursively()
            configDir.toFile().deleteRecursively()
        }
    }

    private inline fun <reified T : Any> assertResolves(koin: Koin) {
        assertTrue(koin.get<T>() is T, "Koin could not resolve ${T::class.simpleName}")
    }
}
