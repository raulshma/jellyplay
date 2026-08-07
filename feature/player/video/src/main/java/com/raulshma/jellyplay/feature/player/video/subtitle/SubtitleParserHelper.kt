package com.raulshma.jellyplay.feature.player.video.subtitle

import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser

// Source-compatibility alias: TimedCue's canonical home moved to
// `:feature:player:core` (engine package) so MediaEngine can expose it as
// `currentCues`. Existing imports of `subtitle.TimedCue` keep compiling.
// (@UnstableApi is not applicable to typealiases; the underlying class is
// already annotated, and @OptIn propagates through the alias.)
typealias TimedCue = com.raulshma.jellyplay.feature.player.video.engine.TimedCue

/**
 * The active subtitle cue at a position, bracketed by its immediate neighbours.
 * Used by the subtitle-sync preview to render a previous / active / next stack.
 * Any field may be null near the start/end of the track or in a gap.
 */
@UnstableApi
data class ActiveCueContext(
    val previous: TimedCue?,
    val active: TimedCue?,
    val next: TimedCue?,
)

@UnstableApi
object SubtitleParserHelper {

    private val defaultFactory = DefaultSubtitleParserFactory()

    fun parseSubtitles(data: ByteArray, mimeType: String): List<TimedCue> {
        val format = Format.Builder()
            .setSampleMimeType(mimeType)
            .build()

        if (!defaultFactory.supportsFormat(format)) {
            return emptyList()
        }

        val parser = defaultFactory.create(format)
        val cues = mutableListOf<CuesWithTiming>()
        val collector = Consumer<CuesWithTiming> { cues.add(it) }

        parser.parse(data, 0, data.size, SubtitleParser.OutputOptions.allCues(), collector)
        parser.reset()

        return cues.flatMap { cuesWithTiming ->
            cuesWithTiming.cues.mapNotNull { cue ->
                val text = cue.text
                if (text.isNullOrBlank()) return@mapNotNull null
                TimedCue(
                    startTimeUs = cuesWithTiming.startTimeUs,
                    endTimeUs = cuesWithTiming.endTimeUs,
                    text = text,
                )
            }
        }.sortedBy { it.startTimeUs }
    }

    fun findActiveCue(cues: List<TimedCue>, positionUs: Long, offsetUs: Long): TimedCue? =
        findActiveCues(cues, positionUs, offsetUs).firstOrNull()

    fun findActiveCues(cues: List<TimedCue>, positionUs: Long, offsetUs: Long): List<TimedCue> {
        if (cues.isEmpty()) return emptyList()
        val adjustedPosition = positionUs + offsetUs
        val startIndex = findFirstActiveCandidate(cues, adjustedPosition)
        if (startIndex < 0) return emptyList()
        val result = mutableListOf<TimedCue>()
        for (i in startIndex..cues.lastIndex) {
            val cue = cues[i]
            if (cue.startTimeUs > adjustedPosition) break
            if (adjustedPosition < cue.endTimeUs) {
                result.add(cue)
            }
        }
        return result
    }

    /**
     * Resolves the active cue at [positionUs] (shifted by [offsetUs]) plus the
     * cues immediately before and after it, for the sync-preview 3-line stack.
     *
     * Gap handling: when [positionUs] (+ offset) falls between two cues there is
     * no active cue; the surrounding cues are returned as previous/next so the
     * preview still shows context. Before the first cue, previous/active = null.
     * After the last cue, active/next = null.
     */
    fun findAdjacentCues(cues: List<TimedCue>, positionUs: Long, offsetUs: Long): ActiveCueContext {
        if (cues.isEmpty()) return ActiveCueContext(null, null, null)
        val adjustedPosition = positionUs + offsetUs
        val activeList = findActiveCues(cues, positionUs, offsetUs)
        if (activeList.isNotEmpty()) {
            val active = activeList.first()
            val activeIndex = cues.indexOf(active)
            val previous = cues.getOrNull(activeIndex - 1)
            // first non-active cue strictly after the active block
            val nextIndex = activeList.last().let { cues.indexOf(it) + 1 }
            val next = cues.getOrNull(nextIndex)
            return ActiveCueContext(previous, active, next)
        }
        // In a gap: previous = last cue that ends before the position, next =
        // first cue that starts after it. Both are null-able at the track edges.
        val previousIndex = findLastCueEndingAtOrBefore(cues, adjustedPosition)
        val nextIndex = findFirstCueStartingAfter(cues, adjustedPosition)
        return ActiveCueContext(
            previous = cues.getOrNull(previousIndex),
            active = null,
            next = cues.getOrNull(nextIndex),
        )
    }

    /**
     * Index of the last cue whose end time is at or before [positionUs], or -1
     * when the position precedes every cue. Used for gap-context resolution.
     */
    private fun findLastCueEndingAtOrBefore(cues: List<TimedCue>, positionUs: Long): Int {
        var low = 0
        var high = cues.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (cues[mid].endTimeUs <= positionUs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /** Index of the first cue whose start time is strictly after [positionUs], or -1. */
    private fun findFirstCueStartingAfter(cues: List<TimedCue>, positionUs: Long): Int {
        var low = 0
        var high = cues.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (cues[mid].startTimeUs <= positionUs) {
                low = mid + 1
            } else {
                result = mid
                high = mid - 1
            }
        }
        return result
    }

    private fun findFirstActiveCandidate(cues: List<TimedCue>, positionUs: Long): Int {
        var low = 0
        var high = cues.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val cue = cues[mid]
            if (positionUs < cue.startTimeUs) {
                high = mid - 1
            } else if (positionUs >= cue.endTimeUs) {
                low = mid + 1
            } else {
                result = mid
                high = mid - 1
            }
        }
        if (result >= 0) return result
        return if (low in cues.indices && cues[low].startTimeUs <= positionUs) low else -1
    }
}
