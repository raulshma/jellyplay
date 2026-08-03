package com.raulshma.jellyplay.core.datastore.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the audio-player preference store, focusing on the legacy
 * `REPLAYGAIN` → `TRACK` normalization-mode alias and round-trips that
 * previously lived inline in the `UserPreferencesStore` god object with **no**
 * unit coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: AudioStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = AudioStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.audio.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.audio.first()
        assertEquals(1.0f, slice.audioDefaultSpeed)
        assertEquals(PreloadBufferSize.MEDIUM, slice.audioPreloadBufferSize)
        assertEquals(AudioNormalizationMode.NONE, slice.audioNormalizationMode)
        assertEquals(ChannelMixMode.AUTO, slice.channelMixMode)
        assertTrue(slice.audioAutoplayNext)
        assertTrue(slice.audioGaplessEnabled)
        assertEquals(0L, slice.audioCrossfadeDurationMs)
    }

    @Test
    fun `setAudioNormalizationMode round-trips`() = runTest {
        store.setAudioNormalizationMode(AudioNormalizationMode.ALBUM)
        assertEquals(
            AudioNormalizationMode.ALBUM,
            store.audio.first().audioNormalizationMode,
        )
    }

    @Test
    fun `setChannelMixMode round-trips`() = runTest {
        store.setChannelMixMode(ChannelMixMode.MONO)
        assertEquals(ChannelMixMode.MONO, store.audio.first().channelMixMode)
    }

    @Test
    fun `setAudioDefaultSpeed round-trips`() = runTest {
        store.setAudioDefaultSpeed(1.5f)
        assertEquals(1.5f, store.audio.first().audioDefaultSpeed)
    }

    @Test
    fun `legacy REPLAYGAIN normalization mode maps to TRACK`() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("audio_normalization_mode")] = "REPLAYGAIN"
        }
        val slice = store.audio.first()
        assertEquals(AudioNormalizationMode.TRACK, slice.audioNormalizationMode)
    }

    @Test
    fun `setSleepTimerDurationMs round-trips`() = runTest {
        store.setSleepTimerDurationMs(1_800_000L)
        assertEquals(1_800_000L, store.audio.first().sleepTimerDurationMs)
    }

    @Test
    fun `restore(slice) round-trips a fully-populated slice`() = runTest {
        val slice = AudioSlice(
            audioDefaultSpeed = 1.5f,
            audioNightModeVolume = 0.7f,
            audioNightModeGain = 2400,
            audioSkipPreviousThresholdMs = 5_000L,
            audioAutoplayNext = false,
            audioPreloadBufferSize = PreloadBufferSize.HIGH,
            audioNormalizationMode = AudioNormalizationMode.ALBUM,
            audioNormalizationEnabled = true,
            replayGainPreAmpDb = -3.0f,
            channelMixMode = ChannelMixMode.MONO,
            channelMixEnabled = true,
            audioGaplessEnabled = false,
            audioCrossfadeDurationMs = 4_000L,
            audioDelayMs = 250L,
            audioLyricsVisible = true,
            audioVisualizerEnabled = true,
            sleepTimerDurationMs = 1_800_000L,
            sleepTimerEndOfEpisode = true,
        )

        store.restore(slice)

        assertEquals(slice, store.audio.first())
    }
}
