package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.tan

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
 * Passthrough processor: output [AudioProcessor.AudioFormat] == input.
 */
@UnstableApi
class HighPassFilterAudioProcessor : AudioProcessor {

    @Volatile private var enabled: Boolean = false
    private var cutoffHz: Float = DEFAULT_CUTOFF_HZ

    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var isInputEnded = false

    private var sampleRate: Int = 0
    private var alpha: Float = 0f
    private var prevState: FloatArray = FloatArray(0) // x[n-1] per channel
    private var prevOutput: FloatArray = FloatArray(0) // y[n-1] per channel
    private var channelCount: Int = 0

    private var cachedShortBuffer: ShortBuffer? = null
    private var cachedFloatBuffer: FloatBuffer? = null

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    @Synchronized
    fun setCutoffHz(hz: Float) {
        cutoffHz = hz.coerceAtLeast(10f)
        recomputeCoefficients()
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(
                "HighPassFilterAudioProcessor only supports PCM 16-bit and PCM float",
                inputAudioFormat,
            )
        }
        this.inputAudioFormat = inputAudioFormat
        outputAudioFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        prevState = FloatArray(channelCount)
        prevOutput = FloatArray(channelCount)
        recomputeCoefficients()
        cachedShortBuffer = null
        cachedFloatBuffer = null
        return inputAudioFormat
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

        val ch = channelCount
        val a = alpha

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val shortBuffer = cachedShortBuffer?.apply { clear() }
                ?: buffer.asShortBuffer().also { cachedShortBuffer = it }
            val inputShorts = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
            inputShorts.position(position / 2)
            inputShorts.limit(limit / 2)
            var c = 0
            while (inputShorts.hasRemaining()) {
                val sample = inputShorts.get() / 32768f
                val y = a * (prevOutput[c] + sample - prevState[c])
                prevState[c] = sample
                prevOutput[c] = y
                val scaled = y.coerceIn(-1f, 1f) * 32767f
                shortBuffer.put(scaled.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                c = (c + 1) % ch
            }
            buffer.position(0)
            buffer.limit(shortBuffer.position() * 2)
        } else {
            val floatBuffer = cachedFloatBuffer?.apply { clear() }
                ?: buffer.asFloatBuffer().also { cachedFloatBuffer = it }
            val inputFloats = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
            inputFloats.position(position / 4)
            inputFloats.limit(limit / 4)
            var c = 0
            while (inputFloats.hasRemaining()) {
                val sample = inputFloats.get()
                val y = a * (prevOutput[c] + sample - prevState[c])
                prevState[c] = sample
                prevOutput[c] = y
                floatBuffer.put(y.coerceIn(-1f, 1f))
                c = (c + 1) % ch
            }
            buffer.position(0)
            buffer.limit(floatBuffer.position() * 4)
        }

        inputBuffer.position(limit)
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
        // Clear filter state so a seek / rebuffer doesn't smear a transient.
        prevState.fill(0f)
        prevOutput.fill(0f)
    }

    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        cachedShortBuffer = null
        cachedFloatBuffer = null
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        channelCount = 0
        alpha = 0f
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
        /** Below the ~85 Hz fundamental of the lowest male voice. */
        const val DEFAULT_CUTOFF_HZ: Float = 80f

        @Suppress("unused")
        private const val TAG = "HighPassFilterAudioProcessor"
    }
}
