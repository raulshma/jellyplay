package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Virtualizer

/**
 * Virtualizer wrapper. Effect-specific state is the integer [strength]
 * (0–1000); the lifecycle skeleton lives in [AudioFxHelper].
 */
open class VirtualizerHelper(
    private val effectFactory: (Int) -> Virtualizer = ::defaultVirtualizer,
) : AudioFxHelper<Virtualizer>(TAG) {

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

    override fun create(audioSessionId: Int): Virtualizer? =
        // Construct first, then configure inside createSafely so a throw during
        // setStrength still releases the native Virtualizer handle — otherwise
        // the object never reaches `fx` and detach()/releaseFx() can't free it.
        createSafely(audioSessionId, effectFactory) {
            it.setStrength(strength.coerceIn(0, 1000).toShort())
        }

    companion object {
        fun defaultVirtualizer(audioSessionId: Int): Virtualizer = Virtualizer(0, audioSessionId)
        private const val TAG = "VirtualizerHelper"
    }
}
