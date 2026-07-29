package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
 * Verifies the `homeBackdropEnabled` preference round-trips through DataStore,
 * mirroring [AudioCachePreferencesTest]'s real-store pattern.
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
    private lateinit var store: UserPreferencesStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = com.raulshma.jellyplay.core.datastore.TestDataStoreProvider.get(context)
        val widgetDataStore = com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore(dataStore, scope)
        val serverIdentityStore = com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore(dataStore, scope)
        val pinRateLimiter = com.raulshma.jellyplay.core.datastore.security.PinRateLimiter(dataStore, scope)
        store = UserPreferencesStore(scope, dataStore, widgetDataStore, serverIdentityStore, pinRateLimiter)
    }

    @Test
    fun `a_default is enabled`() = runTest {
        assertTrue(store.preferences.first().homeBackdropEnabled)
    }

    @Test
    fun `b_setHomeBackdropEnabled persists and reads back`() = runTest {
        store.setHomeBackdropEnabled(false)
        assertFalse(store.preferences.first().homeBackdropEnabled)
    }

    @Test
    fun `c_re-enabling persists after disable`() = runTest {
        store.setHomeBackdropEnabled(false)
        store.setHomeBackdropEnabled(true)
        assertTrue(store.preferences.first().homeBackdropEnabled)
    }
}
