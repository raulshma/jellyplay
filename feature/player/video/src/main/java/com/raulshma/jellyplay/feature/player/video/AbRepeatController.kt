package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A/B repeat for video — loop playback between a user-set A point and B point.
 *
 * Mirrors the controller-extraction pattern of [SleepTimerController] /
 * [AutoPlayController]: pure workflow logic lifted out of the ViewModel. The
 * loop monitor runs against the high-frequency position flow
 * (`VideoPlayerViewModel.currentPositionMs`) rather than a ~4 Hz uiState, so
 * re-seeking at B is responsive without allocating a state copy per tick.
 *
 * Design notes:
 *  - When `enabled` and both points are set, reaching or passing B seeks back
 *    to A. To avoid a tight seek loop when B is at/near the end, the seek fires
 *    once per crossing (a small re-arm hysteresis: we only re-trigger after the
 *    position drops below B again, which the seek-to-A guarantees).
 *  - A must be `<` B; setting A above B clamps A to just below B, and vice
 *    versa, so the invariant holds regardless of set order.
 *  - Disabled or single-point states are inert (no seeks). Clearing both points
 *    and disabling resets the controller.
 *
 * **Item-switch semantics: the window does NOT persist across episodes.**
 * [resetForItem] clears both points (and re-arms), and the ViewModel's
 * item-switch path calls it. This also fixes the former divergence bug where
 * the reset ritual wiped the UiState mirror of this state but not the
 * controller's own copy — after an episode switch the loop monitor could seek
 * the *next* episode back to the *previous* episode's A point, and one tap on
 * the toggle resurrected the stale points.
 */
internal class AbRepeatController(
    private val scope: CoroutineScope,
    private val getEngine: () -> MediaEngine?,
    private val positionFlow: StateFlow<Long>,
) {
    private val _state = MutableStateFlow(AbRepeatState())
    val state: StateFlow<AbRepeatState> = _state.asStateFlow()

    /** True while the loop is mid-crossing (B reached, awaiting re-arm at A). */
    private var armed = true

    /** Start monitoring the position flow for B-crossings. */
    fun start() {
        scope.launch {
            positionFlow.collect { pos ->
                val s = _state.value
                if (!s.enabled || s.aMs == null || s.bMs == null) return@collect
                if (armed && pos >= s.bMs) {
                    armed = false
                    val a = s.aMs
                    getEngine()?.seekTo(a)
                    // Re-arm once the seek lands at/under B (it will, at A).
                    scope.launch {
                        positionFlow.collect { p ->
                            if (p < (s.bMs)) {
                                armed = true
                                return@collect
                            }
                        }
                    }
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
        armed = true
    }

    /** Sets the A point to the current playback position (clamped below B). */
    fun setPointA(ms: Long) {
        val b = _state.value.bMs
        val a = if (b != null && ms >= b) (b - 1).coerceAtLeast(0) else ms.coerceAtLeast(0)
        _state.value = _state.value.copy(aMs = a)
        armed = true
    }

    /** Sets the B point to the current playback position (clamped above A). */
    fun setPointB(ms: Long) {
        val a = _state.value.aMs
        val b = if (a != null && ms <= a) (a + 1) else ms.coerceAtLeast(0)
        _state.value = _state.value.copy(bMs = b)
        armed = true
    }

    fun clear() {
        _state.value = AbRepeatState()
        armed = true
    }

    /**
     * Item-switch reset: clears the window and re-arms the crossing monitor.
     * Called by the ViewModel's `releaseInternals()` on every item switch so a
     * previous episode's A/B points can neither seek the new episode nor be
     * resurrected by a single tap on the toggle.
     */
    fun resetForItem() {
        clear()
    }
}

/** A/B repeat window. Both points null → inert. */
data class AbRepeatState(
    val enabled: Boolean = false,
    val aMs: Long? = null,
    val bMs: Long? = null,
) {
    /** True only when enabled and both points are set with A < B. */
    val isActive: Boolean get() = enabled && aMs != null && bMs != null && aMs < bMs
}
