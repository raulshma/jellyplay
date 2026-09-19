package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlin.math.abs
import kotlin.math.ln

/**
 * A feed-forward dynamics compressor for the "Dynamic Compression"
 * normalization mode ([com.raulshma.jellyplay.core.model.AudioNormalizationMode.DYNAMIC]).
 *
 * Implements a textbook compressor (Zölzer / Pirkle): per-sample
 * side-chain → gain computer with soft knee → peak detector with
 * smoothed attack/release → makeup gain.
 *
 * ## Defaults
 *
 * Defaults mirror the mpv `af` filter used by [MpvPlayerEngine]:
 * `acompressor=ratio=3:threshold=0.05:attack=10:release=200` — i.e.
 * threshold ≈ -26 dBFS (`0.05` linear), ratio 3:1, attack 10 ms,
 * release 200 ms. A modest 6 dB makeup gain keeps perceived loudness up.
 *
 * ## Threading
 *
 * [configure] and [setEnabled]/[setParams] are `@Synchronized`; the
 * envelope state read/written in [processFloatSample] is `@Volatile` where it
 * overlaps those calls. In practice the audio thread runs single-threaded,
 * matching the rest of the playback package.
 *
 * Buffer scaffolding lives in [BasePcmAudioProcessor]; this class is just the
 * compressor DSP. Output format is identical to input (passthrough processor).
 */
@UnstableApi
class DynamicsCompressorAudioProcessor : BasePcmAudioProcessor() {

    /** Compressor parameters. All in DSP-friendly units. */
    data class Params(
        /** Linear amplitude threshold (0..1). 0.05 ≈ -26 dBFS. */
        val thresholdLinear: Float = 0.05f,
        /** Compression ratio (N:1). 3f = 3:1. */
        val ratio: Float = 3f,
        /** Soft-knee width in dB. 0 = hard knee. */
        val kneeWidthDb: Float = 6f,
        /** Attack time constant in ms. */
        val attackMs: Float = 10f,
        /** Release time constant in ms. */
        val releaseMs: Float = 200f,
        /** Makeup gain in dB. */
        val makeupGainDb: Float = 6f,
    )

    private var params: Params = Params()
    @Volatile private var enabled: Boolean = false

    // Derived per-sample coefficients.
    private var attackCoeff: Float = 0f
    private var releaseCoeff: Float = 0f
    private var sampleRate: Int = 0
    // Cached dB threshold so the per-sample hook doesn't recompute it.
    private var thresholdDb: Float = linearToDb(params.thresholdLinear)

    // Envelope state.
    @Volatile private var envelopeDb: Float = -1000f

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    @Synchronized
    fun setParams(params: Params) {
        this.params = params
        thresholdDb = linearToDb(params.thresholdLinear)
        recomputeCoefficients()
    }

    override fun computeIsActive(): Boolean = enabled

    override fun onConfigure(format: AudioProcessor.AudioFormat) {
        sampleRate = format.sampleRate
        recomputeCoefficients()
    }

    override fun processFloatSample(sample: Float, channelIndex: Int): Float =
        processSample(sample, params, thresholdDb, attackCoeff, releaseCoeff)

    override fun onFlush() {
        // Reset envelope so a seek doesn't smear a transient with stale gain.
        envelopeDb = -1000f
    }

    override fun reset() {
        super.reset()
        sampleRate = 0
        attackCoeff = 0f
        releaseCoeff = 0f
    }

    private fun recomputeCoefficients() {
        if (sampleRate <= 0) return
        attackCoeff = timeConstantToCoeff(params.attackMs, sampleRate)
        releaseCoeff = timeConstantToCoeff(params.releaseMs, sampleRate)
    }

    /**
     * One-pole smoothing coefficient for a time constant `tMs` at
     * `sampleRate`. α = exp(-1 / (t·fs)). Exposed for testing.
     */
    internal fun timeConstantToCoeff(tMs: Float, sampleRate: Int): Float {
        val tSamples = tMs * 0.001f * sampleRate
        return kotlin.math.exp(-1f / tSamples)
    }

    /**
     * Process one sample: side-chain → gain computer (soft knee) →
     * smoothed detector → makeup. Returns the output sample in linear
     * range, soft-clipped to [-1, 1]. Exposed for testing.
     */
    internal fun processSample(
        inputLinear: Float,
        p: Params,
        thresholdDb: Float,
        aCoeff: Float,
        rCoeff: Float,
    ): Float {
        val xDb = linearToDb(abs(inputLinear))
        val target = gainComputer(xDb, thresholdDb, p.ratio, p.kneeWidthDb)

        // Smooth: fast attack when target < envelope (gain reducing),
        // slower release when target > envelope (gain recovering).
        val env = if (target < envelopeDb) {
            aCoeff * envelopeDb + (1f - aCoeff) * target
        } else {
            rCoeff * envelopeDb + (1f - rCoeff) * target
        }
        envelopeDb = env

        val gainDb = env + p.makeupGainDb
        val gainLinear = dbToLinear(gainDb)
        val out = inputLinear * gainLinear
        return out.coerceIn(-1f, 1f)
    }

    /**
     * Gain computer with soft knee. Returns the *gain reduction* in dB
     * applied to the input (a negative value). For a soft knee, the
     * reduction ramps linearly across `[threshold - knee/2,
     * threshold + knee/2]`. Exposed for testing.
     */
    internal fun gainComputer(
        inputDb: Float,
        thresholdDb: Float,
        ratio: Float,
        kneeWidthDb: Float,
    ): Float {
        val halfKnee = kneeWidthDb / 2f
        val kneeLo = thresholdDb - halfKnee
        val kneeHi = thresholdDb + halfKnee
        return when {
            inputDb <= kneeLo -> 0f
            inputDb >= kneeHi -> {
                // Above knee: full compression.
                (inputDb - thresholdDb) * (1f / ratio - 1f)
            }
            else -> {
                // Soft-knee quadratic region.
                val knee = kneeWidthDb
                val x = inputDb - kneeLo
                (1f / ratio - 1f) * (x * x) / (2f * knee)
            }
        }
    }

    /** 20·log10(x). Returns a very negative value for x ≈ 0. */
    private fun linearToDb(linear: Float): Float =
        if (linear <= 0f) -1000f else 20f * (ln(linear) / LN10)

    /** 10^(db/20). `10^x = e^(x·ln10)`. */
    private fun dbToLinear(db: Float): Float =
        kotlin.math.exp(db * LN10 / 20f)

    private companion object {
        private const val LN10 = 2.302585f
    }
}
