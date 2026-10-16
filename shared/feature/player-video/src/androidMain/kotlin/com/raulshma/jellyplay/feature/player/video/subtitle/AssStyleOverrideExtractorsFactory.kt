package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.extractor.AssMatroskaExtractor

/**
 * ass-media's `withAssMkvSupport` extension swaps `MatroskaExtractor` for
 * [AssMatroskaExtractor] but requires the CONCRETE AssSubtitleParserFactory,
 * which forced the old wiring to bypass the FORCE-mode header-rewrite wrapper.
 * [AssMatroskaExtractor] itself accepts the SubtitleParser.Factory interface,
 * so this local twin hands it the wrapper instead — embedded ASS-in-MKV gets
 * the user-style header rewrite at track creation, same as side-car .ass.
 * Extraction order is preserved (only Matroska extractors are replaced), and
 * the offsetting wrapper is intentionally NOT part of the chain here: embedded
 * MKV ASS still bypasses the subtitle-delay slider (pre-existing limitation,
 * unchanged by the rewrite work).
 */
@OptIn(UnstableApi::class)
internal class AssStyleOverrideExtractorsFactory(
    private val base: ExtractorsFactory,
    private val assParserFactory: androidx.media3.extractor.text.SubtitleParser.Factory,
    private val assHandler: AssHandler,
) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> {
        val extractors = base.createExtractors()
        extractors.forEachIndexed { index, extractor ->
            if (extractor is MatroskaExtractor) {
                extractors[index] = AssMatroskaExtractor(assParserFactory, assHandler)
            }
        }
        return extractors
    }
}
