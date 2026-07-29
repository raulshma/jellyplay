package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.BassBoost
import com.raulshma.jellyplay.core.model.EffectStrength

/**
 * Bass-boost wrapper. Effect-specific state is the [strength] enum; the
 * release-before-create / session-id-remember / enabled-flag skeleton lives
 * in [AudioFxHelper].
 */
class BassBoostHelper : AudioFxHelper<BassBoost>(TAG) {

    var strength: EffectStrength = EffectStrength.MODERATE
        private set

    private val strengthValue: Short
        get() = when (strength) {
            EffectStrength.NONE -> 0
            EffectStrength.LOW -> 400
            EffectStrength.MODERATE -> 700
            EffectStrength.HIGH -> 1000
        }

    fun setStrength(strength: EffectStrength) {
        this.strength = strength
        if (isEnabled) {
            try {
                fx?.setStrength(strengthValue)
            } catch (_: Exception) {}
        }
    }

    override fun create(audioSessionId: Int): BassBoost? =
        BassBoost(0, audioSessionId).apply { setStrength(strengthValue) }

    companion object {
        private const val TAG = "BassBoostHelper"
    }
}
