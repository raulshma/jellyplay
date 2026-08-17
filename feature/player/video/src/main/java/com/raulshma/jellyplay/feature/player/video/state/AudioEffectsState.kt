package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset

/**
 * Audio-effects state owned by
 * [com.raulshma.jellyplay.feature.player.video.VideoEffectsController]: night
 * mode, passthrough, decoder mode, normalization, channel mix, bass boost,
 * virtualizer, reverb, audio delay. Mutated via the AVSync/Effects sheets;
 * read by the engine config.
 *
 * `dialogueBoost*` deliberately does NOT live here: dialogue boost is
 * per-item/series resolver-driven state with multiple writers (the preference
 * resolver, the engine-bind seeding, the user toggle), so it stays on
 * [com.raulshma.jellyplay.feature.player.video.VideoPlayerUiState].
 */
@Immutable
data class AudioEffectsState(
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val audioPassthrough: Boolean = false,
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val audioDelayMs: Long = 0L,
)
