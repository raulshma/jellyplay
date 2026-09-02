package com.raulshma.jellyplay.core.data.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.network.config.OkHttpConfig
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath

/**
 * Config-mapping test for the [com.raulshma.jellyplay.core.data.network.OkHttpConfigProviderImpl]
 * bridge: a write into the NetworkOfflineStore preference domain must land in
 * the [OkHttpConfig] StateFlow the network layer reads — including the
 * self-signed trust set this wave added. Backed by a REAL preferences
 * DataStore over a per-test temp file (the datastore module's
 * TestDataStoreProvider is test-scope-private to its own module).
 *
 * The DataStore → slice → config chain hops through the IO dispatcher, so
 * assertions AWAIT the expected emission (`first { predicate }`) rather than
 * racing a synchronous read.
 */
class OkHttpConfigProviderImplTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var storeDir: File
    private lateinit var store: NetworkOfflineStore
    private lateinit var provider: OkHttpConfigProviderImpl

    @BeforeTest
    fun setUp() {
        runBlocking {
            // Unique per test run: AndroidX forbids two DataStore instances on
            // one file in the same process.
            storeDir = File(
                File(System.getProperty("java.io.tmpdir"), "jellyplay-okhttp-config-test"),
                "run-${System.nanoTime()}",
            ).apply { mkdirs() }
            val dataStore = PreferenceDataStoreFactory.createWithPath(scope = ioScope) {
                File(storeDir, "user_prefs.preferences_pb").absolutePath.toPath()
            }
            store = NetworkOfflineStore(dataStore, scope)
            provider = OkHttpConfigProviderImpl(
                networkOfflineStore = store,
                scope = scope,
            )
            // Drain the Eagerly-shared flows so the test observes writes made
            // AFTER this point (same discipline as NetworkOfflineStoreTest).
            awaitConfig { true }
        }
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        ioScope.cancel()
        storeDir.deleteRecursively()
    }

    /** Suspends until the provider's config StateFlow emits a matching value. */
    private suspend fun awaitConfig(predicate: (OkHttpConfig) -> Boolean): OkHttpConfig =
        withTimeout(5_000) { provider.config.first { predicate(it) } }

    @Test
    fun `initial config carries the empty defaults`() = runBlocking {
        val config = awaitConfig { true }

        assertEquals(0, config.maxCacheSizeMb)
        assertEquals(NetworkTimeoutPreset.DEFAULT, config.networkTimeoutPreset)
        assertEquals(false, config.verboseNetworkLogging)
        assertEquals(emptySet(), config.selfSignedTrustHosts)
    }

    @Test
    fun `granting a trust host flows into the config`() = runBlocking {
        store.addSelfSignedTrustHost("https://192.168.1.10:8920")

        val config = awaitConfig { it.selfSignedTrustHosts.isNotEmpty() }

        assertEquals(
            setOf("https://192.168.1.10:8920"),
            config.selfSignedTrustHosts,
            "the granted set must land in the network layer's live config",
        )
    }

    @Test
    fun `revoking a trust host flows into the config`() = runBlocking {
        store.addSelfSignedTrustHost("https://192.168.1.10:8920")
        store.addSelfSignedTrustHost("https://other.example.com")
        awaitConfig { it.selfSignedTrustHosts.size == 2 }

        store.removeSelfSignedTrustHost("https://192.168.1.10:8920")

        val config = awaitConfig { !it.selfSignedTrustHosts.contains("https://192.168.1.10:8920") }
        assertEquals(
            setOf("https://other.example.com"),
            config.selfSignedTrustHosts,
        )
    }

    @Test
    fun `sibling preferences keep mapping alongside the trust set`() = runBlocking {
        store.setVerboseNetworkLogging(true)
        store.setNetworkTimeoutPreset(NetworkTimeoutPreset.RELAXED)
        store.setMaxCacheSize(256)
        store.addSelfSignedTrustHost("https://media.example.com")

        val config = awaitConfig { it.selfSignedTrustHosts.isNotEmpty() }

        assertEquals(true, config.verboseNetworkLogging)
        assertEquals(NetworkTimeoutPreset.RELAXED, config.networkTimeoutPreset)
        assertEquals(256, config.maxCacheSizeMb)
        assertEquals(setOf("https://media.example.com"), config.selfSignedTrustHosts)
    }

    @Test
    fun `state flow value stays live without a collector`() = runBlocking {
        // The network layer reads `.value` synchronously at handshake time —
        // no collector registered. Eager sharing must keep it current.
        store.addSelfSignedTrustHost("https://media.example.com")

        // Force the async DataStore → config chain to settle first...
        awaitConfig { "https://media.example.com" in it.selfSignedTrustHosts }
        // ...then read the way the trust manager does: plain .value.
        assertTrue(
            "https://media.example.com" in provider.config.value.selfSignedTrustHosts,
            "expected the grant in .value (handshake-time read path), got ${provider.config.value.selfSignedTrustHosts}",
        )
    }
}
