package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.network.OkHttpConfigProviderImpl
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.LocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.RealtimeConnection
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.StoragePolicy
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.TimeSource
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
 * DownloadRepository is deliberately NOT resolved: it has no Koin definition
 * (Hilt-owned until Phase X — WorkManager-coupled DownloadRepositoryImpl).
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
