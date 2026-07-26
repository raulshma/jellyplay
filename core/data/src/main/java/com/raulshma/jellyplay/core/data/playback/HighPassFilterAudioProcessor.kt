package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlin.math.PI

/**
 * A first-order one-pole high-pass filter, used by the dialogue-boost
 * effect to cut sub-bass rumble (HVAC, traffic, plosives) below the
 * voice band and improve speech clarity before the EQ vocal boost.
 *
 * The standard analog RC high-pass transfer `H(s) = s / (s + ωc)` is
 * discretized with the bilinear transform, giving:
 *
 * ```
 * y[n] = α·(y[n-1] + x[n] - x[n-1])
 * α = RC / (RC + T)     where RC = 1/(2π·fc), T = 1/fs
 * ```
 *
 * One state per channel so interleaved multichannel PCM is filtered
 * independently. Cutoff defaults to 80 Hz (below the fundamental of the
 * lowest male voice, ~85 Hz), adjustable via [setCutoffHz].
 *
 * Buffer scaffolding lives in [BasePcmAudioProcessor]; this class is just the
 * per-channel filter state + coefficient math.
 */
@UnstableApi
class HighPassFilterAudioProcessor : BasePcmAudioProcessor() {

    @Volatile private var enabled: Boolean = false
    private var cutoffHz: Float = DEFAULT_CUTOFF_HZ

    private var sampleRate: Int = 0
    private var alpha: Float = 0f
    private var prevState: FloatArray = FloatArray(0) // x[n-1] per channel
    private var prevOutput: FloatArray = FloatArray(0) // y[n-1] per channel

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    @Synchronized
    fun setCutoffHz(hz: Float) {
        cutoffHz = hz.coerceAtLeast(10f)
        recomputeCoefficients()
    }

    override fun computeIsActive(): Boolean = enabled

    override fun onConfigure(format: AudioProcessor.AudioFormat) {
        sampleRate = format.sampleRate
        prevState = FloatArray(format.channelCount)
        prevOutput = FloatArray(format.channelCount)
        recomputeCoefficients()
    }

    override fun processFloatSample(sample: Float, channelIndex: Int): Float {
        val a = alpha
        val y = a * (prevOutput[channelIndex] + sample - prevState[channelIndex])
        prevState[channelIndex] = sample
        prevOutput[channelIndex] = y
        // Clamp filter output to valid PCM range — a transient can overshoot.
        return y.coerceIn(-1f, 1f)
    }

    override fun onFlush() {
        // Clear filter state so a seek / rebuffer doesn't smear a transient.
        prevState.fill(0f)
        prevOutput.fill(0f)
    }

    override fun reset() {
        super.reset()
        sampleRate = 0
        alpha = 0f
    }

    private fun recomputeCoefficients() {
        if (sampleRate <= 0 || cutoffHz <= 0f) return
        // Bilinear-transform one-pole HPF coefficient.
        val dt = 1f / sampleRate
        val rc = 1f / (2f * PI.toFloat() * cutoffHz)
        alpha = rc / (rc + dt)
    }

    /** Pure coefficient calc. Exposed for testing. */
    internal fun computeAlpha(fcHz: Float, sampleRateHz: Int): Float {
        val dt = 1f / sampleRateHz
        val rc = 1f / (2f * PI.toFloat() * fcHz)
        return rc / (rc + dt)
    }

    companion object {
        /** Below the ~85 Hz fundamental of the lowest male voice. */
        const val DEFAULT_CUTOFF_HZ: Float = 80f
    }
}
