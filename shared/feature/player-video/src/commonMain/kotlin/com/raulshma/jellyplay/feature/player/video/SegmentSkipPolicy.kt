package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegmentType

/**
 * Pure decision logic for the "Skip Intro" / "Skip Credits" buttons,
 * extracted verbatim from `VideoPlayerViewModel` so the precedence ladder is
 * reachable by the JVM tests instead of living inline in the ViewModel (the
 * [PlayerScreenPolicies] precedent — the third sibling policy beside its
 * step-seek targets and the gesture path's
 * [com.raulshma.jellyplay.feature.player.video.state.GestureSeekMath.seekTarget]).
 * The ViewModel keeps only the effect shells — the position-aware state
 * snapshot, the `canSkipToNext` evaluation and the seek / play-next-episode /
 * cinema-advance dispatch — and reduces each decision to a one-line call
 * here. No engine or session types in these signatures.
 */

/**
 * Which skip affordance was pressed. [INTRO] matches [MediaSegmentType.INTRO]
 * segments and consults the cinema-intro escape; [CREDITS] matches
 * [MediaSegmentType.OUTRO] segments (the model calls credits "outro") and
 * consults the outro-near-end next-episode branch.
 */
internal enum class SegmentSkipKind(val segmentType: MediaSegmentType) {
    INTRO(MediaSegmentType.INTRO),
    CREDITS(MediaSegmentType.OUTRO),
}

/**
 * What a skip press resolves to, as data: a seek position in the unit the
 * engine seeks in (milliseconds — the ticks→ms conversion happens in
 * [segmentEndSeekTarget]) or one of the two non-seek escapes.
 */
internal sealed interface SegmentSkipTarget {
    /**
     * Seek to [positionMs] — milliseconds from the start of the item
     * (truncating `ticks / 10_000`, like every other ticks→ms conversion in
     * the player).
     */
    data class SeekToPosition(val positionMs: Long) : SegmentSkipTarget

    /** Jump straight to the next episode (outro near end + autoplay armed). */
    data object SkipToNextEpisode : SegmentSkipTarget

    /** Advance the Cinema Mode pre-roll intro (the session escape). */
    data object AdvanceCinemaIntro : SegmentSkipTarget

    /** Nothing to skip — the press is silently ignored. */
    data object None : SegmentSkipTarget
}

/**
 * Seek target for an already-resolved segment-end ticks value: the shared
 * `non-null and positive` guard plus the ticks→ms conversion. Used both by
 * the skip-button ladder below and by `VideoPlayerViewModel.skipSegment`
 * (the position-tick auto-skip path), which resolve their ticks against the
 * segment list on the ViewModel.
 */
internal fun segmentEndSeekTarget(endTicks: Long?): SegmentSkipTarget =
    if (endTicks != null && endTicks > 0L) {
        SegmentSkipTarget.SeekToPosition(endTicks / 10_000)
    } else {
        SegmentSkipTarget.None
    }

/**
 * The segment facts the skip ladder consults, snapshotted together by the
 * ViewModel from the position-aware uiState: which segment (if any) is
 * active and its pre-resolved end ticks, plus the per-kind end-ticks
 * fallbacks ([introEndTicks] / [creditEndTicks]). One type instead of four
 * travelling params — the fields all derive from the same segment state and
 * arrive from the same read.
 */
internal data class SegmentSnapshot(
    val activeType: MediaSegmentType?,
    val activeEndTicks: Long?,
    val introEndTicks: Long?,
    val creditEndTicks: Long?,
)

/**
 * The skip-button decision ladder. Precedence, verbatim from the inline
 * ladders this replaces (skipIntro / skipCredits):
 *
 *  1. INTRO only: an active Cinema Mode pre-roll always wins — the press
 *     advances the pre-roll instead of touching playback. (CREDITS has no
 *     cinema branch: the pre-roll has no credits.)
 *  2. CREDITS only: when the outro runs into the up-next window
 *     ([isOutroNearEnd]) and autoplay can advance ([canSkipToNext]), the
 *     press jumps straight to the next episode — even over an active OUTRO
 *     segment.
 *  3. An active segment of the pressed kind ([SegmentSnapshot.activeType])
 *     seeks to its pre-resolved end ticks
 *     ([SegmentSnapshot.activeEndTicks] — the ViewModel resolves the
 *     API-matched end via `segmentEndTicks`). Winning this rung with an
 *     invalid ticks value is a no-op: it does NOT fall through to rung 4,
 *     preserving the original early return.
 *  4. Otherwise the kind's end-ticks fallback ([SegmentSnapshot.introEndTicks] /
 *     [SegmentSnapshot.creditEndTicks]) seeks, if known and positive. (With
 *     the current `SegmentCalculator` wiring this rung is unreachable —
 *     `segmentEndTicksForType` is only non-null while the active segment is
 *     of that type, which already won rung 3 — but it is preserved verbatim
 *     rather than silently dropped.)
 *
 * All tick fields are Jellyfin ticks (1 ms == 10_000 ticks). The inputs are
 * plain values snapshotted by the ViewModel (plus the pre-evaluated
 * [canSkipToNext] from `AutoPlayController`); the returned target's effects —
 * the seek latches, the next-episode load, the session's cinema advance —
 * stay on the ViewModel / session side.
 */
internal fun segmentSkipTarget(
    kind: SegmentSkipKind,
    cinemaIntroActive: Boolean,
    isOutroNearEnd: Boolean,
    canSkipToNext: Boolean,
    segments: SegmentSnapshot,
): SegmentSkipTarget {
    val fallbackEndTicks = when (kind) {
        SegmentSkipKind.INTRO -> segments.introEndTicks
        SegmentSkipKind.CREDITS -> segments.creditEndTicks
    }
    return when {
        kind == SegmentSkipKind.INTRO && cinemaIntroActive ->
            SegmentSkipTarget.AdvanceCinemaIntro
        kind == SegmentSkipKind.CREDITS && isOutroNearEnd && canSkipToNext ->
            SegmentSkipTarget.SkipToNextEpisode
        segments.activeType == kind.segmentType ->
            segmentEndSeekTarget(segments.activeEndTicks)
        else ->
            segmentEndSeekTarget(fallbackEndTicks)
    }
}
