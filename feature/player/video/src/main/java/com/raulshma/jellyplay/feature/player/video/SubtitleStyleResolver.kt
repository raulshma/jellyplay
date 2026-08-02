package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle

/** True when any stream is HDR (HDR10/HDR10+/HLG/Dolby Vision). */
fun isHdrFromStreams(streams: List<MediaStream>): Boolean =
    streams.any { stream ->
        val range = stream.videoRange?.lowercase()
        val rangeType = stream.videoRangeType?.lowercase()
        val raw = (range ?: "") + " " + (rangeType ?: "")
        raw.contains("hdr") || raw.contains("hlg") || raw.contains("dovi") || raw.contains("dolbyvision")
    }

/** Resolves the effective subtitle style: high-contrast override, HDR style, or the user's SDR style. */
fun resolveSubtitleStyle(slice: SubtitleSlice, isHdr: Boolean = false): SubtitleStyle = when {
    slice.highContrastSubtitles -> SubtitleStyle(
        applyCustomStyle = true,
        fontSize = (slice.subtitleStyle.fontSize.coerceAtLeast(24) + 4).coerceAtMost(48),
        fontColor = SubtitleColor.YELLOW,
        backgroundColor = SubtitleColor.BLACK,
        backgroundOpacity = 1.0f,
        edgeType = SubtitleEdgeType.OUTLINE,
        edgeColor = SubtitleColor.BLACK,
        offsetMs = slice.subtitleStyle.offsetMs,
        verticalPosition = slice.subtitleStyle.verticalPosition,
    )
    isHdr && slice.hdrSubtitleStyleEnabled -> slice.hdrSubtitleStyle.copy(applyCustomStyle = true)
    else -> slice.subtitleStyle
}
