package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import org.robolectric.annotation.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the audio-cache preference fields round-trip through DataStore.
 * Uses Robolectric for the Android Context required by the DataStore delegate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioCachePreferencesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: UserPreferencesStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = UserPreferencesStore(context, scope)
    }

    @Test
    fun `defaults are applied when no keys are set`() = runTest {
        val prefs = store.preferences.first()
        assertTrue(prefs.audioCachingEnabled)
        assertEquals(1024, prefs.audioCacheSizeMb)
        assertEquals(3, prefs.audioPrefetchLookahead)
        assertEquals(5, prefs.audioPrefetchBackfill)
        assertEquals(AudioCacheNetworkPolicy.WIFI_ONLY, prefs.audioCacheNetworkPolicy)
        assertEquals(500, prefs.audioCacheCellularMonthlyCapMb)
    }

    @Test
    fun `setAudioCachingEnabled persists and reads back`() = runTest {
        store.setAudioCachingEnabled(false)
        assertFalse(store.preferences.first().audioCachingEnabled)
    }

    @Test
    fun `setAudioCacheSizeMb persists and reads back`() = runTest {
        store.setAudioCacheSizeMb(4096)
        assertEquals(4096, store.preferences.first().audioCacheSizeMb)
    }

    @Test
    fun `setAudioPrefetchLookahead persists and reads back`() = runTest {
        store.setAudioPrefetchLookahead(7)
        assertEquals(7, store.preferences.first().audioPrefetchLookahead)
    }

    @Test
    fun `setAudioPrefetchBackfill persists and reads back`() = runTest {
        store.setAudioPrefetchBackfill(12)
        assertEquals(12, store.preferences.first().audioPrefetchBackfill)
    }

    @Test
    fun `setAudioCacheNetworkPolicy persists and reads back`() = runTest {
        store.setAudioCacheNetworkPolicy(AudioCacheNetworkPolicy.ANY_NETWORK)
        assertEquals(
            AudioCacheNetworkPolicy.ANY_NETWORK,
            store.preferences.first().audioCacheNetworkPolicy,
        )
    }

    @Test
    fun `setAudioCacheCellularMonthlyCapMb persists and reads back`() = runTest {
        store.setAudioCacheCellularMonthlyCapMb(2000)
        assertEquals(2000, store.preferences.first().audioCacheCellularMonthlyCapMb)
    }
}
