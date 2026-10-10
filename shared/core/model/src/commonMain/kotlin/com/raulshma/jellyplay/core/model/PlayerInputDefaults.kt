package com.raulshma.jellyplay.core.model

/**
 * The default input mapping and the [GestureMode] preset engine.
 *
 * Lives in `core:model` (not the player feature) because the datastore's
 * `input_bindings` row derives its absent-blob fallback from it — the same
 * reason [SegmentBehavior.DEFAULT_BEHAVIORS] lives here. Pure data + pure
 * functions; the resolver that consults a map at input time is the
 * player-video module's `PlayerInputPolicy`.
 *
 * The default rows reproduce the pre-mapping behavior EXACTLY — they are the
 * legacy decision tables (`mediaKeyAction`/`mediaKeySeek`, the D-pad branch,
 * `PlayerWheelPolicy`, the swipe arms, the tap zones) expressed as bindings.
 * `PlayerInputDefaultsParityTest` pins that reproduction.
 */
object PlayerInputDefaults {

    // ── Stable binding ids ──────────────────────────────────────────────
    // The editor's reorder/reset handles and the preset engine key off these;
    // ids are wire-stable (they ride inside the persisted JSON blob).

    const val ID_TAP = "touch.tap"
    const val ID_DOUBLE_TAP_LEFT = "touch.double_tap_left"
    const val ID_DOUBLE_TAP_CENTER = "touch.double_tap_center"
    const val ID_DOUBLE_TAP_RIGHT = "touch.double_tap_right"
    const val ID_DOUBLE_TAP_HOLD_LEFT = "touch.double_tap_hold_left"
    const val ID_DOUBLE_TAP_HOLD_RIGHT = "touch.double_tap_hold_right"
    const val ID_LONG_PRESS = "touch.long_press"
    const val ID_SWIPE_BRIGHTNESS = "touch.swipe_brightness"
    const val ID_SWIPE_VOLUME = "touch.swipe_volume"
    const val ID_SWIPE_SEEK = "touch.swipe_seek"
    const val ID_EDGE_SWIPE_LEFT = "touch.edge_swipe_left"
    const val ID_EDGE_SWIPE_RIGHT = "touch.edge_swipe_right"
    const val ID_PINCH = "touch.pinch"

    const val ID_WHEEL_VOLUME = "wheel.volume"
    const val ID_WHEEL_SEEK = "wheel.seek"
    const val ID_WHEEL_SEEK_SHIFT = "wheel.seek_shift"

    const val ID_DPAD_LEFT = "dpad.left"
    const val ID_DPAD_RIGHT = "dpad.right"
    const val ID_DPAD_UP = "dpad.up"
    const val ID_DPAD_DOWN = "dpad.down"
    const val ID_DPAD_SELECT = "dpad.select"
    const val ID_DPAD_BACK = "dpad.back"
    const val ID_DPAD_PLAY_PAUSE = "dpad.play_pause"
    const val ID_DPAD_FAST_FORWARD = "dpad.fast_forward"
    const val ID_DPAD_REWIND = "dpad.rewind"

    const val ID_KEY_SPACE = "key.space"
    const val ID_KEY_K = "key.k"
    const val ID_KEY_MEDIA_PLAY = "key.media_play"
    const val ID_KEY_MEDIA_PAUSE = "key.media_pause"
    const val ID_KEY_MEDIA_PLAY_PAUSE = "key.media_play_pause"
    const val ID_KEY_DPAD_UP = "key.dpad_up"
    const val ID_KEY_VOLUME_UP = "key.volume_up"
    const val ID_KEY_DPAD_DOWN = "key.dpad_down"
    const val ID_KEY_VOLUME_DOWN = "key.volume_down"
    const val ID_KEY_F = "key.f"
    const val ID_KEY_F1 = "key.f1"
    const val ID_KEY_F2 = "key.f2"
    const val ID_KEY_F3 = "key.f3"
    const val ID_KEY_F4 = "key.f4"
    const val ID_KEY_M = "key.m"
    const val ID_KEY_V = "key.v"
    const val ID_KEY_BRACKET_LEFT = "key.bracket_left"
    const val ID_KEY_BRACKET_RIGHT = "key.bracket_right"
    const val ID_KEY_CTRL_BRACKET_LEFT = "key.ctrl_bracket_left"
    const val ID_KEY_CTRL_BRACKET_RIGHT = "key.ctrl_bracket_right"
    const val ID_KEY_CTRL_SHIFT_BRACKET_LEFT = "key.ctrl_shift_bracket_left"
    const val ID_KEY_CTRL_SHIFT_BRACKET_RIGHT = "key.ctrl_shift_bracket_right"
    const val ID_KEY_CTRL_ALT_BRACKET_LEFT = "key.ctrl_alt_bracket_left"
    const val ID_KEY_CTRL_ALT_BRACKET_RIGHT = "key.ctrl_alt_bracket_right"
    const val ID_KEY_CTRL_SHIFT_ALT_BRACKET_LEFT = "key.ctrl_shift_alt_bracket_left"
    const val ID_KEY_CTRL_SHIFT_ALT_BRACKET_RIGHT = "key.ctrl_shift_alt_bracket_right"
    const val ID_KEY_G = "key.g"
    const val ID_KEY_H = "key.h"
    const val ID_KEY_DPAD_LEFT = "key.dpad_left"
    const val ID_KEY_DPAD_RIGHT = "key.dpad_right"
    const val ID_KEY_J = "key.j"
    const val ID_KEY_L = "key.l"
    const val ID_KEY_MEDIA_RW = "key.media_rw"
    const val ID_KEY_MEDIA_FF = "key.media_ff"
    const val ID_KEY_PAGE_UP = "key.page_up"
    const val ID_KEY_PAGE_DOWN = "key.page_down"
    const val ID_KEY_MOVE_HOME = "key.move_home"
    const val ID_KEY_MOVE_END = "key.move_end"
    const val ID_KEY_ESCAPE = "key.escape"
    const val ID_KEY_BACK = "key.back"

    // ── Family classification (the preset engine's switches) ────────────

    /** The touch surface: everything the on-screen detectors can fire. */
    fun isTouchPattern(pattern: InputPattern): Boolean = when (pattern) {
        is InputPattern.Tap,
        is InputPattern.DoubleTap,
        is InputPattern.DoubleTapHold,
        is InputPattern.LongPress,
        is InputPattern.VerticalSwipe,
        is InputPattern.HorizontalSwipe,
        is InputPattern.EdgeSwipe,
        is InputPattern.Pinch,
        -> true

        is InputPattern.Wheel, is InputPattern.Key, is InputPattern.DPad -> false
    }

    /**
     * The single-finger swipe tier: the four behaviors `GestureMode`'s
     * `swipesEnabled` used to gate as one lump (swipe seek / brightness /
     * volume / edge swipe). [GestureMode.TAP_ONLY] disables exactly these.
     */
    fun isSwipeTierPattern(pattern: InputPattern): Boolean = when (pattern) {
        is InputPattern.VerticalSwipe,
        is InputPattern.HorizontalSwipe,
        is InputPattern.EdgeSwipe,
        -> true

        else -> false
    }

    // ── Default rows ────────────────────────────────────────────────────

    private fun touchRows(gestureMode: GestureMode, holdSpeedEnabled: Boolean, doubleTapHoldSeekEnabled: Boolean): List<PlayerBinding> {
        val taps = gestureMode.tapsEnabled
        val swipes = gestureMode.swipesEnabled
        return listOf(
            PlayerBinding(ID_TAP, InputPattern.Tap, PlayerAction.TOGGLE_CONTROLS, enabled = taps),
            PlayerBinding(ID_DOUBLE_TAP_LEFT, InputPattern.DoubleTap(TouchZone.LEFT), PlayerAction.SEEK_BACK, enabled = taps),
            PlayerBinding(
                ID_DOUBLE_TAP_CENTER,
                InputPattern.DoubleTap(TouchZone.CENTER),
                PlayerAction.TOGGLE_PLAY_PAUSE,
                enabled = taps,
            ),
            PlayerBinding(ID_DOUBLE_TAP_RIGHT, InputPattern.DoubleTap(TouchZone.RIGHT), PlayerAction.SEEK_FORWARD, enabled = taps),
            PlayerBinding(
                ID_DOUBLE_TAP_HOLD_LEFT,
                InputPattern.DoubleTapHold(TouchZone.LEFT),
                PlayerAction.SEEK_BACK,
                enabled = taps && doubleTapHoldSeekEnabled,
            ),
            PlayerBinding(
                ID_DOUBLE_TAP_HOLD_RIGHT,
                InputPattern.DoubleTapHold(TouchZone.RIGHT),
                PlayerAction.SEEK_FORWARD,
                enabled = taps && doubleTapHoldSeekEnabled,
            ),
            PlayerBinding(ID_LONG_PRESS, InputPattern.LongPress, PlayerAction.HOLD_SPEED, enabled = taps && holdSpeedEnabled),
            PlayerBinding(ID_SWIPE_BRIGHTNESS, InputPattern.VerticalSwipe(SwipeSide.LEFT), PlayerAction.SWIPE_BRIGHTNESS, enabled = swipes),
            PlayerBinding(ID_SWIPE_VOLUME, InputPattern.VerticalSwipe(SwipeSide.RIGHT), PlayerAction.SWIPE_VOLUME, enabled = swipes),
            PlayerBinding(ID_SWIPE_SEEK, InputPattern.HorizontalSwipe, PlayerAction.SWIPE_SEEK, enabled = swipes),
            PlayerBinding(ID_EDGE_SWIPE_LEFT, InputPattern.EdgeSwipe(SwipeEdge.LEFT), PlayerAction.BACK_OR_HIDE, enabled = swipes),
            PlayerBinding(ID_EDGE_SWIPE_RIGHT, InputPattern.EdgeSwipe(SwipeEdge.RIGHT), PlayerAction.BACK_OR_HIDE, enabled = swipes),
            PlayerBinding(ID_PINCH, InputPattern.Pinch, PlayerAction.ZOOM, enabled = taps),
        )
    }

    private fun wheelRows(): List<PlayerBinding> = listOf(
        // The wheel-volume notch: direction comes from the scroll delta, so the
        // row binds the directional SWIPE_VOLUME family (the wheel site maps it
        // to notched volume steps — PlayerWheelPolicy's calibration).
        PlayerBinding(ID_WHEEL_VOLUME, InputPattern.Wheel(WheelAxis.VERTICAL), PlayerAction.SWIPE_VOLUME),
        // Shift+wheel / horizontally-dominant delta: the seek-notch rows. The
        // vertical+Shift row is its own default (the retired PlayerWheelPolicy
        // seeked on Shift regardless of axis); the horizontally-dominant case
        // folds into the HORIZONTAL row.
        PlayerBinding(ID_WHEEL_SEEK, InputPattern.Wheel(WheelAxis.HORIZONTAL), PlayerAction.SWIPE_SEEK),
        PlayerBinding(ID_WHEEL_SEEK_SHIFT, InputPattern.Wheel(WheelAxis.VERTICAL, shift = true), PlayerAction.SWIPE_SEEK),
    )

    private fun dpadRows(): List<PlayerBinding> = listOf(
        PlayerBinding(ID_DPAD_LEFT, InputPattern.DPad(DpadControl.LEFT), PlayerAction.SEEK_BACK),
        PlayerBinding(ID_DPAD_RIGHT, InputPattern.DPad(DpadControl.RIGHT), PlayerAction.SEEK_FORWARD),
        PlayerBinding(ID_DPAD_UP, InputPattern.DPad(DpadControl.UP), PlayerAction.TOGGLE_CONTROLS),
        PlayerBinding(ID_DPAD_DOWN, InputPattern.DPad(DpadControl.DOWN), PlayerAction.TOGGLE_CONTROLS),
        PlayerBinding(ID_DPAD_SELECT, InputPattern.DPad(DpadControl.SELECT), PlayerAction.TOGGLE_CONTROLS),
        PlayerBinding(ID_DPAD_BACK, InputPattern.DPad(DpadControl.BACK), PlayerAction.BACK_OR_HIDE),
        PlayerBinding(ID_DPAD_PLAY_PAUSE, InputPattern.DPad(DpadControl.PLAY_PAUSE), PlayerAction.TOGGLE_PLAY_PAUSE),
        PlayerBinding(ID_DPAD_FAST_FORWARD, InputPattern.DPad(DpadControl.FAST_FORWARD), PlayerAction.SEEK_FORWARD),
        PlayerBinding(ID_DPAD_REWIND, InputPattern.DPad(DpadControl.REWIND), PlayerAction.SEEK_BACK),
    )

    /**
     * The keyboard rows — the retired `mediaKeyAction`/`mediaKeySeek` tables
     * verbatim. Seek-family keys carry ONE canonical (modifier-less) row: the
     * modifier steps (Shift/Ctrl/Alt over the configured jump) are event-time
     * semantics the seek site folds in, not separate bindings.
     */
    private fun keyRows(): List<PlayerBinding> = listOf(
        // Transport
        PlayerBinding(ID_KEY_SPACE, InputPattern.Key(PlayerInputKey.SPACE), PlayerAction.TOGGLE_PLAY_PAUSE),
        PlayerBinding(ID_KEY_K, InputPattern.Key(PlayerInputKey.K), PlayerAction.TOGGLE_PLAY_PAUSE),
        PlayerBinding(ID_KEY_MEDIA_PLAY, InputPattern.Key(PlayerInputKey.MEDIA_PLAY), PlayerAction.TOGGLE_PLAY_PAUSE),
        PlayerBinding(ID_KEY_MEDIA_PAUSE, InputPattern.Key(PlayerInputKey.MEDIA_PAUSE), PlayerAction.TOGGLE_PLAY_PAUSE),
        PlayerBinding(ID_KEY_MEDIA_PLAY_PAUSE, InputPattern.Key(PlayerInputKey.MEDIA_PLAY_PAUSE), PlayerAction.TOGGLE_PLAY_PAUSE),
        // Volume (arrows on the keyboard branch are the volume keys)
        PlayerBinding(ID_KEY_DPAD_UP, InputPattern.Key(PlayerInputKey.DPAD_UP), PlayerAction.VOLUME_UP),
        PlayerBinding(ID_KEY_VOLUME_UP, InputPattern.Key(PlayerInputKey.VOLUME_UP), PlayerAction.VOLUME_UP),
        PlayerBinding(ID_KEY_DPAD_DOWN, InputPattern.Key(PlayerInputKey.DPAD_DOWN), PlayerAction.VOLUME_DOWN),
        PlayerBinding(ID_KEY_VOLUME_DOWN, InputPattern.Key(PlayerInputKey.VOLUME_DOWN), PlayerAction.VOLUME_DOWN),
        // Orientation
        PlayerBinding(ID_KEY_F, InputPattern.Key(PlayerInputKey.F), PlayerAction.TOGGLE_ORIENTATION),
        PlayerBinding(ID_KEY_F1, InputPattern.Key(PlayerInputKey.F1), PlayerAction.TOGGLE_ORIENTATION),
        PlayerBinding(ID_KEY_F2, InputPattern.Key(PlayerInputKey.F2), PlayerAction.TOGGLE_ORIENTATION),
        PlayerBinding(ID_KEY_F3, InputPattern.Key(PlayerInputKey.F3), PlayerAction.TOGGLE_ORIENTATION),
        PlayerBinding(ID_KEY_F4, InputPattern.Key(PlayerInputKey.F4), PlayerAction.TOGGLE_ORIENTATION),
        // Mute / subtitles
        PlayerBinding(ID_KEY_M, InputPattern.Key(PlayerInputKey.M), PlayerAction.TOGGLE_MUTE),
        PlayerBinding(ID_KEY_V, InputPattern.Key(PlayerInputKey.V), PlayerAction.TOGGLE_SUBTITLES),
        // Subtitle delay (plain brackets + G/H) and audio delay (Ctrl+brackets).
        // The retired table folded ANY Ctrl+bracket press — Shift/Alt held
        // alongside made no difference — so every Ctrl combo carries its own
        // row: the exact-modifier candidate must hit the audio-delay row, not
        // fall through the ladder to the plain subtitle row.
        PlayerBinding(ID_KEY_BRACKET_LEFT, InputPattern.Key(PlayerInputKey.BRACKET_LEFT), PlayerAction.SUBTITLE_DELAY_DECREASE),
        PlayerBinding(ID_KEY_BRACKET_RIGHT, InputPattern.Key(PlayerInputKey.BRACKET_RIGHT), PlayerAction.SUBTITLE_DELAY_INCREASE),
        PlayerBinding(
            ID_KEY_CTRL_BRACKET_LEFT,
            InputPattern.Key(PlayerInputKey.BRACKET_LEFT, ctrl = true),
            PlayerAction.AUDIO_DELAY_DECREASE,
        ),
        PlayerBinding(
            ID_KEY_CTRL_BRACKET_RIGHT,
            InputPattern.Key(PlayerInputKey.BRACKET_RIGHT, ctrl = true),
            PlayerAction.AUDIO_DELAY_INCREASE,
        ),
        PlayerBinding(
            ID_KEY_CTRL_SHIFT_BRACKET_LEFT,
            InputPattern.Key(PlayerInputKey.BRACKET_LEFT, ctrl = true, shift = true),
            PlayerAction.AUDIO_DELAY_DECREASE,
        ),
        PlayerBinding(
            ID_KEY_CTRL_SHIFT_BRACKET_RIGHT,
            InputPattern.Key(PlayerInputKey.BRACKET_RIGHT, ctrl = true, shift = true),
            PlayerAction.AUDIO_DELAY_INCREASE,
        ),
        PlayerBinding(
            ID_KEY_CTRL_ALT_BRACKET_LEFT,
            InputPattern.Key(PlayerInputKey.BRACKET_LEFT, ctrl = true, alt = true),
            PlayerAction.AUDIO_DELAY_DECREASE,
        ),
        PlayerBinding(
            ID_KEY_CTRL_ALT_BRACKET_RIGHT,
            InputPattern.Key(PlayerInputKey.BRACKET_RIGHT, ctrl = true, alt = true),
            PlayerAction.AUDIO_DELAY_INCREASE,
        ),
        PlayerBinding(
            ID_KEY_CTRL_SHIFT_ALT_BRACKET_LEFT,
            InputPattern.Key(PlayerInputKey.BRACKET_LEFT, ctrl = true, shift = true, alt = true),
            PlayerAction.AUDIO_DELAY_DECREASE,
        ),
        PlayerBinding(
            ID_KEY_CTRL_SHIFT_ALT_BRACKET_RIGHT,
            InputPattern.Key(PlayerInputKey.BRACKET_RIGHT, ctrl = true, shift = true, alt = true),
            PlayerAction.AUDIO_DELAY_INCREASE,
        ),
        PlayerBinding(ID_KEY_G, InputPattern.Key(PlayerInputKey.G), PlayerAction.SUBTITLE_DELAY_DECREASE),
        PlayerBinding(ID_KEY_H, InputPattern.Key(PlayerInputKey.H), PlayerAction.SUBTITLE_DELAY_INCREASE),
        // Seek family (canonical rows; modifier steps are event-time)
        PlayerBinding(ID_KEY_DPAD_LEFT, InputPattern.Key(PlayerInputKey.DPAD_LEFT), PlayerAction.SEEK_BACK),
        PlayerBinding(ID_KEY_DPAD_RIGHT, InputPattern.Key(PlayerInputKey.DPAD_RIGHT), PlayerAction.SEEK_FORWARD),
        PlayerBinding(ID_KEY_J, InputPattern.Key(PlayerInputKey.J), PlayerAction.SEEK_BACK),
        PlayerBinding(ID_KEY_L, InputPattern.Key(PlayerInputKey.L), PlayerAction.SEEK_FORWARD),
        PlayerBinding(ID_KEY_MEDIA_RW, InputPattern.Key(PlayerInputKey.MEDIA_REWIND), PlayerAction.SEEK_BACK),
        PlayerBinding(ID_KEY_MEDIA_FF, InputPattern.Key(PlayerInputKey.MEDIA_FAST_FORWARD), PlayerAction.SEEK_FORWARD),
        PlayerBinding(ID_KEY_PAGE_UP, InputPattern.Key(PlayerInputKey.PAGE_UP), PlayerAction.SEEK_FORWARD),
        PlayerBinding(ID_KEY_PAGE_DOWN, InputPattern.Key(PlayerInputKey.PAGE_DOWN), PlayerAction.SEEK_BACK),
        PlayerBinding(ID_KEY_MOVE_HOME, InputPattern.Key(PlayerInputKey.MOVE_HOME), PlayerAction.SEEK_BACK),
        PlayerBinding(ID_KEY_MOVE_END, InputPattern.Key(PlayerInputKey.MOVE_END), PlayerAction.SEEK_FORWARD),
        // Esc / Back
        PlayerBinding(ID_KEY_ESCAPE, InputPattern.Key(PlayerInputKey.ESCAPE), PlayerAction.BACK_OR_HIDE),
        PlayerBinding(ID_KEY_BACK, InputPattern.Key(PlayerInputKey.BACK), PlayerAction.BACK_OR_HIDE),
    )

    /**
     * The parameterized default map as an id → row lookup: the binding
     * editor's baseline for "this row differs from what Reset-all would
     * give it" and the per-row swipe-reset target. Parameterized by the
     * SAME stored behavior flags a reset honors, so "unmodified" and "what
     * reset restores" can never disagree. Ids absent here belong to
     * user-captured rows (`PlayerBindingIds.customKeyId`) — those reset by
     * removal, not by lookup.
     */
    fun defaultBindingsById(
        gestureMode: GestureMode = GestureMode.ALL,
        holdSpeedEnabled: Boolean = true,
        doubleTapHoldSeekEnabled: Boolean = true,
    ): Map<String, PlayerBinding> =
        defaultMap(gestureMode, holdSpeedEnabled, doubleTapHoldSeekEnabled)
            .bindings.associateBy { it.id }

    /**
     * The default map for a fresh install: every row the detectors can fire,
     * touch rows gated by [gestureMode] (the user's existing tier choice —
     * the absent-blob legacy fallback seeds from it too).
     */
    fun defaultMap(
        gestureMode: GestureMode = GestureMode.ALL,
        holdSpeedEnabled: Boolean = true,
        doubleTapHoldSeekEnabled: Boolean = true,
    ): PlayerInputMap = PlayerInputMap(
        bindings = touchRows(gestureMode, holdSpeedEnabled, doubleTapHoldSeekEnabled) +
            wheelRows() +
            dpadRows() +
            keyRows(),
    )

    /**
     * Apply a [GestureMode] preset to an existing map by mass-flipping the
     * touch-family enabled flags. This is the demoted preset semantics: the
     * mode row is a convenience switch over the mapping, never a second gate
     * (the detectors consult the map only). Wheel/keyboard/D-pad rows are
     * untouched — [GestureMode] never gated them.
     *
     * The hold-speed and double-tap-hold rows flip with the tap tier like
     * every other touch row: NONE must kill the whole touch surface (the
     * retired tier gate did, and the absent-blob legacy read seeds those rows
     * off the mode too — a preset flip and a fresh read must agree). The two
     * behaviors' own preference rows stay authoritative through the runtime
     * AND gates at their fire sites, so re-enabling a row the user's
     * preference had disabled changes nothing until the preference is on.
     */
    fun applyGestureModePreset(map: PlayerInputMap, mode: GestureMode): PlayerInputMap {
        fun targetEnabled(pattern: InputPattern): Boolean = when {
            isSwipeTierPattern(pattern) -> mode.swipesEnabled
            else -> mode.tapsEnabled
        }
        return map.copy(
            bindings = map.bindings.map { binding ->
                if (isTouchPattern(binding.pattern) &&
                    binding.enabled != targetEnabled(binding.pattern)
                ) {
                    binding.copy(enabled = targetEnabled(binding.pattern))
                } else {
                    binding
                }
            },
        )
    }
}
