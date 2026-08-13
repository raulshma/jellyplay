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

/**
 * Resolves the effective subtitle-sync delay for [itemId] from [slice]: a stored
 * per-item correction wins; otherwise the slice's global "Subtitle sync offset"
 * default applies. Delay is never inherited from a previously-played item — only
 * from the explicit global default. Shared by every site that re-derives subtitle
 * state from preferences (load, engine-bind, and the prefs projector) so the
 * per-item value survives DataStore re-emissions triggered by its own write.
 */
fun resolveSubtitleDelayMs(slice: SubtitleSlice, itemId: String?): Long =
    slice.subtitleDelayByItem[itemId] ?: slice.subtitleStyle.offsetMs

/**
 * Resolves the effective subtitle style AND its per-item delay in one step:
 * [resolveSubtitleStyle] picks the high-contrast/HDR/SDR style, then its
 * [SubtitleStyle.offsetMs] is overridden by the per-item delay from
 * [resolveSubtitleDelayMs]. Shared by every site that rebuilds subtitle state
 * from preferences (engine config build, settings projector, engine-bind seed)
 * so the delay-resolution shape isn't copy-pasted across call sites.
 */
fun resolveSubtitleStyleWithDelay(
    slice: SubtitleSlice,
    itemId: String?,
    isHdr: Boolean = false,
): SubtitleStyle =
    resolveSubtitleStyle(slice, isHdr = isHdr)
        .copy(offsetMs = resolveSubtitleDelayMs(slice, itemId))
