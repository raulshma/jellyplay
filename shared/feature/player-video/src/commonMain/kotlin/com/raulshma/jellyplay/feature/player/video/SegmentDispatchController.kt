package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType

/**
 * The position-aware facts one skip-button dispatch consults, snapshotted by
 * the VM from a SINGLE position-aware ui-state read: the cinema/outro/next
 * gates the ladder branches on, plus the pre-resolved [SegmentSnapshot].
 *
 * One snapshot type instead of per-fact lambdas is load-bearing: the active
 * segment and its end ticks MUST come from the same read — a fresh ui-state
 * read per fact could pair one read's segment with another read's end ticks.
 * The VM builds this in one place ([VideoPlayerViewModel]'s position-aware
 * snapshot fold); this controller only consumes it.
 */
internal data class SegmentDispatchFacts(
    val cinemaIntroActive: Boolean,
    val isOutroNearEnd: Boolean,
    val canSkipToNext: Boolean,
    val segments: SegmentSnapshot,
)

/**
 * Owns the segment-skip dispatch glue extracted from [VideoPlayerViewModel]
 * (the [SubtitlePreviewController] shape): the skip buttons
 * ([skipIntro] / [skipSegment]), the position-tick auto-skip arm
 * ([autoSkipSegment]) and the shared effect executor. The DECISION halves
 * already live in SegmentSkipPolicy.kt ([segmentSkipTarget] /
 * [segmentEndSeekTarget] / [SegmentSnapshot]) — only the dispatch moved: the
 * facts snapshot arrives through [getFacts], the per-segment end-ticks
 * resolution through [getSegmentEndTicks] (the VM resolves it against its
 * segment list), and each effect — the seek funnel, the next-episode load,
 * the session's cinema advance, the "Skipped …" notice — is a constructor
 * lambda back onto its owning collaborator.
 *
 * Never references the ui state bag (the facts are plain values), so the
 * god-count ratchet is unmoved.
 */
internal class SegmentDispatchController(
    private val getFacts: () -> SegmentDispatchFacts,
    private val getSegmentEndTicks: (MediaSegment) -> Long?,
    private val seekTo: (positionMs: Long, userInitiated: Boolean) -> Unit,
    private val playNextEpisode: () -> Unit,
    private val advanceCinemaIntro: () -> Unit,
    private val showSkippedNotice: (MediaSegmentType) -> Unit,
) {

    fun skipIntro() {
        dispatchSegmentSkip(SegmentSkipKind.INTRO)
    }

    /**
     * The overlay button press for the active segment (user-initiated — the
     * seek itself is the feedback, so no confirmation notice).
     */
    fun skipSegment(segment: MediaSegment) {
        executeSegmentSkip(segmentEndSeekTarget(getSegmentEndTicks(segment)), userInitiated = true)
    }

    /**
     * The position-tick auto-skip arm (`PlaybackProgressReporter`'s
     * `onAutoSkip`): not user-initiated (never clamped) and confirmed with
     * the "Skipped …" notice so an invisible automatic jump is explained.
     */
    fun autoSkipSegment(segment: MediaSegment) {
        executeSegmentSkip(segmentEndSeekTarget(getSegmentEndTicks(segment)), userInitiated = false)
        showSkippedNotice(segment.type)
    }

    /**
     * Shared dispatch for the skip buttons: snapshot the position-aware facts,
     * reduce them to a [SegmentSkipTarget] via the pure policy in
     * SegmentSkipPolicy.kt, then execute the one-line effect. The active
     * segment's end ticks ride the snapshot ([SegmentSnapshot.activeEndTicks]
     * — resolved against the same read the active segment came from); the
     * policy sees only plain values.
     */
    private fun dispatchSegmentSkip(kind: SegmentSkipKind) {
        val facts = getFacts()
        executeSegmentSkip(
            segmentSkipTarget(
                kind = kind,
                cinemaIntroActive = facts.cinemaIntroActive,
                isOutroNearEnd = facts.isOutroNearEnd,
                canSkipToNext = facts.canSkipToNext,
                segments = facts.segments,
            ),
            userInitiated = true,
        )
    }

    private fun executeSegmentSkip(target: SegmentSkipTarget, userInitiated: Boolean) {
        when (target) {
            is SegmentSkipTarget.SeekToPosition -> seekTo(target.positionMs, userInitiated)
            SegmentSkipTarget.SkipToNextEpisode -> playNextEpisode()
            SegmentSkipTarget.AdvanceCinemaIntro -> advanceCinemaIntro()
            SegmentSkipTarget.None -> Unit
        }
    }
}
