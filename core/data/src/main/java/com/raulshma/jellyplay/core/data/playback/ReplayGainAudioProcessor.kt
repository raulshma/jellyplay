package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.util.UnstableApi
import java.util.Random
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Applies a ReplayGain dB offset to PCM, with `tanh` soft-clip on overflow
 * and triangular dither on the 16-bit path. Scaffolding (buffer management,
 * format handling, 16-bit/float dispatch) lives in [BasePcmAudioProcessor];
 * this class is just the per-sample gain + the dithering quantizer.
 */
@UnstableApi
class ReplayGainAudioProcessor : BasePcmAudioProcessor() {

    private var pendingGainDb: Float = 0f
    @Volatile private var multiplier: Float = 1f

    private val ditherRandom = Random()
    private var previousDitherSample: Float = 0f

    @Synchronized
    fun setGainDb(gainDb: Float) {
        pendingGainDb = gainDb
        multiplier = 10f.pow(gainDb / 20f)
    }

    @Synchronized
    fun getGainDb(): Float = pendingGainDb

    override fun computeIsActive(): Boolean = pendingGainDb != 0f

    override fun processFloatSample(sample: Float, channelIndex: Int): Float {
        val amplified = sample * multiplier
        // Float path: soft-clip on overflow (no clamp — tanh curve preserves
        // waveshape near the limit instead of hard-cutting).
        return softClip(amplified)
    }

    override fun denormalize(sample: Float): Short {
        // 16-bit path: soft-clip then add triangular dither before quantization.
        val dithered = applyTriangularDither(sample) * 32767f
        return dithered.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    override fun onFlush() {
        previousDitherSample = 0f
    }

    private fun softClip(sample: Float): Float {
        if (sample >= -1f && sample <= 1f) return sample
        return tanh(sample.toDouble()).toFloat()
    }

    private fun applyTriangularDither(sample: Float): Float {
        val r1 = ditherRandom.nextFloat() - 0.5f
        val r2 = ditherRandom.nextFloat() - 0.5f
        val dither = r1 - r2
        val dithered = sample + dither * (1f / 32768f)
        previousDitherSample = dithered - sample
        return dithered
    }
}
