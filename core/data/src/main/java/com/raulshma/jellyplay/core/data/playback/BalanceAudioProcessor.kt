package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.max
import kotlin.math.min

@UnstableApi
class BalanceAudioProcessor : AudioProcessor {

    private var pendingBalance: Float = 0f
    private var leftGain: Float = 1f
    private var rightGain: Float = 1f
    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var isInputEnded = false
    private var isActive = false
    private var channelGains: FloatArray = FloatArray(2) { 1f }
    private var combinedGains: FloatArray = channelGains

    private var cachedShortBuffer: ShortBuffer? = null
    private var cachedFloatBuffer: FloatBuffer? = null

    @Synchronized
    fun setBalance(balance: Float) {
        pendingBalance = balance.coerceIn(-1f, 1f)
        updateGains()
        isActive = pendingBalance != 0f
    }

    @Synchronized
    fun getBalance(): Float = pendingBalance

    private fun updateGains() {
        if (pendingBalance >= 0f) {
            leftGain = 1f - pendingBalance
            rightGain = 1f
        } else {
            leftGain = 1f
            rightGain = 1f + pendingBalance
        }
        rebuildCombinedGains()
    }

    private fun rebuildCombinedGains() {
        val channelCount = channelGains.size
        if (combinedGains.size != channelCount) {
            combinedGains = FloatArray(channelCount)
        }
        for (ch in 0 until channelCount) {
            val balanceGain = balanceGainFor(ch)
            combinedGains[ch] = balanceGain * channelGains[ch]
        }
    }

    private fun balanceGainFor(ch: Int): Float =
        if (ch == 0 || ch == 2 || ch == 4 || ch == 6) leftGain else rightGain

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(
                "BalanceAudioProcessor only supports PCM 16-bit and PCM float",
                inputAudioFormat,
            )
        }
        if (inputAudioFormat.channelCount < 2) {
            isActive = false
            this.inputAudioFormat = inputAudioFormat
            outputAudioFormat = inputAudioFormat
            return inputAudioFormat
        }
        this.inputAudioFormat = inputAudioFormat
        outputAudioFormat = inputAudioFormat
        channelGains = buildChannelGains(inputAudioFormat.channelCount)
        cachedShortBuffer = null
        cachedFloatBuffer = null
        updateGains()
        return inputAudioFormat
    }

    /**
     * Base per-channel gains. All channels start at unity: this processor
     * only applies L/R balance, not cross-channel mixing. Any downmix,
     * upmix, or surround attenuation belongs to [ChannelMixAudioProcessor].
     */
    private fun buildChannelGains(channelCount: Int): FloatArray =
        FloatArray(channelCount) { 1f }

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

        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            cachedShortBuffer = null
            cachedFloatBuffer = null
        } else {
            buffer.clear()
        }

        val channelCount = inputAudioFormat.channelCount
        val gains = combinedGains

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val shortBuffer = cachedShortBuffer?.apply { clear() }
                ?: buffer.asShortBuffer().also { cachedShortBuffer = it }
            val inputShorts = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
            inputShorts.position(position / 2)
            inputShorts.limit(limit / 2)
            var ch = 0
            while (inputShorts.hasRemaining()) {
                val sample = inputShorts.get()
                val amplified = (sample * gains[ch]).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                shortBuffer.put(amplified.toShort())
                ch = (ch + 1) % channelCount
            }
            buffer.position(0)
            buffer.limit(shortBuffer.position() * 2)
        } else {
            val floatBuffer = cachedFloatBuffer?.apply { clear() }
                ?: buffer.asFloatBuffer().also { cachedFloatBuffer = it }
            val inputFloats = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
            inputFloats.position(position / 4)
            inputFloats.limit(limit / 4)
            var ch = 0
            while (inputFloats.hasRemaining()) {
                val sample = inputFloats.get()
                val amplified = (sample * gains[ch]).coerceIn(-1.0f, 1.0f)
                floatBuffer.put(amplified)
                ch = (ch + 1) % channelCount
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
    }

    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        cachedShortBuffer = null
        cachedFloatBuffer = null
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
    }
}
