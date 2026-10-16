package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import com.raulshma.jellyplay.core.model.SubtitleStyle

/**
 * Sits in front of the ass-media [AssSubtitleParserFactory] (its delegate) and
 * rewrites the ASS/SSA header text with the user's [SubtitleStyle] when the
 * style requests FORCE — the ExoPlayer stand-in for mpv's
 * `--ass-override=force`, because ass-media 0.5.0 binds no style-override JNI
 * and the header text is the only non-fork seam.
 *
 * Both header transport paths are covered:
 *  - Embedded Matroska ASS: the raw `[V4+ Styles]` header rides
 *    `Format.initializationData[1]`; the rewrite happens here, before the
 *    delegate's `createTrack` feeds it to libass. (The format is rebuilt via
 *    `buildUpon` so id/mime/container fields — which ass-media and the track
 *    registry key on — are preserved byte-for-byte.)
 *  - Side-car `.ass`: no init data exists; the header travels inside the body
 *    handed to [SubtitleParser.parse], so the parser is wrapped and the body
 *    text is rewritten before delegation. MKV dialogue chunks (which carry no
 *    styles section) pass through [AssStyleHeaderRewriter] unchanged.
 *
 * Reads the style lazily at [create] time so a style edit made before the next
 * subtitle-track load is honored. This is inherently apply-at-load: the parser
 * (and the libass track built from the header) exists per media period, and
 * ass-media's parsers hold their track reference privately, so a mid-playback
 * restyle cannot swap the track without forking the library.
 */
@UnstableApi
internal class AssStyleOverrideParserFactory(
    private val delegate: SubtitleParser.Factory,
    private val styleProvider: () -> SubtitleStyle,
) : SubtitleParser.Factory {

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser {
        val style = styleProvider()
        if (format.sampleMimeType != MimeTypes.TEXT_SSA ||
            !AssStyleHeaderRewriter.isForceOverride(style)
        ) {
            return delegate.create(format)
        }
        val initData = format.initializationData
        if (initData.size >= 2) {
            // Embedded header path — index 1 is the CodecPrivate header text
            // (index 0 is Media3's own "Format:" events line, left untouched).
            val original = initData[1].decodeToString()
            val rewritten = AssStyleHeaderRewriter.rewrite(original, style)
            val effectiveFormat = if (rewritten != original) {
                format.buildUpon()
                    // Entries beyond index 1 are none of ours — carry them
                    // through untouched.
                    .setInitializationData(
                        listOf(initData[0], rewritten.encodeToByteArray()) + initData.drop(2),
                    )
                    .build()
            } else {
                format
            }
            // Embedded parsers receive per-dialogue chunks (no header), so no
            // parser wrapping is wanted here.
            return delegate.create(effectiveFormat)
        }
        // Side-car path — rewrite the full file body on parse.
        return BodyRewritingSubtitleParser(delegate.create(format), style)
    }

    /** Rewrites the parsed ASS body (header + events for side-car files). */
    private class BodyRewritingSubtitleParser(
        private val delegate: SubtitleParser,
        private val style: SubtitleStyle,
    ) : SubtitleParser {

        override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior

        override fun reset() {
            delegate.reset()
        }

        override fun parse(
            data: ByteArray,
            offset: Int,
            length: Int,
            outputOptions: SubtitleParser.OutputOptions,
            output: Consumer<CuesWithTiming>,
        ) {
            val rewritten = AssStyleHeaderRewriter.rewrite(
                data.decodeToString(offset, offset + length),
                style,
            ).encodeToByteArray()
            delegate.parse(rewritten, 0, rewritten.size, outputOptions, output)
        }
    }
}
