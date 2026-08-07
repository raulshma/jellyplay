package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import org.robolectric.annotation.Config
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

/**
 * Verifies the audio-cache preference fields round-trip through DataStore via
 * [com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore] (the
 * canonical slice owner).
 *
 * Uses Robolectric for the Android Context required by the DataStore delegate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioCachePreferencesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var graph: PreferenceSliceGraph

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
            graph.audioCacheStore.audioCache.first()
        }
    }

    @Test
    fun `defaults are applied when no keys are set`() = runTest {
        val prefs = graph.audioCacheStore.audioCache.value
        assertTrue(prefs.audioCachingEnabled)
        assertEquals(1024, prefs.audioCacheSizeMb)
        assertEquals(3, prefs.audioPrefetchLookahead)
        assertEquals(5, prefs.audioPrefetchBackfill)
        assertEquals(AudioCacheNetworkPolicy.WIFI_ONLY, prefs.audioCacheNetworkPolicy)
        assertEquals(500, prefs.audioCacheCellularMonthlyCapMb)
    }

    @Test
    fun `setAudioCachingEnabled persists and reads back`() = runTest {
        graph.audioCacheStore.setAudioCachingEnabled(false)
        assertFalse(graph.audioCacheStore.audioCache.first().audioCachingEnabled)
    }

    @Test
    fun `setAudioCacheSizeMb persists and reads back`() = runTest {
        graph.audioCacheStore.setAudioCacheSizeMb(4096)
        assertEquals(4096, graph.audioCacheStore.audioCache.first().audioCacheSizeMb)
    }

    @Test
    fun `setAudioPrefetchLookahead persists and reads back`() = runTest {
        graph.audioCacheStore.setAudioPrefetchLookahead(7)
        assertEquals(7, graph.audioCacheStore.audioCache.first().audioPrefetchLookahead)
    }

    @Test
    fun `setAudioPrefetchBackfill persists and reads back`() = runTest {
        graph.audioCacheStore.setAudioPrefetchBackfill(12)
        assertEquals(12, graph.audioCacheStore.audioCache.first().audioPrefetchBackfill)
    }

    @Test
    fun `setAudioCacheNetworkPolicy persists and reads back`() = runTest {
        graph.audioCacheStore.setAudioCacheNetworkPolicy(AudioCacheNetworkPolicy.ANY_NETWORK)
        assertEquals(
            AudioCacheNetworkPolicy.ANY_NETWORK,
            graph.audioCacheStore.audioCache.first().audioCacheNetworkPolicy,
        )
    }

    @Test
    fun `setAudioCacheCellularMonthlyCapMb persists and reads back`() = runTest {
        graph.audioCacheStore.setAudioCacheCellularMonthlyCapMb(2000)
        assertEquals(2000, graph.audioCacheStore.audioCache.first().audioCacheCellularMonthlyCapMb)
    }
}
