package com.raulshma.jellyplay.core.datastore.search

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises the search filter blob store: raw JSON string round-trip under its
 * single `search_filters` key, overwrite, clear, and the read-error degrade —
 * a failing DataStore must settle the state flow at the `null` default via the
 * `.catch { emptyPreferences() }` guard instead of throwing into the collector.
 */
class SearchFiltersStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SearchFiltersStore

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = SearchFiltersStore(dataStore, scope)
            // Drain the Eagerly-cached state flow so the cleared state is
            // observed before each test writes + reads.
            store.searchFiltersJson.first()
        }
    }

    @Test
    fun `defaults to null when no filters have been saved`() = runTest {
        assertNull(store.searchFiltersJson.first())
    }

    @Test
    fun `setSearchFilters round-trips the raw JSON string`() = runTest {
        val filtersJson = """{"genres":["Action","Sci-Fi"],"minRating":7.5,"sort":"Popularity"}"""

        store.setSearchFilters(filtersJson)

        assertEquals(filtersJson, store.searchFiltersJson.first())
    }

    @Test
    fun `setSearchFilters overwrites the previous blob`() = runTest {
        store.setSearchFilters("""{"genres":["Old"]}""")
        store.setSearchFilters("""{"genres":["New"]}""")

        assertEquals("""{"genres":["New"]}""", store.searchFiltersJson.first())
    }

    @Test
    fun `clearSearchFilters removes the blob`() = runTest {
        store.setSearchFilters("""{"year":2020}""")

        store.clearSearchFilters()

        assertNull(store.searchFiltersJson.first())
    }

    @Test
    fun `read errors degrade to the null default instead of throwing`() = runTest {
        val throwingStore = SearchFiltersStore(ThrowingDataStore(), scope)

        // The state flow is eagerly collected on construction; a DataStore whose
        // reads throw must be caught by the store's `.catch { emptyPreferences() }`
        // guard and surface as the null default (never rethrow / crash).
        assertNull(throwingStore.searchFiltersJson.first())
    }

    /**
     * Hand-written failing [DataStore] fake (module convention — no mockk): its
     * data flow throws the same way a corrupt/unreadable preferences file does.
     */
    private class ThrowingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            throw CorruptionException("simulated corrupt preferences file")
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            throw CorruptionException("simulated corrupt preferences file")
        }
    }
}
