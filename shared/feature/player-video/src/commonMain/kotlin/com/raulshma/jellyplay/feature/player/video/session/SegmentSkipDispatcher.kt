package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.ui.viewmodel.StateFlowHandle
import com.raulshma.jellyplay.feature.player.video.SegmentCalculator
import com.raulshma.jellyplay.feature.player.video.SegmentDispatchFacts
import com.raulshma.jellyplay.feature.player.video.SegmentProjection
import com.raulshma.jellyplay.feature.player.video.SegmentSkipKind
import com.raulshma.jellyplay.feature.player.video.SegmentSkipTarget
import com.raulshma.jellyplay.feature.player.video.SegmentSnapshot
import com.raulshma.jellyplay.feature.player.video.SkippedSegmentNotice
import com.raulshma.jellyplay.feature.player.video.VideoPlayerUiState
import com.raulshma.jellyplay.feature.player.video.segmentEndSeekTarget
import com.raulshma.jellyplay.feature.player.video.segmentSkipTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the "Skipped …" segment-skip confirmation caption stays up. */
private const val SKIPPED_SEGMENT_NOTICE_MS = 2_500L


/**
 * The segment-skip family's module — the composition half the pure policy
 * ([com.raulshma.jellyplay.feature.player.video.segmentSkipTarget] in
 * SegmentSkipPolicy.kt) deliberately leaves out. Four arms:
 *
 *  - [skipIntro] — the "Skip Intro" button / menu press (the VM's event
 *    funnel forwards through the session's one-line delegate);
 *  - [skipSegment] — the overlay button press for the active segment
 *    (user-initiated — the seek itself is the feedback, so no notice);
 *  - [autoSkip] — the position-tick auto-skip arm
 *    ([com.raulshma.jellyplay.feature.player.video.PlaybackProgressReporter]'s
 *    `onAutoSkip`): not user-initiated (never clamped) and confirmed with the
 *    "Skipped …" notice so an invisible automatic jump is explained;
 *  - [onClampedSeek] — the transport clamp's notice-only arm: the router
 *    already adjusted the seek target, the dispatcher only raises the notice.
 *
 * Everything the arms share lives here: the ONE position-aware snapshot fold
 * behind the button dispatch ([SegmentDispatchFacts]' pairing rule — every
 * fact read from a single snapshot that pairs the live position/duration from
 * the dedicated high-frequency flows with the low-frequency uiState slices,
 * so the active segment and its end ticks can never come from different
 * reads), the policy dispatch, the effect execution and the notice lifecycle
 * (the seq counter + the auto-clear job — a notice replacing a live one
 * restarts the window, and an older auto-clear never clobbers a newer
 * notice). The module is shaped like its wiring siblings: the session
 * constructs it with narrow lambdas ([SessionPositionLedger] precedent), the
 * effects stay session/collaborator-owned, and the JVM tests drive it through
 * this interface with recording doubles instead of through the whole session
 * graph.
 *
 * @param scope the notice auto-clear job's launch scope (the session's scope).
 * @param uiState the notice caption's write seam (the session's uiState handle).
 * @param positionMs the live position read (the dedicated high-frequency flow).
 * @param durationMs the live duration read (the dedicated high-frequency flow).
 * @param canSkipToNext the autoplay gate, evaluated against the snapshot's
 *   next episode (the `AutoPlayController.canSkipToNext` verdict) — a
 *   parameter, not a lambda over the live state, so every fact the ladder
 *   consults comes from the ONE snapshot read.
 * @param seekLocal the local seek effect (the transport's local seek arm — the
 *   segment-skip dispatch never rides the SyncPlay/cast ladder: the group and
 *   the receiver own their timelines).
 * @param playNextManually the next-episode effect (the Up Next controller's
 *   manual advance).
 * @param advanceCinemaIntro the cinema escape effect (the session's pre-roll
 *   advance; the field is session-owned).
 */
internal class SegmentSkipDispatcher(
    private val scope: CoroutineScope,
    private val uiState: StateFlowHandle<VideoPlayerUiState>,
    private val positionMs: () -> Long,
    private val durationMs: () -> Long,
    private val canSkipToNext: (nextEpisode: MediaItem?) -> Boolean,
    private val seekLocal: (positionMs: Long, userInitiated: Boolean) -> Unit,
    private val playNextManually: () -> Unit,
    private val advanceCinemaIntro: () -> Unit,
) {
    /** Bumped per notice so same-type consecutive skips re-trigger the UI. */
    private var noticeSeq: Long = 0

    /** The auto-clear job backing [showSkippedSegmentNotice]'s window. */
    private var noticeJob: Job? = null

    /** The "Skip Intro" button / menu arm (the VM's event funnel forwards). */
    internal fun skipIntro() {
        dispatch(SegmentSkipKind.INTRO)
    }

    /**
     * The overlay button press for the active segment (user-initiated — the
     * seek itself is the feedback, so no confirmation notice).
     */
    internal fun skipSegment(segment: MediaSegment) {
        execute(segmentEndSeekTarget(uiState.value.segmentEndTicks(segment)), userInitiated = true)
    }

    /**
     * The position-tick auto-skip arm: not user-initiated (never clamped) and
     * confirmed with the "Skipped …" notice so an invisible automatic jump is
     * explained.
     */
    internal fun autoSkip(segment: MediaSegment) {
        execute(segmentEndSeekTarget(uiState.value.segmentEndTicks(segment)), userInitiated = false)
        showSkippedSegmentNotice(segment.type)
    }

    /**
     * The transport clamp's notice-only arm: the router resolved the seek
     * across a segment under the `skipSegmentsOnSeek` pref and already
     * adjusted its target — the dispatcher raises the SAME notice the
     * auto-skip arm raises, so the notice keeps ONE home.
     */
    internal fun onClampedSeek(segmentType: MediaSegmentType) {
        showSkippedSegmentNotice(segmentType)
    }

    /**
     * Shared dispatch for the skip buttons: snapshot the position-aware facts,
     * reduce them to a [SegmentSkipTarget] via the pure policy, then execute
     * the one-line effect. The active segment's end ticks ride the snapshot
     * ([SegmentSnapshot.activeEndTicks] — resolved against the same read the
     * active segment came from); the policy sees only plain values.
     */
    private fun dispatch(kind: SegmentSkipKind) {
        val facts = dispatchFacts()
        execute(
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

    /**
     * The ONE position-aware snapshot fold behind the button dispatch: every
     * fact the skip ladder consults, read from a single snapshot that pairs
     * the live position/duration from the dedicated high-frequency flows with
     * the low-frequency uiState slices ([SegmentProjection]), so the active
     * segment and its end ticks can never come from different reads (the
     * pairing rule [SegmentDispatchFacts] exists to enforce). The active scan
     * runs once (precomputed overloads); the intro/credit end-tick reads
     * re-scan the short list per read.
     */
    private fun dispatchFacts(): SegmentDispatchFacts {
        val state = uiState.value
        val input = SegmentProjection(state).toSegmentInput(durationMs())
        val positionMsValue = positionMs()
        val seg = SegmentCalculator.computeActiveSegment(input, positionMsValue)
        return SegmentDispatchFacts(
            cinemaIntroActive = state.cinemaIntroState != null,
            isOutroNearEnd = SegmentCalculator.isOutroNearEnd(input, seg),
            canSkipToNext = canSkipToNext(state.episodes.nextEpisode),
            segments = SegmentSnapshot(
                activeType = seg?.type,
                activeEndTicks = seg?.let { SegmentCalculator.segmentEndTicks(input, it) },
                introEndTicks = SegmentCalculator.segmentEndTicksForType(input, positionMsValue, MediaSegmentType.INTRO),
                creditEndTicks = SegmentCalculator.segmentEndTicksForType(input, positionMsValue, MediaSegmentType.OUTRO),
            ),
        )
    }

    private fun execute(target: SegmentSkipTarget, userInitiated: Boolean) {
        when (target) {
            is SegmentSkipTarget.SeekToPosition -> seekLocal(target.positionMs, userInitiated)
            SegmentSkipTarget.SkipToNextEpisode -> playNextManually()
            SegmentSkipTarget.AdvanceCinemaIntro -> advanceCinemaIntro()
            SegmentSkipTarget.None -> Unit
        }
    }

    /**
     * Raises the ephemeral "Skipped …" caption on the uiState and re-arms the
     * auto-clear. A notice replacing a live one restarts the window; the
     * caption's [com.raulshma.jellyplay.feature.player.video.SkippedSegmentNotice.seq]
     * makes the same-type consecutive case visible to the collector. Fired
     * from BOTH notice arms — the transport clamp's local seek
     * ([onClampedSeek]) and the position-tick auto-skip ([autoSkip]) — so the
     * notice has ONE home.
     */
    private fun showSkippedSegmentNotice(segmentType: MediaSegmentType) {
        uiState.update {
            it.copy(
                skippedSegmentNotice = SkippedSegmentNotice(
                    segmentType = segmentType,
                    seq = ++noticeSeq,
                ),
            )
        }
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(SKIPPED_SEGMENT_NOTICE_MS)
            uiState.update { current ->
                // Only clear OUR notice — a newer one may have replaced it.
                if (current.skippedSegmentNotice?.seq == noticeSeq) {
                    current.copy(skippedSegmentNotice = null)
                } else {
                    current
                }
            }
        }
    }
}
