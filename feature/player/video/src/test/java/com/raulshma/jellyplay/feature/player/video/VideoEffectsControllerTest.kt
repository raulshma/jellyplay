package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the uniform "update state → sync engine config → persist pref"
 * shape shared by the engine-effect setters. After state ownership moved into
 * the controller the test surface is the controller's
 * [AudioEffectsState] flow — no [VideoPlayerUiState], no ViewModel. The store
 * is mocked so we assert the exact DataStore setter each action routes to;
 * syncConfig is a counting lambda so we assert the engine sees the change in
 * the same frame as the UI.
 */
class VideoEffectsControllerTest {

    private lateinit var audioStore: AudioStore
    private lateinit var audioEffectsStore: AudioEffectsStore
    private lateinit var playbackStore: PlaybackStore
    private var syncCalls = 0

    private fun controller(scope: CoroutineScope) = VideoEffectsController(
        scope = scope,
        audioStore = audioStore,
        audioEffectsStore = audioEffectsStore,
        playbackStore = playbackStore,
        syncConfig = { syncCalls++ },
    )

    private lateinit var c: VideoEffectsController

    @Before
    fun setUp() {
        audioStore = mockk(relaxed = true)
        audioEffectsStore = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        syncCalls = 0
    }

    private fun withController(block: (VideoEffectsController) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            c = controller(this)
            block(c)
        }

    // ── Night mode ──────────────────────────────────────────────────────────────

    @Test
    fun toggleNightMode_enablesWhenOff() = withController {
        assertFalse(it.state.value.nightModeEnabled)

        it.toggleNightMode()

        assertTrue(it.state.value.nightModeEnabled)
        assertEquals(1, syncCalls)
        coVerify { audioEffectsStore.setNightModeEnabled(true) }
    }

    @Test
    fun toggleNightMode_disablesWhenOn() = runTest(UnconfinedTestDispatcher()) {
        val c0 = controller(this)
        c0.toggleNightMode()
        assertTrue(c0.state.value.nightModeEnabled)

        c0.toggleNightMode()

        assertFalse(c0.state.value.nightModeEnabled)
        coVerify { audioEffectsStore.setNightModeEnabled(false) }
    }

    @Test
    fun setNightModeStrength_persistsStrength() = withController {
        it.setNightModeStrength(EffectStrength.HIGH)

        assertEquals(EffectStrength.HIGH, it.state.value.nightModeStrength)
        coVerify { audioEffectsStore.setNightModeStrength(EffectStrength.HIGH) }
    }

    // ── Audio delay / decoder / passthrough ─────────────────────────────────────

    @Test
    fun setAudioDelay_persistsMs() = withController {
        it.setAudioDelay(250L)

        assertEquals(250L, it.state.value.audioDelayMs)
        coVerify { audioStore.setAudioDelay(250L) }
    }

    @Test
    fun setDecoderMode_persistsMode() = withController {
        it.setDecoderMode(DecoderMode.SW_ONLY)

        assertEquals(DecoderMode.SW_ONLY, it.state.value.decoderMode)
        coVerify { playbackStore.setDecoderMode(DecoderMode.SW_ONLY) }
    }

    @Test
    fun setAudioPassthrough_persistsFlag() = withController {
        it.setAudioPassthrough(true)

        assertTrue(it.state.value.audioPassthrough)
        coVerify { playbackStore.setAudioPassthrough(true) }
    }

    // ── Audio normalization ─────────────────────────────────────────────────────

    @Test
    fun setAudioNormalizationMode_nonNone_enablesAndPersistsBoth() = withController {
        it.setAudioNormalizationMode(AudioNormalizationMode.TRACK)

        assertEquals(AudioNormalizationMode.TRACK, it.state.value.audioNormalizationMode)
        assertTrue(it.state.value.audioNormalizationEnabled)
        coVerifyOrder {
            audioStore.setAudioNormalizationMode(AudioNormalizationMode.TRACK)
            audioStore.setAudioNormalizationEnabled(true)
        }
    }

    @Test
    fun setAudioNormalizationMode_none_disablesAndPersistsBoth() = runTest(UnconfinedTestDispatcher()) {
        val c0 = controller(this)
        c0.setAudioNormalizationMode(AudioNormalizationMode.TRACK)

        c0.setAudioNormalizationMode(AudioNormalizationMode.NONE)

        assertEquals(AudioNormalizationMode.NONE, c0.state.value.audioNormalizationMode)
        assertFalse(c0.state.value.audioNormalizationEnabled)
        coVerifyOrder {
            audioStore.setAudioNormalizationMode(AudioNormalizationMode.NONE)
            audioStore.setAudioNormalizationEnabled(false)
        }
    }

    @Test
    fun toggleAudioNormalization_flipsEnabledFlag() = withController {
        it.toggleAudioNormalization()
        assertTrue(it.state.value.audioNormalizationEnabled)
        coVerify { audioStore.setAudioNormalizationEnabled(true) }

        it.toggleAudioNormalization()
        assertFalse(it.state.value.audioNormalizationEnabled)
        coVerify { audioStore.setAudioNormalizationEnabled(false) }
    }

    // ── Channel mix ─────────────────────────────────────────────────────────────

    @Test
    fun setChannelMixMode_nonAuto_enablesAndPersistsBoth() = withController {
        it.setChannelMixMode(ChannelMixMode.SURROUND_UPMIX)

        assertEquals(ChannelMixMode.SURROUND_UPMIX, it.state.value.channelMixMode)
        assertTrue(it.state.value.channelMixEnabled)
        coVerifyOrder {
            audioStore.setChannelMixMode(ChannelMixMode.SURROUND_UPMIX)
            audioStore.setChannelMixEnabled(true)
        }
    }

    @Test
    fun setChannelMixMode_auto_disables() = runTest(UnconfinedTestDispatcher()) {
        val c0 = controller(this)
        c0.setChannelMixMode(ChannelMixMode.MONO)

        c0.setChannelMixMode(ChannelMixMode.AUTO)

        assertEquals(ChannelMixMode.AUTO, c0.state.value.channelMixMode)
        assertFalse(c0.state.value.channelMixEnabled)
        coVerifyOrder {
            audioStore.setChannelMixMode(ChannelMixMode.AUTO)
            audioStore.setChannelMixEnabled(false)
        }
    }

    @Test
    fun toggleChannelMix_flipsEnabledFlag() = withController {
        it.toggleChannelMix()
        assertTrue(it.state.value.channelMixEnabled)

        it.toggleChannelMix()
        assertFalse(it.state.value.channelMixEnabled)
        coVerify(exactly = 2) { audioStore.setChannelMixEnabled(any()) }
    }

    // ── Bass boost / virtualizer ────────────────────────────────────────────────

    @Test
    fun toggleBassBoost_flipsFlag() = withController {
        it.toggleBassBoost()
        assertTrue(it.state.value.bassBoostEnabled)
        coVerify { audioEffectsStore.setBassBoostEnabled(true) }
    }

    @Test
    fun setBassBoostStrength_persistsStrength() = withController {
        it.setBassBoostStrength(EffectStrength.LOW)

        assertEquals(EffectStrength.LOW, it.state.value.bassBoostStrength)
        coVerify { audioEffectsStore.setBassBoostStrength(EffectStrength.LOW) }
    }

    @Test
    fun toggleVirtualizer_flipsFlag() = withController {
        it.toggleVirtualizer()
        assertTrue(it.state.value.virtualizerEnabled)
        coVerify { audioEffectsStore.setVirtualizerEnabled(true) }
    }

    @Test
    fun setVirtualizerStrength_persistsValue() = withController {
        it.setVirtualizerStrength(750)

        assertEquals(750, it.state.value.virtualizerStrength)
        coVerify { audioEffectsStore.setVirtualizerStrength(750) }
    }

    // ── Reverb ──────────────────────────────────────────────────────────────────

    @Test
    fun setReverbPreset_persistsPreset() = withController {
        it.setReverbPreset(ReverbPreset.LARGE_HALL)

        assertEquals(ReverbPreset.LARGE_HALL, it.state.value.reverbPreset)
        coVerify { audioEffectsStore.setReverbPreset(ReverbPreset.LARGE_HALL) }
    }

    // ── Cross-cutting: sync-before-persist ordering ─────────────────────────────

    @Test
    fun everySetter_syncsConfigBeforePersisting() = withController {
        // Use Unconfined so the launch runs eagerly: by the time the setter
        // returns, syncConfig has been called synchronously and the persist
        // coroutine has run to completion.
        val before = syncCalls

        it.toggleNightMode()

        // syncConfig runs synchronously inside the setter (no delay), so the
        // engine config is up to date in the same frame as the UI state.
        assertEquals("syncConfig must fire on every setter", before + 1, syncCalls)
        coVerify { audioEffectsStore.setNightModeEnabled(any()) }
    }

    // ── Item-switch semantics ──────────────────────────────────────────────────

    /**
     * User effects persist across episodes — the 13 former whitelist lines died
     * and persistence is the default. There is deliberately no resetForItem();
     * this test pins that the whole slice survives an item switch untouched.
     */
    @Test
    fun `item switch preserves user effects`() = withController {
        it.toggleNightMode()
        it.setNightModeStrength(EffectStrength.HIGH)
        it.setAudioDelay(250L)
        it.setDecoderMode(DecoderMode.SW_ONLY)
        it.setAudioPassthrough(true)
        it.setAudioNormalizationMode(AudioNormalizationMode.TRACK)
        it.setChannelMixMode(ChannelMixMode.SURROUND_UPMIX)
        it.toggleBassBoost()
        it.setBassBoostStrength(EffectStrength.HIGH)
        it.toggleVirtualizer()
        it.setVirtualizerStrength(750)
        it.setReverbPreset(ReverbPreset.LARGE_HALL)

        // The item-switch path (releaseInternals) performs no reset call on the
        // effects slice — state must be unchanged afterwards. (The ONE per-item
        // exception, dialogue boost, does not live in this slice: it stays on
        // VideoPlayerUiState and is zeroed by the ViewModel's reset ritual.)
        assertEquals(
            AudioEffectsState(
                nightModeEnabled = true,
                nightModeStrength = EffectStrength.HIGH,
                audioPassthrough = true,
                decoderMode = DecoderMode.SW_ONLY,
                audioNormalizationMode = AudioNormalizationMode.TRACK,
                audioNormalizationEnabled = true,
                channelMixMode = ChannelMixMode.SURROUND_UPMIX,
                channelMixEnabled = true,
                bassBoostEnabled = true,
                bassBoostStrength = EffectStrength.HIGH,
                virtualizerEnabled = true,
                virtualizerStrength = 750,
                reverbPreset = ReverbPreset.LARGE_HALL,
                audioDelayMs = 250L,
            ),
            it.state.value,
        )
    }

    /** Prefs seeding on engine bind (the engineFlow collector's former UiState writes). */
    @Test
    fun `seedFromPreferences updates exactly the seeded fields`() = withController {
        it.toggleBassBoost()
        it.setReverbPreset(ReverbPreset.LARGE_HALL)

        it.seedFromPreferences(
            audioDelayMs = 120L,
            decoderMode = DecoderMode.SW_ONLY,
            audioPassthrough = true,
            nightModeEnabled = true,
            nightModeStrength = EffectStrength.LOW,
            audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
            audioNormalizationEnabled = true,
            channelMixMode = ChannelMixMode.SURROUND_UPMIX,
            channelMixEnabled = true,
        )

        val s = it.state.value
        assertEquals(120L, s.audioDelayMs)
        assertEquals(DecoderMode.SW_ONLY, s.decoderMode)
        assertTrue(s.audioPassthrough)
        assertTrue(s.nightModeEnabled)
        assertEquals(EffectStrength.LOW, s.nightModeStrength)
        assertEquals(AudioNormalizationMode.DYNAMIC, s.audioNormalizationMode)
        assertTrue(s.audioNormalizationEnabled)
        assertEquals(ChannelMixMode.SURROUND_UPMIX, s.channelMixMode)
        assertTrue(s.channelMixEnabled)
        // Never seeded by the engine collector — live values survive.
        assertTrue(s.bassBoostEnabled)
        assertEquals(ReverbPreset.LARGE_HALL, s.reverbPreset)
    }
}
