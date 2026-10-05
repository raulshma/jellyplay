package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerBinding
import com.raulshma.jellyplay.core.model.PlayerInputKey
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.SwipeEdge
import com.raulshma.jellyplay.core.model.SwipeSide
import com.raulshma.jellyplay.core.model.TouchZone
import com.raulshma.jellyplay.core.model.WheelAxis

/**
 * Pure resolution + audit logic over a persisted [PlayerInputMap] (the
 * `PlayerWheelPolicy`/`PlayerKeyPolicy` convention: zero Compose deps,
 * JVM-testable). The input pipelines translate a raw event into an ORDERED
 * candidate list of catalog patterns — most specific first — and
 * [resolve] picks the first pattern carrying an enabled binding, so
 * candidate order IS the specificity order and the map's row order is the
 * only remaining tie-break (identical patterns cannot coexist: the editor
 * blocks exact duplicates).
 *
 * Candidate translation rules (each pinned by `PlayerInputPolicyTest`):
 *  - Keyboard keys try the EXACT modifier combo first, then the canonical
 *    modifier-less row — so plain-letter defaults keep absorbing modified
 *    presses (Ctrl+M stays mute today) while an explicit Ctrl+M binding, if
 *    the user creates one, wins over the plain row. Seek-family keys
 *    ([isSeekFamilyKey]) skip the exact arm entirely: their modifier steps
 *    are event-time semantics (the VLC step ladder), not separate bindings.
 *  - Wheel scrolls try the exact (axis, shift) row first, then the
 *    shift-less axis row (Shift+vertical falls back to the plain volume row
 *    exactly when no explicit Shift row exists).
 *  - Touch / D-pad events map 1:1 to one pattern.
 */
internal object PlayerInputPolicy {

    /** Seek-family keys: modifier combos fold into the event-time step. */
    internal val SEEK_FAMILY_KEYS: Set<PlayerInputKey> = setOf(
        PlayerInputKey.DPAD_LEFT,
        PlayerInputKey.DPAD_RIGHT,
        PlayerInputKey.J,
        PlayerInputKey.L,
        PlayerInputKey.MEDIA_FAST_FORWARD,
        PlayerInputKey.MEDIA_REWIND,
        PlayerInputKey.PAGE_UP,
        PlayerInputKey.PAGE_DOWN,
        PlayerInputKey.MOVE_HOME,
        PlayerInputKey.MOVE_END,
    )

    /** Candidate patterns for a keyboard press, most specific first. */
    fun keyCandidates(
        key: PlayerInputKey,
        isCtrlPressed: Boolean,
        isShiftPressed: Boolean,
        isAltPressed: Boolean,
    ): List<InputPattern> {
        val exact = InputPattern.Key(
            key = key,
            ctrl = isCtrlPressed,
            shift = isShiftPressed,
            alt = isAltPressed,
        )
        if (key in SEEK_FAMILY_KEYS) return listOf(InputPattern.Key(key = key))
        return if (exact == InputPattern.Key(key = key)) {
            listOf(exact)
        } else {
            listOf(exact, InputPattern.Key(key = key))
        }
    }

    /** Candidate patterns for a wheel scroll, most specific first. */
    fun wheelCandidates(axis: WheelAxis, isShiftPressed: Boolean): List<InputPattern> {
        val exact = InputPattern.Wheel(axis = axis, shift = isShiftPressed)
        val plain = InputPattern.Wheel(axis = axis, shift = false)
        return if (exact == plain) listOf(exact) else listOf(exact, plain)
    }

    /**
     * First enabled binding across [candidates] (most specific wins), or null
     * when no candidate is bound-and-enabled. A pattern bound to
     * [PlayerAction.NONE] STOPS resolution — an explicit "do nothing" is a
     * binding, so it shadows the fallback candidate (the exact-then-plain
     * ladder) behind it.
     */
    fun resolve(map: PlayerInputMap, candidates: List<InputPattern>): PlayerBinding? =
        candidates.firstNotNullOfOrNull { pattern ->
            map.enabledBindingFor(pattern)?.let { binding ->
                // NONE absorbs: return the row (non-null) so callers see an
                // explicit unbind rather than falling through the ladder.
                binding
            }
        }

    /** Single-pattern convenience for the 1:1 pipelines (touch / D-pad). */
    fun resolve(map: PlayerInputMap, pattern: InputPattern): PlayerBinding? =
        resolve(map, listOf(pattern))

    /**
     * [resolve] with an explicit-NONE row dropped: pattern ladder → bound
     * action, or null when unbound/disabled/NONE (the "rebound action or
     * fall through" shape every arm site wants).
     */
    fun resolveAction(map: PlayerInputMap, candidates: List<InputPattern>): PlayerAction? =
        resolve(map, candidates)?.takeIf { it.action != PlayerAction.NONE }?.action

    /** Single-pattern convenience for the 1:1 pipelines (touch / D-pad). */
    fun resolveAction(map: PlayerInputMap, pattern: InputPattern): PlayerAction? =
        resolveAction(map, listOf(pattern))

    /**
     * Gate-style check for the detector arms (the swipe halves, the tap
     * zones, the wheel rows, the D-pad arms): the pattern must have an
     * ENABLED row whose action is not [PlayerAction.NONE] — an explicit
     * unbind reads as OFF, so a disabled arm attaches no semantics and
     * consumes nothing.
     */
    fun isActionEnabled(map: PlayerInputMap, pattern: InputPattern): Boolean {
        val binding = map.enabledBindingFor(pattern) ?: return false
        return binding.action != PlayerAction.NONE
    }
}

/**
 * The per-detector-arm gate bundle the player surface reads: every boolean
 * is "this pattern has an enabled, non-NONE binding" — the exact question
 * the old two coarse tier flags (`tapGesturesEnabled`/`swipeGesturesEnabled`)
 * answered at 1/13th the granularity (issue #171's brightness-vs-volume
 * split lives here). Compose-free so it snapshots cheaply into
 * `pointerInput` keys and test assertions.
 */
internal data class PlayerInputGates(
    val tap: Boolean,
    val doubleTapBack: Boolean,
    val doubleTapCenter: Boolean,
    val doubleTapForward: Boolean,
    val holdBack: Boolean,
    val holdForward: Boolean,
    val longPress: Boolean,
    val pinch: Boolean,
    val swipeBrightness: Boolean,
    val swipeVolume: Boolean,
    val swipeSeek: Boolean,
    val edgeLeft: Boolean,
    val edgeRight: Boolean,
    val wheelVolume: Boolean,
    val wheelSeek: Boolean,
) {
    /** Any swipe-surface arm on (the drag `pointerInput` attaches when this holds). */
    val anySwipe: Boolean
        get() = swipeBrightness || swipeVolume || swipeSeek || edgeLeft || edgeRight

    companion object {
        private fun on(map: PlayerInputMap, pattern: InputPattern): Boolean =
            PlayerInputPolicy.isActionEnabled(map, pattern)

        fun of(map: PlayerInputMap): PlayerInputGates = PlayerInputGates(
            tap = on(map, InputPattern.Tap),
            doubleTapBack = on(map, InputPattern.DoubleTap(TouchZone.LEFT)),
            doubleTapCenter = on(map, InputPattern.DoubleTap(TouchZone.CENTER)),
            doubleTapForward = on(map, InputPattern.DoubleTap(TouchZone.RIGHT)),
            holdBack = on(map, InputPattern.DoubleTapHold(TouchZone.LEFT)),
            holdForward = on(map, InputPattern.DoubleTapHold(TouchZone.RIGHT)),
            longPress = on(map, InputPattern.LongPress),
            pinch = on(map, InputPattern.Pinch),
            swipeBrightness = on(map, InputPattern.VerticalSwipe(SwipeSide.LEFT)),
            swipeVolume = on(map, InputPattern.VerticalSwipe(SwipeSide.RIGHT)),
            swipeSeek = on(map, InputPattern.HorizontalSwipe),
            edgeLeft = on(map, InputPattern.EdgeSwipe(SwipeEdge.LEFT)),
            edgeRight = on(map, InputPattern.EdgeSwipe(SwipeEdge.RIGHT)),
            wheelVolume = on(map, InputPattern.Wheel(WheelAxis.VERTICAL)),
            wheelSeek = on(map, InputPattern.Wheel(WheelAxis.HORIZONTAL)),
        )
    }
}
