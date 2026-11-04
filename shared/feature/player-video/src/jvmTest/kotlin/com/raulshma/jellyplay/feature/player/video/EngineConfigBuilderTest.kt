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
import com.raulshma.jellyplay.feature.player.video.engine.HdrContentInfo
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins [EngineConfigBuilder] — the audio-effects / decoder orchestration mapping
 * extracted from `VideoPlayerViewModel`. Pure function, no
 * Android, so it is exercised directly.
 */
class EngineConfigBuilderTest {

    private fun baselineState() = VideoPlayerUiState()
    private fun baselineEffects() = com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState()

    @Test
    fun build_defaultsPropagateForNeutralState() {
        val config = EngineConfigBuilder.build(baselineState(), baselineEffects(), equalizerEnabled = false, agg = VideoPlayerAggregate())
        assertEquals(EngineConfig().decoderMode, config.decoderMode)
        assertFalse(config.audioPassthrough)
        assertEquals(0L, config.audioDelayMs)
        assertEquals(0L, config.subtitleDelayMs)
        assertFalse(config.audioEffects.dialogueBoostEnabled)
        assertFalse(config.audioEffects.equalizerEnabled)
    }

    @Test
    fun build_carriesAudioEffectsFromStateAndEffectsSlice() {
        // Dialogue boost stays on the UiState (per-item resolver-driven); the
        // other effects come from the controller-owned AudioEffectsState.
        val state = baselineState().copy(
            dialogueBoostEnabled = true,
            dialogueBoostStrength = EffectStrength.HIGH,
        )
        val effects = com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState(
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
        val config = EngineConfigBuilder.build(state, effects, equalizerEnabled = true, agg = VideoPlayerAggregate())

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
        val config = EngineConfigBuilder.build(baselineState(), baselineEffects(), equalizerEnabled = true, agg = VideoPlayerAggregate())
        assertTrue(config.audioEffects.equalizerEnabled)
    }

    @Test
    fun build_equalizerAndVolumeBoostComeFromAgg() {
        val eqSettings = EqualizerSettings(bandLevels = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        val agg = VideoPlayerAggregate(
            audioEffects = AudioEffectsSlice(
                equalizerSettings = eqSettings,
                volumeBoostEnabled = true,
                volumeBoostGain = 6,
            ),
        )
        val config = EngineConfigBuilder.build(baselineState(), baselineEffects(), equalizerEnabled = false, agg = agg)

        assertEquals(eqSettings, config.audioEffects.equalizerSettings)
        assertTrue(config.audioEffects.volumeBoostEnabled)
        assertEquals(6, config.audioEffects.volumeBoostGain)
    }

    @Test
    fun build_subtitleDelayReadsStyleOffset() {
        val state = baselineState().copy(subtitleStyle = SubtitleStyle(offsetMs = 250L))
        val config = EngineConfigBuilder.build(state, baselineEffects(), equalizerEnabled = false, agg = VideoPlayerAggregate())
        assertEquals(250L, config.subtitleDelayMs)
        assertEquals(250L, config.subtitleStyle.offsetMs)
    }

    @Test
    fun build_decoderAndPassthroughReadFromEffectsSlice() {
        val effects = baselineEffects().copy(decoderMode = DecoderMode.SW_ONLY, audioPassthrough = true)
        val config = EngineConfigBuilder.build(baselineState(), effects, equalizerEnabled = false, agg = VideoPlayerAggregate())
        assertEquals(DecoderMode.SW_ONLY, config.decoderMode)
        assertTrue(config.audioPassthrough)
    }

    @Test
    fun build_videoEffectsPropagatedFromState() {
        val effects = VideoEffectsConfig(brightness = 0.2f, saturation = 1.5f)
        val state = baselineState().copy(videoFx = VideoFxState(videoEffects = effects))
        val config = EngineConfigBuilder.build(state, baselineEffects(), equalizerEnabled = false, agg = VideoPlayerAggregate())
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
    fun hdrContent_propagatesWithBooleanTwin() {
        // The typed HDR family rides beside the Boolean gate on BOTH entry
        // points, and the invariant hdrSource == (hdrContent != NONE) holds.
        val dvStreams = listOf(
            MediaStream(index = 1, type = StreamType.AUDIO),
            MediaStream(index = 0, type = StreamType.VIDEO, videoRangeType = "HDR10Plus"),
        )
        val built = EngineConfigBuilder.buildFromPreferences(VideoPlayerAggregate(), dvStreams, null, null)
        assertEquals(HdrContentInfo.HDR10_PLUS, built.hdrContent)
        assertTrue(built.hdrSource)

        val sliced = EngineConfigBuilder.buildFromSlices(
            subtitleStyle = SubtitleStyle(),
            videoEffects = VideoEffectsConfig(),
            dialogueBoostEnabled = false,
            dialogueBoostStrength = EffectStrength.MODERATE,
            mediaStreams = dvStreams,
            effects = baselineEffects(),
            equalizerEnabled = false,
            agg = VideoPlayerAggregate(),
        )
        assertEquals(HdrContentInfo.HDR10_PLUS, sliced.hdrContent)
        assertTrue(sliced.hdrSource)

        val sdr = EngineConfigBuilder.buildFromPreferences(
            VideoPlayerAggregate(),
            listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "SDR")),
            null,
            null,
        )
        assertEquals(HdrContentInfo.NONE, sdr.hdrContent)
        assertFalse(sdr.hdrSource)
    }

    @Test
    fun buildFromPreferences_engineSpecificPropagated() {
        // Non-mpv payloads pass through by identity; an mpv payload is
        // deliberately COPIED — the four persisted render prefs fold into it
        // unconditionally (see foldEngineSpecific), pinned below.
        val mpvConfig: EngineSpecificConfig = MpvEngineConfig()
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = VideoPlayerAggregate(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = mpvConfig,
        )
        assertEquals(
            MpvEngineConfig(
                toneMappingCurve = com.raulshma.jellyplay.core.model.MpvToneMappingCurve.AUTO,
                debandMode = com.raulshma.jellyplay.core.model.MpvDebandMode.OFF,
                motionInterpolation = false,
                bitmapSubtitleScaleMode = com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode.FIT,
            ),
            config.engineSpecific,
        )
    }

    @Test
    fun buildFromPreferences_mpvRenderPrefsFoldIntoTheEngineConfig() {
        val agg = VideoPlayerAggregate(
            videoPlayer = com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice(
                mpvToneMappingCurve = com.raulshma.jellyplay.core.model.MpvToneMappingCurve.BT_2390,
                mpvDebandMode = com.raulshma.jellyplay.core.model.MpvDebandMode.HEAVY,
                mpvMotionInterpolation = true,
                bitmapSubtitleScaleMode = com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode.COVER,
            ),
        )
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = MpvEngineConfig(shaderPack = com.raulshma.jellyplay.core.model.MpvShaderPack.ANIME4K_A),
        )
        val mpv = config.engineSpecific as MpvEngineConfig
        assertEquals(com.raulshma.jellyplay.core.model.MpvToneMappingCurve.BT_2390, mpv.toneMappingCurve)
        assertEquals(com.raulshma.jellyplay.core.model.MpvDebandMode.HEAVY, mpv.debandMode)
        assertEquals(true, mpv.motionInterpolation)
        assertEquals(com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode.COVER, mpv.bitmapSubtitleScaleMode)
        // The non-folded fields ride the original config untouched.
        assertEquals(com.raulshma.jellyplay.core.model.MpvShaderPack.ANIME4K_A, mpv.shaderPack)
    }

    @Test
    fun buildFromPreferences_mpvInputConfFoldsBlankToNull() {
        // Blank (the default) applies nothing: userInputConf lands null so
        // the engine layer skips the input.conf sync entirely.
        val agg = VideoPlayerAggregate()
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = MpvEngineConfig(),
        )
        assertNull((config.engineSpecific as MpvEngineConfig).userInputConf)
    }

    @Test
    fun buildFromPreferences_mpvInputConfFoldsIntoTheEngineConfigOnBothPaths() {
        val agg = VideoPlayerAggregate(
            videoPlayer = com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice(
                mpvInputConf = "MBTN_RIGHT cycle pause",
            ),
        )
        val fromPrefs = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = MpvEngineConfig(),
        )
        assertEquals("MBTN_RIGHT cycle pause", (fromPrefs.engineSpecific as MpvEngineConfig).userInputConf)
        // The runtime path (the slices entry) folds identically — the two
        // build paths cannot drift.
        val fromSlices = EngineConfigBuilder.build(
            state = baselineState(),
            effects = baselineEffects(),
            equalizerEnabled = false,
            agg = agg,
            engineSpecific = MpvEngineConfig(),
        )
        assertEquals("MBTN_RIGHT cycle pause", (fromSlices.engineSpecific as MpvEngineConfig).userInputConf)
    }

    @Test
    fun buildFromPreferences_exoBitmapSubtitleScaleModeFolds() {
        // The Exo side is FIT-only today (Media3 constraint — see the field's
        // KDoc); the fold still carries the pref so the seam stays symmetric.
        val agg = VideoPlayerAggregate(
            videoPlayer = com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice(
                bitmapSubtitleScaleMode = com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode.STRETCH,
            ),
        )
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = ExoPlayerEngineConfig(),
        )
        assertEquals(
            com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode.STRETCH,
            (config.engineSpecific as ExoPlayerEngineConfig).bitmapSubtitleScaleMode,
        )
    }

    @Test
    fun build_hdrFormatPreferenceFoldsFromThePlaybackSliceOnBothPaths() {
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(hdrFormatPreference = com.raulshma.jellyplay.core.model.HdrFormatPreference.SDR_ONLY),
        )
        val fromPrefs = EngineConfigBuilder.buildFromPreferences(agg, emptyList(), null, null)
        assertEquals(
            com.raulshma.jellyplay.core.model.HdrFormatPreference.SDR_ONLY,
            fromPrefs.hdrFormatPreference,
        )
        val fromSlices = EngineConfigBuilder.buildFromSlices(
            subtitleStyle = SubtitleStyle(),
            videoEffects = VideoEffectsConfig(),
            dialogueBoostEnabled = false,
            dialogueBoostStrength = EffectStrength.MODERATE,
            mediaStreams = emptyList(),
            effects = baselineEffects(),
            equalizerEnabled = false,
            agg = agg,
        )
        assertEquals(
            com.raulshma.jellyplay.core.model.HdrFormatPreference.SDR_ONLY,
            fromSlices.hdrFormatPreference,
        )
    }

    @Test
    fun buildFromPreferences_exoAndVlcEngineConfigsPropagated() {
        val exoConfig: EngineSpecificConfig = ExoPlayerEngineConfig(audioOffloadMode = ExoPlayerEngineConfig().audioOffloadMode)
        val vlcConfig: EngineSpecificConfig = LibVlcEngineConfig(networkCaching = 1500)

        // The Exo payload is deliberately COPIED — the playback slice's HDR
        // format preference AND the video-player slice's bitmap-subtitle
        // scale mode fold into it (see foldEngineSpecific); under the neutral
        // AUTO pref the HDR fold is the original's null-standing, and the
        // scale-mode fold writes the pref's FIT default (the mode prefs are
        // the source of truth — the same unconditional-write convention the
        // mpv render prefs follow).
        val exoBuilt = EngineConfigBuilder.buildFromPreferences(VideoPlayerAggregate(), emptyList(), null, exoConfig)
        assertEquals(
            (exoConfig as ExoPlayerEngineConfig).copy(
                bitmapSubtitleScaleMode = com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode.FIT,
            ),
            exoBuilt.engineSpecific,
        )

        val vlcBuilt = EngineConfigBuilder.buildFromPreferences(VideoPlayerAggregate(), emptyList(), null, vlcConfig)
        // Since issue #186 §5.8 the VLC payload is COPIED too — the
        // performance-profile tuning folds into it (identity under the
        // default NONE tuning: every fold is the value's own no-op). The
        // mpv-payload convention above, extended to the libVLC arm.
        assertEquals(vlcConfig, vlcBuilt.engineSpecific)
        assertNotNull(vlcBuilt.engineSpecific as LibVlcEngineConfig)
    }

    @Test
    fun buildFromPreferences_hdrFormatPreferenceFoldsIntoTheExoEngineConfig() {
        // A non-AUTO posture lands on the blob's nullable field verbatim —
        // the DECODER-side half the Exo MIME ordering consumes.
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(
                hdrFormatPreference = com.raulshma.jellyplay.core.model.HdrFormatPreference.DOLBY_VISION_ONLY,
            ),
        )
        val built = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = ExoPlayerEngineConfig(preferredVideoMimeTypes = listOf("video/hevc")),
        )
        val exo = built.engineSpecific as ExoPlayerEngineConfig
        assertEquals(
            com.raulshma.jellyplay.core.model.HdrFormatPreference.DOLBY_VISION_ONLY,
            exo.hdrFormatPreference,
        )
        // The non-folded fields ride the original config untouched.
        assertEquals(listOf("video/hevc"), exo.preferredVideoMimeTypes)
        // AUTO folds to the blob's null = unset (the engine's default
        // negotiation stands) — the same AUTO/null equivalence the Exo MIME
        // table applies.
        val autoBuilt = EngineConfigBuilder.buildFromPreferences(
            agg = VideoPlayerAggregate(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = ExoPlayerEngineConfig(hdrFormatPreference = com.raulshma.jellyplay.core.model.HdrFormatPreference.SDR_ONLY),
        )
        assertNull((autoBuilt.engineSpecific as ExoPlayerEngineConfig).hdrFormatPreference)
    }

    @Test
    fun buildFromPreferences_playbackBufferRowsFoldAsDirectInputsOnBothPaths() {
        // The four playback-slice Exo buffer rows are DIRECT engine inputs
        // (not ceilings): the blob's fields are overwritten verbatim, and
        // the MB row goes through the AUTO-sentinel conversion. Both build
        // paths fold identically — they cannot drift.
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(
                exoInitialBufferMs = 2_500,
                exoRebufferMs = 6_000,
                exoTargetBufferMb = 64,
                exoRetainBackBuffer = true,
            ),
        )
        val fromPrefs = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = ExoPlayerEngineConfig(),
        )
        val exo = fromPrefs.engineSpecific as ExoPlayerEngineConfig
        assertEquals(2_500, exo.exoInitialBufferMs)
        assertEquals(6_000, exo.exoRebufferBufferMs)
        assertEquals(64 * 1_048_576, exo.exoTargetBufferBytes)
        assertTrue(exo.exoRetainBackBuffer)

        val fromSlices = EngineConfigBuilder.buildFromSlices(
            subtitleStyle = SubtitleStyle(),
            videoEffects = VideoEffectsConfig(),
            dialogueBoostEnabled = false,
            dialogueBoostStrength = EffectStrength.MODERATE,
            mediaStreams = emptyList(),
            effects = baselineEffects(),
            equalizerEnabled = false,
            agg = agg,
            engineSpecific = ExoPlayerEngineConfig(),
        )
        assertEquals(exo, fromSlices.engineSpecific)
    }

    @Test
    fun buildFromPreferences_autoTargetBufferMb_foldsToTheDeriveFromTracksSentinel() {
        // The store's 0 AUTO default must land on the blob's -1 (Media3
        // derives the target from the tracks) — never a raw 0 byte target.
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(exoTargetBufferMb = 0),
        )
        val built = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = ExoPlayerEngineConfig(exoTargetBufferBytes = 999_999),
        )
        assertEquals(-1, (built.engineSpecific as ExoPlayerEngineConfig).exoTargetBufferBytes)
    }

    @Test
    fun buildFromPreferences_playbackBufferRowsOverrideTheEngineBlob() {
        // The ENGINE-domain blob also persists buffer-shaped fields, but the
        // playback slice owns these rows: its values win even when the blob
        // disagrees (the settings UI edits the playback store — the engine
        // blob's stale numbers are not a competing source).
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(
                exoInitialBufferMs = 4_000,
                exoRetainBackBuffer = true,
            ),
        )
        val built = EngineConfigBuilder.buildFromPreferences(
            agg = agg,
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = ExoPlayerEngineConfig(
                exoInitialBufferMs = 1,
                exoRebufferBufferMs = 2,
                exoTargetBufferBytes = 3,
                exoRetainBackBuffer = false,
            ),
        )
        val exo = built.engineSpecific as ExoPlayerEngineConfig
        assertEquals(4_000, exo.exoInitialBufferMs)
        // Rows the playback slice leaves at default fold too — the slice is
        // the source of truth for ALL four, not a partial overlay.
        assertEquals(3_000, exo.exoRebufferBufferMs)
        assertEquals(-1, exo.exoTargetBufferBytes)
        assertTrue(exo.exoRetainBackBuffer)
    }

    @Test
    fun buildFromPreferences_neutralPlaybackSlice_preservesTheHistoricalBufferPosture() {
        // Untouched install: the pref defaults equal the engine defaults, so
        // the fold is a byte-level no-op on the buffer fields — the pinned
        // engine posture (1_000 / 3_000 / derive-from-tracks / no retain)
        // survives unchanged.
        val built = EngineConfigBuilder.buildFromPreferences(
            agg = VideoPlayerAggregate(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = ExoPlayerEngineConfig(
                exoInitialBufferMs = 1_000,
                exoRebufferBufferMs = 3_000,
                exoTargetBufferBytes = -1,
                exoRetainBackBuffer = false,
            ),
        )
        val exo = built.engineSpecific as ExoPlayerEngineConfig
        assertEquals(1_000, exo.exoInitialBufferMs)
        assertEquals(3_000, exo.exoRebufferBufferMs)
        assertEquals(-1, exo.exoTargetBufferBytes)
        assertFalse(exo.exoRetainBackBuffer)
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

    @Test
    fun buildFromPreferences_subtitleDelayResolvesPerItemOverride() {
        // Regression: the initial-load / engine-swap config must carry the saved
        // per-item delay, not the global default — otherwise the engine boots at
        // the global value and the saved correction never reaches it (shows in
        // metadata, not applied).
        val agg = VideoPlayerAggregate(
            subtitle = SubtitleSlice(
                subtitleStyle = SubtitleStyle(offsetMs = 0L),
                subtitleDelayByItem = mapOf("item-42" to -750L),
            ),
        )
        val config = EngineConfigBuilder.buildFromPreferences(agg, emptyList(), "item-42", null)
        assertEquals(-750L, config.subtitleDelayMs)
        assertEquals(-750L, config.subtitleStyle.offsetMs)
    }

    // ── The §5.14 EngineConfigBuilderProfileTest bullets (issue #186 §5.8) ──

    /** A user who armed every performance-sensitive mpv knob to the hilt. */
    private fun heavyMpvAgg() = VideoPlayerAggregate(
        videoPlayer = com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice(
            mpvToneMappingCurve = com.raulshma.jellyplay.core.model.MpvToneMappingCurve.BT_2390,
            mpvDebandMode = com.raulshma.jellyplay.core.model.MpvDebandMode.HEAVY,
            mpvMotionInterpolation = true,
            bitmapSubtitleScaleMode = com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode.COVER,
        ),
    )

    private fun heavyMpvConfig() = MpvEngineConfig(
        renderQuality = com.raulshma.jellyplay.core.model.MpvRenderQuality.HIGH,
        scaler = com.raulshma.jellyplay.core.model.MpvScaler.EWA_LANCZOS_SHARP,
        deband = true,
        interpolation = true,
        shaderPack = com.raulshma.jellyplay.core.model.MpvShaderPack.ANIME4K_A,
    )

    private val lowTuning =
        com.raulshma.jellyplay.core.model.VideoPerformanceMatrix.forTier(
            com.raulshma.jellyplay.core.model.DevicePerformanceTier.LOW,
        )
    private val mediumTuning =
        com.raulshma.jellyplay.core.model.VideoPerformanceMatrix.forTier(
            com.raulshma.jellyplay.core.model.DevicePerformanceTier.MEDIUM,
        )
    private val noTuning = com.raulshma.jellyplay.core.model.VideoPerformanceTuning.NONE

    @Test
    fun lowMpv_overridesThePerformanceKnobsAndPreservesTheRest() {
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = heavyMpvAgg(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = heavyMpvConfig(),
            performanceTuning = lowTuning,
        )
        val mpv = config.engineSpecific as MpvEngineConfig
        // §5.8's matrix row: PERFORMANCE render, bilinear scaler, deband and
        // interpolation off — LOW wins over the user's armed knobs.
        assertEquals(com.raulshma.jellyplay.core.model.MpvRenderQuality.PERFORMANCE, mpv.renderQuality)
        assertEquals(com.raulshma.jellyplay.core.model.MpvScaler.BILINEAR, mpv.scaler)
        assertFalse(mpv.deband)
        assertFalse(mpv.interpolation)
        // The force-offs pin BOTH surfaces of each knob (the persisted render
        // prefs would otherwise override the structured rows by MpvConfigMapping
        // pair order).
        assertEquals(com.raulshma.jellyplay.core.model.MpvDebandMode.OFF, mpv.debandMode)
        assertEquals(false, mpv.motionInterpolation)
        // Unrelated fields preserved: the user's tone-mapping curve, bitmap
        // scaling and shader pack are quality decisions, not performance taxes.
        assertEquals(com.raulshma.jellyplay.core.model.MpvToneMappingCurve.BT_2390, mpv.toneMappingCurve)
        assertEquals(com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode.COVER, mpv.bitmapSubtitleScaleMode)
        assertEquals(com.raulshma.jellyplay.core.model.MpvShaderPack.ANIME4K_A, mpv.shaderPack)
    }

    @Test
    fun highMpv_preservesTheUserValuesExactly() {
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = heavyMpvAgg(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = heavyMpvConfig(),
            performanceTuning = noTuning,
        )
        val mpv = config.engineSpecific as MpvEngineConfig
        assertEquals(com.raulshma.jellyplay.core.model.MpvRenderQuality.HIGH, mpv.renderQuality)
        assertEquals(com.raulshma.jellyplay.core.model.MpvScaler.EWA_LANCZOS_SHARP, mpv.scaler)
        assertTrue(mpv.deband)
        assertTrue(mpv.interpolation)
        assertEquals(com.raulshma.jellyplay.core.model.MpvDebandMode.HEAVY, mpv.debandMode)
        assertEquals(true, mpv.motionInterpolation)
    }

    @Test
    fun mediumMpv_appliesNoOverrides() {
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = heavyMpvAgg(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = heavyMpvConfig(),
            performanceTuning = mediumTuning,
        )
        val mpv = config.engineSpecific as MpvEngineConfig
        assertEquals(com.raulshma.jellyplay.core.model.MpvRenderQuality.HIGH, mpv.renderQuality)
        assertEquals(com.raulshma.jellyplay.core.model.MpvScaler.EWA_LANCZOS_SHARP, mpv.scaler)
        assertTrue(mpv.deband)
        assertTrue(mpv.interpolation)
    }

    /** A user with a small libVLC cache and the quality-forward decoder posture. */
    private fun qualityLibVlcConfig() = LibVlcEngineConfig(
        networkCaching = 500,
        skipLoopFilter = 2,
        decoderThreads = 0,
        dropLateFrames = false,
        skipFrames = false,
    )

    @Test
    fun lowLibVlc_appliesTheDecoderPostureAndRaisesTheJitterBuffer() {
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = VideoPlayerAggregate(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = qualityLibVlcConfig(),
            performanceTuning = lowTuning,
        )
        val vlc = config.engineSpecific as LibVlcEngineConfig
        // §5.8's matrix row: caching takes the MAX (a jitter buffer is not a
        // quality tax — 500 ms raised to LOW's 1500 ms floor), decoder threads
        // pinned to 2, loop filter skipped at 1, late/frame dropping on.
        assertEquals(1_500, vlc.networkCaching)
        assertEquals(2, vlc.decoderThreads)
        assertEquals(1, vlc.skipLoopFilter)
        assertTrue(vlc.dropLateFrames)
        assertTrue(vlc.skipFrames)
    }

    @Test
    fun lowLibVlc_keepsTheUserLargerNetworkCache() {
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = VideoPlayerAggregate(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = LibVlcEngineConfig(networkCaching = 3_000),
            performanceTuning = lowTuning,
        )
        assertEquals(3_000, (config.engineSpecific as LibVlcEngineConfig).networkCaching)
    }

    @Test
    fun highLibVlc_preservesTheUserValues() {
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = VideoPlayerAggregate(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = qualityLibVlcConfig(),
            performanceTuning = noTuning,
        )
        assertEquals(qualityLibVlcConfig(), config.engineSpecific)
    }

    @Test
    fun mediumLibVlc_isUnchanged() {
        val config = EngineConfigBuilder.buildFromPreferences(
            agg = VideoPlayerAggregate(),
            mediaStreams = emptyList(),
            itemId = null,
            engineSpecific = qualityLibVlcConfig(),
            performanceTuning = mediumTuning,
        )
        assertEquals(qualityLibVlcConfig(), config.engineSpecific)
    }

    @Test
    fun buildFromSlices_foldsTheTuningIdentically() {
        // The two build paths cannot drift: the slices entry folds the same
        // tuning (the runtime path the later-wave VM passes uiPrefs from).
        val fromSlices = EngineConfigBuilder.buildFromSlices(
            subtitleStyle = SubtitleStyle(),
            videoEffects = VideoEffectsConfig(),
            dialogueBoostEnabled = false,
            dialogueBoostStrength = EffectStrength.MODERATE,
            mediaStreams = emptyList(),
            effects = baselineEffects(),
            equalizerEnabled = false,
            agg = heavyMpvAgg(),
            engineSpecific = heavyMpvConfig(),
            performanceTuning = lowTuning,
        )
        val mpv = fromSlices.engineSpecific as MpvEngineConfig
        assertEquals(com.raulshma.jellyplay.core.model.MpvRenderQuality.PERFORMANCE, mpv.renderQuality)
        assertEquals(com.raulshma.jellyplay.core.model.MpvScaler.BILINEAR, mpv.scaler)
        assertFalse(mpv.deband)
        assertFalse(mpv.interpolation)
    }
}
