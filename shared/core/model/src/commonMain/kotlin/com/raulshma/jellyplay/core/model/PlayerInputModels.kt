package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The player input-mapping vocabulary: what a recognized input MEANS
 ([PlayerAction]) and which inputs can be bound ([InputPattern]). The mapping
 itself ([PlayerInputMap]) is a free-form list of [PlayerBinding] rows —
 any catalog pattern can carry any action — persisted as one JSON blob
 (the video-player `input_bindings` key) and resolved at input time by the
 player-video module's pure `PlayerInputPolicy`.

 Scope guards kept from the design round:
  - the pattern catalog is CLOSED (the gestures/keys/D-pad controls the
    detectors already recognize) — custom drawn zones are a later pattern
    arm, not a schema break;
  - disabled = [PlayerBinding.enabled] `false`, never an action erasure, so
    the in-player quick toggles flip switches without destroying bindings;
  - priority is list order (the resolver's tie-break) — the shipped editor
    never reorders rows today (defaults carry the priority), so the order is
    a persistence-level contract, not a UI one, yet.

 Labels for these enums resolve through the `core:ui` `PreferenceEnumNames`
 seam — no hardcoded display strings here, matching [GestureMode].
 */

/**
 * What one bound input does. The superset of the legacy `PlayerKeyAction`
 * table, the gesture-tier behaviors and the wheel/D-pad rows; each input
 * pipeline's effect shell maps the resolved action onto the same lambdas it
 * always drove, so no two pipelines can drift.
 *
 * [SEEK_FORWARD]/[SEEK_BACK] are step semantics — the magnitude comes from
 * the site that fired (the user's seek duration for keys/D-pad/double-tap,
 * the fixed page/home rows for PgUp/PgDn/Home/End, one notch for the wheel).
 * [SWIPE_SEEK]/[SWIPE_VOLUME]/[SWIPE_BRIGHTNESS]/[EDGE_SWIPE] are the
 * finger-following continuous behaviors only the swipe detector can express.
 */
@Immutable
@Serializable
enum class PlayerAction {
    // Transport
    TOGGLE_PLAY_PAUSE,
    PLAY,
    PAUSE,
    SEEK_FORWARD,
    SEEK_BACK,
    TOGGLE_CONTROLS,
    BACK_OR_HIDE,
    EXIT_PLAYER,

    // Volume / brightness
    VOLUME_UP,
    VOLUME_DOWN,
    TOGGLE_MUTE,
    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,

    // Swipe-class continuous behaviors
    SWIPE_SEEK,
    SWIPE_VOLUME,
    SWIPE_BRIGHTNESS,

    // Tracks / delays / speed
    TOGGLE_SUBTITLES,
    SUBTITLE_DELAY_DECREASE,
    SUBTITLE_DELAY_INCREASE,
    AUDIO_DELAY_DECREASE,
    AUDIO_DELAY_INCREASE,
    CYCLE_SPEED,
    HOLD_SPEED,

    // Picture
    ZOOM,
    TOGGLE_ORIENTATION,

    // System
    LOCK_CONTROLS,
    SCREENSHOT,
    NEXT_EPISODE,
    PREVIOUS_EPISODE,

    /** Explicitly unbound: the pattern is recognized but does nothing. */
    NONE,
    ;
}

/**
 * Whether rebounding this action onto a discrete row (key, wheel notch,
 * D-pad control, tap-class touch arm) actually does something: every input
 * pipeline's rebound routes to the one shared executor, which implements
 * every action EXCEPT the finger-following continuous class — [SWIPE_SEEK],
 * [SWIPE_VOLUME], [SWIPE_BRIGHTNESS], [HOLD_SPEED] and [ZOOM] live only in
 * their dedicated gesture detectors, so a discrete rebound of one is a
 * saved dead binding (the executor's unmatched arm returns false). The
 * binding editor's picker filters this class out; a new continuous action
 * must join this arm — there is no other guard. [NONE] is `false` too, but
 * for a different reason: it is the explicit erase, not an offerable
 * rebinding (the picker re-adds it as the "Unbound" entry).
 */
val PlayerAction.bindableToDiscreteRow: Boolean
    get() = when (this) {
        PlayerAction.SWIPE_SEEK,
        PlayerAction.SWIPE_VOLUME,
        PlayerAction.SWIPE_BRIGHTNESS,
        PlayerAction.HOLD_SPEED,
        PlayerAction.ZOOM,
        PlayerAction.NONE,
        -> false

        else -> true
    }

/** Horizontal band of the player surface for tap-class patterns. */
@Serializable
enum class TouchZone { LEFT, CENTER, RIGHT }

/** Vertical-swipe half: the fixed brightness (left) / volume (right) arms. */
@Serializable
enum class SwipeSide { LEFT, RIGHT }

/** Wheel scroll axis (Compose scroll delta: y = vertical, x = tilt/pan). */
@Serializable
enum class WheelAxis { VERTICAL, HORIZONTAL }

/** Horizontal screen edge for the edge-swipe pattern. */
@Serializable
enum class SwipeEdge { LEFT, RIGHT }

/**
 * The keyboard/D-pad key vocabulary — exactly the keys the player's key
 * layers already recognize (the `PlayerKeyCodes` platform seam's catalog).
 * Persisted by NAME, never as platform key codes.
 */
@Serializable
enum class PlayerInputKey {
    // Transport letters / media keys
    SPACE, K, M, V, F, G, H, J, L,
    BRACKET_LEFT, BRACKET_RIGHT,
    F1, F2, F3, F4,
    ESCAPE, BACK,
    // Arrows / volume / paging
    DPAD_LEFT, DPAD_RIGHT, DPAD_UP, DPAD_DOWN,
    VOLUME_UP, VOLUME_DOWN,
    PAGE_UP, PAGE_DOWN, MOVE_HOME, MOVE_END,
    // Media transport keys
    MEDIA_PLAY, MEDIA_PAUSE, MEDIA_PLAY_PAUSE,
    MEDIA_FAST_FORWARD, MEDIA_REWIND,
}

/** One D-pad control on the TV remote scheme. */
@Serializable
enum class DpadControl {
    LEFT, RIGHT, UP, DOWN, SELECT, BACK,
    PLAY_PAUSE, FAST_FORWARD, REWIND,
}

/**
 * A bindable input: gesture primitive × region, wheel row, key (+modifiers)
 * or D-pad control. Closed catalog — equality is structural, so duplicate
 * detection is plain `==`.
 */
@Immutable
@Serializable
sealed class InputPattern {

    /**
     * Single tap anywhere on the surface (zone-split tap classes are a later
     * catalog extension — today every single tap toggles the controls).
     */
    @Serializable
    data object Tap : InputPattern()

    /** Double tap in a [zone] — left/right are the step-seek zones. */
    @Serializable
    data class DoubleTap(val zone: TouchZone = TouchZone.CENTER) : InputPattern()

    /**
     * The second press of a double-tap HELD in a seek [zone] — the
     * continuous accelerating seek. LEFT/RIGHT only (center holds
     * fall through to hold-speed).
     */
    @Serializable
    data class DoubleTapHold(val zone: TouchZone) : InputPattern()

    /** Long-press on a first press, anywhere (hold-speed today). */
    @Serializable
    data object LongPress : InputPattern()

    /** Single-finger vertical swipe on a half — the brightness/volume arms. */
    @Serializable
    data class VerticalSwipe(val side: SwipeSide) : InputPattern()

    /** Single-finger horizontal swipe anywhere — the finger-following seek. */
    @Serializable
    data object HorizontalSwipe : InputPattern()

    /** Swipe starting within the edge band. */
    @Serializable
    data class EdgeSwipe(val edge: SwipeEdge) : InputPattern()

    /** Two-finger pinch — zoom/reframe. */
    @Serializable
    data object Pinch : InputPattern()

    /**
     * Mouse-wheel scroll row. [shift] distinguishes the plain wheel from the
     * Shift+wheel seek row (the horizontally-dominant delta folds into
     * [WheelAxis.HORIZONTAL]).
     */
    @Serializable
    data class Wheel(val axis: WheelAxis, val shift: Boolean = false) : InputPattern()

    /** Hardware key with exact modifiers. */
    @Serializable
    data class Key(
        val key: PlayerInputKey,
        val ctrl: Boolean = false,
        val shift: Boolean = false,
        val alt: Boolean = false,
    ) : InputPattern()

    /** TV remote D-pad control. */
    @Serializable
    data class DPad(val control: DpadControl) : InputPattern()
}

/**
 * One pattern → action row. [id] is a stable wire identity (the editor's
 * reorder/reset handles key off it); [enabled] is the quick-toggle flag —
 * `false` keeps the action for later, it never erases it.
 */
@Immutable
@Serializable
data class PlayerBinding(
    val id: String,
    val pattern: InputPattern,
    val action: PlayerAction,
    val enabled: Boolean = true,
)

/**
 * The whole mapping: an ordered list of [PlayerBinding]s (order = priority,
 * so lookups keep the FIRST row a pattern owns). Absent patterns simply fall
 * through to "not bound"; the defaults factory fills every row the detectors
 * can fire.
 */
@Immutable
@Serializable
data class PlayerInputMap(
    val bindings: List<PlayerBinding> = emptyList(),
) {
    private val byPattern: Map<InputPattern, PlayerBinding> by lazy {
        // First-wins: with list order as priority, a duplicate pattern (only
        // possible via a hand-edited blob — the editor blocks saves) must
        // resolve to its first row, not silently drop it.
        buildMap {
            for (binding in bindings) putIfAbsent(binding.pattern, binding)
        }
    }

    /** First enabled binding whose pattern is exactly [pattern], if any. */
    fun enabledBindingFor(pattern: InputPattern): PlayerBinding? =
        byPattern[pattern]?.takeIf { it.enabled }

    /**
     * True when no two rows claim the same pattern — the binding editor's
     * write guard (only a hand-edited blob can violate it; the editor blocks
     * such a save).
     */
    fun hasNoDuplicates(): Boolean =
        bindings.size == bindings.distinctBy { it.pattern }.size

    /**
     * Copy with [bindingId]'s enabled flag set. Unknown ids (and no-op
     * flips) return `this` unchanged — callers treat identity as "nothing
     * to write".
     */
    fun withBindingEnabled(bindingId: String, enabled: Boolean): PlayerInputMap {
        if (bindings.none { it.id == bindingId }) return this
        var changed = false
        val newBindings = bindings.map { binding ->
            if (binding.id == bindingId) {
                if (binding.enabled != enabled) changed = true
                binding.copy(enabled = enabled)
            } else {
                binding
            }
        }
        return if (changed) PlayerInputMap(newBindings) else this
    }

    /**
     * Copy with [bindingId] rebound to [action]. A rebound row wakes up
     * (enabled = true); an [PlayerAction.NONE] rebind keeps the row's flag
     * (explicit unbind, not a disable). Unknown ids and no-op rebinds return
     * `this` unchanged, matching [withBindingEnabled].
     */
    fun withBindingAction(bindingId: String, action: PlayerAction): PlayerInputMap {
        if (bindings.none { it.id == bindingId }) return this
        var changed = false
        val newBindings = bindings.map { binding ->
            if (binding.id == bindingId && binding.action != action) {
                changed = true
                binding.copy(
                    action = action,
                    enabled = if (action == PlayerAction.NONE) binding.enabled else true,
                )
            } else {
                binding
            }
        }
        return if (changed) PlayerInputMap(newBindings) else this
    }
}
