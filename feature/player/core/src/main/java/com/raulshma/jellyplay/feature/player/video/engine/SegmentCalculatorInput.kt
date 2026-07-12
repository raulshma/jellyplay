package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior

/**
 * The segment-relevant slice of playback state, packaged for [SegmentCalculator].
 *
 * Holding these together (rather than passing 8 parameters to every function)
 * lets the ViewModel project the low-frequency inputs via `distinctUntilChanged`
 * and only recompute segment state when one of them changes — not on every
 * 4 Hz position tick. The high-frequency inputs (`positionMs`, `durationMs`)
 * are passed as function parameters so they can vary per tick without allocating.
 *
 * @param hasNextEpisode `true` iff a next-episode MediaItem exists. The full
 *   MediaItem is not needed — only its null/non-null matters for up-next logic.
 */
data class SegmentCalculatorInput(
    val segments: List<MediaSegment> = emptyList(),
    val chapters: List<ChapterInfo> = emptyList(),
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
    val durationMs: Long = 0L,
    val autoplayCancelled: Boolean = false,
    val isInSyncPlaySession: Boolean = false,
    val hasNextEpisode: Boolean = false,
    val seriesId: String? = null,
)
