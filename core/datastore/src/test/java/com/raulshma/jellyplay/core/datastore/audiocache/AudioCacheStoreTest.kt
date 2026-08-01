package com.raulshma.jellyplay.core.datastore.audiocache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the audio-cache policy preference store. These keys were never
 * string-typed in the legacy store, so the round-trips here cover the plain
 * `prefs[key] ?: default` read path that previously lived inline in the
 * `UserPreferencesStore` god object with **no** unit coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioCacheStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: AudioCacheStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = AudioCacheStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.audioCache.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.audioCache.first()
        assertTrue(slice.audioCachingEnabled)
        assertEquals(1024, slice.audioCacheSizeMb)
        assertEquals(3, slice.audioPrefetchLookahead)
        assertEquals(5, slice.audioPrefetchBackfill)
        assertEquals(AudioCacheNetworkPolicy.DEFAULT, slice.audioCacheNetworkPolicy)
        assertEquals(500, slice.audioCacheCellularMonthlyCapMb)
    }

    @Test
    fun `setAudioCachingEnabled round-trips`() = runTest {
        store.setAudioCachingEnabled(false)
        assertFalse(store.audioCache.first().audioCachingEnabled)
    }

    @Test
    fun `setAudioCacheNetworkPolicy round-trips`() = runTest {
        store.setAudioCacheNetworkPolicy(AudioCacheNetworkPolicy.ANY_NETWORK)
        assertEquals(
            AudioCacheNetworkPolicy.ANY_NETWORK,
            store.audioCache.first().audioCacheNetworkPolicy,
        )
    }

    @Test
    fun `setAudioCacheSizeMb round-trips`() = runTest {
        store.setAudioCacheSizeMb(2048)
        assertEquals(2048, store.audioCache.first().audioCacheSizeMb)
    }
}
