package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
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
 * envelope state read/written in [queueInput] is `@Volatile` where it
 * overlaps those calls. In practice [queueInput] runs single-threaded on
 * the ExoPlayer audio thread, matching the rest of the playback package.
 *
 * This is a passthrough processor: the output [AudioProcessor.AudioFormat]
 * is identical to the input (channel count and encoding preserved).
 */
@UnstableApi
class DynamicsCompressorAudioProcessor : AudioProcessor {

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

    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var isInputEnded = false

    // Derived per-sample coefficients.
    private var attackCoeff: Float = 0f
    private var releaseCoeff: Float = 0f
    private var sampleRate: Int = 0

    // Envelope state.
    @Volatile private var envelopeDb: Float = -1000f

    private var cachedShortBuffer: ShortBuffer? = null
    private var cachedFloatBuffer: FloatBuffer? = null

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    @Synchronized
    fun setParams(params: Params) {
        this.params = params
        recomputeCoefficients()
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(
                "DynamicsCompressorAudioProcessor only supports PCM 16-bit and PCM float",
                inputAudioFormat,
            )
        }
        this.inputAudioFormat = inputAudioFormat
        outputAudioFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        recomputeCoefficients()
        cachedShortBuffer = null
        cachedFloatBuffer = null
        return inputAudioFormat
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

    override fun isActive(): Boolean = enabled && inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) {
            outputBuffer = inputBuffer
            return
        }

        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position
        if (remaining == 0) return

        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            cachedShortBuffer = null
            cachedFloatBuffer = null
        } else {
            buffer.clear()
        }

        val p = params
        val thresholdDb = linearToDb(p.thresholdLinear)
        val aCoeff = attackCoeff
        val rCoeff = releaseCoeff

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val shortBuffer = cachedShortBuffer?.apply { clear() }
                ?: buffer.asShortBuffer().also { cachedShortBuffer = it }
            val inputShorts = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
            inputShorts.position(position / 2)
            inputShorts.limit(limit / 2)
            while (inputShorts.hasRemaining()) {
                val sample = inputShorts.get() / 32768f
                val out = processSample(sample, p, thresholdDb, aCoeff, rCoeff)
                val scaled = out * 32767f
                shortBuffer.put(scaled.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
            buffer.position(0)
            buffer.limit(shortBuffer.position() * 2)
        } else {
            val floatBuffer = cachedFloatBuffer?.apply { clear() }
                ?: buffer.asFloatBuffer().also { cachedFloatBuffer = it }
            val inputFloats = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
            inputFloats.position(position / 4)
            inputFloats.limit(limit / 4)
            while (inputFloats.hasRemaining()) {
                val sample = inputFloats.get()
                floatBuffer.put(processSample(sample, p, thresholdDb, aCoeff, rCoeff))
            }
            buffer.position(0)
            buffer.limit(floatBuffer.position() * 4)
        }

        inputBuffer.position(limit)
        outputBuffer = buffer
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

    override fun queueEndOfStream() {
        isInputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = isInputEnded && outputBuffer === EMPTY_BUFFER

    override fun flush() {
        isInputEnded = false
        outputBuffer = EMPTY_BUFFER
        envelopeDb = -1000f
    }

    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        cachedShortBuffer = null
        cachedFloatBuffer = null
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        attackCoeff = 0f
        releaseCoeff = 0f
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
        private const val LN10 = 2.302585f
    }
}
