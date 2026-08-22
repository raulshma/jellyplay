package com.raulshma.jellyplay.core.datastore.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.LibraryViewMode
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
 * Exercises the library-browsing preference store, focusing on the
 * read-modify-write JSON map setters that previously lived inline in the
 * `UserPreferencesStore` god object with no unit coverage.
 */
class LibraryStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: LibraryStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = LibraryStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.library.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.library.first()
        assertEquals(LibraryViewMode.GRID, slice.libraryViewMode)
        assertEquals(true, slice.episodesDescending)
        assertEquals(false, slice.hideEpisodeThumbnails)
        assertEquals(false, slice.skipSpecials)
        assertEquals(false, slice.compactEpisodeList)
        assertEquals(true, slice.showDetailUpNext)
        assertEquals(emptyMap<String, String>(), slice.defaultLibrarySortOrders)
    }

    @Test
    fun `setLibraryViewMode round-trips`() = runTest {
        store.setLibraryViewMode(LibraryViewMode.LIST)
        assertEquals(LibraryViewMode.LIST, store.library.first().libraryViewMode)
    }

    @Test
    fun `setShowDetailUpNext round-trips`() = runTest {
        store.setShowDetailUpNext(false)
        assertEquals(false, store.library.first().showDetailUpNext)
        store.setShowDetailUpNext(true)
        assertEquals(true, store.library.first().showDetailUpNext)
    }

    @Test
    fun `setCompactEpisodeList round-trips`() = runTest {
        store.setCompactEpisodeList(true)
        assertEquals(true, store.library.first().compactEpisodeList)
        store.setCompactEpisodeList(false)
        assertEquals(false, store.library.first().compactEpisodeList)
    }

    @Test
    fun `setDefaultLibrarySortOrder merges into the map`() = runTest {
        store.setDefaultLibrarySortOrder("lib_a", "SortName")
        store.setDefaultLibrarySortOrder("lib_b", "DateCreated")
        val slice = store.library.first()
        assertEquals(slice.defaultLibrarySortOrders["lib_a"], "SortName")
        assertEquals(slice.defaultLibrarySortOrders["lib_b"], "DateCreated")
    }
}
