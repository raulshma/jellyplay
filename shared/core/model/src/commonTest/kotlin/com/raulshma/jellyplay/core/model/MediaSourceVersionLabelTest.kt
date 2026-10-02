package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the version-label ladder ([MediaSource.versionLabel] /
 * [MediaSource.qualityLabel] / [mediaQualityLabel]) — the display contract
 * the detail-screen badges and both version pickers share, so the three
 * surfaces can never drift apart.
 */
class MediaSourceVersionLabelTest {

    private fun video(
        height: Int? = null,
        doVi: String? = null,
        rangeType: String? = null,
        range: String? = null,
    ) = MediaStream(
        index = 0,
        type = StreamType.VIDEO,
        height = height,
        videoDoViTitle = doVi,
        videoRangeType = rangeType,
        videoRange = range,
    )

    private fun source(
        name: String = "",
        streams: List<MediaStream> = emptyList(),
        container: String? = null,
        id: String = "source-1",
    ) = MediaSource(id = id, name = name, container = container, mediaStreams = streams)

    // ── mediaQualityLabel (stream-level) ────────────────────────────────

    @Test
    fun `quality buckets — 4K over 2160, HD from 720, SD below`() {
        assertEquals("4K SDR", mediaQualityLabel(video(height = 2160)))
        assertEquals("HD SDR", mediaQualityLabel(video(height = 1080)))
        assertEquals("HD SDR", mediaQualityLabel(video(height = 720)))
        assertEquals("SD SDR", mediaQualityLabel(video(height = 480)))
    }

    @Test
    fun `quality without a height reads Auto`() {
        assertEquals("Auto SDR", mediaQualityLabel(video()))
        assertEquals("Auto SDR", mediaQualityLabel(null))
    }

    @Test
    fun `range precedence is Dolby Vision over rangeType over range`() {
        assertEquals("4K DOLBY VISION", mediaQualityLabel(video(height = 2160, doVi = "Dolby Vision", rangeType = "HDR10")))
        assertEquals("4K HDR10PLUS", mediaQualityLabel(video(height = 2160, rangeType = "HDR10Plus")))
        assertEquals("4K HDR", mediaQualityLabel(video(height = 2160, range = "HDR")))
        // The suffix is uppercased whatever the server casing.
        assertEquals("4K HDR10", mediaQualityLabel(video(height = 2160, range = "hdr10")))
    }

    // ── MediaSource.qualityLabel / versionLabel ─────────────────────────

    @Test
    fun `qualityLabel is null without a video stream`() {
        assertNull(source().qualityLabel())
    }

    @Test
    fun `versionLabel prefers the server name`() {
        val s = source(name = "Theatrical Cut", streams = listOf(video(height = 2160)))
        assertEquals("Theatrical Cut", s.versionLabel())
    }

    @Test
    fun `versionLabel falls back to the quality label for an unnamed source`() {
        val s = source(streams = listOf(video(height = 2160, rangeType = "HDR10")))
        assertEquals("4K HDR10", s.versionLabel())
    }

    @Test
    fun `versionLabel falls back to container then id`() {
        assertEquals("mkv", source(container = "mkv").versionLabel())
        assertEquals("source-9", source(id = "source-9").versionLabel())
    }
}
