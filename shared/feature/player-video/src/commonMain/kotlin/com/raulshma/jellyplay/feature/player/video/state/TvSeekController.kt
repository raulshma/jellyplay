package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The fraction of the bar one seek step advances: the seek-step divergence
 * from `TvControllableSeekBar`'s former inline `seekStep` — one D-pad tick
 * seeks 30 s on TV and 10 s on touch, expressed as a fraction of the
 * duration so the accumulated ticks always resolve to positions on the
 * bar. Unbounded when [durationMs] is zero (the caller guards duration-less
 * streams before any step is applied).
 */
internal fun tvSeekStepFraction(isTv: Boolean, durationMs: Long): Float =
    if (isTv) 30_000f / durationMs else 10_000f / durationMs

/**
 * The TV/touch seek bar's input-event state machine, extracted from
 * `PlayerControls.TvControllableSeekBar` (the [GestureSeekController]
 * shape: a plain class remembered in the composable, [StateFlow] state out,
 * side-effect callbacks in). It owns the decisions that previously lived as
 * five loose `remember` variables plus inline lambdas:
 *
 *  - **Focus entry seeding / flush-on-unfocus:** gaining focus seeds the
 *    thumb at the current live position (or 0 when the duration is
 *    unknown); losing focus with an uncommitted D-pad seek COMMITS it —
 *    the pending seek never dies silently on a focus steal.
 *  - **D-pad accumulate-and-clamp:** each tick nudges the seek fraction by
 *    [tvSeekStepFraction] and clamps directionally (`coerceAtMost(1f)`
 *    forward, `coerceAtLeast(0f)` back); a duration-less stream reports
 *    the tick unconsumed so the key propagates to other handlers.
 *  - **Drag-vs-TV-vs-live priority** ([progress]): while dragging the
 *    dragged fraction wins; else while focused the D-pad-seeded fraction;
 *    else the live position — and a duration-less stream renders 0.
 *  - **The seek callbacks:** [onSeekStart]/[onSeekPreview]/[onSeekEnd]
 *    fire in the same order and at the same points as the former inline
 *    handlers (preview per tick/drag move, start on the first tick of an
 *    unstarted seek, end on select/focus-loss/drag-release).
 *
 * The composable keeps only drawing and event wiring (see
 * [rememberTvSeekController]).
 *
 * Internal: consumed only by PlayerControls and this module's jvmTest —
 * not a stable API surface (the GestureSeekController seam shape).
 */
internal class TvSeekController(
    private val onSeekStart: () -> Unit,
    private val onSeekPreview: (positionMs: Long) -> Unit,
    private val onSeekEnd: () -> Unit,
) {
    /**
     * The stream duration in ms — the DurationChanged input, assigned by the
     * composable on every recomposition (duration is a plain recomposition
     * input, so no frame can dispatch an event against a stale duration).
     * `<= 0` means unknown (live) and disables seeking.
     */
    var durationMs: Long = 0L

    /** The seek bar currently holds TV focus (TV mode only — the composable
     *  installs the focus handlers only there, so `isFocused` implies TV). */
    private val _isFocused = MutableStateFlow(false)
    val isFocused: StateFlow<Boolean> = _isFocused.asStateFlow()

    /** The D-pad seek fraction seeded on focus entry, advanced per tick. */
    private val _tvSeekPosition = MutableStateFlow(0f)
    val tvSeekPosition: StateFlow<Float> = _tvSeekPosition.asStateFlow()

    /** Whether a D-pad seek is in flight (started, not yet flushed). */
    private val _tvSeekStarted = MutableStateFlow(false)
    val tvSeekStarted: StateFlow<Boolean> = _tvSeekStarted.asStateFlow()

    private val _isDragging = MutableStateFlow(false)
    val isDragging: StateFlow<Boolean> = _isDragging.asStateFlow()

    private val _dragFraction = MutableStateFlow(0f)
    val dragFraction: StateFlow<Float> = _dragFraction.asStateFlow()

    /**
     * The drag-vs-TV-vs-live progress resolution (verbatim from the former
     * inline `progress` — the `isTv &&` conjunct is implied here: the focus
     * state can only become true when the TV modifiers are installed).
     */
    fun progress(livePositionMs: Long): Float = if (durationMs > 0) {
        if (_isDragging.value) _dragFraction.value
        else if (_isFocused.value) _tvSeekPosition.value
        else livePositionMs.toFloat() / durationMs
    } else 0f

    /**
     * FocusGained: seed the thumb at [livePositionMs] (0 on a duration-less
     * stream) and clear any stale in-flight seek — without a SeekEnd (the
     * historical asymmetry: a committed seek never straddles a focus entry).
     */
    fun onFocusGained(livePositionMs: Long) {
        _isFocused.value = true
        _tvSeekPosition.value = if (durationMs > 0) livePositionMs.toFloat() / durationMs else 0f
        _tvSeekStarted.value = false
    }

    /**
     * FocusLost: drop TV focus and flush — an uncommitted D-pad seek is
     * committed on focus loss (e.g. the user pressed up/down to leave the
     * bar), exactly as the former inline handler did.
     */
    fun onFocusLost() {
        _isFocused.value = false
        flush()
    }

    /**
     * DpadTick: advance the seek fraction one [step] ([direction] positive =
     * forward, negative = back), starting the seek on the first tick of an
     * unstarted sequence. Returns false — the key event unconsumed — on a
     * duration-less stream; true otherwise.
     */
    fun onDpadTick(direction: Int, step: Float): Boolean {
        if (durationMs <= 0L) return false
        if (!_tvSeekStarted.value) {
            _tvSeekStarted.value = true
            onSeekStart()
        }
        val current = _tvSeekPosition.value
        _tvSeekPosition.value = if (direction >= 0) {
            (current + step).coerceAtMost(1f)
        } else {
            (current - step).coerceAtLeast(0f)
        }
        onSeekPreview((_tvSeekPosition.value * durationMs).toLong())
        return true
    }

    /**
     * Flush: commit the pending D-pad seek (the select key, focus loss).
     * No-op when nothing is in flight.
     */
    fun flush() {
        if (_tvSeekStarted.value) {
            _tvSeekStarted.value = false
            onSeekEnd()
        }
    }

    /**
     * DragStart: the touch down. Starts the seek BEFORE publishing the
     * fraction (the historical ordering), previews it, then raises the
     * dragging flag. The composable guards duration-less streams before
     * installing the pointer handler, so no duration check here.
     */
    fun onDragStart(fraction: Float) {
        onSeekStart()
        _dragFraction.value = fraction
        onSeekPreview((fraction * durationMs).toLong())
        _isDragging.value = true
    }

    /** DragTo: a drag move — publish the new fraction and its preview. */
    fun onDragTo(fraction: Float) {
        _dragFraction.value = fraction
        onSeekPreview((fraction * durationMs).toLong())
    }

    /** DragEnd: the release — drop the dragging flag and commit the seek. */
    fun onDragEnd() {
        _isDragging.value = false
        onSeekEnd()
    }
}

/**
 * Remembers a [TvSeekController] across recompositions. The caller's
 * (possibly re-created) seek callbacks are forwarded through
 * [rememberUpdatedState] so the remembered controller never holds a stale
 * lambda — the former inline modifier lambdas re-captured them every
 * recomposition, and that freshness must survive the extraction.
 */
@Composable
internal fun rememberTvSeekController(
    onSeekStart: () -> Unit,
    onSeekPreview: (positionMs: Long) -> Unit,
    onSeekEnd: () -> Unit,
): TvSeekController {
    val currentOnSeekStart by rememberUpdatedState(onSeekStart)
    val currentOnSeekPreview by rememberUpdatedState(onSeekPreview)
    val currentOnSeekEnd by rememberUpdatedState(onSeekEnd)
    return remember {
        TvSeekController(
            onSeekStart = { currentOnSeekStart() },
            onSeekPreview = { currentOnSeekPreview(it) },
            onSeekEnd = { currentOnSeekEnd() },
        )
    }
}
