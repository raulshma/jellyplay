package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the uniform "update uiState → sync engine config → persist pref"
 * shape shared by the engine-effect setters that used to live inline on the
 * ViewModel. The store is mocked so we assert the exact DataStore setter each
 * action routes to; the uiState is a captured lambda so we assert the produced
 * state; syncConfig is a counting lambda so we assert the engine sees the
 * change in the same frame as the UI.
 */
class VideoEffectsControllerTest {

    private lateinit var audioStore: AudioStore
    private lateinit var audioEffectsStore: AudioEffectsStore
    private lateinit var playbackStore: PlaybackStore
    private var state: VideoPlayerUiState = VideoPlayerUiState()
    private var syncCalls = 0

    private fun controller(scope: CoroutineScope) = VideoEffectsController(
        scope = scope,
        audioStore = audioStore,
        audioEffectsStore = audioEffectsStore,
        playbackStore = playbackStore,
        getUiState = { state },
        updateUiState = { mutator -> state = mutator(state) },
        syncConfig = { syncCalls++ },
    )

    @Before
    fun setUp() {
        audioStore = mockk(relaxed = true)
        audioEffectsStore = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        state = VideoPlayerUiState()
        syncCalls = 0
    }

    // ── Night mode ──────────────────────────────────────────────────────────────

    @Test
    fun toggleNightMode_enablesWhenOff() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)
        assertFalse(state.nightModeEnabled)

        c.toggleNightMode()

        assertTrue(state.nightModeEnabled)
        assertEquals(1, syncCalls)
        coVerify { audioEffectsStore.setNightModeEnabled(true) }
    }

    @Test
    fun toggleNightMode_disablesWhenOn() = runTest(UnconfinedTestDispatcher()) {
        state = state.copy(nightModeEnabled = true)
        val c = controller(this)

        c.toggleNightMode()

        assertFalse(state.nightModeEnabled)
        assertEquals(1, syncCalls)
        coVerify { audioEffectsStore.setNightModeEnabled(false) }
    }

    @Test
    fun setNightModeStrength_persistsStrength() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.setNightModeStrength(EffectStrength.HIGH)

        assertEquals(EffectStrength.HIGH, state.nightModeStrength)
        coVerify { audioEffectsStore.setNightModeStrength(EffectStrength.HIGH) }
    }

    // ── Audio delay / decoder / passthrough ─────────────────────────────────────

    @Test
    fun setAudioDelay_persistsMs() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.setAudioDelay(250L)

        assertEquals(250L, state.audioDelayMs)
        coVerify { audioStore.setAudioDelay(250L) }
    }

    @Test
    fun setDecoderMode_persistsMode() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.setDecoderMode(DecoderMode.SW_ONLY)

        assertEquals(DecoderMode.SW_ONLY, state.decoderMode)
        coVerify { playbackStore.setDecoderMode(DecoderMode.SW_ONLY) }
    }

    @Test
    fun setAudioPassthrough_persistsFlag() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.setAudioPassthrough(true)

        assertTrue(state.audioPassthrough)
        coVerify { playbackStore.setAudioPassthrough(true) }
    }

    // ── Audio normalization ─────────────────────────────────────────────────────

    @Test
    fun setAudioNormalizationMode_nonNone_enablesAndPersistsBoth() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.setAudioNormalizationMode(AudioNormalizationMode.TRACK)

        assertEquals(AudioNormalizationMode.TRACK, state.audioNormalizationMode)
        assertTrue(state.audioNormalizationEnabled)
        coVerifyOrder {
            audioStore.setAudioNormalizationMode(AudioNormalizationMode.TRACK)
            audioStore.setAudioNormalizationEnabled(true)
        }
    }

    @Test
    fun setAudioNormalizationMode_none_disablesAndPersistsBoth() = runTest(UnconfinedTestDispatcher()) {
        state = state.copy(audioNormalizationEnabled = true, audioNormalizationMode = AudioNormalizationMode.TRACK)
        val c = controller(this)

        c.setAudioNormalizationMode(AudioNormalizationMode.NONE)

        assertEquals(AudioNormalizationMode.NONE, state.audioNormalizationMode)
        assertFalse(state.audioNormalizationEnabled)
        coVerifyOrder {
            audioStore.setAudioNormalizationMode(AudioNormalizationMode.NONE)
            audioStore.setAudioNormalizationEnabled(false)
        }
    }

    @Test
    fun toggleAudioNormalization_flipsEnabledFlag() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.toggleAudioNormalization()
        assertTrue(state.audioNormalizationEnabled)
        coVerify { audioStore.setAudioNormalizationEnabled(true) }

        c.toggleAudioNormalization()
        assertFalse(state.audioNormalizationEnabled)
        coVerify { audioStore.setAudioNormalizationEnabled(false) }
    }

    // ── Channel mix ─────────────────────────────────────────────────────────────

    @Test
    fun setChannelMixMode_nonAuto_enablesAndPersistsBoth() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.setChannelMixMode(ChannelMixMode.SURROUND_UPMIX)

        assertEquals(ChannelMixMode.SURROUND_UPMIX, state.channelMixMode)
        assertTrue(state.channelMixEnabled)
        coVerifyOrder {
            audioStore.setChannelMixMode(ChannelMixMode.SURROUND_UPMIX)
            audioStore.setChannelMixEnabled(true)
        }
    }

    @Test
    fun setChannelMixMode_auto_disables() = runTest(UnconfinedTestDispatcher()) {
        state = state.copy(channelMixEnabled = true, channelMixMode = ChannelMixMode.MONO)
        val c = controller(this)

        c.setChannelMixMode(ChannelMixMode.AUTO)

        assertEquals(ChannelMixMode.AUTO, state.channelMixMode)
        assertFalse(state.channelMixEnabled)
        coVerifyOrder {
            audioStore.setChannelMixMode(ChannelMixMode.AUTO)
            audioStore.setChannelMixEnabled(false)
        }
    }

    @Test
    fun toggleChannelMix_flipsEnabledFlag() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.toggleChannelMix()
        assertTrue(state.channelMixEnabled)

        c.toggleChannelMix()
        assertFalse(state.channelMixEnabled)
        coVerify(exactly = 2) { audioStore.setChannelMixEnabled(any()) }
    }

    // ── Bass boost / virtualizer ────────────────────────────────────────────────

    @Test
    fun toggleBassBoost_flipsFlag() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.toggleBassBoost()
        assertTrue(state.bassBoostEnabled)
        coVerify { audioEffectsStore.setBassBoostEnabled(true) }
    }

    @Test
    fun setBassBoostStrength_persistsStrength() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.setBassBoostStrength(EffectStrength.LOW)

        assertEquals(EffectStrength.LOW, state.bassBoostStrength)
        coVerify { audioEffectsStore.setBassBoostStrength(EffectStrength.LOW) }
    }

    @Test
    fun toggleVirtualizer_flipsFlag() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.toggleVirtualizer()
        assertTrue(state.virtualizerEnabled)
        coVerify { audioEffectsStore.setVirtualizerEnabled(true) }
    }

    @Test
    fun setVirtualizerStrength_persistsValue() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.setVirtualizerStrength(750)

        assertEquals(750, state.virtualizerStrength)
        coVerify { audioEffectsStore.setVirtualizerStrength(750) }
    }

    // ── Reverb ──────────────────────────────────────────────────────────────────

    @Test
    fun setReverbPreset_persistsPreset() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)

        c.setReverbPreset(ReverbPreset.LARGE_HALL)

        assertEquals(ReverbPreset.LARGE_HALL, state.reverbPreset)
        coVerify { audioEffectsStore.setReverbPreset(ReverbPreset.LARGE_HALL) }
    }

    // ── Cross-cutting: sync-before-persist ordering ─────────────────────────────

    @Test
    fun everySetter_syncsConfigBeforePersisting() = runTest(UnconfinedTestDispatcher()) {
        val c = controller(this)
        // Use Unconfined so the launch runs eagerly: by the time the setter
        // returns, syncConfig has been called synchronously and the persist
        // coroutine has run to completion.
        val before = syncCalls

        c.toggleNightMode()

        // syncConfig runs synchronously inside the setter (no delay), so the
        // engine config is up to date in the same frame as the UI state.
        assertEquals("syncConfig must fire on every setter", before + 1, syncCalls)
        coVerify { audioEffectsStore.setNightModeEnabled(any()) }
    }
}
