package com.raulshma.jellyplay.core.datastore.videoplayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Pins the video byte-cache cap preference (`video_cache_size_mb`) — the
 * video sibling of [com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore]'s
 * `audio_cache_size_mb`, consumed by the player-video module's
 * VideoStreamCache as its LRU eviction bound. Like the audio keys this was
 * never string-typed in the legacy store, so the plain `prefs[key] ?: default`
 * read is what's under test.
 */
class VideoCacheSizePreferenceTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: VideoPlayerStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = VideoPlayerStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.videoPlayer.first()
        }
    }

    @Test
    fun `defaults to the audio cache's 1024 MB bound`() = runTest {
        assertEquals(1024, store.videoPlayer.first().videoCacheSizeMb)
    }

    @Test
    fun `setVideoCacheSizeMb round-trips`() = runTest {
        store.setVideoCacheSizeMb(2048)
        assertEquals(2048, store.videoPlayer.first().videoCacheSizeMb)
    }

    @Test
    fun `raw key read falls back to the default when absent`() = runTest {
        dataStore.edit { prefs ->
            prefs[intPreferencesKey("video_cache_size_mb")] = 512
        }
        assertEquals(512, store.videoPlayer.first().videoCacheSizeMb)
        dataStore.edit { it.clear() }
        assertEquals(1024, store.videoPlayer.first().videoCacheSizeMb)
    }
}
