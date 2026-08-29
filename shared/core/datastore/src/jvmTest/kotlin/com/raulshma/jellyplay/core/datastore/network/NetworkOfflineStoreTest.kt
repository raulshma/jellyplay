package com.raulshma.jellyplay.core.datastore.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the network / offline-mode preference store extracted from the
 * `UserPreferencesStore` god object.
 */
class NetworkOfflineStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: NetworkOfflineStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = NetworkOfflineStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.networkOffline.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.networkOffline.first()
        assertEquals(false, slice.manualOfflineEnabled)
        assertEquals(true, slice.autoOfflineEnabled)
        assertEquals(0L, slice.manualBandwidthCap)
        assertEquals(MeteredNetworkBehavior.WARN, slice.meteredNetworkBehavior)
        assertEquals(NetworkTimeoutPreset.DEFAULT, slice.networkTimeoutPreset)
        assertEquals(0, slice.maxCacheSizeMb)
        assertEquals(true, slice.autoDeleteCache)
    }

    @Test
    fun `setManualBandwidthCap round-trips`() = runTest {
        store.setManualBandwidthCap(5_000_000L)
        assertEquals(5_000_000L, store.networkOffline.first().manualBandwidthCap)
    }

    @Test
    fun `setMeteredNetworkBehavior round-trips`() = runTest {
        store.setMeteredNetworkBehavior(MeteredNetworkBehavior.BLOCK)
        assertEquals(MeteredNetworkBehavior.BLOCK, store.networkOffline.first().meteredNetworkBehavior)
    }

    // ------------------------------- wave 21A: self-signed trust entries

    @Test
    fun `selfSignedTrustHosts default empty, grants and revokes round-trip`() = runTest {
        assertEquals(emptySet<String>(), store.networkOffline.first().selfSignedTrustHosts)

        store.addSelfSignedTrustHost("https://192.168.1.10:8920")
        store.addSelfSignedTrustHost("https://media.example.com")
        // Idempotent on repeat.
        store.addSelfSignedTrustHost("https://media.example.com")
        assertEquals(
            setOf("https://192.168.1.10:8920", "https://media.example.com"),
            store.networkOffline.first().selfSignedTrustHosts,
        )

        store.removeSelfSignedTrustHost("https://media.example.com")
        assertEquals(
            setOf("https://192.168.1.10:8920"),
            store.networkOffline.first().selfSignedTrustHosts,
        )
        // Revoking the last entry is fine (and idempotent).
        store.removeSelfSignedTrustHost("https://192.168.1.10:8920")
        store.removeSelfSignedTrustHost("https://192.168.1.10:8920")
        assertEquals(emptySet<String>(), store.networkOffline.first().selfSignedTrustHosts)
    }

    @Test
    fun `addSelfSignedTrustHost trims trailing slashes and ignores blanks`() = runTest {
        store.addSelfSignedTrustHost("  https://media.example.com/ ")
        assertEquals(setOf("https://media.example.com"), store.networkOffline.first().selfSignedTrustHosts)

        store.addSelfSignedTrustHost("")
        store.addSelfSignedTrustHost("   ")
        assertEquals(setOf("https://media.example.com"), store.networkOffline.first().selfSignedTrustHosts)
    }
}
