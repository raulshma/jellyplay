package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.EffectStrength

/**
 * Night-mode volume boost = a [LoudnessEnhancerHelper] whose gain is derived
 * from an [EffectStrength] rather than supplied explicitly. Delegates the
 * `LoudnessEnhancer` lifecycle (attach / enable / detach) to
 * [loudnessEnhancer] so the two do not maintain parallel copies of the same
 * Android-audiofx plumbing; only the strength→millibel mapping lives here.
 */
class NightModeHelper {

    private val loudnessEnhancer = LoudnessEnhancerHelper()

    var isEnabled: Boolean = false
        private set

    var strength: EffectStrength = EffectStrength.MODERATE
        private set

    private val targetGainForStrength: Int
        get() = when (strength) {
            EffectStrength.NONE -> 0
            EffectStrength.LOW -> 1500
            EffectStrength.MODERATE -> 3000
            EffectStrength.HIGH -> 4500
        }

    fun setStrength(strength: EffectStrength) {
        this.strength = strength
        loudnessEnhancer.setGain(targetGainForStrength)
    }

    fun attach(audioSessionId: Int) {
        loudnessEnhancer.setGain(targetGainForStrength)
        loudnessEnhancer.attach(audioSessionId)
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        loudnessEnhancer.setEnabled(enabled)
    }

    fun detach() {
        loudnessEnhancer.detach()
    }
}
