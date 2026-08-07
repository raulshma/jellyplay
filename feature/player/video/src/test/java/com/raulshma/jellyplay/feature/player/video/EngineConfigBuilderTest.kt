package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [EngineConfigBuilder] — the audio-effects / decoder orchestration mapping
 * extracted from `VideoPlayerViewModel`. Pure function, no
 * Android, so it is exercised directly.
 */
class EngineConfigBuilderTest {

    private fun baselineState() = VideoPlayerUiState()

    @Test
    fun build_defaultsPropagateForNeutralState() {
        val config = EngineConfigBuilder.build(baselineState(), equalizerEnabled = false, agg = VideoPlayerAggregate())
        assertEquals(EngineConfig().decoderMode, config.decoderMode)
        assertFalse(config.audioPassthrough)
        assertEquals(0L, config.audioDelayMs)
        assertEquals(0L, config.subtitleDelayMs)
        assertFalse(config.audioEffects.dialogueBoostEnabled)
        assertFalse(config.audioEffects.equalizerEnabled)
    }

    @Test
    fun build_carriesAudioEffectsFromUiState() {
        val state = baselineState().copy(
            dialogueBoostEnabled = true,
            dialogueBoostStrength = EffectStrength.HIGH,
            nightModeEnabled = true,
            nightModeStrength = EffectStrength.MODERATE,
            audioNormalizationMode = AudioNormalizationMode.DYNAMIC,
            audioNormalizationEnabled = true,
            channelMixMode = ChannelMixMode.STEREO_DOWNMIX,
            channelMixEnabled = true,
            bassBoostEnabled = true,
            bassBoostStrength = EffectStrength.LOW,
            virtualizerEnabled = true,
            virtualizerStrength = 750,
            reverbPreset = ReverbPreset.LARGE_HALL,
            audioDelayMs = 42L,
        )
        val config = EngineConfigBuilder.build(state, equalizerEnabled = true, agg = VideoPlayerAggregate())

        with(config.audioEffects) {
            assertTrue(dialogueBoostEnabled)
            assertEquals(EffectStrength.HIGH, dialogueBoostStrength)
            assertTrue(nightModeEnabled)
            assertEquals(AudioNormalizationMode.DYNAMIC, audioNormalizationMode)
            assertTrue(audioNormalizationEnabled)
            assertEquals(ChannelMixMode.STEREO_DOWNMIX, channelMixMode)
            assertTrue(channelMixEnabled)
            assertTrue(bassBoostEnabled)
            assertTrue(virtualizerEnabled)
            assertEquals(750, virtualizerStrength)
            assertEquals(ReverbPreset.LARGE_HALL, reverbPreset)
            assertTrue(equalizerEnabled)
        }
        assertEquals(42L, config.audioDelayMs)
    }

    @Test
    fun build_equalizerEnabledComesFromParam_notState() {
        val config = EngineConfigBuilder.build(baselineState(), equalizerEnabled = true, agg = VideoPlayerAggregate())
        assertTrue(config.audioEffects.equalizerEnabled)
    }

    @Test
    fun build_equalizerAndVolumeBoostComeFromAgg() {
        val eqSettings = EqualizerSettings(bandLevels = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(pauseOnAudioFocusLoss = false),
            audioEffects = AudioEffectsSlice(
                equalizerSettings = eqSettings,
                volumeBoostEnabled = true,
                volumeBoostGain = 6,
            ),
        )
        val config = EngineConfigBuilder.build(baselineState(), equalizerEnabled = false, agg = agg)

        assertEquals(eqSettings, config.audioEffects.equalizerSettings)
        assertTrue(config.audioEffects.volumeBoostEnabled)
        assertEquals(6, config.audioEffects.volumeBoostGain)
        assertFalse(config.pauseOnAudioFocusLoss)
    }

    @Test
    fun build_subtitleDelayReadsStyleOffset() {
        val state = baselineState().copy(subtitleStyle = SubtitleStyle(offsetMs = 250L))
        val config = EngineConfigBuilder.build(state, equalizerEnabled = false, agg = VideoPlayerAggregate())
        assertEquals(250L, config.subtitleDelayMs)
        assertEquals(250L, config.subtitleStyle.offsetMs)
    }

    @Test
    fun build_decoderAndPassthroughReadFromState() {
        val state = baselineState().copy(decoderMode = DecoderMode.SW_ONLY, audioPassthrough = true)
        val config = EngineConfigBuilder.build(state, equalizerEnabled = false, agg = VideoPlayerAggregate())
        assertEquals(DecoderMode.SW_ONLY, config.decoderMode)
        assertTrue(config.audioPassthrough)
    }

    @Test
    fun build_videoEffectsPropagatedFromState() {
        val effects = VideoEffectsConfig(brightness = 0.2f, saturation = 1.5f)
        val state = baselineState().copy(videoEffects = effects)
        val config = EngineConfigBuilder.build(state, equalizerEnabled = false, agg = VideoPlayerAggregate())
        assertEquals(effects, config.videoEffects)
    }

    // ─── buildFromPreferences: initial-load / engine-swap path ──────────────────
    // Regression guard: this entry point was added because PlayerSessionManager
    // previously built its own inline EngineConfig literal that silently dropped
    // bass boost / virtualizer / reverb / volume boost / video effects.

    @Test
    fun buildFromPreferences_defaultsPropagateForNeutralAgg() {
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = VideoPlayerAggregate(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = null,
        )
        assertEquals(DecoderMode.HW_PREFERRED, config.decoderMode)
        assertFalse(config.audioPassthrough)
        assertEquals(0L, config.audioDelayMs)
        assertEquals(VideoEffectsConfig(), config.videoEffects)
        assertNull(config.engineSpecific)
        // Bass/virtualizer/reverb/volume-boost must survive the load path.
        with(config.audioEffects) {
            assertFalse(bassBoostEnabled)
            assertEquals(500, virtualizerStrength)
            assertEquals(ReverbPreset.NONE, reverbPreset)
            assertFalse(volumeBoostEnabled)
        }
    }

    @Test
    fun buildFromPreferences_carriesAudioEffectsFromAgg() {
        val agg = VideoPlayerAggregate(
            audioEffects = AudioEffectsSlice(
                dialogueBoostEnabled = true,
                dialogueBoostStrength = EffectStrength.HIGH,
                nightModeEnabled = true,
                nightModeStrength = EffectStrength.MODERATE,
                bassBoostEnabled = true,
                bassBoostStrength = EffectStrength.LOW,
                virtualizerEnabled = true,
                virtualizerStrength = 750,
                reverbPreset = ReverbPreset.LARGE_HALL,
                volumeBoostEnabled = true,
                volumeBoostGain = 6,
            ),
        )
        val config = EngineConfigBuilder.buildFromPreferences(agg, emptyList(), null, null)

        with(config.audioEffects) {
            assertTrue(dialogueBoostEnabled)
            assertEquals(EffectStrength.HIGH, dialogueBoostStrength)
            assertTrue(nightModeEnabled)
            assertTrue(bassBoostEnabled)
            assertTrue(virtualizerEnabled)
            assertEquals(750, virtualizerStrength)
            assertEquals(ReverbPreset.LARGE_HALL, reverbPreset)
            assertTrue(volumeBoostEnabled)
            assertEquals(6, volumeBoostGain)
        }
    }

    @Test
    fun buildFromPreferences_equalizerSettingsAndNightModeGainFromAgg() {
        val eqSettings = EqualizerSettings(bandLevels = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        val agg = VideoPlayerAggregate(
            audioEffects = AudioEffectsSlice(
                equalizerEnabled = true,
                equalizerSettings = eqSettings,
                nightModeEnabled = true,
            ),
            audio = AudioSlice(audioNightModeGain = 1500),
        )
        val config = EngineConfigBuilder.buildFromPreferences(agg, emptyList(), null, null)
        assertTrue(config.audioEffects.equalizerEnabled)
        assertEquals(eqSettings, config.audioEffects.equalizerSettings)
        assertEquals(1500, config.audioEffects.nightModeGain)
    }

    @Test
    fun buildFromPreferences_pauseOnAudioFocusLossPropagated() {
        val agg = VideoPlayerAggregate(playback = PlaybackSlice(pauseOnAudioFocusLoss = false))
        val config = EngineConfigBuilder.buildFromPreferences(agg, emptyList(), null, null)
        assertFalse(config.pauseOnAudioFocusLoss)
    }

    @Test
    fun buildFromPreferences_nullItemId_fallsBackToNeutralVideoEffects() {
        val agg = VideoPlayerAggregate(
            engine = PlayerEngineSlice(videoEffectsByItem = mapOf("known" to VideoEffectsConfig(brightness = 0.5f))),
        )
        val config = EngineConfigBuilder.buildFromPreferences(agg, emptyList(), itemId = null, engineSpecific = null)
        assertEquals(VideoEffectsConfig(), config.videoEffects)
    }

    @Test
    fun buildFromPreferences_knownItemId_resolvesPerItemVideoEffects() {
        val effects = VideoEffectsConfig(contrast = 1.4f, saturation = 1.2f)
        val agg = VideoPlayerAggregate(
            engine = PlayerEngineSlice(videoEffectsByItem = mapOf("item-42" to effects)),
        )
        val config = EngineConfigBuilder.buildFromPreferences(agg, emptyList(), itemId = "item-42", engineSpecific = null)
        assertEquals(effects, config.videoEffects)
    }

    @Test
    fun buildFromPreferences_hdrStream_appliesHdrSubtitleStyleWhenEnabled() {
        val hdrStyle = SubtitleStyle(applyCustomStyle = true, fontColor = SubtitleColor.WHITE, fontSize = 20)
        val agg = VideoPlayerAggregate(
            subtitle = SubtitleSlice(
                hdrSubtitleStyleEnabled = true,
                hdrSubtitleStyle = hdrStyle,
            ),
        )
        val streams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "HDR10"))
        val config = EngineConfigBuilder.buildFromPreferences(agg, streams, null, null)
        assertEquals(hdrStyle, config.subtitleStyle)
    }

    @Test
    fun buildFromPreferences_sdrStream_usesUserSubtitleStyle() {
        val userStyle = SubtitleStyle(applyCustomStyle = true, fontSize = 18)
        val agg = VideoPlayerAggregate(
            subtitle = SubtitleSlice(
                subtitleStyle = userStyle,
                hdrSubtitleStyleEnabled = true,
                hdrSubtitleStyle = SubtitleStyle(fontSize = 30),
            ),
        )
        val streams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "SDR"))
        val config = EngineConfigBuilder.buildFromPreferences(agg, streams, null, null)
        assertEquals(userStyle, config.subtitleStyle)
        assertEquals(18, config.subtitleStyle.fontSize)
    }

    @Test
    fun buildFromPreferences_engineSpecificPropagated() {
        val mpvConfig: EngineSpecificConfig = MpvEngineConfig()
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = VideoPlayerAggregate(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = mpvConfig,
        )
        assertSame(mpvConfig, config.engineSpecific)
    }

    @Test
    fun buildFromPreferences_exoAndVlcEngineConfigsPropagated() {
        val exoConfig: EngineSpecificConfig = ExoPlayerEngineConfig(audioOffloadMode = ExoPlayerEngineConfig().audioOffloadMode)
        val vlcConfig: EngineSpecificConfig = LibVlcEngineConfig(networkCaching = 1500)

        val exoBuilt = EngineConfigBuilder.buildFromPreferences(VideoPlayerAggregate(), emptyList(), null, exoConfig)
        assertSame(exoConfig, exoBuilt.engineSpecific)

        val vlcBuilt = EngineConfigBuilder.buildFromPreferences(VideoPlayerAggregate(), emptyList(), null, vlcConfig)
        assertSame(vlcConfig, vlcBuilt.engineSpecific)
        assertNotNull(vlcBuilt.engineSpecific as LibVlcEngineConfig)
    }

    @Test
    fun buildFromPreferences_decoderAndPassthroughReadFromAgg() {
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(decoderMode = DecoderMode.SW_ONLY, audioPassthrough = true),
            audio = AudioSlice(audioDelayMs = 99L),
        )
        val config = EngineConfigBuilder.buildFromPreferences(agg, emptyList(), null, null)
        assertEquals(DecoderMode.SW_ONLY, config.decoderMode)
        assertTrue(config.audioPassthrough)
        assertEquals(99L, config.audioDelayMs)
    }

    @Test
    fun buildFromPreferences_subtitleDelayReadsResolvedStyleOffset() {
        val style = SubtitleStyle(applyCustomStyle = true, offsetMs = 333L)
        val agg = VideoPlayerAggregate(subtitle = SubtitleSlice(subtitleStyle = style))
        val config = EngineConfigBuilder.buildFromPreferences(agg, emptyList(), null, null)
        assertEquals(333L, config.subtitleDelayMs)
        assertEquals(333L, config.subtitleStyle.offsetMs)
    }
}
