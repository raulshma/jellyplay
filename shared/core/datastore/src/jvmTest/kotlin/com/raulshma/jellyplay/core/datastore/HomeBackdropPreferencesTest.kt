package com.raulshma.jellyplay.core.datastore

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Verifies the `homeBackdropEnabled` preference round-trips through DataStore
 * via [com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore] (the
 * canonical slice owner).
 *
 * [FixMethodOrder] keeps the default-value assertion first: the shared
 * `"user_prefs"` DataStore file persists between test methods in the class, so
 * the mutating tests must not run before the pristine-default check.
 */
class HomeBackdropPreferencesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var graph: PreferenceSliceGraph

    @BeforeTest
    fun setup() {
        runBlocking {
            val dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
            // Home-discovery keys are per-user; activate one so the write
            // tests below have a namespace to write into.
            graph.identityStore.setActiveUser("backdrop-user")
            graph.homeDiscoveryStore.homeDiscovery.first()
        }
    }

    @Test
    fun `a_default is enabled`() = runTest {
        assertTrue(graph.homeDiscoveryStore.homeDiscovery.value.homeBackdropEnabled)
    }

    @Test
    fun `b_setHomeBackdropEnabled persists and reads back`() = runTest {
        graph.homeDiscoveryStore.setHomeBackdropEnabled(false)
        assertFalse(graph.homeDiscoveryStore.homeDiscovery.first().homeBackdropEnabled)
    }

    @Test
    fun `c_re-enabling persists after disable`() = runTest {
        graph.homeDiscoveryStore.setHomeBackdropEnabled(false)
        graph.homeDiscoveryStore.setHomeBackdropEnabled(true)
        assertTrue(graph.homeDiscoveryStore.homeDiscovery.first().homeBackdropEnabled)
    }
}
