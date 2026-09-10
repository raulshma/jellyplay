package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.BassBoost
import com.raulshma.jellyplay.core.model.EffectStrength

/**
 * Bass-boost wrapper. Effect-specific state is the [strength] enum; the
 * release-before-create / session-id-remember / enabled-flag skeleton lives
 * in [AudioFxHelper].
 */
open class BassBoostHelper(
    private val effectFactory: (Int) -> BassBoost = ::defaultBassEffect,
) : AudioFxHelper<BassBoost>(TAG) {

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
        // Construct first, then configure inside createSafely so a throw during
        // setStrength still releases the native BassBoost handle — otherwise
        // the object never reaches `fx` and detach()/releaseFx() can't free it.
        createSafely(audioSessionId, effectFactory) { it.setStrength(strengthValue) }

    companion object {
        fun defaultBassEffect(audioSessionId: Int): BassBoost = BassBoost(0, audioSessionId)
        private const val TAG = "BassBoostHelper"
    }
}
