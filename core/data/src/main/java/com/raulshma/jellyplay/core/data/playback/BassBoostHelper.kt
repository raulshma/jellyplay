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

    override fun create(audioSessionId: Int): BassBoost? {
        // Construct first, then configure inside try/catch so a throw during
        // setStrength still releases the native BassBoost handle — otherwise
        // the object never reaches `fx` and detach()/releaseFx() can't free it.
        val fx = BassBoost(0, audioSessionId)
        return try {
            fx.setStrength(strengthValue)
            fx
        } catch (e: Exception) {
            runCatching { fx.release() }
            throw e
        }
    }

    companion object {
        private const val TAG = "BassBoostHelper"
    }
}
