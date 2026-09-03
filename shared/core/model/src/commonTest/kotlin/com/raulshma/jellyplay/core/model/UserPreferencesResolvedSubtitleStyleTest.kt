package com.raulshma.jellyplay.core.model

import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of the legacy [UserPreferences] subtitle-style
 * resolution — the shared readability logic every player target calls:
 *
 *  - Default: the user's style is returned verbatim ("respect the Override
 *    Subtitle Styles toggle").
 *  - High-contrast mode REPLACES the style with a maximally legible preset:
 *    yellow on opaque black with an outline, font size clamped into
 *    `[max(user, 24) + 4, 48]`, preserving the user's offset and vertical
 *    position.
 *  - HDR substitution applies ONLY when the stream is HDR, the HDR toggle is
 *    on, and high-contrast is OFF — with `applyCustomStyle` forced so the HDR
 *    preset actually reaches the renderer.
 *  - [UserPreferences.isHdrFromStreams] detects any HDR10/HDR10+/HLG/Dolby
 *    Vision marker across `videoRange` and `videoRangeType`, case-insensitively,
 *    and is false for null fields / empty lists.
 */
class UserPreferencesResolvedSubtitleStyleTest {

    // ── resolvedSubtitleStyle ────────────────────────────────────────────────

    @Test
    fun `default resolution returns the user's style untouched`() {
        val style = SubtitleStyle(fontSize = 18, fontColor = SubtitleColor.CYAN)
        val prefs = UserPreferences(subtitleStyle = style)
        assertEquals(style, prefs.resolvedSubtitleStyle())
        assertEquals(style, prefs.resolvedSubtitleStyle(isHdr = true))
    }

    @Test
    fun `high contrast replaces the style with the legible preset`() {
        val prefs = UserPreferences(
            subtitleStyle = SubtitleStyle(
                fontSize = 20,
                fontColor = SubtitleColor.CYAN,
                backgroundColor = SubtitleColor.WHITE,
                backgroundOpacity = 0.2f,
                offsetMs = 700L,
                verticalPosition = 0.2f,
            ),
            highContrastSubtitles = true,
        )
        val resolved = prefs.resolvedSubtitleStyle()
        assertEquals(SubtitleColor.YELLOW, resolved.fontColor)
        assertEquals(SubtitleColor.BLACK, resolved.backgroundColor)
        assertEquals(1.0f, resolved.backgroundOpacity)
        assertEquals(SubtitleEdgeType.OUTLINE, resolved.edgeType)
        assertEquals(SubtitleColor.BLACK, resolved.edgeColor)
        assertEquals(true, resolved.applyCustomStyle)
        // user modifiers preserved
        assertEquals(700L, resolved.offsetMs)
        assertEquals(0.2f, resolved.verticalPosition)
    }

    @Test
    fun `high contrast clamps a small font size up to 28`() {
        val prefs = UserPreferences(
            subtitleStyle = SubtitleStyle(fontSize = 10),
            highContrastSubtitles = true,
        )
        // max(10, 24) + 4 = 28
        assertEquals(28, prefs.resolvedSubtitleStyle().fontSize)
    }

    @Test
    fun `high contrast keeps a mid-range font size at user plus four`() {
        val prefs = UserPreferences(
            subtitleStyle = SubtitleStyle(fontSize = 30),
            highContrastSubtitles = true,
        )
        assertEquals(34, prefs.resolvedSubtitleStyle().fontSize)
    }

    @Test
    fun `high contrast caps the font size at 48`() {
        val prefs = UserPreferences(
            subtitleStyle = SubtitleStyle(fontSize = 60),
            highContrastSubtitles = true,
        )
        // max(60, 24) + 4 = 64 -> clamped to 48
        assertEquals(48, prefs.resolvedSubtitleStyle().fontSize)
    }

    @Test
    fun `hdr substitution requires both the flag and a hdr stream`() {
        val hdrStyle = SubtitleStyle(fontSize = 28, backgroundOpacity = 0.5f)
        val base = UserPreferences(
            subtitleStyle = SubtitleStyle(fontSize = 22),
            hdrSubtitleStyleEnabled = true,
            hdrSubtitleStyle = hdrStyle,
        )
        // HDR stream + enabled -> the HDR preset, forced to apply
        assertEquals(hdrStyle.copy(applyCustomStyle = true), base.resolvedSubtitleStyle(isHdr = true))
        // No HDR stream -> the user's style
        assertEquals(SubtitleStyle(fontSize = 22), base.resolvedSubtitleStyle(isHdr = false))
        // Toggle off -> the user's style even on HDR streams
        val disabled = base.copy(hdrSubtitleStyleEnabled = false)
        assertEquals(SubtitleStyle(fontSize = 22), disabled.resolvedSubtitleStyle(isHdr = true))
    }

    @Test
    fun `high contrast wins over the hdr substitution`() {
        val prefs = UserPreferences(
            subtitleStyle = SubtitleStyle(fontSize = 22),
            highContrastSubtitles = true,
            hdrSubtitleStyleEnabled = true,
        )
        val resolved = prefs.resolvedSubtitleStyle(isHdr = true)
        assertEquals(SubtitleColor.YELLOW, resolved.fontColor)
    }

    // ── isHdrFromStreams ─────────────────────────────────────────────────────

    @Test
    fun `hdr markers across videoRange and videoRangeType are detected`() {
        val prefs = UserPreferences()
        fun stream(range: String? = null, rangeType: String? = null) = MediaStream(
            index = 0,
            type = StreamType.VIDEO,
            videoRange = range,
            videoRangeType = rangeType,
        )
        assertTrue(prefs.isHdrFromStreams(listOf(stream(range = "HDR"))))
        assertTrue(prefs.isHdrFromStreams(listOf(stream(range = "hdr10"))))
        assertTrue(prefs.isHdrFromStreams(listOf(stream(rangeType = "HDR10Plus"))))
        assertTrue(prefs.isHdrFromStreams(listOf(stream(rangeType = "HLG"))))
        assertTrue(prefs.isHdrFromStreams(listOf(stream(rangeType = "DOVI"))))
        assertTrue(prefs.isHdrFromStreams(listOf(stream(range = "dolbyvision"))))
        assertTrue(prefs.isHdrFromStreams(listOf(stream(range = "SDR"), stream(rangeType = "HDR10"))))
    }

    @Test
    fun `sdr and missing fields are not hdr`() {
        val prefs = UserPreferences()
        assertFalse(prefs.isHdrFromStreams(emptyList()))
        assertFalse(
            prefs.isHdrFromStreams(
                listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "SDR", videoRangeType = "SDR")),
            ),
        )
        assertFalse(
            prefs.isHdrFromStreams(
                listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = null, videoRangeType = null)),
            ),
        )
        // An audio stream without any range fields is not HDR.
        assertFalse(
            prefs.isHdrFromStreams(
                listOf(MediaStream(index = 1, type = StreamType.AUDIO, language = "eng")),
            ),
        )
    }
}
