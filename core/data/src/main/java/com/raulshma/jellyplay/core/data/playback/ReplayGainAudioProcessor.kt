package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.FloatBuffer
import java.util.Random
import kotlin.math.pow
import kotlin.math.tanh

@UnstableApi
class ReplayGainAudioProcessor : AudioProcessor {

    private var pendingGainDb: Float = 0f
    @Volatile private var multiplier: Float = 1f
    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var isInputEnded = false
    private var isActive = false

    private var cachedShortBuffer: ShortBuffer? = null
    private var cachedFloatBuffer: FloatBuffer? = null
    private val ditherRandom = Random()
    private var previousDitherSample: Float = 0f

    @Synchronized
    fun setGainDb(gainDb: Float) {
        pendingGainDb = gainDb
        multiplier = 10f.pow(gainDb / 20f)
        isActive = gainDb != 0f
    }

    @Synchronized
    fun getGainDb(): Float = pendingGainDb

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(
                "ReplayGainAudioProcessor only supports PCM 16-bit and PCM float",
                inputAudioFormat,
            )
        }
        this.inputAudioFormat = inputAudioFormat
        outputAudioFormat = inputAudioFormat
        cachedShortBuffer = null
        cachedFloatBuffer = null
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive && inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
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

        val mult = multiplier

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val shortBuffer = cachedShortBuffer?.apply { clear() }
                ?: buffer.asShortBuffer().also { cachedShortBuffer = it }
            val inputShorts = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
            inputShorts.position(position / 2)
            inputShorts.limit(limit / 2)
            while (inputShorts.hasRemaining()) {
                val sample = inputShorts.get() / 32768f
                val amplified = sample * mult
                val clipped = softClip(amplified)
                val dithered = applyTriangularDither(clipped) * 32767f
                shortBuffer.put(dithered.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
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
                val amplified = sample * mult
                floatBuffer.put(softClip(amplified))
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

    override fun flush() {
        isInputEnded = false
        outputBuffer = EMPTY_BUFFER
        previousDitherSample = 0f
    }

    override fun reset() {
        flush()
        buffer.clear()
        cachedShortBuffer = null
        cachedFloatBuffer = null
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
    }
}
