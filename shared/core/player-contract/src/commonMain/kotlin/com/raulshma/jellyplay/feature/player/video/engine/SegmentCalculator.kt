package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior

/**
 * Pure, side-effect-free segment / chapter / up-next detection logic.
 *
 * Extracted from `VideoPlayerUiState` so it can be unit-tested without
 * constructing a 129-field state object, and so the ViewModel's
 * `SegmentProjection.computeOverlay` can compute overlays without allocating
 * a throwaway `VideoPlayerUiState` per segment-boundary change.
 *
 * All functions are pure: same [SegmentCalculatorInput] + position → same
 * output. No caching, no mutation, no allocation beyond the result (the one
 * memo in this cluster — normalized chapter names — lives on the input, once
 * per input build, not per call).
 *
 * Tick conversion: 1 ms == 10_000 ticks (Jellyfin's runTimeTicks unit).
 */
object SegmentCalculator {

    /** Up-next pre-roll window: show the "next episode" card this far before the end. */
    const val UP_NEXT_PRE_ROLL_MS = 30_000L

    /** Outro-near-end threshold: if the outro ends within this of total duration, up-next fires. */
    const val OUTRO_NEAR_END_MS = 30_000L

    private val INTRO_CHAPTER_NAMES = setOf("intro", "introduction", "opening", "op")
    private val OUTRO_CHAPTER_NAMES = setOf("outro", "credits", "end credits", "ending", "ed")
    private val PREVIEW_CHAPTER_NAMES = setOf("preview", "coming up", "cold open", "teaser")
    private val RECAP_CHAPTER_NAMES = setOf("recap", "previously on", "previously")
    private val COMMERCIAL_CHAPTER_NAMES = setOf("commercial", "ad break", "advertisement")

    val CHAPTER_NAME_MAP: Map<MediaSegmentType, Set<String>> = mapOf(
        MediaSegmentType.INTRO to INTRO_CHAPTER_NAMES,
        MediaSegmentType.OUTRO to OUTRO_CHAPTER_NAMES,
        MediaSegmentType.PREVIEW to PREVIEW_CHAPTER_NAMES,
        MediaSegmentType.RECAP to RECAP_CHAPTER_NAMES,
        MediaSegmentType.COMMERCIAL to COMMERCIAL_CHAPTER_NAMES,
    )

    fun behaviorForType(
        input: SegmentCalculatorInput,
        type: MediaSegmentType,
    ): SegmentBehavior = input.segmentBehaviors[type] ?: SegmentBehavior.IGNORE

    fun computeActiveSegment(
        input: SegmentCalculatorInput,
        positionMs: Long,
    ): MediaSegment? = computeActiveSegmentInternal(input, positionMs)

    fun isInSegmentType(
        input: SegmentCalculatorInput,
        positionMs: Long,
        type: MediaSegmentType,
    ): Boolean {
        if (behaviorForType(input, type) == SegmentBehavior.IGNORE) return false
        return isInSegmentType(input, computeActiveSegment(input, positionMs), type)
    }

    /**
     * Precomputed-segment variant: the caller supplies the active segment from
     * a single [computeActiveSegment] scan so callers that need several
     * type verdicts per position (the overlay projection) do not re-run the
     * scan per verdict.
     */
    fun isInSegmentType(
        input: SegmentCalculatorInput,
        activeSegment: MediaSegment?,
        type: MediaSegmentType,
    ): Boolean {
        if (behaviorForType(input, type) == SegmentBehavior.IGNORE) return false
        return activeSegment != null && activeSegment.type == type
    }

    fun segmentEndTicksForType(
        input: SegmentCalculatorInput,
        positionMs: Long,
        type: MediaSegmentType,
    ): Long? = segmentEndTicksForType(computeActiveSegment(input, positionMs), type)

    private fun segmentEndTicksForType(
        activeSegment: MediaSegment?,
        type: MediaSegmentType,
    ): Long? {
        val seg = activeSegment ?: return null
        if (seg.type != type) return null
        return seg.endTicks
    }

    fun segmentEndTicks(
        input: SegmentCalculatorInput,
        segment: MediaSegment,
    ): Long? {
        if (!segment.hasSegment) return null
        val apiMatch = input.segments.firstOrNull { it.id == segment.id }
        return apiMatch?.endTicks ?: segment.endTicks
    }

    fun shouldShowUpNext(
        input: SegmentCalculatorInput,
        positionMs: Long,
    ): Boolean = shouldShowUpNextInternal(input, positionMs) {
        computeActiveSegment(input, positionMs)
    }

    /**
     * Precomputed-segment variant: the caller supplies the active segment from
     * a single [computeActiveSegment] scan so the outro-near-end branch does
     * not re-run the scan.
     */
    fun shouldShowUpNext(
        input: SegmentCalculatorInput,
        positionMs: Long,
        activeSegment: MediaSegment?,
    ): Boolean = shouldShowUpNextInternal(input, positionMs) { activeSegment }

    private fun shouldShowUpNextInternal(
        input: SegmentCalculatorInput,
        positionMs: Long,
        activeSegment: () -> MediaSegment?,
    ): Boolean {
        if (input.autoplayCancelled) return false
        if (input.isInSyncPlaySession) return false
        if (!input.hasNextEpisode) return false
        if (input.seriesId == null) return false
        if (isOutroNearEnd(input, activeSegment())) return true
        if (input.durationMs > 0 && positionMs >= (input.durationMs - UP_NEXT_PRE_ROLL_MS)) return true
        return false
    }

    fun isOutroNearEnd(
        input: SegmentCalculatorInput,
        positionMs: Long,
    ): Boolean = isOutroNearEnd(input, computeActiveSegment(input, positionMs))

    private fun isOutroNearEnd(
        input: SegmentCalculatorInput,
        activeSegment: MediaSegment?,
    ): Boolean {
        val outroEnd = segmentEndTicksForType(activeSegment, MediaSegmentType.OUTRO) ?: return false
        val durationTicks = input.durationMs * 10_000
        if (durationTicks <= 0) return false
        val remainingMs = (durationTicks - outroEnd).coerceAtLeast(0) / 10_000
        return remainingMs < OUTRO_NEAR_END_MS
    }

    private fun computeActiveSegmentInternal(
        input: SegmentCalculatorInput,
        positionMs: Long,
    ): MediaSegment? {
        val posTicks = positionMs * 10_000
        fun MediaSegment.containsPos() =
            hasSegment && posTicks >= startTicks && posTicks < endTicks
        val apiMatch = MediaSegmentType.SEGMENT_PRIORITY.firstNotNullOfOrNull { priority ->
            input.segments.firstOrNull { it.type == priority && it.containsPos() }
        } ?: input.segments.firstOrNull { it.containsPos() }
        return apiMatch ?: detectChapterSegment(input, positionMs)
    }

    private fun detectChapterSegment(
        input: SegmentCalculatorInput,
        positionMs: Long,
    ): MediaSegment? {
        if (input.chapters.isEmpty()) return null
        val posTicks = positionMs * 10_000
        val idx = input.chapters.indexOfLast { it.startPositionTicks <= posTicks }
        if (idx < 0) return null
        val chapter = input.chapters[idx]
        // Pre-normalized per input build (memoized on the input), so the hot
        // per-tick path does not re-lowercase the chapter name on every scan.
        val name = input.normalizedChapterNames[idx]
        for (type in MediaSegmentType.SEGMENT_PRIORITY) {
            val names = CHAPTER_NAME_MAP[type] ?: continue
            val isMatch = names.any { keyword ->
                if (keyword.length <= 2) name == keyword || name.startsWith("$keyword ") || name.endsWith(" $keyword") || name.contains(" $keyword ")
                else name.contains(keyword)
            }
            if (isMatch) {
                val chapterEndTicks = if (idx + 1 < input.chapters.size) {
                    input.chapters[idx + 1].startPositionTicks
                } else {
                    input.durationMs * 10_000
                }
                return MediaSegment(
                    id = "chapter-${type.name}-$idx",
                    itemId = "",
                    type = type,
                    startTicks = chapter.startPositionTicks,
                    endTicks = chapterEndTicks,
                )
            }
        }
        return null
    }
}
