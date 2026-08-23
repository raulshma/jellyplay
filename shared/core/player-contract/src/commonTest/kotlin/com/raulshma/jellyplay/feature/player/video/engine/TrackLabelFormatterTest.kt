package com.raulshma.jellyplay.feature.player.video.engine

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class TrackLabelFormatterTest {

    private val savedLocale = Locale.getDefault()

    @BeforeTest
    fun fixLocale() {
        // displayLanguage is locale-dependent; pin to English for determinism.
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun `primary joins title, language, codec and channels with dash separator`() {
        val label = TrackLabelFormatter.primary(
            TrackLabelInfo(
                title = "Signs & Songs",
                language = "en",
                codec = "text/x-ssa",
                channels = null,
            )
        )
        assertEquals("Signs & Songs - English - text/x-ssa", label)
    }

    @Test
    fun `primary includes channel layout for audio`() {
        val label = TrackLabelFormatter.primary(
            TrackLabelInfo(language = "eng", codec = "audio/e-ac-3", channels = 6)
        )
        assertEquals("English - eac3 - 5.1", label)
    }

    @Test
    fun `primary omits null parts`() {
        assertEquals("English", TrackLabelFormatter.primary(TrackLabelInfo(language = "en")))
        assertEquals("Forced Narrative", TrackLabelFormatter.primary(TrackLabelInfo(title = "Forced Narrative")))
    }

    @Test
    fun `primary falls back to Unknown when everything blank`() {
        assertEquals("Unknown", TrackLabelFormatter.primary(TrackLabelInfo()))
        assertEquals("Unknown", TrackLabelFormatter.primary(TrackLabelInfo(title = "   ", language = "")))
    }

    @Test
    fun `media3-cues synthetic mime is dropped so server enrichment can recover the codec`() {
        // The root-cause defect: ExoPlayer surfaces application/x-media3-cues, which
        // is meaningless to users. mimeToCodec must return null so the codec part
        // is omitted entirely rather than shown raw.
        assertNull(TrackLabelFormatter.mimeToCodec("application/x-media3-cues"))
        assertNull(TrackLabelFormatter.mimeToCodec("application/x-media3-cues-text"))

        val label = TrackLabelFormatter.primary(
            TrackLabelInfo(language = "en", codec = "application/x-media3-cues")
        )
        assertEquals("English", label)
    }

    @Test
    fun `mimeToCodec maps common Media3 mimes to server-style codec strings`() {
        assertEquals("aac", TrackLabelFormatter.mimeToCodec("audio/mp4a-latm"))
        assertEquals("eac3", TrackLabelFormatter.mimeToCodec("audio/e-ac-3"))
        assertEquals("ac3", TrackLabelFormatter.mimeToCodec("audio/ac-3"))
        assertEquals("truehd", TrackLabelFormatter.mimeToCodec("audio/true-hd"))
        assertEquals("dts", TrackLabelFormatter.mimeToCodec("audio/vnd.dts"))
        assertEquals("mp3", TrackLabelFormatter.mimeToCodec("audio/mpeg"))
        assertEquals("opus", TrackLabelFormatter.mimeToCodec("audio/opus"))
        assertEquals("flac", TrackLabelFormatter.mimeToCodec("audio/flac"))
    }

    @Test
    fun `mimeToCodec preserves jellyfin-style subtitle mimes verbatim`() {
        assertEquals("text/x-ssa", TrackLabelFormatter.mimeToCodec("text/x-ssa"))
        assertEquals("text/x-ssa", TrackLabelFormatter.mimeToCodec("text/x-ass"))
        assertEquals("vtt", TrackLabelFormatter.mimeToCodec("text/vtt"))
        assertEquals("vtt", TrackLabelFormatter.mimeToCodec("application/x-webvtt"))
        assertEquals("subrip", TrackLabelFormatter.mimeToCodec("application/x-subrip"))
        assertEquals("hdmv_pgs_subtitle", TrackLabelFormatter.mimeToCodec("application/pgs"))
        assertEquals("mov_text", TrackLabelFormatter.mimeToCodec("application/tx3g"))
        assertEquals("ttml", TrackLabelFormatter.mimeToCodec("application/ttml+xml"))
    }

    @Test
    fun `mimeToCodec passes raw codec strings through and strips audio-video prefixes`() {
        assertEquals("subrip", TrackLabelFormatter.mimeToCodec("subrip"))
        assertEquals("ass", TrackLabelFormatter.mimeToCodec("ass"))
        assertEquals("hdmv_pgs_subtitle", TrackLabelFormatter.mimeToCodec("hdmv_pgs_subtitle"))
        assertEquals("hevc", TrackLabelFormatter.mimeToCodec("video/hevc"))
        assertEquals("av1", TrackLabelFormatter.mimeToCodec("video/av01"))
    }

    @Test
    fun `mimeToCodec handles null and blank`() {
        assertNull(TrackLabelFormatter.mimeToCodec(null))
        assertNull(TrackLabelFormatter.mimeToCodec(""))
        assertNull(TrackLabelFormatter.mimeToCodec("   "))
    }

    @Test
    fun `badges surfaces forced and sdh, and default only when alone`() {
        assertEquals(
            listOf(TrackBadge.FORCED, TrackBadge.SDH),
            TrackLabelFormatter.badges(
                TrackLabelInfo(isForced = true, isHearingImpaired = true, isDefault = true)
            )
        )
        assertEquals(
            listOf(TrackBadge.DEFAULT),
            TrackLabelFormatter.badges(TrackLabelInfo(isDefault = true))
        )
        assertTrue(TrackLabelFormatter.badges(TrackLabelInfo()).isEmpty())
    }

    // ─── duplication / index-prefix fixes ─────────────────────────────────────

    @Test
    fun `primary dedups title equal to language`() {
        // Common defect: container title is the bare language, engine also
        // reports a language code → previously "English - English".
        assertEquals(
            "English",
            TrackLabelFormatter.primary(TrackLabelInfo(title = "English", language = "eng"))
        )
        assertEquals(
            "Spanish",
            TrackLabelFormatter.primary(TrackLabelInfo(title = "Spanish", language = "spa"))
        )
    }

    @Test
    fun `primary strips a leading demuxer index from the title`() {
        assertEquals(
            "English",
            TrackLabelFormatter.primary(TrackLabelInfo(title = "1 - English", language = "eng"))
        )
        assertEquals(
            "Spanish",
            TrackLabelFormatter.primary(TrackLabelInfo(title = "2. Spanish", language = "spa"))
        )
    }

    @Test
    fun `primary keeps a distinct title plus language`() {
        assertEquals(
            "Signs & Songs - English",
            TrackLabelFormatter.primary(
                TrackLabelInfo(title = "Signs & Songs", language = "eng", codec = "text/x-ssa")
            ).let { it.substringBefore(" - text/x-ssa") }
        )
    }

    // ─── title-derived badges ─────────────────────────────────────────────────

    @Test
    fun `badges detected from title text when flags absent`() {
        assertEquals(
            listOf(TrackBadge.FORCED),
            TrackLabelFormatter.badges(TrackLabelInfo(title = "English (Forced)"))
        )
        assertEquals(
            listOf(TrackBadge.SDH),
            TrackLabelFormatter.badges(TrackLabelInfo(title = "English (SDH)"))
        )
        assertEquals(
            listOf(TrackBadge.SDH),
            TrackLabelFormatter.badges(TrackLabelInfo(title = "English [CC]"))
        )
    }

    @Test
    fun `badges marker does not false-positive on unrelated words`() {
        // "hi"/"cc" only count when bracketed; titles like "Chapter"/"This" must stay badge-free.
        assertTrue(TrackLabelFormatter.badges(TrackLabelInfo(title = "Chapter One")).isEmpty())
        assertTrue(TrackLabelFormatter.badges(TrackLabelInfo(title = "This is it")).isEmpty())
    }

    @Test
    fun `primary strips badge markers so they render as badges not text`() {
        val info = TrackLabelInfo(title = "English (SDH)", language = "eng")
        assertEquals("English", TrackLabelFormatter.primary(info))
        assertEquals(listOf(TrackBadge.SDH), TrackLabelFormatter.badges(info))
    }
}
