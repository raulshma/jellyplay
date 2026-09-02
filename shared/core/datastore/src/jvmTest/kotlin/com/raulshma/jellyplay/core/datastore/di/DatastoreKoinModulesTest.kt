package com.raulshma.jellyplay.core.datastore.di

import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Phase C4 smoke: the shared Koin modules resolve standalone (local
 * KoinApplication, not the global context) with only the platform module's
 * data directory as input.
 */
class DatastoreKoinModulesTest {

    @Test
    fun `common plus desktop modules resolve singleton scope and stores`() {
        val dataDir = createTempDirectory("jellyplay-datastore-koin")
        val app = startKoin {
            modules(datastoreCommonModule, desktopDatastoreModule(dataDir.toString().toPath()))
        }
        try {
            val koin = app.koin

            val scope1 = koin.get<CoroutineScope>(DatastoreQualifiers.applicationScope)
            val scope2 = koin.get<CoroutineScope>(DatastoreQualifiers.applicationScope)
            assertSame(scope1, scope2, "applicationScope must be a single Koin instance")

            assertNotNull(koin.get<AppearanceStore>())
            assertNotNull(koin.get<UserPreferencesStore>())
            assertNotNull(koin.get<VideoPlayerAggregateStore>())
            // Credential store over the desktop keyring-backed storage.
            assertNotNull(koin.get<SeerrSecureCredentialsStore>())
        } finally {
            stopKoin()
            dataDir.toFile().deleteRecursively()
        }
    }
}
