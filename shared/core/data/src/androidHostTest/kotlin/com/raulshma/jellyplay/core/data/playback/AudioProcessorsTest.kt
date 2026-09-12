package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.EqualizerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReplayGainAudioProcessorTest {

    private lateinit var processor: ReplayGainAudioProcessor

    @Before
    fun setup() {
        processor = ReplayGainAudioProcessor()
    }

    @Test
    fun `initial state is not active`() {
        assertFalse(processor.isActive())
    }

    @Test
    fun `setGainDb makes processor active when non-zero`() {
        processor.setGainDb(6f)
        assertTrue(processor.getGainDb() == 6f)
    }

    @Test
    fun `setGainDb zero keeps processor inactive`() {
        processor.setGainDb(0f)
        assertEquals(0f, processor.getGainDb(), 0.001f)
    }

    @Test
    fun `flush resets output`() {
        processor.flush()
        val output = processor.output
        assertEquals(0, output.remaining())
    }

    @Test
    fun `reset clears state`() {
        processor.setGainDb(10f)
        processor.reset()
        assertFalse(processor.isActive())
    }

    @Test
    fun `isEnded returns true after queueEndOfStream when output is empty`() {
        processor.queueEndOfStream()
        assertTrue(processor.isEnded())
    }

    @Test
    fun `getOutput returns empty buffer initially`() {
        val output = processor.output
        assertEquals(0, output.remaining())
    }
}

class BalanceAudioProcessorTest {

    private lateinit var processor: BalanceAudioProcessor

    @Before
    fun setup() {
        processor = BalanceAudioProcessor()
    }

    @Test
    fun `5_1 surround channels keep unity at centered balance`() {
        // Regression guard: previously buildChannelGains permanently
        // attenuated surround/LFE channels (0.8/0.7) whenever the
        // processor was configured for 5.1, even at centered balance.
        // Surround attenuation now belongs to ChannelMixAudioProcessor;
        // balance must leave every channel at unity when balance == 0.
        val surround = androidx.media3.common.audio.AudioProcessor.AudioFormat(
            48_000, 6, androidx.media3.common.C.ENCODING_PCM_FLOAT,
        )
        processor.configure(surround)
        processor.setBalance(0f)
        // Centered balance → processor inactive → passthrough.
        assertFalse(processor.isActive())
    }

    @Test
    fun `initial balance is zero`() {
        assertEquals(0f, processor.getBalance(), 0.001f)
    }

    @Test
    fun `setBalance clamps to valid range`() {
        processor.setBalance(2f)
        assertEquals(1f, processor.getBalance(), 0.001f)

        processor.setBalance(-2f)
        assertEquals(-1f, processor.getBalance(), 0.001f)
    }

    @Test
    fun `setBalance makes processor active when non-zero`() {
        processor.setBalance(0.5f)
        assertEquals(0.5f, processor.getBalance(), 0.001f)
    }

    @Test
    fun `setBalance zero makes processor inactive`() {
        processor.setBalance(0.5f)
        processor.setBalance(0f)
        assertFalse(processor.isActive())
    }

    @Test
    fun `reset clears state`() {
        processor.setBalance(0.8f)
        processor.reset()
        assertFalse(processor.isActive())
    }

    @Test
    fun `flush resets output`() {
        processor.flush()
        val output = processor.output
        assertEquals(0, output.remaining())
    }
}
