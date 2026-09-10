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
import com.raulshma.jellyplay.core.model.platformEngineSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
            settledSlice()
        }
    }

    /**
     * The Eagerly-cached slice is updated by a collector racing this test, so
     * a plain `playback.first()` samples whatever is current — the stateIn
     * seed (whose defaults differ from an empty-store read, e.g. playbackMode
     * AUTO vs the FORCE_DIRECT_PLAY legacy migration), or the previous test's
     * leftovers. Await until the cached slice equals the projection of the
     * CURRENT disk state and return it. The wait runs off runTest's virtual
     * clock: the collector it races lives on real dispatchers.
     */
    private suspend fun settledSlice(): PlaybackSlice {
        val expected = store.read(dataStore.data.first())
        return withContext(Dispatchers.Default) {
            withTimeout(5_000) { store.playback.first { it == expected } }
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = settledSlice()
        // The raw stored default is EXO_PLAYER; the read clamps it to whatever
        // engine this binary actually ships (desktop → MPV).
        assertEquals(platformEngineSupport.default, slice.preferredPlayer)
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
        val slice = settledSlice()
        assertTrue(slice.frameRateMatching)
        assertEquals(RefreshRateMode.FRAME_RATE_ONLY, slice.refreshRateMode)
    }

    @Test
    fun `setFrameRateMatching false forces refresh rate mode off`() = runTest {
        store.setRefreshRateMode(RefreshRateMode.FRAME_RATE_AND_RESOLUTION)
        store.setFrameRateMatching(false)
        val slice = settledSlice()
        assertFalse(slice.frameRateMatching)
        assertEquals(RefreshRateMode.OFF, slice.refreshRateMode)
    }

    @Test
    fun `setRefreshRateMode keeps boolean in sync`() = runTest {
        store.setRefreshRateMode(RefreshRateMode.FRAME_RATE_AND_RESOLUTION)
        var slice = settledSlice()
        assertTrue(slice.frameRateMatching)
        assertEquals(RefreshRateMode.FRAME_RATE_AND_RESOLUTION, slice.refreshRateMode)

        store.setRefreshRateMode(RefreshRateMode.OFF)
        slice = settledSlice()
        assertFalse(slice.frameRateMatching)
    }

    @Test
    fun `legacy force_direct_play true migrates to FORCE_DIRECT_PLAY`() = runTest {
        dataStore.edit {
            it[booleanPreferencesKey("force_direct_play")] = true
        }
        val slice = settledSlice()
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, slice.playbackMode)
    }

    @Test
    fun `legacy force_direct_play false migrates to AUTO`() = runTest {
        dataStore.edit {
            it[booleanPreferencesKey("force_direct_play")] = false
        }
        val slice = settledSlice()
        assertEquals(PlaybackMode.AUTO, slice.playbackMode)
    }

    @Test
    fun `typed playback mode key wins over legacy bool`() = runTest {
        dataStore.edit {
            it[booleanPreferencesKey("force_direct_play")] = true
            it[stringPreferencesKey("playback_mode")] = PlaybackMode.AUTO.name
        }
        val slice = settledSlice()
        assertEquals(PlaybackMode.AUTO, slice.playbackMode)
    }

    @Test
    fun `setPlaybackMode round-trips`() = runTest {
        store.setPlaybackMode(PlaybackMode.FORCE_DIRECT_PLAY)
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, settledSlice().playbackMode)
    }

    @Test
    fun `corrupt enum values fall back to defaults, siblings keep real values`() = runTest {
        store.setKeepScreenOnDuringVideo(false)
        dataStore.edit {
            it[stringPreferencesKey("streaming_quality")] = "nonsense"
            // Legacy lowercase casing matches no enum name either.
            it[stringPreferencesKey("decoder_mode")] = "hw_preferred"
        }
        val slice = settledSlice()
        assertEquals(StreamingQuality.AUTO, slice.streamingQuality)
        assertEquals(DecoderMode.HW_PREFERRED, slice.decoderMode)
        assertEquals(false, slice.keepScreenOnDuringVideo)
    }

    @Test
    fun `corrupt refresh_rate_mode falls back to the legacy boolean migration`() = runTest {
        // A corrupt stored value (not an absent key, which reads OFF directly)
        // rescues the legacy frame_rate_matching boolean: off → OFF, on →
        // FRAME_RATE_ONLY (the old single-resolution behaviour).
        dataStore.edit { it[stringPreferencesKey("refresh_rate_mode")] = "nonsense" }
        assertEquals(RefreshRateMode.OFF, settledSlice().refreshRateMode)
        dataStore.edit { it[booleanPreferencesKey("frame_rate_matching")] = true }
        assertEquals(RefreshRateMode.FRAME_RATE_ONLY, settledSlice().refreshRateMode)
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

        assertEquals(slice, settledSlice())
    }
}
