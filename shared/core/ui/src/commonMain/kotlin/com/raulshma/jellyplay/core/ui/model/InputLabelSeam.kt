package com.raulshma.jellyplay.core.ui.model

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.DpadControl
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerInputKey
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.SwipeEdge
import com.raulshma.jellyplay.core.model.SwipeSide
import com.raulshma.jellyplay.core.model.TouchZone
import com.raulshma.jellyplay.core.model.WheelAxis
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_audio_delay_decrease
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_audio_delay_increase
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_back_or_hide
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_brightness_down
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_brightness_up
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_cycle_speed
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_exit_player
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_hold_speed
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_lock_controls
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_next_episode
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_none
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_pause
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_play
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_previous_episode
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_screenshot
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_seek_back
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_seek_forward
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_short_brightness
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_short_volume
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_sub_delay_decrease
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_sub_delay_increase
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_swipe_brightness
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_swipe_seek
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_swipe_volume
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_toggle_controls
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_toggle_mute
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_toggle_orientation
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_toggle_play_pause
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_toggle_subtitles
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_volume_down
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_volume_up
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_action_zoom
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_dpad_back
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_dpad_down
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_dpad_fast_forward
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_dpad_left
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_dpad_play_pause
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_dpad_rewind
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_dpad_right
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_dpad_select
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_dpad_up
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_edge_left
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_edge_right
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_alt
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_back
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_bracket_left
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_bracket_right
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_ctrl
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_dpad_down
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_dpad_left
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_dpad_right
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_dpad_up
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_end
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_escape
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_f
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_f1
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_f2
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_f3
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_f4
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_g
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_h
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_home
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_j
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_k
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_l
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_m
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_media_ff
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_media_pause
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_media_play
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_media_play_pause
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_media_rew
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_page_down
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_page_up
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_shift
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_space
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_v
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_volume_down
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_volume_up
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_double_tap_center
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_double_tap_hold_left
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_double_tap_hold_right
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_double_tap_left
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_double_tap_right
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_horizontal_swipe
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_long_press
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_pinch
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_tap
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_vertical_swipe_left
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_vertical_swipe_right
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_wheel_seek
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_pattern_wheel_volume
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Localizable display labels for the input-mapping vocabulary
 * ([PlayerAction], [PlayerInputKey], [DpadControl], and the composed
 * [InputPattern] row labels) — the [PreferenceEnumNames] seam's twin for the
 * player input mapping (issue #171 generalized). The model enums carry no
 * resource access; the binding editor and the in-player quick-toggle sheet
 * render every row through these handles.
 */

// region PlayerAction

/** The localized label resource for this player action. */
fun PlayerAction.labelResource(): StringResource = when (this) {
    PlayerAction.TOGGLE_PLAY_PAUSE -> Res.string.core_input_action_toggle_play_pause
    PlayerAction.PLAY -> Res.string.core_input_action_play
    PlayerAction.PAUSE -> Res.string.core_input_action_pause
    PlayerAction.SEEK_FORWARD -> Res.string.core_input_action_seek_forward
    PlayerAction.SEEK_BACK -> Res.string.core_input_action_seek_back
    PlayerAction.TOGGLE_CONTROLS -> Res.string.core_input_action_toggle_controls
    PlayerAction.BACK_OR_HIDE -> Res.string.core_input_action_back_or_hide
    PlayerAction.EXIT_PLAYER -> Res.string.core_input_action_exit_player
    PlayerAction.VOLUME_UP -> Res.string.core_input_action_volume_up
    PlayerAction.VOLUME_DOWN -> Res.string.core_input_action_volume_down
    PlayerAction.TOGGLE_MUTE -> Res.string.core_input_action_toggle_mute
    PlayerAction.BRIGHTNESS_UP -> Res.string.core_input_action_brightness_up
    PlayerAction.BRIGHTNESS_DOWN -> Res.string.core_input_action_brightness_down
    PlayerAction.SWIPE_SEEK -> Res.string.core_input_action_swipe_seek
    PlayerAction.SWIPE_VOLUME -> Res.string.core_input_action_swipe_volume
    PlayerAction.SWIPE_BRIGHTNESS -> Res.string.core_input_action_swipe_brightness
    PlayerAction.TOGGLE_SUBTITLES -> Res.string.core_input_action_toggle_subtitles
    PlayerAction.SUBTITLE_DELAY_DECREASE -> Res.string.core_input_action_sub_delay_decrease
    PlayerAction.SUBTITLE_DELAY_INCREASE -> Res.string.core_input_action_sub_delay_increase
    PlayerAction.AUDIO_DELAY_DECREASE -> Res.string.core_input_action_audio_delay_decrease
    PlayerAction.AUDIO_DELAY_INCREASE -> Res.string.core_input_action_audio_delay_increase
    PlayerAction.CYCLE_SPEED -> Res.string.core_input_action_cycle_speed
    PlayerAction.HOLD_SPEED -> Res.string.core_input_action_hold_speed
    PlayerAction.ZOOM -> Res.string.core_input_action_zoom
    PlayerAction.TOGGLE_ORIENTATION -> Res.string.core_input_action_toggle_orientation
    PlayerAction.LOCK_CONTROLS -> Res.string.core_input_action_lock_controls
    PlayerAction.SCREENSHOT -> Res.string.core_input_action_screenshot
    PlayerAction.NEXT_EPISODE -> Res.string.core_input_action_next_episode
    PlayerAction.PREVIOUS_EPISODE -> Res.string.core_input_action_previous_episode
    PlayerAction.NONE -> Res.string.core_input_action_none
}

/** Localized display name for this player action. */
@Composable
fun PlayerAction.localizedDisplayName(): String = stringResource(labelResource())

/**
 * The short lowercase word the vertical-swipe row labels parenthesize
 * ("Swipe left side up/down (…)"). Only the continuous drag actions have one
 * — any other action bound to a swipe half renders through the full
 * [PlayerAction.localizedDisplayName] instead.
 */
fun PlayerAction.shortLabelResource(): StringResource? = when (this) {
    PlayerAction.SWIPE_BRIGHTNESS -> Res.string.core_input_action_short_brightness
    PlayerAction.SWIPE_VOLUME -> Res.string.core_input_action_short_volume
    else -> null
}

// endregion

// region PlayerInputKey / DpadControl

/** The localized label resource for this catalog key. */
fun PlayerInputKey.labelResource(): StringResource = when (this) {
    PlayerInputKey.SPACE -> Res.string.core_input_key_space
    PlayerInputKey.K -> Res.string.core_input_key_k
    PlayerInputKey.M -> Res.string.core_input_key_m
    PlayerInputKey.V -> Res.string.core_input_key_v
    PlayerInputKey.F -> Res.string.core_input_key_f
    PlayerInputKey.G -> Res.string.core_input_key_g
    PlayerInputKey.H -> Res.string.core_input_key_h
    PlayerInputKey.J -> Res.string.core_input_key_j
    PlayerInputKey.L -> Res.string.core_input_key_l
    PlayerInputKey.BRACKET_LEFT -> Res.string.core_input_key_bracket_left
    PlayerInputKey.BRACKET_RIGHT -> Res.string.core_input_key_bracket_right
    PlayerInputKey.F1 -> Res.string.core_input_key_f1
    PlayerInputKey.F2 -> Res.string.core_input_key_f2
    PlayerInputKey.F3 -> Res.string.core_input_key_f3
    PlayerInputKey.F4 -> Res.string.core_input_key_f4
    PlayerInputKey.ESCAPE -> Res.string.core_input_key_escape
    PlayerInputKey.BACK -> Res.string.core_input_key_back
    PlayerInputKey.DPAD_LEFT -> Res.string.core_input_key_dpad_left
    PlayerInputKey.DPAD_RIGHT -> Res.string.core_input_key_dpad_right
    PlayerInputKey.DPAD_UP -> Res.string.core_input_key_dpad_up
    PlayerInputKey.DPAD_DOWN -> Res.string.core_input_key_dpad_down
    PlayerInputKey.VOLUME_UP -> Res.string.core_input_key_volume_up
    PlayerInputKey.VOLUME_DOWN -> Res.string.core_input_key_volume_down
    PlayerInputKey.PAGE_UP -> Res.string.core_input_key_page_up
    PlayerInputKey.PAGE_DOWN -> Res.string.core_input_key_page_down
    PlayerInputKey.MOVE_HOME -> Res.string.core_input_key_home
    PlayerInputKey.MOVE_END -> Res.string.core_input_key_end
    PlayerInputKey.MEDIA_PLAY -> Res.string.core_input_key_media_play
    PlayerInputKey.MEDIA_PAUSE -> Res.string.core_input_key_media_pause
    PlayerInputKey.MEDIA_PLAY_PAUSE -> Res.string.core_input_key_media_play_pause
    PlayerInputKey.MEDIA_FAST_FORWARD -> Res.string.core_input_key_media_ff
    PlayerInputKey.MEDIA_REWIND -> Res.string.core_input_key_media_rew
}

/** Localized display name for this catalog key. */
@Composable
fun PlayerInputKey.localizedDisplayName(): String = stringResource(labelResource())

/** The localized label resource for this D-pad control. */
fun DpadControl.labelResource(): StringResource = when (this) {
    DpadControl.LEFT -> Res.string.core_input_dpad_left
    DpadControl.RIGHT -> Res.string.core_input_dpad_right
    DpadControl.UP -> Res.string.core_input_dpad_up
    DpadControl.DOWN -> Res.string.core_input_dpad_down
    DpadControl.SELECT -> Res.string.core_input_dpad_select
    DpadControl.BACK -> Res.string.core_input_dpad_back
    DpadControl.PLAY_PAUSE -> Res.string.core_input_dpad_play_pause
    DpadControl.FAST_FORWARD -> Res.string.core_input_dpad_fast_forward
    DpadControl.REWIND -> Res.string.core_input_dpad_rewind
}

/** Localized display name for this D-pad control. */
@Composable
fun DpadControl.localizedDisplayName(): String = stringResource(labelResource())

// endregion

// region InputPattern row labels

/**
 * The localized row label for a bindable input pattern — the editor's row
 * title. Composed patterns (double-tap zones, swipe halves, wheel rows)
 * resolve to their concrete row label; the keyboard pattern resolves to the
 * bare key label (the editor composes modifiers at display time via
 * [localizedDisplayName]).
 */
fun InputPattern.labelResource(): StringResource = when (this) {
    InputPattern.Tap -> Res.string.core_input_pattern_tap
    is InputPattern.DoubleTap -> when (zone) {
        TouchZone.LEFT -> Res.string.core_input_pattern_double_tap_left
        TouchZone.CENTER -> Res.string.core_input_pattern_double_tap_center
        TouchZone.RIGHT -> Res.string.core_input_pattern_double_tap_right
    }
    is InputPattern.DoubleTapHold -> when (zone) {
        TouchZone.LEFT -> Res.string.core_input_pattern_double_tap_hold_left
        TouchZone.RIGHT -> Res.string.core_input_pattern_double_tap_hold_right
        TouchZone.CENTER -> Res.string.core_input_pattern_double_tap_hold_left
    }
    InputPattern.LongPress -> Res.string.core_input_pattern_long_press
    is InputPattern.VerticalSwipe -> when (side) {
        SwipeSide.LEFT -> Res.string.core_input_pattern_vertical_swipe_left
        SwipeSide.RIGHT -> Res.string.core_input_pattern_vertical_swipe_right
    }
    InputPattern.HorizontalSwipe -> Res.string.core_input_pattern_horizontal_swipe
    is InputPattern.EdgeSwipe -> when (edge) {
        SwipeEdge.LEFT -> Res.string.core_input_edge_left
        SwipeEdge.RIGHT -> Res.string.core_input_edge_right
    }
    InputPattern.Pinch -> Res.string.core_input_pattern_pinch
    is InputPattern.Wheel ->
        if (axis == WheelAxis.HORIZONTAL || shift) {
            Res.string.core_input_pattern_wheel_seek
        } else {
            Res.string.core_input_pattern_wheel_volume
        }
    is InputPattern.Key -> key.labelResource()
    is InputPattern.DPad -> control.labelResource()
}

/**
 * Localized display label for this bindable input pattern (modifiers composed).
 * The vertical-swipe halves fill their placeholder with the side's DEFAULT
 * action word — binding-context-free; rows that must reflect the persisted
 * map render through [verticalSwipeLabel] instead.
 */
@Composable
fun InputPattern.localizedDisplayName(): String = when (this) {
    is InputPattern.Key -> {
        val modifiers = listOf(
            ctrl to Res.string.core_input_key_ctrl,
            shift to Res.string.core_input_key_shift,
            alt to Res.string.core_input_key_alt,
        ).filter { it.first }.map { stringResource(it.second) }
        (modifiers + key.localizedDisplayName()).joinToString(separator = " + ")
    }
    is InputPattern.VerticalSwipe -> verticalSwipeLabel(side, side.defaultVerticalAction())
    else -> stringResource(labelResource())
}

/**
 * The side's factory pairing — LEFT = brightness drag, RIGHT = volume drag.
 * The fallback whenever no binding-context action exists (a pattern shown
 * without a row) or the persisted map lacks the row (hand-edited blob).
 */
fun SwipeSide.defaultVerticalAction(): PlayerAction = when (this) {
    SwipeSide.LEFT -> PlayerAction.SWIPE_BRIGHTNESS
    SwipeSide.RIGHT -> PlayerAction.SWIPE_VOLUME
}

/**
 * The full vertical-swipe row label for a side plus the action ACTUALLY bound
 * to it — the side→action pairing lives in the persisted input map, so the
 * label must follow it ("Swipe left side up/down (volume)" after a swap).
 * Continuous drag actions render their short word; anything else (a discrete
 * rebind, NONE) falls back to the action's full display name.
 */
@Composable
fun verticalSwipeLabel(side: SwipeSide, action: PlayerAction): String {
    val actionText = action.shortLabelResource()?.let { stringResource(it) }
        ?: action.localizedDisplayName()
    return stringResource(
        when (side) {
            SwipeSide.LEFT -> Res.string.core_input_pattern_vertical_swipe_left
            SwipeSide.RIGHT -> Res.string.core_input_pattern_vertical_swipe_right
        },
        actionText,
    )
}

// endregion

// region Quick-toggle labels

/**
 * The player-overflow quick-toggle labels (issue #171), resolved here
 * in core:ui — the seam module owns the resource resolution, the player
 * feature only reads strings. Field order matches the menu's row order;
 * the two vertical-swipe fields are SIDE-keyed (left/right), their text
 * composed with the action bound in [map] — see [verticalSwipeLabel].
 */
data class InputQuickToggleLabels(
    val leftSwipe: String,
    val rightSwipe: String,
    val seekSwipe: String,
    val edgeSwipeLeft: String,
    val edgeSwipeRight: String,
    val pinch: String,
)

/**
 * Resolves the quick-toggle label set ([InputQuickToggleLabels]) against the
 * CURRENT persisted [map] — the swipe-half labels re-resolve when the sides
 * are swapped in the binding editor.
 */
@Composable
fun inputQuickToggleLabels(map: PlayerInputMap): InputQuickToggleLabels {
    fun boundAction(side: SwipeSide): PlayerAction =
        map.bindings.firstOrNull { it.pattern == InputPattern.VerticalSwipe(side) }?.action
            ?: side.defaultVerticalAction()
    return InputQuickToggleLabels(
        leftSwipe = verticalSwipeLabel(SwipeSide.LEFT, boundAction(SwipeSide.LEFT)),
        rightSwipe = verticalSwipeLabel(SwipeSide.RIGHT, boundAction(SwipeSide.RIGHT)),
        seekSwipe = stringResource(InputPattern.HorizontalSwipe.labelResource()),
        edgeSwipeLeft = stringResource(InputPattern.EdgeSwipe(SwipeEdge.LEFT).labelResource()),
        edgeSwipeRight = stringResource(InputPattern.EdgeSwipe(SwipeEdge.RIGHT).labelResource()),
        pinch = stringResource(InputPattern.Pinch.labelResource()),
    )
}
