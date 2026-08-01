package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Virtualizer

/**
 * Virtualizer wrapper. Effect-specific state is the integer [strength]
 * (0–1000); the lifecycle skeleton lives in [AudioFxHelper].
 */
class VirtualizerHelper : AudioFxHelper<Virtualizer>(TAG) {

    var strength: Int = 500
        private set

    fun setStrength(strength: Int) {
        this.strength = strength.coerceIn(0, 1000)
        if (isEnabled) {
            try {
                fx?.setStrength(this.strength.toShort())
            } catch (_: Exception) {}
        }
    }

    override fun create(audioSessionId: Int): Virtualizer? {
        // Construct first, then configure inside try/catch so a throw during
        // setStrength still releases the native Virtualizer handle — otherwise
        // the object never reaches `fx` and detach()/releaseFx() can't free it.
        val fx = Virtualizer(0, audioSessionId)
        return try {
            fx.setStrength(strength.coerceIn(0, 1000).toShort())
            fx
        } catch (e: Exception) {
            runCatching { fx.release() }
            throw e
        }
    }

    companion object {
        private const val TAG = "VirtualizerHelper"
    }
}
