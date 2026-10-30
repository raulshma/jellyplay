package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.hdrContentFromStreams
import com.raulshma.jellyplay.feature.player.video.state.AudioEffectsState

/**
 * Builds the engine [EngineConfig] from the current UI state plus the cached
 * preferences snapshot.
 *
 * Extracted verbatim from `VideoPlayerViewModel.updateConfigWithUiState()`
 * so the audio-effects / decoder / subtitle-delay → [EngineConfig] mapping is a pure,
 * unit-testable function instead of an inline block inside the ~2 kLOC
 * ViewModel. The ViewModel still owns the live state and applies the result to
 * the active engine; this object performs only the translation.
 *
 * Notes:
 *  - `equalizerEnabled` is passed explicitly because it lives on the ViewModel
 *    (it is not part of [VideoPlayerUiState]).
 *  - `equalizerSettings` and `volumeBoost*` are read
 *    from the cached [VideoPlayerAggregate] snapshot, preserving the prior behaviour.
 *    (`pauseOnAudioFocusLoss` left the config with the video focus slice — the OS
 *    audio-focus seat moved into core:data's PlaybackFocus module.)
 *
 * Use [buildFromPreferences] on the *initial-load* / engine-swap paths where the
 * config is being constructed before any UI state exists (e.g. from
 * `PlayerSessionManager`). Routing both paths through this object keeps a single
 * construction site — previously `PlayerSessionManager` built its own inline
 * `EngineConfig` literal that silently dropped bass boost / virtualizer /
 * reverb / volume boost / video effects (which only appear in `build`/`buildFromPreferences`),
 * so those prefs were ignored until the first runtime `updateConfig`.
 */
internal object EngineConfigBuilder {

    /**
     * The state-bag entry: projects the [VideoPlayerUiState] slices the
     * builder reads and delegates to [buildFromSlices]. Kept as the
     * adapter for tests pinning the original mapping; the RUNTIME path
     * enters through [buildFromSlices] directly — the EngineConfigSync
     * controller takes the slices as narrow constructor lambdas, so the
     * state bag never crosses the controller boundary (the ownership
     * guard).
     */
    fun build(
        state: VideoPlayerUiState,
        effects: AudioEffectsState,
        equalizerEnabled: Boolean,
        agg: VideoPlayerAggregate,
        engineSpecific: EngineSpecificConfig? = null,
        deinterlace: com.raulshma.jellyplay.core.model.DeinterlaceMode =
            com.raulshma.jellyplay.core.model.DeinterlaceMode.AUTO,
    ): EngineConfig = buildFromSlices(
        subtitleStyle = state.subtitleStyle,
        videoEffects = state.videoFx.videoEffects,
        dialogueBoostEnabled = state.dialogueBoostEnabled,
        dialogueBoostStrength = state.dialogueBoostStrength,
        mediaStreams = state.media.mediaStreams,
        effects = effects,
        equalizerEnabled = equalizerEnabled,
        agg = agg,
        engineSpecific = engineSpecific,
        deinterlace = deinterlace,
    )

    /**
     * The slice entry (the fields the runtime builder actually reads, one
     * mapping shared with [build] so the two entries cannot drift): the
     * audio-effects / decoder / subtitle-delay → [EngineConfig] translation
     * over the individual state slices instead of the state bag.
     */
    fun buildFromSlices(
        subtitleStyle: SubtitleStyle,
        videoEffects: VideoEffectsConfig,
        dialogueBoostEnabled: Boolean,
        dialogueBoostStrength: EffectStrength,
        mediaStreams: List<MediaStream>,
        effects: AudioEffectsState,
        equalizerEnabled: Boolean,
        agg: VideoPlayerAggregate,
        engineSpecific: EngineSpecificConfig? = null,
        deinterlace: com.raulshma.jellyplay.core.model.DeinterlaceMode =
            com.raulshma.jellyplay.core.model.DeinterlaceMode.AUTO,
    ): EngineConfig {
        val hdrContent = hdrContentFromStreams(mediaStreams)
        return EngineConfig(
            decoderMode = effects.decoderMode,
            audioPassthrough = effects.audioPassthrough,
            audioPassthroughCodecs = agg.playback.audioPassthroughCodecs,
            audioDelayMs = effects.audioDelayMs,
            subtitleDelayMs = subtitleStyle.offsetMs,
            subtitleStyle = subtitleStyle,
            videoEffects = videoEffects,
            audioEffects = audioEffects(
                dialogueBoostEnabled = dialogueBoostEnabled,
                dialogueBoostStrength = dialogueBoostStrength,
                nightModeEnabled = effects.nightModeEnabled,
                nightModeStrength = effects.nightModeStrength,
                equalizerEnabled = equalizerEnabled,
                audioNormalizationMode = effects.audioNormalizationMode,
                audioNormalizationEnabled = effects.audioNormalizationEnabled,
                channelMixMode = effects.channelMixMode,
                channelMixEnabled = effects.channelMixEnabled,
                maxAudioChannels = agg.playback.maxAudioChannels,
                downmixBoostDb = agg.playback.downmixBoostDb,
                bassBoostEnabled = effects.bassBoostEnabled,
                bassBoostStrength = effects.bassBoostStrength,
                virtualizerEnabled = effects.virtualizerEnabled,
                virtualizerStrength = effects.virtualizerStrength,
                reverbPreset = effects.reverbPreset,
                spatialAudioEnabled = effects.spatialAudioEnabled,
                spatialAudioOnlyHeadphones = effects.spatialAudioOnlyHeadphones,
                audioSlice = agg.audio,
                audioEffectsSlice = agg.audioEffects,
            ),
            engineSpecific = foldEngineSpecific(engineSpecific, agg),
            // the session-scoped deinterlace override + the per-item HDR
            // gate ride every runtime build (the UI-state path has the streams).
            deinterlace = deinterlace,
            hdrSource = hdrContent.isHdr,
            hdrContent = hdrContent,
            hdrFormatPreference = agg.playback.hdrFormatPreference,
        )
    }

    /**
     * Initial-load / engine-swap variant. Builds the config directly from a
     * [VideoPlayerAggregate] snapshot (the state available before the player UI has
     * hydrated). [mediaStreams] drives HDR-aware subtitle styling; [itemId]
     * resolves per-item video effects; [engineSpecific] is the caller-resolved
     * typed per-engine config (or `null`).
     */
    fun buildFromPreferences(
        agg: VideoPlayerAggregate,
        mediaStreams: List<MediaStream>,
        itemId: String?,
        engineSpecific: EngineSpecificConfig?,
    ): EngineConfig {
        val hdrContent = hdrContentFromStreams(mediaStreams)
        val isHdr = hdrContent.isHdr
        // Subtitle delay is per-media: resolve the per-item override (else the
        // global default) so the engine boots with the correct delay on initial
        // load / engine swap. Without this the engine starts at the global
        // default while the engineFlow collector seeds uiState with the per-item
        // value — the load path then sees no diff and never pushes the delay, so
        // a saved correction would show in metadata but never reach the engine.
        val subtitleStyle = resolveSubtitleStyleWithDelay(agg.subtitle, itemId, isHdr = isHdr)
        return EngineConfig(
            decoderMode = agg.playback.decoderMode,
            audioPassthrough = agg.playback.audioPassthrough,
            audioPassthroughCodecs = agg.playback.audioPassthroughCodecs,
            audioDelayMs = agg.audio.audioDelayMs,
            subtitleDelayMs = subtitleStyle.offsetMs,
            subtitleStyle = subtitleStyle,
            videoEffects = itemId?.let { agg.engine.videoEffectsByItem[it] } ?: VideoEffectsConfig(),
            audioEffects = audioEffects(
                dialogueBoostEnabled = agg.audioEffects.dialogueBoostEnabled,
                dialogueBoostStrength = agg.audioEffects.dialogueBoostStrength,
                nightModeEnabled = agg.audioEffects.nightModeEnabled,
                nightModeStrength = agg.audioEffects.nightModeStrength,
                equalizerEnabled = agg.audioEffects.equalizerEnabled,
                audioNormalizationMode = agg.audio.audioNormalizationMode,
                audioNormalizationEnabled = agg.audio.audioNormalizationEnabled,
                channelMixMode = agg.audio.channelMixMode,
                channelMixEnabled = agg.audio.channelMixEnabled,
                maxAudioChannels = agg.playback.maxAudioChannels,
                downmixBoostDb = agg.playback.downmixBoostDb,
                bassBoostEnabled = agg.audioEffects.bassBoostEnabled,
                bassBoostStrength = agg.audioEffects.bassBoostStrength,
                virtualizerEnabled = agg.audioEffects.virtualizerEnabled,
                virtualizerStrength = agg.audioEffects.virtualizerStrength,
                reverbPreset = agg.audioEffects.reverbPreset,
                // The spatial prefs live on the playback slice (the settings
                // screen's home); the engine boots with the persisted values.
                spatialAudioEnabled = agg.playback.spatialAudioEnabled,
                spatialAudioOnlyHeadphones = agg.playback.spatialAudioOnlyHeadphones,
                audioSlice = agg.audio,
                audioEffectsSlice = agg.audioEffects,
            ),
            engineSpecific = foldEngineSpecific(engineSpecific, agg),
            deinterlace = com.raulshma.jellyplay.core.model.DeinterlaceMode.AUTO,
            hdrSource = isHdr,
            hdrContent = hdrContent,
            hdrFormatPreference = agg.playback.hdrFormatPreference,
        )
    }

    /**
     * The engine-specific prefs the persisted [VideoPlayerAggregate] slices
     * own, folded UNCONDITIONALLY into the effective engine config:
     *
     *  - mpv ([MpvEngineConfig]): the video-player slice's render prefs
     *    (tone-mapping curve, deband strength, motion interpolation,
     *    bitmap-subtitle scaling) plus the raw `input.conf` key-bindings text
     *    (blank → `null` = nothing applied). The stored config blob never
     *    carries them (the settings rows live on the video-player slice), and
     *    the pref defaults ARE the mpv no-ops (curve `auto`, deband `no`,
     *    interpolation off, FIT scaling, empty input.conf), so an
     *    unconditional write changes nothing until the user picks something.
     *    Nullable `null`-means-unset config fields are not used here: the
     *    prefs are the source of truth, always present, so the engine diff
     *    caches see a deterministic config per pref state. NOTE: the
     *    input.conf text is applied by the engine layer at handle-INIT time
     *    (mpv reads `input.conf` during `mpv_initialize`), so a mid-session
     *    edit lands on the next engine rebuild/playback, not live.
     *  - Exo ([ExoPlayerEngineConfig]): the playback slice's
     *    [com.raulshma.jellyplay.core.model.HdrFormatPreference] rides the
     *    blob's `hdrFormatPreference` — the DECODER-side half of the posture
     *    the engine's MIME ordering consumes
     *    (`ExoPassthroughOffload.preferredMimeTypes`; the OUTPUT half rides
     *    the shared [EngineConfig.hdrFormatPreference]). The blob field is
     *    nullable (`null` = the engine's default negotiation stands), so
     *    AUTO folds to `null` and the arms fold to themselves — the same
     *    AUTO/`null` equivalence the MIME table applies. The bitmap-subtitle
     *    scale mode rides along too (nullable: `null` = FIT) — the Exo side
     *    is FIT-only today (see the field's KDoc for the Media3 constraint).
     *
     * Any other engine-specific payload passes through untouched.
     */
    private fun foldEngineSpecific(
        engineSpecific: EngineSpecificConfig?,
        agg: VideoPlayerAggregate,
    ): EngineSpecificConfig? = when (engineSpecific) {
        is MpvEngineConfig -> engineSpecific.copy(
            toneMappingCurve = agg.videoPlayer.mpvToneMappingCurve,
            debandMode = agg.videoPlayer.mpvDebandMode,
            motionInterpolation = agg.videoPlayer.mpvMotionInterpolation,
            bitmapSubtitleScaleMode = agg.videoPlayer.bitmapSubtitleScaleMode,
            userInputConf = agg.videoPlayer.mpvInputConf.ifBlank { null },
        )
        is ExoPlayerEngineConfig -> engineSpecific.copy(
            hdrFormatPreference = agg.playback.hdrFormatPreference.takeUnless {
                it == com.raulshma.jellyplay.core.model.HdrFormatPreference.AUTO
            },
            bitmapSubtitleScaleMode = agg.videoPlayer.bitmapSubtitleScaleMode,
        )
        else -> engineSpecific
    }

    /**
     * Shared [AudioEffectsConfig] constructor so the two entry points cannot
     * drift (the original bug was the load path omitting bass/virtualizer/
     * reverb/volume-boost that this centralises).
     */
    private fun audioEffects(
        dialogueBoostEnabled: Boolean,
        dialogueBoostStrength: EffectStrength,
        nightModeEnabled: Boolean,
        nightModeStrength: EffectStrength,
        equalizerEnabled: Boolean,
        audioNormalizationMode: AudioNormalizationMode,
        audioNormalizationEnabled: Boolean,
        channelMixMode: ChannelMixMode,
        channelMixEnabled: Boolean,
        maxAudioChannels: com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum,
        downmixBoostDb: Float,
        bassBoostEnabled: Boolean,
        bassBoostStrength: EffectStrength,
        virtualizerEnabled: Boolean,
        virtualizerStrength: Int,
        reverbPreset: ReverbPreset,
        spatialAudioEnabled: Boolean,
        spatialAudioOnlyHeadphones: Boolean,
        audioSlice: com.raulshma.jellyplay.core.datastore.audio.AudioSlice,
        audioEffectsSlice: com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice,
    ): AudioEffectsConfig = AudioEffectsConfig(
        dialogueBoostEnabled = dialogueBoostEnabled,
        dialogueBoostStrength = dialogueBoostStrength,
        nightModeEnabled = nightModeEnabled,
        nightModeStrength = nightModeStrength,
        nightModeGain = audioSlice.audioNightModeGain,
        equalizerEnabled = equalizerEnabled,
        equalizerSettings = audioEffectsSlice.equalizerSettings,
        audioNormalizationMode = audioNormalizationMode,
        audioNormalizationEnabled = audioNormalizationEnabled,
        channelMixMode = channelMixMode,
        channelMixEnabled = channelMixEnabled,
        maxAudioChannels = maxAudioChannels,
        downmixBoostDb = downmixBoostDb,
        bassBoostEnabled = bassBoostEnabled,
        bassBoostStrength = bassBoostStrength,
        virtualizerEnabled = virtualizerEnabled,
        virtualizerStrength = virtualizerStrength,
        reverbPreset = reverbPreset,
        volumeBoostEnabled = audioEffectsSlice.volumeBoostEnabled,
        volumeBoostGain = audioEffectsSlice.volumeBoostGain,
        spatialAudioEnabled = spatialAudioEnabled,
        spatialAudioOnlyHeadphones = spatialAudioOnlyHeadphones,
    )
}
