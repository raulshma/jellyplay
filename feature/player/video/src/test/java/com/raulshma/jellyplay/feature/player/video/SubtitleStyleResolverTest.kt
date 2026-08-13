package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStyleResolverTest {

    private fun videoStream(range: String?, rangeType: String? = null) = MediaStream(
        index = 0,
        type = StreamType.VIDEO,
        videoRange = range,
        videoRangeType = rangeType,
    )

    private fun hdrStreamWith(range: String?) = videoStream(range, "HDR10")

    @Test
    fun `isHdrFromStreams returns false for empty list`() {
        assertFalse(isHdrFromStreams(emptyList()))
    }

    @Test
    fun `isHdrFromStreams returns false for sdr streams`() {
        val streams = listOf(videoStream("SDR", "SDR"), videoStream(null, null))
        assertFalse(isHdrFromStreams(streams))
    }

    @Test
    fun `isHdrFromStreams detects HDR10`() {
        assertTrue(isHdrFromStreams(listOf(videoStream("HDR10"))))
    }

    @Test
    fun `isHdrFromStreams detects HDR10_plus`() {
        assertTrue(isHdrFromStreams(listOf(videoStream("HDR10+"))))
    }

    @Test
    fun `isHdrFromStreams detects HLG in rangeType`() {
        assertTrue(isHdrFromStreams(listOf(videoStream("PQ", "HLG"))))
    }

    @Test
    fun `isHdrFromStreams detects Dolby Vision in rangeType`() {
        assertTrue(isHdrFromStreams(listOf(videoStream("PQ", "dolbyvision"))))
        assertTrue(isHdrFromStreams(listOf(videoStream("PQ", "dovi"))))
    }

    @Test
    fun `isHdrFromStreams ignores case`() {
        assertTrue(isHdrFromStreams(listOf(videoStream("hdr10"))))
        assertTrue(isHdrFromStreams(listOf(hdrStreamWith("HDR10"))))
    }

    @Test
    fun `isHdrFromStreams ignores non-video streams`() {
        val subtitleStream = MediaStream(index = 1, type = StreamType.SUBTITLE, title = "HDR10 subs")
        assertFalse(isHdrFromStreams(listOf(subtitleStream)))
    }

    @Test
    fun `isHdrFromStreams matches anywhere in concatenated range and type`() {
        val stream = MediaStream(
            index = 0,
            type = StreamType.VIDEO,
            videoRange = "SDR",
            videoRangeType = "SMPTE ST 2086 HDR10 mastered",
        )
        assertTrue(isHdrFromStreams(listOf(stream)))
    }

    @Test
    fun `resolveSubtitleStyle returns stored style by default`() {
        val style = SubtitleStyle(fontSize = 30, fontColor = SubtitleColor.CYAN)
        val slice = SubtitleSlice(subtitleStyle = style)
        assertSame(style, resolveSubtitleStyle(slice))
    }

    @Test
    fun `resolveSubtitleStyle ignores hdr style when content is not hdr`() {
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(fontSize = 20),
            hdrSubtitleStyleEnabled = true,
            hdrSubtitleStyle = SubtitleStyle(fontSize = 32),
        )
        val resolved = resolveSubtitleStyle(slice, isHdr = false)
        assertEquals(20, resolved.fontSize)
    }

    @Test
    fun `resolveSubtitleStyle applies hdr style when enabled`() {
        val hdrStyle = SubtitleStyle(fontSize = 32, fontColor = SubtitleColor.GREEN)
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(fontSize = 20),
            hdrSubtitleStyleEnabled = true,
            hdrSubtitleStyle = hdrStyle,
        )
        val resolved = resolveSubtitleStyle(slice, isHdr = true)
        assertEquals(32, resolved.fontSize)
        assertEquals(SubtitleColor.GREEN, resolved.fontColor)
        assertTrue("hdr style must be marked as active", resolved.applyCustomStyle)
    }

    @Test
    fun `resolveSubtitleStyle does not apply hdr style when disabled`() {
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(fontSize = 20),
            hdrSubtitleStyleEnabled = false,
            hdrSubtitleStyle = SubtitleStyle(fontSize = 32),
        )
        val resolved = resolveSubtitleStyle(slice, isHdr = true)
        assertEquals(20, resolved.fontSize)
    }

    @Test
    fun `resolveSubtitleStyle applies high contrast override`() {
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(fontSize = 20, fontColor = SubtitleColor.CYAN),
            highContrastSubtitles = true,
        )
        val resolved = resolveSubtitleStyle(slice)
        assertEquals(SubtitleColor.YELLOW, resolved.fontColor)
        assertEquals(SubtitleColor.BLACK, resolved.backgroundColor)
        assertEquals(1.0f, resolved.backgroundOpacity, 0.0f)
        assertEquals(SubtitleEdgeType.OUTLINE, resolved.edgeType)
        assertEquals(SubtitleColor.BLACK, resolved.edgeColor)
        assertTrue(resolved.applyCustomStyle)
    }

    @Test
    fun `resolveSubtitleStyle high contrast bumps font size to minimum`() {
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(fontSize = 16),
            highContrastSubtitles = true,
        )
        val resolved = resolveSubtitleStyle(slice)
        assertEquals(28, resolved.fontSize)
    }

    @Test
    fun `resolveSubtitleStyle high contrast caps font size`() {
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(fontSize = 60),
            highContrastSubtitles = true,
        )
        val resolved = resolveSubtitleStyle(slice)
        assertEquals(48, resolved.fontSize)
    }

    @Test
    fun `resolveSubtitleStyle high contrast preserves offset and vertical position`() {
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(offsetMs = 2500L, verticalPosition = 0.8f),
            highContrastSubtitles = true,
        )
        val resolved = resolveSubtitleStyle(slice)
        assertEquals(2_500L, resolved.offsetMs)
        assertEquals(0.8f, resolved.verticalPosition, 0.0f)
    }

    @Test
    fun `resolveSubtitleStyle high contrast wins over hdr style`() {
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(fontSize = 20),
            highContrastSubtitles = true,
            hdrSubtitleStyleEnabled = true,
            hdrSubtitleStyle = SubtitleStyle(fontSize = 32),
        )
        val resolved = resolveSubtitleStyle(slice, isHdr = true)
        assertEquals(SubtitleColor.YELLOW, resolved.fontColor)
    }

    @Test
    fun `resolveSubtitleDelayMs prefers per-item entry over global default`() {
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(offsetMs = 250L),
            subtitleDelayByItem = mapOf("item-1" to -400L),
        )
        assertEquals(-400L, resolveSubtitleDelayMs(slice, "item-1"))
    }

    @Test
    fun `resolveSubtitleDelayMs falls back to global default when no per-item entry`() {
        val slice = SubtitleSlice(
            subtitleStyle = SubtitleStyle(offsetMs = 250L),
            subtitleDelayByItem = mapOf("item-1" to -400L),
        )
        assertEquals(250L, resolveSubtitleDelayMs(slice, "item-2"))
    }

    @Test
    fun `resolveSubtitleDelayMs null itemId uses global default`() {
        val slice = SubtitleSlice(subtitleStyle = SubtitleStyle(offsetMs = 90L))
        assertEquals(90L, resolveSubtitleDelayMs(slice, null))
    }
}