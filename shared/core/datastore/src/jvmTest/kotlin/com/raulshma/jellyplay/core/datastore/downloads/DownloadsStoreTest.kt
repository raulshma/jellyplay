package com.raulshma.jellyplay.core.datastore.downloads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Exercises the downloads preference store, focusing on the
 * `MAX_CONCURRENT_DOWNLOADS` coerce-in(1, 6) read + write invariant that
 * previously lived inline in the `UserPreferencesStore` god object with no unit
 * coverage.
 */
class DownloadsStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: DownloadsStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = DownloadsStore(dataStore, scope, ServerIdentityStore(dataStore, scope))
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.downloads.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.downloads.first()
        assertEquals(true, slice.wifiOnlyDownloads)
        assertEquals(4, slice.downloadConnections)
        // Default 3 is within the 1..6 band, so no clamping.
        assertEquals(3, slice.maxConcurrentDownloads)
        assertEquals(DownloadQuality.ORIGINAL, slice.downloadQuality)
        assertEquals(slice.downloadStorageLocation, "INTERNAL")
        assertEquals(DownloadScheduleWindow(), slice.downloadScheduleWindow)
        assertEquals(false, slice.downloadScheduleEnabled)
    }

    @Test
    fun `setMaxConcurrentDownloads clamps above the band`() = runTest {
        store.setMaxConcurrentDownloads(99)
        val slice = store.downloads.first()
        assertEquals(6, slice.maxConcurrentDownloads)
    }

    @Test
    fun `setMaxConcurrentDownloads clamps below the band`() = runTest {
        store.setMaxConcurrentDownloads(0)
        val slice = store.downloads.first()
        assertEquals(1, slice.maxConcurrentDownloads)
    }

    @Test
    fun `setMaxConcurrentDownloads preserves in-band value`() = runTest {
        store.setMaxConcurrentDownloads(2)
        assertEquals(2, store.downloads.first().maxConcurrentDownloads)
    }

    @Test
    fun `read clamps a raw above-band stored value`() = runTest {
        // Write 99 directly under the key, bypassing the setter's coerce, to
        // confirm the READ projection also enforces the 1..6 invariant.
        dataStore.edit { it[intPreferencesKey("max_concurrent_downloads")] = 99 }
        val slice = store.downloads.first()
        assertEquals(6, slice.maxConcurrentDownloads)
    }

    @Test
    fun `read clamps a raw below-band stored value`() = runTest {
        dataStore.edit { it[intPreferencesKey("max_concurrent_downloads")] = 0 }
        val slice = store.downloads.first()
        assertEquals(1, slice.maxConcurrentDownloads)
    }

    @Test
    fun `corrupt download_quality falls back to the default, siblings keep real values`() = runTest {
        store.setSmartDownloadsEnabled(true)
        dataStore.edit { it[stringPreferencesKey("download_quality")] = "nonsense" }
        val slice = store.downloads.first()
        assertEquals(DownloadQuality.ORIGINAL, slice.downloadQuality)
        assertEquals(true, slice.smartDownloadsEnabled)
    }

    @Test
    fun `setDownloadScheduleWindow round-trips`() = runTest {
        val window = DownloadScheduleWindow(startHour = 2, endHour = 5, wifiOnly = false)
        store.setDownloadScheduleWindow(window)
        val slice = store.downloads.first()
        assertEquals(2, slice.downloadScheduleWindow.startHour)
        assertEquals(5, slice.downloadScheduleWindow.endHour)
        assertEquals(false, slice.downloadScheduleWindow.wifiOnly)
    }

    // ── Auto-download retention policy ────────────────────────────────

    @Test
    fun `retention defaults - lookahead 3, per-pass 0, keep-days 0, empty allow-list`() = runTest {
        val slice = store.downloads.first()
        assertEquals(3, slice.autoDownloadLookahead)
        assertEquals(0, slice.autoDownloadMaxPerPass)
        assertEquals(0, slice.autoDownloadKeepDays)
        assertEquals(emptySet(), slice.autoDownloadServers)
    }

    @Test
    fun `retention setters coerce - lookahead band, per-pass band, keep-days floor`() = runTest {
        store.setAutoDownloadLookahead(99)
        store.setAutoDownloadMaxPerPass(99)
        store.setAutoDownloadKeepDays(-5)
        val slice = store.downloads.first()
        assertEquals(10, slice.autoDownloadLookahead)
        assertEquals(50, slice.autoDownloadMaxPerPass)
        assertEquals(0, slice.autoDownloadKeepDays)

        store.setAutoDownloadLookahead(-1)
        store.setAutoDownloadMaxPerPass(-1)
        assertEquals(0, store.downloads.first().autoDownloadLookahead)
        assertEquals(0, store.downloads.first().autoDownloadMaxPerPass)
    }

    @Test
    fun `read clamps raw above-band retention values`() = runTest {
        // Write directly under the keys, bypassing the setters' coerce, to
        // confirm the READ projection also enforces the bands.
        dataStore.edit {
            it[intPreferencesKey("auto_download_lookahead")] = 99
            it[intPreferencesKey("auto_download_max_per_pass")] = 99
        }
        val slice = store.downloads.first()
        assertEquals(10, slice.autoDownloadLookahead)
        assertEquals(50, slice.autoDownloadMaxPerPass)
    }

    @Test
    fun `auto-download servers round-trips into the active user's namespace`() = runTest {
        val identity = ServerIdentityStore(dataStore, scope)
        identity.setActiveUser("user-1")

        store.setAutoDownloadServers(setOf("srv-1", "srv-2"))

        val slice = store.downloads.first()
        assertEquals(setOf("srv-1", "srv-2"), slice.autoDownloadServers)
        // The key lives under u_<userId>::, never as a flat canonical key.
        assertTrue(dataStore.data.first().asMap().keys.any { it.name == "u_user-1::auto_download_servers" })
        assertTrue(dataStore.data.first().asMap().keys.none { it.name == "auto_download_servers" })
    }

    @Test
    fun `auto-download servers are per-user - a second user reads the default`() = runTest {
        val identity = ServerIdentityStore(dataStore, scope)
        identity.setActiveUser("user-1")
        store.setAutoDownloadServers(setOf("srv-1"))

        identity.setActiveUser("user-2")
        assertEquals(emptySet(), store.downloads.first().autoDownloadServers)

        identity.setActiveUser("user-1")
        assertEquals(setOf("srv-1"), store.downloads.first().autoDownloadServers)
    }

    @Test
    fun `auto-download servers write is skipped pre-login and corrupt JSON degrades to default`() = runTest {
        // No active user: no namespace to write into.
        store.setAutoDownloadServers(setOf("srv-1"))
        assertEquals(emptySet(), store.downloads.first().autoDownloadServers)

        // A corrupted blob under the namespaced key degrades to the default.
        val identity = ServerIdentityStore(dataStore, scope)
        identity.setActiveUser("user-1")
        dataStore.edit { it[stringPreferencesKey("u_user-1::auto_download_servers")] = "nonsense" }
        assertEquals(emptySet(), store.downloads.first().autoDownloadServers)
    }
}
