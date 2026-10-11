package com.raulshma.jellyplay.feature.player.video

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
    /**
     * Per-type auto-skip delay (ms) on top of a segment's start — the
     * AUTO_SKIP fire gate becomes `position >= startMs + delay`. Only read
     * for types whose [segmentBehaviors] entry is AUTO_SKIP; missing types
     * mean 0 (skip at segment start).
     */
    val segmentSkipDelaysMs: Map<MediaSegmentType, Long> = MediaSegmentType.DEFAULT_SKIP_DELAYS_MS,
    val durationMs: Long = 0L,
    val autoplayCancelled: Boolean = false,
    val isInSyncPlaySession: Boolean = false,
    val hasNextEpisode: Boolean = false,
    /**
     * The manual up-next queue holds an item (queue-first semantics — counts
     * as a next even when [hasNextEpisode] is false, e.g. a movie).
     */
    val hasQueuedNext: Boolean = false,
    val seriesId: String? = null,
) {
    /**
     * Chapter names pre-normalized (lowercase + trimmed), indexed like
     * [chapters]. Memoized per input instance so the calculator's chapter
     * fallback does not re-normalize the active chapter's name on every
     * position-tick scan — inputs are rebuilt only when a segment-relevant
     * field changes, so the one-time cost amortizes across ticks. Not a
     * constructor parameter, so `equals`/`hashCode`/`copy` stay defined by
     * the raw inputs alone.
     */
    internal val normalizedChapterNames: List<String> by lazy {
        chapters.map { it.name.lowercase().trim() }
    }
}
