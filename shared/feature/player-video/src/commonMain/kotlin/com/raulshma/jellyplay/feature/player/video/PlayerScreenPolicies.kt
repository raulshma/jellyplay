package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio

/**
 * Pure decision logic extracted verbatim from `VideoPlayerScreen` so it is
 * reachable by the JVM tests instead of living inline in composition (the
 * Home-feature `homeQuickActionEffect` precedent). The screen keeps only the
 * effect shells — delay/timing, engine/viewModel dispatch — and reduces each
 * decision to a one-line call here. No Compose types in these signatures.
 */

/**
 * Backward step-seek target (button / keyboard / D-pad commit path): floor at
 * zero, no upper clamp.
 */
internal fun seekBackTargetMs(currentPositionMs: Long, stepMs: Long): Long =
    (currentPositionMs - stepMs).coerceAtLeast(0L)

/**
 * Forward step-seek target. Live streams report no duration (`0`) until
 * resolved, which would pin every forward seek to 0 via the upper clamp, so
 * the clamp is skipped when there is no known duration — the engine clamps on
 * its own at seek time. Semantically a sibling of the gesture path's
 * [com.raulshma.jellyplay.feature.player.video.state.GestureSeekMath.seekTarget],
 * but a separate policy: gestures cap the per-gesture delta for live streams,
 * the step path has no cap and clamps direction-asymmetrically.
 */
internal fun seekForwardTargetMs(currentPositionMs: Long, stepMs: Long, durationMs: Long): Long =
    if (durationMs <= 0L) {
        (currentPositionMs + stepMs).coerceAtLeast(0L)
    } else {
        (currentPositionMs + stepMs).coerceAtMost(durationMs)
    }

/**
 * Resume-skip target behind the ViewModel's `applyResumeSkip` funnel — the
 * `videoSkipBackOnResumeMs` rewind shared by the audio-focus regain path and
 * `resumePlayback`. A non-positive [skipMs] means the preference is disabled
 * and the position passes through unchanged; otherwise the target is the
 * position rewound by [skipMs], floored at zero (no upper clamp — the seek
 * always lands at or before the current position).
 */
internal fun resumeSkipTargetMs(currentPositionMs: Long, skipMs: Long): Long =
    if (skipMs <= 0L) {
        currentPositionMs
    } else {
        (currentPositionMs - skipMs).coerceAtLeast(0L)
    }

/**
 * Direction-folded step target behind the ViewModel's `seekByStep` funnel —
 * the single owner of the discrete skip-step path shared by the screen's
 * skip buttons / keyboard / D-pad commits and the PiP transport's SKIP
 * actions. [direction] < 0 steps back ([seekBackTargetMs]); anything else
 * steps forward ([seekForwardTargetMs]). The gesture/hold paths do NOT go
 * through here.
 */
internal fun stepSeekTargetMs(
    direction: Int,
    currentPositionMs: Long,
    stepMs: Long,
    durationMs: Long,
): Long =
    if (direction < 0) seekBackTargetMs(currentPositionMs, stepMs)
    else seekForwardTargetMs(currentPositionMs, stepMs, durationMs)

/**
 * What the player's entry orientation lock MEANS, as data: TV and cast paths
 * lock immediately, the per-preference path applies only after the screen's
 * `delay(400)` race guard (the timing shell stays in the LaunchedEffect).
 */
internal sealed interface OrientationLockDecision {
    /** Lock now — no settle delay. */
    data class Immediate(val lock: PlayerOrientationLock) : OrientationLockDecision

    /** Lock only after the screen's settle delay elapses. */
    data class SettleFirst(val lock: PlayerOrientationLock) : OrientationLockDecision
}

/**
 * The player's entry orientation fold: TV wins (always sensor-landscape for
 * TV), then cast (follow the user), otherwise the user's orientation
 * preference mapped to its platform-neutral lock.
 */
internal fun orientationLockDecision(
    isTv: Boolean,
    isCastConnected: Boolean,
    preference: OrientationMode,
): OrientationLockDecision = when {
    isTv -> OrientationLockDecision.Immediate(PlayerOrientationLock.TV_LANDSCAPE)
    isCastConnected -> OrientationLockDecision.Immediate(PlayerOrientationLock.USER)
    else -> OrientationLockDecision.SettleFirst(preference.toPlayerOrientationLock())
}

private fun OrientationMode.toPlayerOrientationLock(): PlayerOrientationLock = when (this) {
    OrientationMode.SENSOR_LANDSCAPE -> PlayerOrientationLock.SENSOR_LANDSCAPE
    OrientationMode.SENSOR_PORTRAIT -> PlayerOrientationLock.SENSOR_PORTRAIT
    OrientationMode.SENSOR -> PlayerOrientationLock.SENSOR
    OrientationMode.LOCKED_LANDSCAPE -> PlayerOrientationLock.LOCKED_LANDSCAPE
    OrientationMode.LOCKED_PORTRAIT -> PlayerOrientationLock.LOCKED_PORTRAIT
}

/**
 * AUTO ladder: an explicit selection wins; AUTO resolves to the detected
 * content ratio, falling back to FIT when nothing was detected yet.
 */
internal fun effectiveAspectRatio(selected: AspectRatio, detected: AspectRatio?): AspectRatio =
    if (selected == AspectRatio.AUTO) detected ?: AspectRatio.FIT else selected

/**
 * Skip-segment button visibility precedence: a segment must exist and its
 * configured behavior must ask for a button, and the button is suppressed by
 * PiP and the cinema-intro overlay. During an OUTRO the up-next overlay wins
 * (its own play affordance replaces the skip button); up-next does not
 * suppress non-OUTRO segments.
 */
internal fun isSkipSegmentButtonVisible(
    activeSegment: MediaSegment?,
    segmentBehavior: SegmentBehavior,
    isInPipMode: Boolean,
    isCinemaIntroVisible: Boolean,
    shouldShowUpNext: Boolean,
): Boolean =
    activeSegment != null &&
        segmentBehavior == SegmentBehavior.SHOW_BUTTON &&
        !isInPipMode &&
        !isCinemaIntroVisible &&
        !(activeSegment.type == MediaSegmentType.OUTRO && shouldShowUpNext)

/**
 * Whether the auto-hide timer may be scheduled at all: controls must be
 * visible with no seek gesture, open sheet, or overflow menu in progress, and
 * on non-TV a controls layer holding focus (the user is actively using the
 * controls) suppresses the hide entirely.
 */
internal fun shouldScheduleControlsAutoHide(
    showControls: Boolean,
    isSeeking: Boolean,
    isSheetOpen: Boolean,
    isOverflowMenuOpen: Boolean,
    isTv: Boolean,
    controlsHasFocus: Boolean,
): Boolean =
    showControls && !isSeeking && !isSheetOpen && !isOverflowMenuOpen &&
        (isTv || !controlsHasFocus)

// The TV-doubling fold (`controlsAutoHideTimeoutMs`) moved to the shared
// player-contract home
// (com.raulshma.jellyplay.feature.player.video.engine.PlayerChromePolicies)
// so the live player's screen cites the same ONE policy instead of a
// byte-identical copy.

/**
 * User-supplied font gate: the picked file must be TrueType or OpenType
 * (case-insensitive match on the display name's extension).
 */
internal fun isSupportedUserFontFile(displayName: String?): Boolean {
    val name = displayName?.lowercase().orEmpty()
    return name.endsWith(".ttf") || name.endsWith(".otf")
}

// ── Mark-watched-and-skip / mark-unwatched-and-quit ────────────────

/**
 * How a played-mark reaches the item — the same two arms the watched-threshold
 * callback uses (`PlaybackProgressReporter`'s `onWatchedThresholdReached`):
 * the server round-trip through `UserDataMutator.setPlayed` (via
 * `PlayedStateSync`, outbox when offline) or the local-only offline mark
 * (`OfflinePlaybackFacade.recordPlayed`). `SeenMediaRepository` is notification
 * de-dup — deliberately NOT one of these.
 */
internal enum class WatchedMarkPath { SERVER, OFFLINE_LOCAL }

/**
 * The mark-and-then decisions behind the two overflow actions, as data. The
 * ViewModel keeps only the effect shells (the mark dispatch, the
 * advance-vs-close verbs) and reduces each press to the fields here:
 *
 *  - [watchedMarkPath] — incognito routes "mark watched" to the offline-local
 *    mark exactly like the threshold callback (never the server, never an
 *    outbox row);
 *  - [watchedAdvancesToNext] — "mark watched & skip" advances to the next
 *    episode when one is known. A SyncPlay session advances even without a
 *    locally-resolved sibling: `EpisodeNavigator.next` resolves the sibling
 *    itself and routes through the group queue when it holds the item, so
 *    reusing the verb keeps the group in step (an absent sibling is the
 *    navigator's no-op, not a close);
 *  - [unwatchedMarkApplied] — "mark unwatched & exit" is a no-op mark in
 *    incognito (the whole point of incognito is leaving no watch state); the
 *    exit still happens on both paths.
 */
internal data class WatchedActionDecision(
    val watchedMarkPath: WatchedMarkPath,
    val watchedAdvancesToNext: Boolean,
    val unwatchedMarkApplied: Boolean,
)

/**
 * The decision matrix for the two mark-and-exit overflow actions. Pure so the
 * incognito/SyncPlay/has-next combinations stay pinned by the JVM matrix test
 * instead of living inline in the ViewModel.
 */
internal fun decideWatchedActions(
    hasNext: Boolean,
    incognito: Boolean,
    isInSyncPlay: Boolean,
): WatchedActionDecision = WatchedActionDecision(
    watchedMarkPath = if (incognito) WatchedMarkPath.OFFLINE_LOCAL else WatchedMarkPath.SERVER,
    watchedAdvancesToNext = hasNext || isInSyncPlay,
    unwatchedMarkApplied = !incognito,
)
