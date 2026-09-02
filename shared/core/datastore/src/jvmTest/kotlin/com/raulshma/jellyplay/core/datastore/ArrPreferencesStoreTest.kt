package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.raulshma.jellyplay.core.model.arr.ArrPreferences
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the *arr preference store: the int/boolean DataStore keys (with the
 * poll-interval clamp), the manual-server credential passthrough into
 * [ArrPreferences.manualServers] (non-manual configs are filtered), and the
 * parse-error degrade — a corrupt encrypted blob yields an empty manual list
 * rather than throwing.
 */
class ArrPreferencesStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var secureStorage: FakeSecureKeyValueStorage
    private lateinit var secureCredentialsStore: ArrSecureCredentialsStore
    private lateinit var store: ArrPreferencesStore

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            secureStorage = FakeSecureKeyValueStorage()
            secureCredentialsStore = ArrSecureCredentialsStore(secureStorage)
            store = ArrPreferencesStore(dataStore, secureCredentialsStore, scope)
            // Drain the Eagerly-cached state flow so the cleared state is
            // observed before each test writes + reads.
            store.preferences.first()
        }
    }

    private fun radarr(isManual: Boolean = true) = ArrServerConfig(
        id = "radarr-1",
        baseUrl = "http://radarr.local:7878",
        apiKey = "radarr-key",
        name = "Radarr",
        kind = ArrServiceKind.RADARR,
        isManual = isManual,
    )

    private fun sonarr(isManual: Boolean = true) = ArrServerConfig(
        id = "sonarr-1",
        baseUrl = "http://sonarr.local:8989",
        apiKey = "sonarr-key",
        name = "Sonarr",
        kind = ArrServiceKind.SONARR,
        isManual = isManual,
    )

    @Test
    fun `defaults when empty`() = runTest {
        assertEquals(ArrPreferences(), store.preferences.first())
    }

    @Test
    fun `boolean key persists useSeerrDiscovery`() = runTest {
        store.setUseSeerrDiscovery(false)
        assertFalse(store.preferences.first().useSeerrDiscovery)

        store.setUseSeerrDiscovery(true)
        assertTrue(store.preferences.first().useSeerrDiscovery)
    }

    @Test
    fun `int key persists poll interval and clamps below the floor`() = runTest {
        store.setPollIntervalSeconds(120)
        assertEquals(120, store.preferences.first().pollIntervalSeconds)

        store.setPollIntervalSeconds(5)
        assertEquals(15, store.preferences.first().pollIntervalSeconds)
    }

    @Test
    fun `manual servers pass through into preferences`() = runTest {
        val manualRadarr = radarr(isManual = true)
        val manualSonarr = sonarr(isManual = true)
        val autoDiscovered = sonarr(isManual = false)

        store.setManualServers(listOf(manualRadarr, autoDiscovered, manualSonarr))

        val prefs = store.preferences.first()
        // setManualServers filters to manual entries only before persisting.
        assertEquals(listOf(manualRadarr, manualSonarr), prefs.manualServers)
        assertEquals(manualRadarr.apiKey, prefs.manualServers.first().apiKey)
        // The secure store holds the same list for the next read.
        assertEquals(listOf(manualRadarr, manualSonarr), secureCredentialsStore.getManualServers())
    }

    @Test
    fun `corrupt manual-server blob degrades to an empty list`() = runTest {
        // Seed garbage directly into the encrypted storage before construction —
        // getManualServers must swallow the parse failure.
        secureStorage.raw["arr_manual_servers"] = "{not a json array"

        val degradedStore = ArrPreferencesStore(dataStore, secureCredentialsStore, scope)

        assertEquals(emptyList(), secureCredentialsStore.getManualServers())
        assertEquals(emptyList(), degradedStore.preferences.first().manualServers)
    }

    @Test
    fun `typed poll interval key round-trips through the shared file`() = runTest {
        store.setPollIntervalSeconds(90)
        dataStore.data.first().let { prefs ->
            assertEquals(90, prefs[intPreferencesKey("arr_poll_interval_seconds")])
        }
    }
}
