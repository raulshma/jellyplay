package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the `homeBackdropEnabled` preference round-trips through DataStore
 * via [com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore] (the
 * canonical slice owner).
 *
 * [FixMethodOrder] keeps the default-value assertion first: the shared
 * `"user_prefs"` DataStore file persists between test methods in the class, so
 * the mutating tests must not run before the pristine-default check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class HomeBackdropPreferencesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var graph: PreferenceSliceGraph

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
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
