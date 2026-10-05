package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the binding editor's model-level semantics: the captured-combo id
 * scheme ([PlayerBindingIds.customKeyId]) can never mint a colliding id for
 * a pattern the default catalog doesn't carry, and the add/remove row
 * mutators ([PlayerInputMap.withBindingAdded] / [PlayerInputMap.withoutBinding])
 * hold the one-pattern-one-row invariant the write guard enforces.
 */
class PlayerBindingEditorSemanticsTest {

    // ── customKeyId ──────────────────────────────────────────────────────

    @Test
    fun customKeyId_is_deterministic_and_modifier_order_is_ctrl_shift_alt() {
        val pattern = InputPattern.Key(PlayerInputKey.M, ctrl = true, shift = true)
        assertEquals(PlayerBindingIds.customKeyId(pattern), PlayerBindingIds.customKeyId(pattern))
        assertEquals("key.ctrl_shift.m", PlayerBindingIds.customKeyId(pattern))
        assertEquals(
            "key.ctrl_shift_alt.bracket_left",
            PlayerBindingIds.customKeyId(
                InputPattern.Key(PlayerInputKey.BRACKET_LEFT, ctrl = true, shift = true, alt = true),
            ),
        )
    }

    @Test
    fun customKeyId_is_unique_per_pattern_across_the_whole_combo_space() {
        val ids = PlayerInputKey.entries.flatMap { key ->
            val combos = listOf(false to false, true to false, false to true, true to true)
            combos.flatMap { (ctrl, shift) ->
                listOf(true, false).map { alt ->
                    PlayerBindingIds.customKeyId(
                        InputPattern.Key(key, ctrl = ctrl, shift = shift, alt = alt),
                    )
                }
            }
        }
        assertEquals(ids.size, ids.distinct().size, "two combos minted the same id")
    }

    @Test
    fun modifier_combo_ids_never_collide_with_a_default_row_id() {
        val defaults = PlayerInputDefaults.defaultMap().bindings
        val defaultIds = defaults.associateBy { it.id }
        for (key in PlayerInputKey.entries) {
            for (ctrl in listOf(false, true)) {
                for (shift in listOf(false, true)) {
                    for (alt in listOf(false, true)) {
                        if (!ctrl && !shift && !alt) continue // plain keys may share the default scheme
                        val pattern = InputPattern.Key(key, ctrl, shift, alt)
                        val id = PlayerBindingIds.customKeyId(pattern)
                        val collision = defaultIds[id]
                        if (collision != null) {
                            assertEquals(pattern, collision.pattern, "id $id minted for a different pattern")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun plain_key_capture_lands_on_the_default_id_scheme() {
        assertEquals("key.v", PlayerBindingIds.customKeyId(InputPattern.Key(PlayerInputKey.V)))
    }

    // ── withBindingAdded ─────────────────────────────────────────────────

    @Test
    fun withBindingAdded_appends_a_pattern_no_row_owns() {
        val map = PlayerInputDefaults.defaultMap()
        val pattern = InputPattern.Key(PlayerInputKey.M, ctrl = true)
        val next = map.withBindingAdded(PlayerBinding("key.ctrl.m", pattern, PlayerAction.NONE))
        assertEquals(1, next.bindings.size - map.bindings.size)
        val added = next.bindings.last()
        assertEquals("key.ctrl.m", added.id)
        assertEquals(pattern, added.pattern)
        assertEquals(PlayerAction.NONE, added.action)
        assertTrue(added.enabled)
        assertTrue(next.hasNoDuplicates())
    }

    @Test
    fun withBindingAdded_edits_the_existing_row_and_keeps_its_id() {
        val map = PlayerInputDefaults.defaultMap()
        val pattern = InputPattern.Key(PlayerInputKey.BRACKET_LEFT, ctrl = true)
        val before = map.enabledBindingFor(pattern)!! // key.ctrl_bracket_left
        assertEquals(PlayerAction.AUDIO_DELAY_DECREASE, before.action)
        val next = map.withBindingAdded(PlayerBinding("key.ctrl.bracket_left", pattern, PlayerAction.SEEK_FORWARD))
        assertEquals(before.id, next.enabledBindingFor(pattern)?.id, "replacement must keep the existing row's id")
        assertEquals(PlayerAction.SEEK_FORWARD, next.enabledBindingFor(pattern)?.action)
        assertEquals(map.bindings.size, next.bindings.size)
        assertTrue(next.hasNoDuplicates())
    }

    @Test
    fun withBindingAdded_is_a_no_op_when_the_row_already_matches() {
        val map = PlayerInputDefaults.defaultMap()
        val binding = map.bindings.first { it.id == PlayerInputDefaults.ID_KEY_M }
        assertEquals(map, map.withBindingAdded(binding))
    }

    // ── withoutBinding ───────────────────────────────────────────────────

    @Test
    fun withoutBinding_removes_only_the_named_row() {
        val map = PlayerInputDefaults.defaultMap()
        val next = map.withoutBinding(PlayerInputDefaults.ID_KEY_M)
        assertEquals(map.bindings.size - 1, next.bindings.size)
        assertTrue(next.bindings.none { it.id == PlayerInputDefaults.ID_KEY_M })
        assertTrue(next.hasNoDuplicates())
    }

    @Test
    fun withoutBinding_is_a_no_op_for_unknown_ids() {
        val map = PlayerInputDefaults.defaultMap()
        assertEquals(map, map.withoutBinding("nope"))
    }

    // ── isSeekFamilyKey ──────────────────────────────────────────────────

    @Test
    fun seekFamilyKeys_are_exactly_the_step_ladder_vocabulary() {
        // The capture dialog refuses modified captures over exactly this
        // set (the resolution ladder folds their modifiers away) — drift
        // here would let the editor mint rows that can never resolve.
        assertEquals(
            setOf(
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
            ),
            PlayerInputKey.entries.filter { it.isSeekFamilyKey }.toSet(),
        )
    }

    // ── defaultBindingsById ──────────────────────────────────────────────

    @Test
    fun defaultBindingsById_covers_every_default_row_and_reflects_the_flags() {
        val allOn = PlayerInputDefaults.defaultBindingsById()
        assertEquals(PlayerInputDefaults.defaultMap().bindings.size, allOn.size)

        val gated = PlayerInputDefaults.defaultBindingsById(
            gestureMode = GestureMode.TAP_ONLY,
            holdSpeedEnabled = false,
            doubleTapHoldSeekEnabled = false,
        )
        val longPress = gated.getValue(PlayerInputDefaults.ID_LONG_PRESS)
        assertFalse(longPress.enabled, "hold-speed row must reflect the stored behavior flag")
        assertFalse(
            gated.getValue(PlayerInputDefaults.ID_SWIPE_VOLUME).enabled,
            "swipe rows must reflect the TAP_ONLY preset",
        )
        assertTrue(
            gated.getValue(PlayerInputDefaults.ID_KEY_M).enabled,
            "keyboard rows are never gated by the touch flags",
        )
    }
}
