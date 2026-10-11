package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins [ensureControlsSummonable] — the read-time repair for the one
 * unrecoverable binding state (issue #171's broken-editor fallout): no
 * summon surface left enabled, so the controls can never be shown and no
 * in-player UI exists to fix the mapping. Any NARROWER disabled set is
 * indistinguishable from intent and must pass through untouched.
 */
class PlayerInputSummonHealTest {

    private fun disabled(vararg ids: String): PlayerInputMap =
        ids.fold(PlayerInputDefaults.defaultMap()) { map, id ->
            map.withBindingEnabled(id, enabled = false)
        }

    @Test
    fun healthy_default_map_is_unchanged() {
        val map = PlayerInputDefaults.defaultMap()
        assertEquals(map, map.ensureControlsSummonable())
    }

    @Test
    fun locked_out_map_restores_the_tap_row() {
        // The broken-editor lockout: every touch summon surface AND every
        // D-pad summon control off — the user can never show controls again.
        val locked = disabled(
            PlayerInputDefaults.ID_TAP,
            PlayerInputDefaults.ID_EDGE_SWIPE_LEFT,
            PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT,
            PlayerInputDefaults.ID_DPAD_UP,
            PlayerInputDefaults.ID_DPAD_DOWN,
            PlayerInputDefaults.ID_DPAD_SELECT,
        )
        val healed = locked.ensureControlsSummonable()
        assertNotEquals(locked, healed)
        val tap = healed.bindings.first { it.pattern == InputPattern.Tap }
        assertTrue(tap.enabled, "tap row must wake up")
        assertEquals(PlayerAction.TOGGLE_CONTROLS, tap.action)
        assertEquals(PlayerInputDefaults.ID_TAP, tap.id, "the existing row keeps its id")
    }

    @Test
    fun tap_disabled_with_a_live_edge_swipe_is_intent_not_a_lockout() {
        // Tap off + edge swipe still bound+enabled (BACK_OR_HIDE summons at
        // the edge site) — a deliberate choice; must pass through untouched.
        val deliberate = disabled(PlayerInputDefaults.ID_TAP)
        assertEquals(deliberate, deliberate.ensureControlsSummonable())
    }

    @Test
    fun touch_all_off_with_dpad_summon_alive_is_intent_not_a_lockout() {
        val tvOnly = disabled(
            PlayerInputDefaults.ID_TAP,
            PlayerInputDefaults.ID_EDGE_SWIPE_LEFT,
            PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT,
        )
        assertEquals(tvOnly, tvOnly.ensureControlsSummonable())
    }

    @Test
    fun rebound_tap_does_not_count_as_a_summon_and_is_repaired() {
        // Tap rebound to play/pause + everything else off: re-ENABLING the
        // row alone would still leave no summon — the heal must restore the
        // TOGGLE_CONTROLS action too.
        val rebound = PlayerInputDefaults.defaultMap()
            .withBindingEnabled(PlayerInputDefaults.ID_TAP, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_EDGE_SWIPE_LEFT, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_DPAD_UP, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_DPAD_DOWN, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_DPAD_SELECT, enabled = false)
            .withBindingAction(PlayerInputDefaults.ID_TAP, PlayerAction.TOGGLE_PLAY_PAUSE)
        val healed = rebound.ensureControlsSummonable()
        val tap = healed.bindings.first { it.pattern == InputPattern.Tap }
        assertEquals(PlayerAction.TOGGLE_CONTROLS, tap.action)
        assertTrue(tap.enabled)
    }

    @Test
    fun none_bound_summon_rows_do_not_count_as_alive() {
        // Explicit Unbound (NONE) on the tap row reads as OFF for the
        // guarantee, even though the row is 'enabled'.
        val unbound = PlayerInputDefaults.defaultMap()
            .withBindingAction(PlayerInputDefaults.ID_TAP, PlayerAction.NONE)
            .withBindingEnabled(PlayerInputDefaults.ID_EDGE_SWIPE_LEFT, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_DPAD_UP, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_DPAD_DOWN, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_DPAD_SELECT, enabled = false)
        val healed = unbound.ensureControlsSummonable()
        assertEquals(PlayerAction.TOGGLE_CONTROLS, healed.bindings.first { it.pattern == InputPattern.Tap }.action)
    }

    @Test
    fun map_without_a_tap_row_gets_one_appended() {
        // Stripped tap row AND every other summon surface disabled: the heal
        // must append the row, not just flip flags.
        val stripped = PlayerInputMap(
            PlayerInputDefaults.defaultMap()
                .bindings
                .filterNot { it.pattern == InputPattern.Tap }
                .map { binding ->
                    if (binding.pattern == InputPattern.EdgeSwipe(SwipeEdge.LEFT) ||
                        binding.pattern == InputPattern.EdgeSwipe(SwipeEdge.RIGHT) ||
                        binding.pattern == InputPattern.DPad(DpadControl.UP) ||
                        binding.pattern == InputPattern.DPad(DpadControl.DOWN) ||
                        binding.pattern == InputPattern.DPad(DpadControl.SELECT)
                    ) {
                        binding.copy(enabled = false)
                    } else {
                        binding
                    }
                },
        )
        val healed = stripped.ensureControlsSummonable()
        val tap = healed.bindings.firstOrNull { it.pattern == InputPattern.Tap }
        assertTrue(tap != null && tap.enabled, "a missing tap row must be appended enabled")
    }

    @Test
    fun heal_is_idempotent() {
        val locked = disabled(
            PlayerInputDefaults.ID_TAP,
            PlayerInputDefaults.ID_EDGE_SWIPE_LEFT,
            PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT,
            PlayerInputDefaults.ID_DPAD_UP,
            PlayerInputDefaults.ID_DPAD_DOWN,
            PlayerInputDefaults.ID_DPAD_SELECT,
        )
        val healed = locked.ensureControlsSummonable()
        assertEquals(healed, healed.ensureControlsSummonable())
    }
}
