package com.raulshma.jellyplay.core.network.di

import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.desktopDatastoreModule
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.JellyfinApiClientImpl
import com.raulshma.jellyplay.core.network.config.OkHttpConfig
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import com.raulshma.jellyplay.core.network.realtime.ActivityLogRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.ScheduledTasksRealtimeChannel
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Smoke test for the C4 Koin construction owner: loads [networkJvmModule] +
 * [desktopNetworkModule] against the (concurrently authored) datastore
 * modules and verifies the shapes the Hilt bridges depend on — the facade
 * aliasing its impl, the relocated realtime channels, and the derived
 * streaming/download clients being distinct instances of the base client.
 *
 * The network definitions resolve no database types, so no database module is
 * loaded here.
 */
class NetworkKoinModulesTest {

    private val testConfigProvider = object : OkHttpConfigProvider {
        override val config = MutableStateFlow(
            OkHttpConfig(
                maxCacheSizeMb = 0,
                networkTimeoutPreset = NetworkTimeoutPreset.DEFAULT,
                verboseNetworkLogging = false,
            ),
        )
    }

    @Test
    fun facadeChannelsAndDerivedClientsResolve() {
        val dataDir = createTempDirectory("jellyplay-network-test-data").toString().toPath()
        val configDir = createTempDirectory("jellyplay-network-test-config").toString().toPath()
        val app = startKoin {
            modules(
                module { single<OkHttpConfigProvider> { testConfigProvider } },
                datastoreCommonModule,
                desktopDatastoreModule(dataDir),
                networkJvmModule,
                desktopNetworkModule(configDir),
            )
        }
        try {
            val koin = app.koin

            // The Hilt bridge for JellyfinApiClient must resolve to the same
            // singleton the concrete facade definition builds.
            val facade = koin.get<JellyfinApiClient>()
            assertTrue(facade is JellyfinApiClientImpl)
            assertSame(koin.get<JellyfinApiClientImpl>(), facade)

            // Relocated realtime channels construct off the shared engine /
            // WebSocket client / application scope.
            assertResolves<ActivityLogRealtimeChannel>(koin)
            assertResolves<ScheduledTasksRealtimeChannel>(koin)
            assertResolves<UserDataRealtimeChannel>(koin)

            // Base + derived clients are three distinct instances (the
            // derived ones are newBuilder() clones sharing the pool).
            val base = koin.get<OkHttpClient>()
            val streaming = koin.get<OkHttpClient>(NetworkQualifiers.streamingHttpClient)
            val download = koin.get<OkHttpClient>(NetworkQualifiers.downloadHttpClient)
            assertTrue(base !== streaming)
            assertTrue(base !== download)
            assertTrue(streaming !== download)
            // Derived-client timeout contract (30s floor / 30-60-30 download).
            assertTrue(streaming.readTimeoutMillis >= 30_000)
            assertTrue(download.connectTimeoutMillis == 30_000)
            assertTrue(download.readTimeoutMillis == 60_000)
            assertTrue(download.writeTimeoutMillis == 30_000)
        } finally {
            stopKoin()
            dataDir.toFile().deleteRecursively()
            configDir.toFile().deleteRecursively()
        }
    }

    private inline fun <reified T : Any> assertResolves(koin: org.koin.core.Koin) {
        assertTrue(koin.get<T>() is T, "Koin could not resolve ${T::class.simpleName}")
    }
}
