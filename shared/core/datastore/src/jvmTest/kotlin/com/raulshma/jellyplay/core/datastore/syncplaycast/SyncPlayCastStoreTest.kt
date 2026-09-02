package com.raulshma.jellyplay.core.datastore.syncplaycast

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the SyncPlay + casting + DVR preference store extracted from the
 * `UserPreferencesStore` god object.
 */
class SyncPlayCastStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: SyncPlayCastStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
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
        assertEquals(slice.dvrRecordingQuality, "AUTO")
    }

    @Test
    fun `setSyncPlayToleranceMs round-trips`() = runTest {
        store.setSyncPlayToleranceMs(250L)
        assertEquals(250L, store.syncPlayCast.first().syncPlayToleranceMs)
    }

    @Test
    fun `setPreferredRenderer writes then clears`() = runTest {
        store.setPreferredRenderer("chromecast_1")
        assertEquals(store.syncPlayCast.first().preferredRenderer, "chromecast_1")
        store.setPreferredRenderer(null)
        assertNull(store.syncPlayCast.first().preferredRenderer)
    }
}
