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

/** TV keeps controls twice as long as touch form factors. */
internal fun controlsAutoHideTimeoutMs(baseTimeoutMs: Long, isTv: Boolean): Long =
    if (isTv) baseTimeoutMs * 2 else baseTimeoutMs

/**
 * User-supplied font gate: the picked file must be TrueType or OpenType
 * (case-insensitive match on the display name's extension).
 */
internal fun isSupportedUserFontFile(displayName: String?): Boolean {
    val name = displayName?.lowercase().orEmpty()
    return name.endsWith(".ttf") || name.endsWith(".otf")
}
