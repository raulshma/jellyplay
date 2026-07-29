package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.EffectStrength

/**
 * Deep module: the single home for [EffectStrength] → numeric mappings used by
 * night-mode (volume attenuation + loudness-enhancer gain) and dialogue-boost /
 * bass-boost (per-band short levels).
 *
 * **Why this lives here.** Pre-extraction the night-mode gain table
 * (`NONE=0, LOW=1500, MODERATE=3000, HIGH=4500` millibels) was duplicated in
 * two places that could — and did — drift: [NightModeHelper.targetGainForStrength]
 * and [AudioEffectsProcessor.nightModeGainForStrength]. The volume-attenuation
 * table (`1.0, 0.7, 0.4, 0.2`) lived only on the audio path, so the MPV and
 * ExoPlayer video engines re-derived it via their own helpers. Three sites,
 * one rule, no single home.
 *
 * Centralising the mappings means the next tuning change (e.g. "MODERATE
 * should be 3500 mB not 3000") edits one file, and each mapping has a direct
 * test instead of being asserted only through an attached audio session that
 * needs an Android device.
 */
object EffectStrengthMapping {

    /**
     * Loudness-enhancer target gain in **millibels** for night mode.
     * Compensates for the [nightModeVolumeAttenuation] attenuation so the
     * quietest passages stay audible while loud peaks are pulled down by the
     * player volume cut.
     *
     *   - NONE: no boost
     *   - LOW: +1.5 dB
     *   - MODERATE: +3.0 dB
     *   - HIGH: +4.5 dB
     */
    fun nightModeGainMb(strength: EffectStrength): Int = when (strength) {
        EffectStrength.NONE -> 0
        EffectStrength.LOW -> 1500
        EffectStrength.MODERATE -> 3000
        EffectStrength.HIGH -> 4500
    }

    /**
     * Player-volume multiplier for night mode (compresses dynamic range by
     * attenuating the raw output level before the loudness enhancer boosts
     * quiet content). 1.0 = no attenuation.
     *
     *   - NONE: 1.0 (unity)
     *   - LOW: 0.7
     *   - MODERATE: 0.4
     *   - HIGH: 0.2
     */
    fun nightModeVolumeAttenuation(strength: EffectStrength): Float = when (strength) {
        EffectStrength.NONE -> 1.0f
        EffectStrength.LOW -> 0.7f
        EffectStrength.MODERATE -> 0.4f
        EffectStrength.HIGH -> 0.2f
    }
}
