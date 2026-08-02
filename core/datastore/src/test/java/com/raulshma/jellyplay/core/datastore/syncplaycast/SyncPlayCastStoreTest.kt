package com.raulshma.jellyplay.core.datastore.syncplaycast

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the SyncPlay + casting + DVR preference store extracted from the
 * `UserPreferencesStore` god object.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncPlayCastStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: SyncPlayCastStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = SyncPlayCastStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.syncPlayCast.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.syncPlayCast.first()
        assertEquals(SyncPlayJoinBehavior.ASK, slice.syncPlayJoinBehavior)
        assertEquals(100L, slice.syncPlayToleranceMs)
        assertEquals(false, slice.syncPlayAutoAcceptInvites)
        assertEquals(CastingStrategy.ASK, slice.defaultCastingStrategy)
        assertEquals(true, slice.backgroundCastingEnabled)
        assertNull(slice.preferredRenderer)
        assertEquals("AUTO", slice.dvrRecordingQuality)
    }

    @Test
    fun `setSyncPlayToleranceMs round-trips`() = runTest {
        store.setSyncPlayToleranceMs(250L)
        assertEquals(250L, store.syncPlayCast.first().syncPlayToleranceMs)
    }

    @Test
    fun `setPreferredRenderer writes then clears`() = runTest {
        store.setPreferredRenderer("chromecast_1")
        assertEquals("chromecast_1", store.syncPlayCast.first().preferredRenderer)
        store.setPreferredRenderer(null)
        assertNull(store.syncPlayCast.first().preferredRenderer)
    }
}
