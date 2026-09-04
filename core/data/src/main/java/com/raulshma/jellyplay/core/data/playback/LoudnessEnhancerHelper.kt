package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.LoudnessEnhancer

/**
 * Loudness-enhancer wrapper. Effect-specific state is the integer [gainmB];
 * the lifecycle skeleton lives in [AudioFxHelper]. Enabling re-pushes the
 * target gain (the underlying effect can reset it across detach/attach).
 */
open class LoudnessEnhancerHelper(
    private val effectFactory: (Int) -> LoudnessEnhancer = ::defaultLoudnessEnhancer,
) : AudioFxHelper<LoudnessEnhancer>(TAG) {

    var gainmB: Int = 0
        private set

    fun setGain(gainmB: Int) {
        this.gainmB = gainmB
        if (isEnabled) {
            try {
                fx?.setTargetGain(gainmB)
            } catch (_: Exception) {}
        }
    }

    override fun create(audioSessionId: Int): LoudnessEnhancer? =
        effectFactory(audioSessionId).apply { setTargetGain(gainmB) }

    override fun applyEnabled(effect: LoudnessEnhancer, enabled: Boolean) {
        if (enabled) effect.setTargetGain(gainmB)
        effect.enabled = enabled
    }

    companion object {
        fun defaultLoudnessEnhancer(audioSessionId: Int): LoudnessEnhancer = LoudnessEnhancer(audioSessionId)
        private const val TAG = "LoudnessEnhancerHelper"
    }
}
