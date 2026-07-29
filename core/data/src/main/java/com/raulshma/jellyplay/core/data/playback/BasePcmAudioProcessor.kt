package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Deep module: the Media3 [AudioProcessor] scaffolding shared by every
 * same-shape PCM effect processor (ReplayGain, HighPassFilter,
 * DynamicsCompressor, Balance). Extracted because the four subclasses each
 * carried a verbatim copy of the same buffer-management + format-handling +
 * 16-bit/float-dispatch boilerplate (~80 LOC each), with the actual DSP
 * already factored into `internal` testable functions.
 *
 * **What the base owns** (the duplicated scaffolding):
 *   - `EMPTY_BUFFER` companion, `inputAudioFormat` / `outputAudioFormat` state
 *   - `configure()` — rejects non-PCM-16bit/float via [AudioProcessor.UnhandledAudioFormatException]
 *   - `queueInput()` — allocates/grows a direct native-order buffer, caches
 *     ShortBuffer/FloatBuffer views, dispatches 16-bit (normalize → process →
 *     denormalize) and float paths, advances the input position
 *   - `queueEndOfStream()` / `getOutput()` / `isEnded()` / `flush()` / `reset()`
 *
 * **What subclasses provide** (the DSP that was already isolated):
 *   - [processFloatSample] — the per-sample transform, in/out normalized [-1,1]
 *   - [computeIsActive] — the "is this processor modifying audio?" predicate
 *     (e.g. ReplayGain: `gainDb != 0f`; HPF: `enabled`; Balance: `balance != 0f`)
 *   - [onConfigure] — extra setup when the format arrives (sample rate capture,
 *     coefficient recompute, channel-state allocation)
 *   - [onFlush] — clear DSP state on seek/rebuffer
 *   - [denormalize] — override for custom 16-bit quantization (ReplayGain dithers)
 *
 * **Passthrough when inactive.** When [computeIsActive] returns false,
 * `queueInput` copies the input buffer straight to the output without calling
 * [processFloatSample] — matching the pre-extraction `if (!isActive) { outputBuffer
 * = inputBuffer; return }` early-return in HPF/Dynamics/Balance. Subclasses
 * whose active state is set in `configure` (Balance) must update their active
 * flag from [onConfigure].
 *
 * **Output format == input format.** This base is for same-shape transforms:
 * the output PCM has the same sample rate, channel count, and encoding as the
 * input. [ChannelMixAudioProcessor] is deliberately *not* a subclass — it
 * changes the output channel count (5.1→2.0 downmix, etc.), which makes its
 * `queueInput` structurally different (output buffer holds a different number
 * of samples than input). Folding it in would distort both the base and the
 * subclass. It stays standalone.
 *
 * **Depth.** Interface: one abstract method + four open hooks. Implementation:
 * ~120 LOC of buffer plumbing that was previously ~320 LOC duplicated across
 * four files. The DSP variation points stay in the subclasses where they
 * belong; the scaffolding has one home and one test.
 */
@UnstableApi
abstract class BasePcmAudioProcessor : AudioProcessor {

    protected var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
        private set
    protected var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
        private set

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var isInputEnded = false

    private var cachedShortBuffer: ShortBuffer? = null
    private var cachedFloatBuffer: FloatBuffer? = null

    // ---- hooks -----------------------------------------------------------

    /**
     * Per-sample DSP, in/out normalized float [-1, 1]. Called for every PCM
     * sample on both the 16-bit (post-normalize) and float paths. The base
     * handles normalize/denormalize and channel-index cycling.
     *
     * [channelIndex] is the 0-based channel of this sample within the
     * interleaved frame, modulo [AudioProcessor.AudioFormat.channelCount] —
     * stateful processors (HPF, DynamicsCompressor) use it to keep per-channel
     * state.
     */
    protected abstract fun processFloatSample(sample: Float, channelIndex: Int): Float

    /**
     * Whether this processor is currently modifying audio. Polled by
     * [isActive] and by the passthrough early-return in [queueInput]. Must be
     * kept current by the subclass (typically updated in its public setters
     * and/or [onConfigure]).
     */
    protected abstract fun computeIsActive(): Boolean

    /**
     * Extra setup when the format is configured. Default does nothing;
     * subclasses override to capture sample rate, allocate channel state,
     * recompute coefficients, etc. Runs after [inputAudioFormat] is set.
     */
    protected open fun onConfigure(format: AudioProcessor.AudioFormat) {}

    /**
     * Clear DSP state on flush (seek / rebuffer). Default does nothing;
     * subclasses override to clear filter envelope, dither history, etc.
     * The base already resets `isInputEnded` and `outputBuffer`.
     */
    protected open fun onFlush() {}

    /**
     * Quantize a normalized float sample back to a 16-bit PCM sample. Default
     * rounds and clamps; subclasses override to add noise shaping (ReplayGain
     * applies triangular dither).
     */
    protected open fun denormalize(sample: Float): Short =
        (sample * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    // ---- AudioProcessor implementation ----------------------------------

    final override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(
                "${this::class.simpleName} only supports PCM 16-bit and PCM float",
                inputAudioFormat,
            )
        }
        this.inputAudioFormat = inputAudioFormat
        outputAudioFormat = inputAudioFormat
        cachedShortBuffer = null
        cachedFloatBuffer = null
        onConfigure(inputAudioFormat)
        return inputAudioFormat
    }

    override fun isActive(): Boolean =
        computeIsActive() && inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Passthrough when inactive: copy input straight to output, skipping
        // the per-sample hook entirely. Matches the pre-extraction early-return.
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

        val channelCount = inputAudioFormat.channelCount

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val shortBuffer = cachedShortBuffer?.apply { clear() }
                ?: buffer.asShortBuffer().also { cachedShortBuffer = it }
            val inputShorts = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
            inputShorts.position(position / 2)
            inputShorts.limit(limit / 2)
            var ch = 0
            while (inputShorts.hasRemaining()) {
                val sample = inputShorts.get() / 32768f
                val processed = processFloatSample(sample, ch)
                shortBuffer.put(denormalize(processed))
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
                // Subclass is responsible for any output shaping (clamp, softClip)
                // inside its hook — ReplayGain softClips, HPF clamps, etc.
                floatBuffer.put(processFloatSample(inputFloats.get(), ch))
                ch = (ch + 1) % channelCount
            }
            buffer.position(0)
            buffer.limit(floatBuffer.position() * 4)
        }

        inputBuffer.position(limit)
        outputBuffer = buffer
    }

    final override fun queueEndOfStream() {
        isInputEnded = true
    }

    final override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    final override fun isEnded(): Boolean = isInputEnded && outputBuffer === EMPTY_BUFFER

    @Suppress("DEPRECATION") // AudioProcessor.flush() deprecated in newer Media3; still the interface contract.
    override fun flush() {
        isInputEnded = false
        outputBuffer = EMPTY_BUFFER
        onFlush()
    }

    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        cachedShortBuffer = null
        cachedFloatBuffer = null
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    private companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
    }
}
