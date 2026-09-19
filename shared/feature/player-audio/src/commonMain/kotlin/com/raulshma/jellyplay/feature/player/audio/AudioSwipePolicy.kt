package com.raulshma.jellyplay.feature.player.audio

import kotlin.math.abs

/**
 * Pure swipe/double-tap decision logic extracted verbatim from
 * `AudioPlayerScreen` (the `PlayerScreenPolicies` precedent): the direction
 * lock, the vertical open-queue/dismiss ladders, the horizontal skip
 * thresholds and the double-tap thirds — as sealed arms so the screen keeps
 * only the effect shells (the coroutine `animateTo`/`snapTo` settles and the
 * seek-target math stay there). No Compose types in these signatures.
 */

/** Direction-lock threshold: a dominant axis must cross this to lock (px, strict). */
internal const val AUDIO_DIRECTION_LOCK_PX = 10f

/** Vertical end ladder — open the queue: live offset OR cumulative drag (px, strict). */
internal const val AUDIO_QUEUE_OPEN_OFFSET_PX = -80f
internal const val AUDIO_QUEUE_OPEN_TOTAL_DRAG_PX = -150f

/** Vertical end ladder — dismiss the player: live offset OR cumulative drag (px, strict). */
internal const val AUDIO_DISMISS_OFFSET_PX = 150f
internal const val AUDIO_DISMISS_TOTAL_DRAG_PX = 200f

/** Horizontal end ladder — skip threshold (px, strict on both signs). */
internal const val AUDIO_SKIP_THRESHOLD_PX = 180f

private const val DOUBLE_TAP_LEFT_ZONE_FRACTION = 0.35f
private const val DOUBLE_TAP_RIGHT_ZONE_FRACTION = 0.65f

/**
 * The drag direction lock: the dominant axis wins once it crosses
 * [AUDIO_DIRECTION_LOCK_PX]; equal magnitudes (or sub-threshold drags) stay
 * unlocked. Evaluated on the CUMULATIVE drag since drag start, exactly as the
 * screen's inline machine did.
 */
internal fun audioDragDirection(totalDragX: Float, totalDragY: Float): AudioDragDirection? {
    val absX = abs(totalDragX)
    val absY = abs(totalDragY)
    return when {
        absY > absX && absY > AUDIO_DIRECTION_LOCK_PX -> AudioDragDirection.VERTICAL
        absX > absY && absX > AUDIO_DIRECTION_LOCK_PX -> AudioDragDirection.HORIZONTAL
        else -> null
    }
}

internal enum class AudioDragDirection { VERTICAL, HORIZONTAL }

/**
 * Vertical drag-end decision, in the original order: the open-queue ladder
 * (live offset below [AUDIO_QUEUE_OPEN_OFFSET_PX] or cumulative drag below
 * [AUDIO_QUEUE_OPEN_TOTAL_DRAG_PX]) is checked BEFORE the dismiss ladder, so
 * a dominant upward fling wins even when the cumulative drag also crossed the
 * downward total.
 */
internal sealed interface AudioVerticalSwipeAction {
    data object OpenQueue : AudioVerticalSwipeAction
    data object Dismiss : AudioVerticalSwipeAction
    data object Settle : AudioVerticalSwipeAction
}

internal fun audioVerticalSwipeAction(offsetY: Float, totalDragY: Float): AudioVerticalSwipeAction = when {
    offsetY < AUDIO_QUEUE_OPEN_OFFSET_PX || totalDragY < AUDIO_QUEUE_OPEN_TOTAL_DRAG_PX ->
        AudioVerticalSwipeAction.OpenQueue
    offsetY > AUDIO_DISMISS_OFFSET_PX || totalDragY > AUDIO_DISMISS_TOTAL_DRAG_PX ->
        AudioVerticalSwipeAction.Dismiss
    else -> AudioVerticalSwipeAction.Settle
}

/** Horizontal drag-end decision: past ±[AUDIO_SKIP_THRESHOLD_PX] skips, else settles. */
internal sealed interface AudioHorizontalSwipeAction {
    data object SkipNext : AudioHorizontalSwipeAction
    data object SkipPrevious : AudioHorizontalSwipeAction
    data object Settle : AudioHorizontalSwipeAction
}

internal fun audioHorizontalSwipeAction(offsetX: Float): AudioHorizontalSwipeAction = when {
    offsetX < -AUDIO_SKIP_THRESHOLD_PX -> AudioHorizontalSwipeAction.SkipNext
    offsetX > AUDIO_SKIP_THRESHOLD_PX -> AudioHorizontalSwipeAction.SkipPrevious
    else -> AudioHorizontalSwipeAction.Settle
}

/** Double-tap thirds: leading seeks back, trailing seeks forward, middle toggles. */
internal sealed interface DoubleTapSeekAction {
    data object SeekBack : DoubleTapSeekAction
    data object SeekForward : DoubleTapSeekAction
    data object TogglePlayPause : DoubleTapSeekAction
}

internal fun doubleTapSeekAction(x: Float, width: Float): DoubleTapSeekAction = when {
    x < width * DOUBLE_TAP_LEFT_ZONE_FRACTION -> DoubleTapSeekAction.SeekBack
    x > width * DOUBLE_TAP_RIGHT_ZONE_FRACTION -> DoubleTapSeekAction.SeekForward
    else -> DoubleTapSeekAction.TogglePlayPause
}
