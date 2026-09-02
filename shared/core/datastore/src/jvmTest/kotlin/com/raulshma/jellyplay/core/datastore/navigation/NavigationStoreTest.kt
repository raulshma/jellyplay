package com.raulshma.jellyplay.core.datastore.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the bottom-navigation customisation preference store extracted from
 * the `UserPreferencesStore` god object.
 */
class NavigationStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: NavigationStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = NavigationStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.navigation.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.navigation.first()
        assertEquals(true, slice.navBarShowLabels)
        assertEquals(true, slice.hideBottomNavOnScroll)
        assertTrue(slice.hiddenNavItems.isEmpty())
        assertTrue(slice.navItemOrder.isEmpty())
    }

    @Test
    fun `setHiddenNavItems round-trips`() = runTest {
        store.setHiddenNavItems(setOf("downloads", "live_tv"))
        val slice = store.navigation.first()
        assertEquals(setOf("downloads", "live_tv"), slice.hiddenNavItems)
    }

    @Test
    fun `setNavItemOrder round-trips`() = runTest {
        store.setNavItemOrder(listOf("home", "search"))
        assertEquals(listOf("home", "search"), store.navigation.first().navItemOrder)
    }
}
