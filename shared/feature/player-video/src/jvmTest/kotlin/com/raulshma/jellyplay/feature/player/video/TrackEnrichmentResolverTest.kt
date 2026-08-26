package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests the Direct-Play server-enrichment fix: engine tracks with crude labels
 * (e.g. ExoPlayer's `English · application/x-media3-cues`) get their real
 * title/codec/badges from the matching Jellyfin [MediaStream].
 */
class TrackEnrichmentResolverTest {

    private val savedLocale = Locale.getDefault()

    @BeforeTest fun fixLocale() = Locale.setDefault(Locale.ENGLISH)
    @AfterTest fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun `empty server streams returns options unchanged`() {
        val options = listOf(TrackOption(0, "English", "eng", true))
        assertEquals(options, TrackEnrichmentResolver.enrich(options, emptyList(), StreamType.SUBTITLE))
    }

    @Test
    fun `stream index match rebuilds label and badges from server`() {
        // ExoPlayer-ish engine track with a crude label + synthetic mime.
        val options = listOf(
            TrackOption(0, "English · application/x-media3-cues", "en", false, streamIndex = 3)
        )
        val streams = listOf(
            MediaStream(
                index = 3,
                type = StreamType.SUBTITLE,
                title = "Signs & Songs",
                language = "en",
                codec = "text/x-ssa",
                isForced = true,
            )
        )
        val result = TrackEnrichmentResolver.enrich(options, streams, StreamType.SUBTITLE)
        assertEquals(1, result.size)
        assertEquals(result[0].label, "Signs & Songs - English - text/x-ssa")
        assertEquals(listOf(TrackBadge.FORCED), result[0].badges)
        // Engine identity preserved.
        assertEquals(0, result[0].index)
        assertEquals(3, result[0].streamIndex)
    }

    @Test
    fun `stream index match sets sdh badge from server isHearingImpaired flag`() {
        // The SDH badge must come from the server's explicit flag (not only from
        // the title text) so a track whose displayTitle lacks an "SDH" marker is
        // still recognised across episodes — fixing the "remember subtitle for
        // series" mismatch when conventions vary.
        val options = listOf(
            TrackOption(0, "English · application/x-media3-cues", "en", false, streamIndex = 3)
        )
        val streams = listOf(
            MediaStream(
                index = 3,
                type = StreamType.SUBTITLE,
                title = "English",
                language = "en",
                codec = "subrip",
                isHearingImpaired = true,
            )
        )
        val result = TrackEnrichmentResolver.enrich(options, streams, StreamType.SUBTITLE)
        assertEquals(listOf(TrackBadge.SDH), result[0].badges)
    }

    @Test
    fun `duplicate language tracks resolve to distinct server streams positionally`() {
        // The original screenshot defect: three identical "English" engine rows.
        val options = listOf(
            TrackOption(0, "English", "eng", false, streamIndex = null),
            TrackOption(1, "English", "eng", false, streamIndex = null),
            TrackOption(2, "English", "eng", true, streamIndex = null),
        )
        val streams = listOf(
            MediaStream(index = 10, type = StreamType.SUBTITLE, title = "Signs & Songs", language = "eng", codec = "text/x-ssa"),
            MediaStream(index = 11, type = StreamType.SUBTITLE, title = "Full Subtitles", language = "eng", codec = "text/x-ssa"),
            MediaStream(index = 12, type = StreamType.SUBTITLE, title = "Commentary", language = "eng", codec = "subrip"),
        )
        val result = TrackEnrichmentResolver.enrich(options, streams, StreamType.SUBTITLE)
        // No two should share a label after enrichment.
        assertEquals(3, result.map { it.label }.distinct().size)
        assertEquals(result[0].label, "Signs & Songs - English - text/x-ssa")
        assertEquals(result[1].label, "Full Subtitles - English - text/x-ssa")
        assertEquals(result[2].label, "Commentary - English - subrip")
        // Selection state is preserved.
        assertTrue(result[2].isSelected)
    }

    @Test
    fun `unmatched engine track passes through unchanged`() {
        val options = listOf(TrackOption(0, "Commentary", "en", false))
        val streams = listOf(
            MediaStream(index = 1, type = StreamType.SUBTITLE, title = "Signs & Songs", language = "eng")
        )
        val result = TrackEnrichmentResolver.enrich(options, streams, StreamType.SUBTITLE)
        assertEquals(result[0].label, "Commentary")
        assertTrue(result[0].badges.isEmpty())
    }

    @Test
    fun `only streams of the requested type are considered`() {
        val options = listOf(TrackOption(0, "English", "en", false, streamIndex = 5))
        val streams = listOf(
            MediaStream(index = 5, type = StreamType.AUDIO, title = "Director", language = "en")
        )
        val result = TrackEnrichmentResolver.enrich(options, streams, StreamType.SUBTITLE)
        assertEquals(result[0].label, "English")
    }

    @Test
    fun `title-label fallback matches when no stream index or language`() {
        val options = listOf(TrackOption(0, "Signs & Songs", null, false))
        val streams = listOf(
            MediaStream(index = 2, type = StreamType.SUBTITLE, title = "Signs & Songs", language = "eng", codec = "text/x-ssa")
        )
        val result = TrackEnrichmentResolver.enrich(options, streams, StreamType.SUBTITLE)
        assertEquals(result[0].label, "Signs & Songs - English - text/x-ssa")
    }
}
