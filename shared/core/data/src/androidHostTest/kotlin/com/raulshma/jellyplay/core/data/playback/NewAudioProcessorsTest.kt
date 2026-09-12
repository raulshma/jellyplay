package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.raulshma.jellyplay.core.model.ChannelMixMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * DSP-level tests for the new AudioProcessor chain: ChannelMix,
 * DynamicsCompressor, HighPassFilter. These are the first tests in the
 * module to feed synthetic PCM frames into a processor and assert on the
 * transformed output samples — the pure-matrix and pure-coefficient
 * functions are also covered directly.
 */

private val STEREO_FLOAT = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)
private val MONO_FLOAT = AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_FLOAT)
private val SURROUND_FLOAT = AudioProcessor.AudioFormat(48_000, 6, C.ENCODING_PCM_FLOAT)

/** Build a native-order float PCM ByteBuffer from interleaved samples. */
private fun floatFrame(vararg samples: Float): ByteBuffer =
    ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(samples)
        position(0)
    }

/** Read all float samples out of a processor output buffer. */
private fun ByteBuffer.toFloats(): FloatArray {
    val dup = duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
    val out = FloatArray(dup.remaining())
    dup.get(out)
    return out
}

class ChannelMixAudioProcessorTest {

    private lateinit var processor: ChannelMixAudioProcessor

    @Before
    fun setup() {
        processor = ChannelMixAudioProcessor()
    }

    @Test
    fun `computeOutputChannels mono downmixes multichannel to 1`() {
        val (out, active) = processor.computeOutputChannels(6, ChannelMixMode.MONO, enabled = true)
        assertEquals(1, out)
        assertTrue(active)
    }

    @Test
    fun `computeOutputChannels mono on mono input is inactive`() {
        val (out, active) = processor.computeOutputChannels(1, ChannelMixMode.MONO, enabled = true)
        assertEquals(1, out)
        assertFalse(active)
    }

    @Test
    fun `computeOutputChannels stereo downmixes surround to 2`() {
        val (out, active) = processor.computeOutputChannels(6, ChannelMixMode.STEREO_DOWNMIX, enabled = true)
        assertEquals(2, out)
        assertTrue(active)
    }

    @Test
    fun `computeOutputChannels stereo on stereo input is inactive`() {
        val (out, active) = processor.computeOutputChannels(2, ChannelMixMode.STEREO_DOWNMIX, enabled = true)
        assertEquals(2, out)
        assertFalse(active)
    }

    @Test
    fun `computeOutputChannels surround upmixes stereo to 6`() {
        val (out, active) = processor.computeOutputChannels(2, ChannelMixMode.SURROUND_UPMIX, enabled = true)
        assertEquals(6, out)
        assertTrue(active)
    }

    @Test
    fun `computeOutputChannels disabled is inactive`() {
        val (out, active) = processor.computeOutputChannels(6, ChannelMixMode.MONO, enabled = false)
        assertEquals(6, out)
        assertFalse(active)
    }

    @Test
    fun `configure changes output channel count for mono downmix`() {
        processor.setMode(ChannelMixMode.MONO)
        processor.setEnabled(true)
        val out = processor.configure(STEREO_FLOAT)
        assertEquals(1, out.channelCount)
    }

    @Test
    fun `stereo to mono averages L and R`() {
        processor.setMode(ChannelMixMode.MONO)
        processor.setEnabled(true)
        processor.configure(STEREO_FLOAT)

        processor.queueInput(floatFrame(1.0f, 0.5f)) // one frame: L=1, R=0.5
        val out = processor.output.toFloats()
        assertEquals(1, out.size)
        assertEquals(0.75f, out[0], 0.001f) // (1.0 + 0.5) / 2
    }

    @Test
    fun `5_1 to stereo applies ITU BS_775 coefficients`() {
        processor.setMode(ChannelMixMode.STEREO_DOWNMIX)
        processor.setEnabled(true)
        processor.configure(SURROUND_FLOAT)

        // 5.1 layout: [L, R, C, LFE, Ls, Rs]. Feed L=1, C=1, Ls=1, rest 0.
        processor.queueInput(floatFrame(1f, 0f, 1f, 0f, 1f, 0f))
        val out = processor.output.toFloats()
        assertEquals(2, out.size)
        // Lout = L + 0.707·C + 0.707·Ls = 1 + 0.707 + 0.707
        assertEquals(1f + 0.70710678f + 0.70710678f, out[0], 0.01f)
        // Rout = R + 0.707·C + 0.707·Rs = 0 + 0.707 + 0
        assertEquals(0.70710678f, out[1], 0.01f)
    }

    @Test
    fun `LFE is dropped in 5_1 to stereo downmix`() {
        processor.setMode(ChannelMixMode.STEREO_DOWNMIX)
        processor.setEnabled(true)
        processor.configure(SURROUND_FLOAT)

        // Only LFE carries signal — it must not appear in either output.
        processor.queueInput(floatFrame(0f, 0f, 0f, 1f, 0f, 0f))
        val out = processor.output.toFloats()
        assertEquals(0f, out[0], 0.0001f)
        assertEquals(0f, out[1], 0.0001f)
    }

    @Test
    fun `inactive processor passes buffer through`() {
        // AUTO mode on stereo → inactive → output is the input buffer.
        processor.setMode(ChannelMixMode.AUTO)
        processor.setEnabled(true)
        processor.configure(STEREO_FLOAT)
        assertFalse(processor.isActive())

        val input = floatFrame(0.3f, -0.7f)
        processor.queueInput(input)
        val out = processor.output
        // For an inactive processor the output buffer is the input itself.
        assertTrue(out === input)
    }
}

class DynamicsCompressorAudioProcessorTest {

    private lateinit var processor: DynamicsCompressorAudioProcessor

    @Before
    fun setup() {
        processor = DynamicsCompressorAudioProcessor()
    }

    @Test
    fun `gainComputer returns zero below threshold`() {
        // threshold 0.05 ≈ -26 dBFS; signal at -40 dBFS is well below.
        val gr = processor.gainComputer(inputDb = -40f, thresholdDb = -26f, ratio = 3f, kneeWidthDb = 6f)
        assertEquals(0f, gr, 0.001f)
    }

    @Test
    fun `gainComputer compresses above knee by ratio`() {
        // 14 dB above threshold, ratio 3:1 → reduction = 14·(1/3 - 1) = -9.33 dB
        val gr = processor.gainComputer(inputDb = -12f, thresholdDb = -26f, ratio = 3f, kneeWidthDb = 0f)
        assertEquals(14f * (1f / 3f - 1f), gr, 0.01f)
    }

    @Test
    fun `disabled processor is not active`() {
        processor.configure(STEREO_FLOAT)
        assertFalse(processor.isActive())
    }

    @Test
    fun `enabling processor makes it active after configure`() {
        processor.configure(STEREO_FLOAT)
        processor.setEnabled(true)
        assertTrue(processor.isActive())
    }

    @Test
    fun `loud signal is attenuated over several frames`() {
        processor.configure(STEREO_FLOAT)
        processor.setParams(
            DynamicsCompressorAudioProcessor.Params(
                thresholdLinear = 0.05f,
                ratio = 4f,
                kneeWidthDb = 0f,
                attackMs = 1f,
                releaseMs = 50f,
                makeupGainDb = 0f, // no makeup so attenuation is visible
            ),
        )
        processor.setEnabled(true)

        // Feed many frames of a loud full-scale signal so the envelope settles.
        var lastPeak = 1f
        repeat(20) {
            val input = floatFrame(0.9f, 0.9f, 0.9f, 0.9f) // 2 frames
            processor.queueInput(input)
            val out = processor.output.toFloats()
            lastPeak = out.maxOrNull() ?: 0f
        }
        // After settling with ratio 4:1 and 0 makeup, a 0.9 input (~-0.9 dB)
        // is well above the -26 dBFS threshold and must come out quieter.
        assertTrue("expected attenuation, got peak $lastPeak", lastPeak < 0.85f)
    }
}

class HighPassFilterAudioProcessorTest {

    private lateinit var processor: HighPassFilterAudioProcessor

    @Before
    fun setup() {
        processor = HighPassFilterAudioProcessor()
    }

    @Test
    fun `computeAlpha approaches 1 at very low cutoff`() {
        // At extremely low fc the filter passes almost nothing below DC,
        // i.e. RC grows large → α = RC/(RC+dt) → 1.
        val alpha = processor.computeAlpha(fcHz = 1f, sampleRateHz = 48_000)
        assertTrue("expected α near 1 at low cutoff, got $alpha", alpha > 0.99f)
    }

    @Test
    fun `computeAlpha is small at high cutoff`() {
        // At high fc, RC is tiny → α → 0 (filter passes everything,
        // including DC — the highpass is effectively transparent).
        val alpha = processor.computeAlpha(fcHz = 20_000f, sampleRateHz = 48_000)
        assertTrue("expected α well below 1 at high cutoff, got $alpha", alpha < 0.5f)
    }

    @Test
    fun `disabled processor is not active`() {
        processor.configure(STEREO_FLOAT)
        assertFalse(processor.isActive())
    }

    @Test
    fun `enabled processor is active after configure`() {
        processor.configure(STEREO_FLOAT)
        processor.setEnabled(true)
        assertTrue(processor.isActive())
    }

    @Test
    fun `DC offset is attenuated by highpass`() {
        processor.configure(MONO_FLOAT)
        processor.setEnabled(true)

        // Feed a long run of constant (DC) samples. At the default 80 Hz
        // cutoff α ≈ 0.99, so DC decays as α^n and needs ~1000 samples to
        // fall well below the input level — an ideal HPF drives DC to 0.
        val dc = FloatArray(1_000) { 0.5f }
        val input = ByteBuffer.allocateDirect(dc.size * 4).order(ByteOrder.nativeOrder()).apply {
            asFloatBuffer().put(dc); position(0)
        }
        processor.queueInput(input)
        val out = processor.output.toFloats()
        assertTrue("expected DC attenuation, last=${out.last()}", out.last() < 0.05f)
    }
}
