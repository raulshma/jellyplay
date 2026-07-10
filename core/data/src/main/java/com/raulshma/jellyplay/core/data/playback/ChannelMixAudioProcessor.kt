package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.raulshma.jellyplay.core.model.ChannelMixMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * A PCM matrix channel mixer that performs real upmix/downmix between
 * layouts using standard ITU-R BS.775 mixing coefficients.
 *
 * Unlike [BalanceAudioProcessor] and [ReplayGainAudioProcessor], this
 * processor **changes the output channel count** — [configure] returns a
 * new [AudioProcessor.AudioFormat] with the remixed channel count, and
 * [queueInput] reads `inputChannelCount` samples per frame but writes
 * `outputChannelCount` samples per frame.
 *
 * ## Channel layouts
 *
 * Media3/Android PCM channel ordering (the indices assumed by the mixing
 * matrices below):
 *
 * - **2.0** — `[L, R]`
 * - **5.1** — `[L, R, C, LFE, Ls, Rs]` (front L/R, center, LFE, surround L/R)
 * - **7.1** — `[L, R, C, LFE, Bl, Br, Sl, Sr]` (adds back L/R before side L/R)
 *
 * ## Mixing matrices
 *
 * **5.1 / 7.1 → 2.0** (ITU-R BS.775-3, LFE dropped per consumer practice):
 * ```
 * Lout = L  + 0.707·C + 0.707·Ls
 * Rout = R  + 0.707·C + 0.707·Rs
 * ```
 * For 7.1 the back and side surrounds are folded together (each at 0.5) so
 * the total surround contribution stays near 0.707 and avoids clipping.
 *
 * **Any → mono**: average of all non-LFE channels.
 *
 * **2.0 → 5.1** (surround upmix): a simple delay-free copy —
 * `C = (L+R)·0.5`, `Ls = L·0.5`, `Rs = R·0.5`, `LFE = 0`. This is a basic
 * upmix, not a phase-aware one; it is intentional and cheap.
 *
 * **AUTO / STEREO_DOWNMIX on ≤2-channel input**: passthrough (inactive).
 * **SURROUND_UPMIX on ≥6-channel input**: passthrough (inactive).
 */
@UnstableApi
class ChannelMixAudioProcessor : AudioProcessor {

    private var pendingMode: ChannelMixMode = ChannelMixMode.AUTO
    private var enabled: Boolean = false

    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var isInputEnded = false
    private var isActive = false

    /** inputCh → outputCh coefficient matrix. `[outCh][inCh]`. */
    private var matrix: Array<FloatArray> = EMPTY_MATRIX
    private var inputChannelCount: Int = 0
    private var outputChannelCount: Int = 0

    private var cachedShortBuffer: ShortBuffer? = null
    private var cachedFloatBuffer: FloatBuffer? = null

    @Synchronized
    fun setMode(mode: ChannelMixMode) {
        pendingMode = mode
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(
                "ChannelMixAudioProcessor only supports PCM 16-bit and PCM float",
                inputAudioFormat,
            )
        }
        this.inputAudioFormat = inputAudioFormat
        val inCh = inputAudioFormat.channelCount
        inputChannelCount = inCh

        val (outCh, active) = computeOutputChannels(inCh, pendingMode, enabled)
        outputChannelCount = outCh
        isActive = active

        outputAudioFormat = if (active && outCh != inCh) {
            AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, outCh, inputAudioFormat.encoding)
        } else {
            inputAudioFormat
        }
        matrix = if (active) buildMatrix(inCh, outCh, pendingMode) else EMPTY_MATRIX

        cachedShortBuffer = null
        cachedFloatBuffer = null
        return outputAudioFormat
    }

    /**
     * Pure: returns `(outputChannelCount, isActive)` for a given input
     * channel count, mode and enabled flag. Exposed for testing.
     */
    internal fun computeOutputChannels(
        inCh: Int,
        mode: ChannelMixMode,
        enabled: Boolean,
    ): Pair<Int, Boolean> {
        if (!enabled) return inCh to false
        return when (mode) {
            ChannelMixMode.MONO -> if (inCh <= 1) (inCh to false) else (1 to true)
            ChannelMixMode.STEREO_DOWNMIX -> if (inCh <= 2) (inCh to false) else (2 to true)
            ChannelMixMode.SURROUND_UPMIX -> if (inCh >= 6) (inCh to false) else (6 to true)
            ChannelMixMode.AUTO -> inCh to false
        }
    }

    /**
     * Pure: builds the `[outCh][inCh]` mixing-coefficient matrix. Exposed
     * for testing. Assumes [computeOutputChannels] already decided the
     * shapes are valid for the mode.
     */
    internal fun buildMatrix(inCh: Int, outCh: Int, mode: ChannelMixMode): Array<FloatArray> {
        return when (mode) {
            ChannelMixMode.MONO -> {
                // Average all non-LFE channels. LFE sits at index 3 in 5.1/7.1.
                val m = Array(outCh) { FloatArray(inCh) }
                val indices = (0 until inCh).filter { !isLfeChannel(it, inCh) }
                val g = 1f / indices.size
                indices.forEach { m[0][it] = g }
                m
            }
            ChannelMixMode.STEREO_DOWNMIX -> downmixToStereoMatrix(inCh)
            ChannelMixMode.SURROUND_UPMIX -> upmixToSurroundMatrix(inCh, outCh)
            ChannelMixMode.AUTO -> EMPTY_MATRIX
        }
    }

    private fun downmixToStereoMatrix(inCh: Int): Array<FloatArray> {
        val m = Array(2) { FloatArray(inCh) }
        // Indices for standard layouts.
        val l = 0; val r = 1; val c = 2; val lfe = 3
        val ls = if (inCh >= 6) 4 else -1
        val rs = if (inCh >= 6) 5 else -1
        val bl = if (inCh >= 8) 4 else -1   // 7.1 back pair
        val br = if (inCh >= 8) 5 else -1
        val sl = if (inCh >= 8) 6 else -1   // 7.1 side pair
        val sr = if (inCh >= 8) 7 else -1

        m[0][l] = 1f
        m[1][r] = 1f
        if (c < inCh) {
            m[0][c] = SQRT_HALF
            m[1][c] = SQRT_HALF
        }
        // LFE intentionally dropped.
        if (inCh == 6) {
            if (ls >= 0) m[0][ls] = SQRT_HALF
            if (rs >= 0) m[1][rs] = SQRT_HALF
        } else if (inCh == 8) {
            // Fold back + side surrounds at 0.5 each so the combined
            // surround gain stays near 0.707.
            if (bl >= 0) m[0][bl] = 0.5f
            if (sl >= 0) m[0][sl] = 0.5f
            if (br >= 0) m[1][br] = 0.5f
            if (sr >= 0) m[1][sr] = 0.5f
        } else if (inCh in 3..5) {
            // 3.0/4.0/4.1 — fold everything present on the left/right
            // side at 0.707.
            for (i in 3 until inCh) {
                if (isLfeChannel(i, inCh)) continue
                if (i % 2 == 0) m[0][i] = SQRT_HALF else m[1][i] = SQRT_HALF
            }
        }
        return m
    }

    private fun upmixToSurroundMatrix(inCh: Int, outCh: Int): Array<FloatArray> {
        // outCh is 6 (5.1) here. Source is mono or stereo.
        val m = Array(outCh) { FloatArray(inCh) }
        val l = 0
        val r = if (inCh >= 2) 1 else 0 // mono: use the single channel for both
        m[0][l] = 1f                       // FL
        m[1][r] = 1f                       // FR
        m[2][l] = 0.5f; m[2][r] = 0.5f     // FC = (L+R)·0.5
        m[3][l] = 0f                       // LFE — not synthesized
        m[4][l] = 0.5f                     // Ls = L·0.5
        m[5][r] = 0.5f                     // Rs = R·0.5
        return m
    }

    /** LFE is channel index 3 in 5.1/7.1 layouts. */
    private fun isLfeChannel(index: Int, channelCount: Int): Boolean =
        channelCount >= 6 && index == 3

    override fun isActive(): Boolean = isActive && inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) {
            outputBuffer = inputBuffer
            return
        }

        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val remaining = limit - position
        if (remaining == 0) return

        val inCh = inputChannelCount
        val outCh = outputChannelCount
        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) 2 else 4
        val inFrameBytes = inCh * bytesPerSample
        val frameCount = remaining / inFrameBytes
        val outRemaining = frameCount * outCh * bytesPerSample

        if (buffer.capacity() < outRemaining) {
            buffer = ByteBuffer.allocateDirect(outRemaining).order(ByteOrder.nativeOrder())
            cachedShortBuffer = null
            cachedFloatBuffer = null
        } else {
            buffer.clear()
            buffer.limit(outRemaining)
        }

        val mx = matrix

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val shortBuffer = cachedShortBuffer?.apply { clear() }
                ?: buffer.asShortBuffer().also { cachedShortBuffer = it }
            val inputShorts = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
            inputShorts.position(position / 2)
            inputShorts.limit(limit / 2)

            val inFrame = FloatArray(inCh)
            for (frame in 0 until frameCount) {
                for (ch in 0 until inCh) inFrame[ch] = inputShorts.get() / 32768f
                for (out in 0 until outCh) {
                    var sum = 0f
                    val coeffs = mx[out]
                    for (ch in 0 until inCh) sum += inFrame[ch] * coeffs[ch]
                    val amp = sum.coerceIn(-1f, 1f) * 32767f
                    shortBuffer.put(amp.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                }
            }
            buffer.position(0)
            buffer.limit(shortBuffer.position() * 2)
        } else {
            val floatBuffer = cachedFloatBuffer?.apply { clear() }
                ?: buffer.asFloatBuffer().also { cachedFloatBuffer = it }
            val inputFloats = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
            inputFloats.position(position / 4)
            inputFloats.limit(limit / 4)

            val inFrame = FloatArray(inCh)
            for (frame in 0 until frameCount) {
                for (ch in 0 until inCh) inFrame[ch] = inputFloats.get()
                for (out in 0 until outCh) {
                    var sum = 0f
                    val coeffs = mx[out]
                    for (ch in 0 until inCh) sum += inFrame[ch] * coeffs[ch]
                    // Float path: do NOT clamp to [-1, 1] here. A correct ITU
                    // downmix can legitimately exceed unity (e.g. a 5.1 mix
                    // with hot center + surrounds); hard-clipping would
                    // destroy the mix. Let the downstream processors
                    // (ReplayGain softClip, the audio sink) shape/clip output.
                    floatBuffer.put(sum)
                }
            }
            buffer.position(0)
            buffer.limit(floatBuffer.position() * 4)
        }

        inputBuffer.position(position + frameCount * inFrameBytes)
        outputBuffer = buffer
    }

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
    }

    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        cachedShortBuffer = null
        cachedFloatBuffer = null
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        matrix = EMPTY_MATRIX
        inputChannelCount = 0
        outputChannelCount = 0
        isActive = false
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
        private val EMPTY_MATRIX = Array(0) { FloatArray(0) }
        /** 1/√2 ≈ 0.7071, the ITU BS.775 center/surround downmix coefficient. */
        private const val SQRT_HALF = 0.70710678f
    }
}
