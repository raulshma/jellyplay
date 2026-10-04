package com.raulshma.jellyplay.feature.player.video.state

import kotlin.math.abs

/**
 * Pure decision logic for the player surface's mouse-wheel tier (plain
 * wheel = volume, Shift+wheel or a horizontally-dominant wheel delta = seek —
 * the jellyfin-media-player `EventFilter` pattern). Extracted from
 * `VideoPlayerScreenGestures.playerWheelGestures` so the classification and
 * the sub-notch arithmetic are JVM-testable with zero Compose deps.
 *
 * Compose scroll semantics used here (`PointerInputChange.scrollDelta`, the
 * per-change wheel delta carried by `PointerEventType.Scroll` events —
 * Android's adapter inverts the platform axis so every platform agrees):
 *  - `y > 0` is a wheel turn toward the user (scroll down); `y < 0` away
 *    (scroll up). mpv/JMP map wheel-up to "forward / up", so VOLUME and SEEK
 *    both invert `y`.
 *  - `x > 0` is a wheel tilt/pan to the right — mapped to seek-forward, the
 *    natural timeline direction.
 */
internal object PlayerWheelPolicy {

    /**
     * One plain-wheel notch as a normalized volume delta, fed straight into
     * [GestureSeekController.onVolumeGesture] (whose drag input is normalized
     * 0..1 — a full-screen-height swipe is 0.5). Calibrated to the gesture
     * overlay's accessibility step (`A11Y_STEP_DELTA == 0.1f`): "a moderate
     * drag" and one wheel notch move the same amount, so wheel, swipe and
     * TalkBack all agree. On Android the controller quantizes this into
     * hardware STREAM_MUSIC steps with a fractional remainder carried across
     * notches; on cast it is 10% of the float cast volume.
     */
    const val VOLUME_NOTCH_DELTA = 0.1f

    /**
     * One plain-wheel notch on the ENGINE-volume path (desktop, where there is
     * no system-stream seam): the `RemotePlayableEngine.increaseVolume/
     * decreaseVolume` default delta (5% normalized ≈ 5 mpv points, 10 VLC
     * points) — the same step the remote-control dispatchers use.
     */
    const val ENGINE_VOLUME_NOTCH_DELTA = 0.05f

    /**
     * True when a Scroll event must be interpreted as SEEK: Shift is held, or
     * the horizontal component strictly dominates. A vertical-only delta
     * without Shift is volume.
     */
    fun isSeekScroll(isShiftPressed: Boolean, scrollX: Float, scrollY: Float): Boolean =
        isShiftPressed || abs(scrollX) > abs(scrollY)

    /** Wheel direction → volume direction: scroll up (`y < 0`) = +1, down = −1. */
    fun volumeDirection(scrollY: Float): Int = if (scrollY < 0f) 1 else -1

    /**
     * Wheel direction → seek direction on the seek axis: scroll up/tilt-right
     * (negative axis delta) = +1 (forward), scroll down/tilt-left = −1 (back)
     * — matching mpv's `WHEEL_UP seek 10` / `WHEEL_DOWN seek -10`.
     */
    fun seekDirection(seekAxisDelta: Float): Int = if (seekAxisDelta < 0f) 1 else -1

    /**
     * Accumulate raw (possibly fractional — trackpads) scroll delta on the
     * seek axis and quantize it into WHOLE notches, carrying the remainder so
     * small trackpad pans add up to one step. Returns `(notches, remainder)`;
     * `notches` carries the sign (multiply by [seekDirection]'s output on the
     * raw axis — or read the sign directly, it already IS the seek direction).
     */
    fun accumulateSeekNotches(previousAccumulator: Float, rawDelta: Float): Pair<Int, Float> {
        val accumulated = previousAccumulator + rawDelta
        val notches = accumulated.toInt()
        return notches to (accumulated - notches)
    }

    /** The seek-axis delta of a Scroll event: whichever component dominates. */
    fun seekAxisDelta(scrollX: Float, scrollY: Float): Float =
        if (abs(scrollX) > abs(scrollY)) scrollX else scrollY
}
