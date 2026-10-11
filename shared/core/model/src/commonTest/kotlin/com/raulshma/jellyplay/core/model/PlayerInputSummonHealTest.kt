package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins [ensureControlsSummonable] — the read-time repair for the one
 * unrecoverable binding state (issue #171's broken-editor fallout): NO
 * family (touch / remote) has a summon left enabled, so the controls can
 * never be shown and no in-player UI exists to fix the mapping. Anything
 * narrower — a one-family lockout reachable through the normal UI, or a
 * summon rebound onto a pattern the default map doesn't carry it on — is
 * indistinguishable from intent (or already alive) and must pass through
 * untouched.
 */
class PlayerInputSummonHealTest {

    private fun disabled(vararg ids: String): PlayerInputMap =
        ids.fold(PlayerInputDefaults.defaultMap()) { map, id ->
            map.withBindingEnabled(id, enabled = false)
        }

    /** Every default summon surface (touch + remote) — the total-lockout set. */
    private val allSummonIds = arrayOf(
        PlayerInputDefaults.ID_TAP,
        PlayerInputDefaults.ID_EDGE_SWIPE_LEFT,
        PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT,
        PlayerInputDefaults.ID_DPAD_UP,
        PlayerInputDefaults.ID_DPAD_DOWN,
        PlayerInputDefaults.ID_DPAD_SELECT,
    )

    @Test
    fun healthy_default_map_is_unchanged() {
        val map = PlayerInputDefaults.defaultMap()
        assertEquals(map, map.ensureControlsSummonable())
    }

    @Test
    fun total_lockout_restores_both_family_rows() {
        // The broken-editor lockout: every touch summon surface AND every
        // D-pad summon control off — neither family can show controls again.
        val locked = disabled(*allSummonIds)
        val healed = locked.ensureControlsSummonable()
        assertNotEquals(locked, healed)
        val tap = healed.bindings.first { it.pattern == InputPattern.Tap }
        assertTrue(tap.enabled, "tap row must wake up")
        assertEquals(PlayerAction.TOGGLE_CONTROLS, tap.action)
        assertEquals(PlayerInputDefaults.ID_TAP, tap.id, "the existing row keeps its id")
        val select = healed.bindings.first { it.pattern == InputPattern.DPad(DpadControl.SELECT) }
        assertTrue(select.enabled, "the remote family's summon row must wake up too")
    }

    @Test
    fun tap_disabled_with_a_live_edge_swipe_is_intent_not_a_lockout() {
        // Tap off + edge swipe still bound+enabled (BACK_OR_HIDE summons at
        // the edge site) — a deliberate choice; must pass through untouched.
        val deliberate = disabled(PlayerInputDefaults.ID_TAP)
        assertEquals(deliberate, deliberate.ensureControlsSummonable())
    }

    @Test
    fun touch_lockout_with_remote_alive_is_intent_not_a_lockout() {
        // GestureMode.NONE (and a deliberate per-row kill of the touch
        // summons) leaves the touch family dead while the D-pad rows stay
        // untouched by presets — a one-family lockout is reachable through
        // the normal UI, so the heal must NOT fight it (a spurious heal
        // would re-enable the preset's rows on every read).
        val touchLocked = disabled(
            PlayerInputDefaults.ID_TAP,
            PlayerInputDefaults.ID_EDGE_SWIPE_LEFT,
            PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT,
        )
        assertEquals(touchLocked, touchLocked.ensureControlsSummonable())
    }

    @Test
    fun remote_lockout_with_touch_alive_is_intent_not_a_lockout() {
        // A TV user disabled every D-pad summon control. Deliberate — the
        // tap row stays untouched, and so must the D-pad rows.
        val remoteLocked = disabled(
            PlayerInputDefaults.ID_DPAD_UP,
            PlayerInputDefaults.ID_DPAD_DOWN,
            PlayerInputDefaults.ID_DPAD_SELECT,
        )
        assertEquals(remoteLocked, remoteLocked.ensureControlsSummonable())
    }

    @Test
    fun rebound_tap_does_not_count_as_a_summon_and_is_repaired() {
        // Tap rebound to play/pause + everything else off: re-ENABLING the
        // row alone would still leave no summon — the heal must restore the
        // TOGGLE_CONTROLS action too.
        val rebound = disabled(*allSummonIds)
            .withBindingAction(PlayerInputDefaults.ID_TAP, PlayerAction.TOGGLE_PLAY_PAUSE)
        val healed = rebound.ensureControlsSummonable()
        val tap = healed.bindings.first { it.pattern == InputPattern.Tap }
        assertEquals(PlayerAction.TOGGLE_CONTROLS, tap.action)
        assertTrue(tap.enabled)
    }

    @Test
    fun rebound_double_tap_center_to_toggle_controls_counts_as_a_summon() {
        // The summon guarantee asks ACTIONS, not a pattern table: the
        // executor's TOGGLE_CONTROLS arm fires from every rebound arm, so a
        // center double tap rebound to it is a live touch summon — the heal
        // must NOT fire (a spurious heal would rewrite user data on read).
        val rebound = disabled(*allSummonIds)
            .withBindingAction(PlayerInputDefaults.ID_DOUBLE_TAP_CENTER, PlayerAction.TOGGLE_CONTROLS)
        assertEquals(rebound, rebound.ensureControlsSummonable())
    }

    @Test
    fun back_or_hide_on_a_dpad_row_does_not_count_as_a_summon() {
        // BACK_OR_HIDE exits when the controls are hidden at every site but
        // the edge swipe — a rebound onto D-pad BACK is no summon. With it
        // the remote family is still locked out and gets its row back.
        val rebound = disabled(*allSummonIds)
            .withBindingAction(PlayerInputDefaults.ID_DPAD_BACK, PlayerAction.BACK_OR_HIDE)
        val healed = rebound.ensureControlsSummonable()
        assertTrue(
            healed.bindings.first { it.pattern == InputPattern.DPad(DpadControl.SELECT) }.enabled,
            "the rebound-to-BACK_OR_HIDE D-pad row is no summon; the heal must fire",
        )
    }

    @Test
    fun none_bound_summon_rows_do_not_count_as_alive() {
        // Explicit Unbound (NONE) on the tap row reads as OFF for the
        // guarantee, even though the row is 'enabled'.
        val unbound = disabled(
            PlayerInputDefaults.ID_EDGE_SWIPE_LEFT,
            PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT,
            PlayerInputDefaults.ID_DPAD_UP,
            PlayerInputDefaults.ID_DPAD_DOWN,
            PlayerInputDefaults.ID_DPAD_SELECT,
        ).withBindingAction(PlayerInputDefaults.ID_TAP, PlayerAction.NONE)
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
                    if (binding.id in allSummonIds) binding.copy(enabled = false) else binding
                },
        )
        val healed = stripped.ensureControlsSummonable()
        val tap = healed.bindings.firstOrNull { it.pattern == InputPattern.Tap }
        assertTrue(tap != null && tap.enabled, "a missing tap row must be appended enabled")
    }

    @Test
    fun heal_is_idempotent() {
        val locked = disabled(*allSummonIds)
        val healed = locked.ensureControlsSummonable()
        assertEquals(healed, healed.ensureControlsSummonable())
    }
}
