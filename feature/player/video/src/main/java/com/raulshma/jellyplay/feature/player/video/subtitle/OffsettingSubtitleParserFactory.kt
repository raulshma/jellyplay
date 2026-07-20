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
    private val offsetUsProvider: () -> Long,
) : SubtitleParser {

    override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        // Read the offset on every parse(). Previously the offset was
        // captured once at create() time, so adjusting the subtitle-delay
        // slider after ExoPlayer.prepare() had no effect on side-loaded subs
        // until the media was reloaded. Reading it here lets the live slider
        // shift subsequent cues without a reload.
        val offsetUs = offsetUsProvider()
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
        // Always wrap: a previous fast-path returned the bare delegate
        // when the offset was 0 at create() time, but the offset is now read
        // dynamically on each parse() so the delay slider can change it after
        // prepare(). The wrapper's parse() short-circuits to the delegate when
        // the offset is 0, preserving the zero-cost path at runtime.
        return OffsettingSubtitleParser(delegate.create(format), offsetUsProvider)
    }
}
