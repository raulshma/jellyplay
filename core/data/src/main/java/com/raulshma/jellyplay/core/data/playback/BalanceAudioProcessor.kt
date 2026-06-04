package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

private val CHANNEL_GAINS_STEREO = floatArrayOf(1f, 1f)
private val CHANNEL_GAINS_QUAD = floatArrayOf(1f, 1f, 1f, 1f)
private val CHANNEL_GAINS_5POINT1 = floatArrayOf(1f, 1f, 0.8f, 0.8f, 0.7f, 0.7f)
private val CHANNEL_GAINS_7POINT1 = floatArrayOf(1f, 1f, 0.8f, 0.8f, 0.7f, 0.7f, 0.7f, 0.7f)

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
    private var channelGains: FloatArray = CHANNEL_GAINS_STEREO

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
    }

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
        updateGains()
        return inputAudioFormat
    }

    private fun buildChannelGains(channelCount: Int): FloatArray {
        val gains = FloatArray(channelCount) { 1f }
        when (channelCount) {
            2 -> {
                gains[0] = 1f
                gains[1] = 1f
            }
            4 -> {
                gains[0] = 1f
                gains[1] = 1f
                gains[2] = 1f
                gains[3] = 1f
            }
            6 -> {
                gains[0] = 1f; gains[1] = 1f
                gains[2] = 0.8f; gains[3] = 0.8f
                gains[4] = 0.7f; gains[5] = 0.7f
            }
            8 -> {
                gains[0] = 1f; gains[1] = 1f
                gains[2] = 0.8f; gains[3] = 0.8f
                gains[4] = 0.7f; gains[5] = 0.7f
                gains[6] = 0.7f; gains[7] = 0.7f
            }
        }
        return gains
    }

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
        } else {
            buffer.clear()
        }

        val lGain = leftGain
        val rGain = rightGain
        val channelCount = inputAudioFormat.channelCount
        val gains = channelGains

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val shortBuffer = buffer.asShortBuffer()
            val inputShorts = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
            inputShorts.position(position / 2)
            inputShorts.limit(limit / 2)
            var ch = 0
            while (inputShorts.hasRemaining()) {
                val sample = inputShorts.get()
                val balanceGain = when {
                    channelCount <= 2 -> if (ch == 0) lGain else rGain
                    ch == 0 || ch == 2 || ch == 4 || ch == 6 -> lGain
                    else -> rGain
                }
                val amplified = (sample * balanceGain * gains[ch]).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                shortBuffer.put(amplified.toShort())
                ch = (ch + 1) % channelCount
            }
            buffer.position(0)
            buffer.limit(shortBuffer.position() * 2)
        } else {
            val floatBuffer = buffer.asFloatBuffer()
            val inputFloats = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
            inputFloats.position(position / 4)
            inputFloats.limit(limit / 4)
            var ch = 0
            while (inputFloats.hasRemaining()) {
                val sample = inputFloats.get()
                val balanceGain = when {
                    channelCount <= 2 -> if (ch == 0) lGain else rGain
                    ch == 0 || ch == 2 || ch == 4 || ch == 6 -> lGain
                    else -> rGain
                }
                val amplified = (sample * balanceGain * gains[ch]).coerceIn(-1.0f, 1.0f)
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
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
    }
}
