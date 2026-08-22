package com.raulshma.jellyplay.core.datastore.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the media-delivery preference store, focusing on the cross-key
 * invariants and legacy migrations that previously lived inline in the
 * `UserPreferencesStore` god object with **no** unit coverage.
 */
class PlaybackStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: PlaybackStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = PlaybackStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.playback.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.playback.first()
        assertEquals(PlayerType.EXO_PLAYER, slice.preferredPlayer)
        assertEquals(StreamingQuality.AUTO, slice.streamingQuality)
        // Empty store migrates the legacy `force_direct_play` default (true)
        // to FORCE_DIRECT_PLAY — the historical "always static stream" behaviour.
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, slice.playbackMode)
        assertEquals(RefreshRateMode.OFF, slice.refreshRateMode)
        assertFalse(slice.frameRateMatching)
        assertFalse(slice.audioPassthrough)
        assertTrue(slice.keepScreenOnDuringVideo)
    }

    @Test
    fun `setFrameRateMatching true seeds refresh rate mode when unset`() = runTest {
        store.setFrameRateMatching(true)
        val slice = store.playback.first()
        assertTrue(slice.frameRateMatching)
        assertEquals(RefreshRateMode.FRAME_RATE_ONLY, slice.refreshRateMode)
    }

    @Test
    fun `setFrameRateMatching false forces refresh rate mode off`() = runTest {
        store.setRefreshRateMode(RefreshRateMode.FRAME_RATE_AND_RESOLUTION)
        store.setFrameRateMatching(false)
        val slice = store.playback.first()
        assertFalse(slice.frameRateMatching)
        assertEquals(RefreshRateMode.OFF, slice.refreshRateMode)
    }

    @Test
    fun `setRefreshRateMode keeps boolean in sync`() = runTest {
        store.setRefreshRateMode(RefreshRateMode.FRAME_RATE_AND_RESOLUTION)
        var slice = store.playback.first()
        assertTrue(slice.frameRateMatching)
        assertEquals(RefreshRateMode.FRAME_RATE_AND_RESOLUTION, slice.refreshRateMode)

        store.setRefreshRateMode(RefreshRateMode.OFF)
        slice = store.playback.first()
        assertFalse(slice.frameRateMatching)
    }

    @Test
    fun `legacy force_direct_play true migrates to FORCE_DIRECT_PLAY`() = runTest {
        dataStore.edit {
            it[booleanPreferencesKey("force_direct_play")] = true
        }
        val slice = store.playback.first()
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, slice.playbackMode)
    }

    @Test
    fun `legacy force_direct_play false migrates to AUTO`() = runTest {
        dataStore.edit {
            it[booleanPreferencesKey("force_direct_play")] = false
        }
        val slice = store.playback.first()
        assertEquals(PlaybackMode.AUTO, slice.playbackMode)
    }

    @Test
    fun `typed playback mode key wins over legacy bool`() = runTest {
        dataStore.edit {
            it[booleanPreferencesKey("force_direct_play")] = true
            it[stringPreferencesKey("playback_mode")] = PlaybackMode.AUTO.name
        }
        val slice = store.playback.first()
        assertEquals(PlaybackMode.AUTO, slice.playbackMode)
    }

    @Test
    fun `setPlaybackMode round-trips`() = runTest {
        store.setPlaybackMode(PlaybackMode.FORCE_DIRECT_PLAY)
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, store.playback.first().playbackMode)
    }

    @Test
    fun `restore(slice) round-trips a fully-populated slice`() = runTest {
        val slice = PlaybackSlice(
            preferredPlayer = PlayerType.MPV,
            streamingQuality = StreamingQuality.UHD_4K,
            cellularStreamingQuality = StreamingQuality.LOW_360P,
            playbackMode = PlaybackMode.FORCE_DIRECT_PLAY,
            liveStreamOption = LiveStreamOption.TRANSCODE,
            decoderMode = DecoderMode.SW_ONLY,
            audioPassthrough = true,
            frameRateMatching = true,
            refreshRateMode = RefreshRateMode.FRAME_RATE_AND_RESOLUTION,
            keepScreenOnDuringVideo = false,
            pauseOnAudioFocusLoss = false,
            duckOnTransientFocusLoss = true,
            autoPlayCountdownSec = 30,
            backgroundVideoAudioEnabled = true,
            pgsSubtitleDirectPlay = true,
            userDataSyncEnabled = false,
            androidTvWatchNextEnabled = false,
        )

        store.restore(slice)

        assertEquals(slice, store.playback.first())
    }
}
