package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser

@UnstableApi
internal class OffsettingSubtitleParser(
    private val delegate: SubtitleParser,
    private val offsetUs: Long,
) : SubtitleParser {

    override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        if (offsetUs == 0L) {
            delegate.parse(data, offset, length, outputOptions, output)
            return
        }
        val adjustedOptions = if (outputOptions.startTimeUs != C.TIME_UNSET) {
            SubtitleParser.OutputOptions.onlyCuesAfter(outputOptions.startTimeUs - offsetUs)
        } else {
            outputOptions
        }
        delegate.parse(data, offset, length, adjustedOptions) { cuesWithTiming ->
            val newStartTimeUs = if (cuesWithTiming.startTimeUs != C.TIME_UNSET) {
                cuesWithTiming.startTimeUs + offsetUs
            } else {
                C.TIME_UNSET
            }
            output.accept(CuesWithTiming(cuesWithTiming.cues, newStartTimeUs, cuesWithTiming.durationUs))
        }
    }

    override fun reset() {
        delegate.reset()
    }
}

@UnstableApi
internal class OffsettingSubtitleParserFactory(
    private val delegate: SubtitleParser.Factory,
    private val offsetUsProvider: () -> Long,
) : SubtitleParser.Factory {

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser {
        val delegateParser = delegate.create(format)
        val offsetUs = offsetUsProvider()
        if (offsetUs == 0L) return delegateParser
        return OffsettingSubtitleParser(delegateParser, offsetUs)
    }
}
