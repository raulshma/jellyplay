package com.raulshma.jellyplay.feature.player.video.subtitle

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicLong

class OffsettingSubtitleParserFactoryTest {

    @Test
    fun offsettingSubtitleParser_zeroOffset_wrapsAndDelegatesAtParseTime() {
        // The factory always wraps (so the offset can be read
        // dynamically on each parse). At offset 0 the wrapper delegates
        // unchanged, preserving the zero-cost path at runtime.
        val offsetUs = AtomicLong(0L)
        val factory = OffsettingSubtitleParserFactory(
            FakeParserFactory(),
            offsetUs::get,
        )

        val parser = factory.create(androidx.media3.common.Format.Builder().build())
        assertTrue(parser is OffsettingSubtitleParser)
    }

    @Test
    fun offsettingSubtitleParser_nonZeroOffset_wrapsDelegate() {
        val offsetUs = AtomicLong(5_000_000L)
        val factory = OffsettingSubtitleParserFactory(
            FakeParserFactory(),
            offsetUs::get,
        )

        val parser = factory.create(androidx.media3.common.Format.Builder().build())
        assertTrue(parser is OffsettingSubtitleParser)
    }

    @Test
    fun offsettingSubtitleParser_supportsFormat_delegates() {
        val factory = OffsettingSubtitleParserFactory(
            FakeParserFactory(),
            { 0L },
        )
        val format = androidx.media3.common.Format.Builder()
            .setSampleMimeType("text/vtt")
            .build()
        assertTrue(factory.supportsFormat(format))
    }

    @Test
    fun offsettingSubtitleParser_cueReplacementBehavior_delegates() {
        val factory = OffsettingSubtitleParserFactory(
            FakeParserFactory(),
            { 0L },
        )
        val format = androidx.media3.common.Format.Builder().build()
        assertEquals(42, factory.getCueReplacementBehavior(format))
    }

    @Test
    fun offsettingSubtitleParser_adjustsTimestamps() {
        val offsetUs = AtomicLong(2_000_000L)
        val delegate = FakeParser()
        val offsetting = OffsettingSubtitleParser(delegate, offsetUs::get)

        val result = mutableListOf<androidx.media3.extractor.text.CuesWithTiming>()
        val output = androidx.media3.common.util.Consumer<androidx.media3.extractor.text.CuesWithTiming> { result.add(it) }

        offsetting.parse(
            byteArrayOf(),
            0, 0,
            androidx.media3.extractor.text.SubtitleParser.OutputOptions.allCues(),
            output,
        )

        assertEquals(1, result.size)
        assertEquals(3_000_000L, result[0].startTimeUs)
    }

    @Test
    fun offsettingSubtitleParser_offsetChangeTakesEffectOnSubsequentParse() {
        // The delay is read on each parse(), so adjusting it after the
        // parser was created shifts subsequent cues without a media reload.
        val offsetUs = AtomicLong(0L)
        val delegate = FakeParser()
        val offsetting = OffsettingSubtitleParser(delegate, offsetUs::get)

        val firstResult = mutableListOf<androidx.media3.extractor.text.CuesWithTiming>()
        offsetting.parse(
            byteArrayOf(), 0, 0,
            androidx.media3.extractor.text.SubtitleParser.OutputOptions.allCues(),
            androidx.media3.common.util.Consumer { firstResult.add(it) },
        )
        // Offset 0 → unshifted (FakeParser emits startTimeUs = 1_000_000).
        assertEquals(1_000_000L, firstResult[0].startTimeUs)

        offsetUs.set(5_000_000L)
        val secondResult = mutableListOf<androidx.media3.extractor.text.CuesWithTiming>()
        offsetting.parse(
            byteArrayOf(), 0, 0,
            androidx.media3.extractor.text.SubtitleParser.OutputOptions.allCues(),
            androidx.media3.common.util.Consumer { secondResult.add(it) },
        )
        // Offset now 5s → shifted by 5_000_000.
        assertEquals(6_000_000L, secondResult[0].startTimeUs)
    }

    private class FakeParser : androidx.media3.extractor.text.SubtitleParser {
        override fun getCueReplacementBehavior(): Int = 42
        override fun parse(
            data: ByteArray,
            offset: Int,
            length: Int,
            outputOptions: androidx.media3.extractor.text.SubtitleParser.OutputOptions,
            output: androidx.media3.common.util.Consumer<androidx.media3.extractor.text.CuesWithTiming>,
        ) {
            val cue = androidx.media3.common.text.Cue.Builder().setText("Fake").build()
            output.accept(androidx.media3.extractor.text.CuesWithTiming(listOf(cue), 1_000_000L, 2_000_000L))
        }
        override fun reset() {}
    }

    private class FakeParserFactory : androidx.media3.extractor.text.SubtitleParser.Factory {
        override fun supportsFormat(format: androidx.media3.common.Format): Boolean = true
        override fun getCueReplacementBehavior(format: androidx.media3.common.Format): Int = 42
        override fun create(format: androidx.media3.common.Format): FakeParser = FakeParser()
    }
}
