package com.raulshma.jellyplay.core.datastore.downloads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
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
            store = DownloadsStore(dataStore, scope)
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
    fun `setDownloadScheduleWindow round-trips`() = runTest {
        val window = DownloadScheduleWindow(startHour = 2, endHour = 5, wifiOnly = false)
        store.setDownloadScheduleWindow(window)
        val slice = store.downloads.first()
        assertEquals(2, slice.downloadScheduleWindow.startHour)
        assertEquals(5, slice.downloadScheduleWindow.endHour)
        assertEquals(false, slice.downloadScheduleWindow.wifiOnly)
    }
}
