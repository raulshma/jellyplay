package com.raulshma.jellyplay.core.datastore.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MpvEngineConfig
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
 * Exercises the player-engine preference store, focusing on the JSON
 * round-trips for the engine configs and the 100-entry LRU cap on the per-item
 * media-stream map that previously lived inline in the `UserPreferencesStore`
 * god object with **no** unit coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerEngineStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: PlayerEngineStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = PlayerEngineStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.playerEngine.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.playerEngine.first()
        assertEquals(MpvEngineConfig(), slice.mpvConfig)
        assertEquals(LibVlcEngineConfig(), slice.libVlcConfig)
        assertEquals(ExoPlayerEngineConfig(), slice.exoPlayerConfig)
        assertTrue(slice.mediaStreamSelections.isEmpty())
        assertTrue(slice.videoEffectsByItem.isEmpty())
    }

    @Test
    fun `setMpvConfig round-trips a non-default config`() = runTest {
        val nonDefault = MpvEngineConfig(deband = true, interpolation = true)
        // Guard: the value must actually differ from the default for the test to
        // exercise anything meaningful.
        assertFalse(nonDefault == MpvEngineConfig())

        store.setMpvConfig(nonDefault)
        val read = store.playerEngine.first().mpvConfig
        assertEquals(nonDefault, read)
        assertTrue(read.deband)
        assertTrue(read.interpolation)
    }

    @Test
    fun `setLibVlcConfig and setExoPlayerConfig round-trip`() = runTest {
        val libVlc = LibVlcEngineConfig(networkCaching = 1500, decoderThreads = 4)
        val exo = ExoPlayerEngineConfig(skipSilence = true, backBufferDurationMs = 30_000)
        store.setLibVlcConfig(libVlc)
        store.setExoPlayerConfig(exo)
        val slice = store.playerEngine.first()
        assertEquals(libVlc, slice.libVlcConfig)
        assertEquals(exo, slice.exoPlayerConfig)
    }

    /**
     * Headline: the per-item media-stream map is LRU-capped at 100 entries.
     * Inserting 102 distinct items must evict the 2 oldest (insertion order),
     * leaving exactly 100.
     */
    @Test
    fun `mediaStreamSelection LRU caps at 100 entries`() = runTest {
        repeat(102) { i ->
            store.setMediaStreamSelection(
                itemId = "item-$i",
                audioStreamIndex = i,
                subtitleStreamIndex = i + 1,
            )
        }
        val selections = store.playerEngine.first().mediaStreamSelections
        assertEquals(100, selections.size)
        // The two oldest (item-0, item-1) are evicted; item-2 survives.
        assertFalse(selections.containsKey("item-0"))
        assertFalse(selections.containsKey("item-1"))
        assertTrue(selections.containsKey("item-2"))
        assertTrue(selections.containsKey("item-101"))
    }

    @Test
    fun `setMediaStreamSelection with null indices clears the entry`() = runTest {
        store.setMediaStreamSelection("item-0", audioStreamIndex = 5)
        assertEquals(1, store.playerEngine.first().mediaStreamSelections.size)
        store.setMediaStreamSelection("item-0", audioStreamIndex = null, subtitleStreamIndex = null)
        assertTrue(store.playerEngine.first().mediaStreamSelections.isEmpty())
    }

    @Test
    fun `setVideoEffectsForItem neutral config clears the entry`() = runTest {
        store.setVideoEffectsForItem("item-0", com.raulshma.jellyplay.core.model.VideoEffectsConfig(brightness = 0.5f))
        assertEquals(1, store.playerEngine.first().videoEffectsByItem.size)
        // Default-constructed config is neutral per VideoEffectsConfig.isNeutral.
        store.setVideoEffectsForItem("item-0", com.raulshma.jellyplay.core.model.VideoEffectsConfig())
        assertTrue(store.playerEngine.first().videoEffectsByItem.isEmpty())
    }
}
