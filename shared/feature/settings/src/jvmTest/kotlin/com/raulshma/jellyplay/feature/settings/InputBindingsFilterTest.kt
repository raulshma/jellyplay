package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerBinding
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the editor's display policy ([InputBindingsFilter]): the four-section
 * partition, the modified/deletable derivation against the parameterized
 * default map, and the query filtering over pre-resolved corpus strings.
 */
class InputBindingsFilterTest {

    private val defaultsById = PlayerInputDefaults.defaultBindingsById()
    private val defaultMap = PlayerInputDefaults.defaultMap()

    /** Corpus = id itself, so tests match rows deterministically by id. */
    private fun build(
        bindings: List<PlayerBinding> = defaultMap.bindings,
        query: String = "",
        defaults: Map<String, PlayerBinding> = defaultsById,
    ) = InputBindingsFilter.build(
        bindings = bindings,
        defaultsById = defaults,
        query = query,
        corpusById = bindings.associate { it.id to it.id },
    )

    @Test
    fun partitions_the_default_map_into_the_four_fixed_sections() {
        val model = build()
        val total = model.touch.totalCount + model.mouse.totalCount +
            model.keyboard.totalCount + model.tv.totalCount
        assertEquals(defaultMap.bindings.size, total)
        assertEquals(defaultMap.bindings.size, model.touch.rows.size + model.mouse.rows.size +
            model.keyboard.rows.size + model.tv.rows.size)
        assertTrue(model.keyboard.rows.all { it.binding.pattern is InputPattern.Key })
        assertTrue(model.tv.rows.all { it.binding.pattern is InputPattern.DPad })
        assertTrue(model.mouse.rows.all { it.binding.pattern is InputPattern.Wheel })
    }

    @Test
    fun unmodified_default_rows_are_neither_modified_nor_deletable() {
        val model = build()
        for (section in listOf(model.touch, model.mouse, model.keyboard, model.tv)) {
            for (row in section.rows) {
                assertFalse(row.modified, "${row.binding.id} must be unmodified")
                assertFalse(row.deletable, "${row.binding.id} must not be deletable")
            }
        }
    }

    @Test
    fun a_rebound_row_is_modified_but_not_deletable() {
        val next = defaultMap.withBindingAction(PlayerInputDefaults.ID_KEY_M, PlayerAction.EXIT_PLAYER)
        val model = build(bindings = next.bindings)
        val row = model.keyboard.rows.single { it.binding.id == PlayerInputDefaults.ID_KEY_M }
        assertTrue(row.modified)
        assertFalse(row.deletable)
    }

    @Test
    fun a_user_captured_row_is_modified_and_deletable() {
        val next = defaultMap.withBindingAdded(
            PlayerBinding("key.ctrl.v", InputPattern.Key(PlayerInputKey.V, ctrl = true), PlayerAction.NONE),
        )
        val model = build(bindings = next.bindings)
        val row = model.keyboard.rows.single { it.binding.id == "key.ctrl.v" }
        assertTrue(row.modified)
        assertTrue(row.deletable)
    }

    @Test
    fun modified_counts_reflect_the_parameterized_flags_not_just_actions() {
        val gated = PlayerInputDefaults.defaultBindingsById(
            gestureMode = GestureMode.TAP_ONLY,
            holdSpeedEnabled = true,
            doubleTapHoldSeekEnabled = true,
        )
        // Same map as the ALL preset baseline: under a TAP_ONLY baseline the
        // swipe rows match, under the ALL baseline they are modified.
        val tapOnlyMap = PlayerInputDefaults.applyGestureModePreset(defaultMap, GestureMode.TAP_ONLY)
        val againstAll = build(defaults = defaultsById, bindings = tapOnlyMap.bindings)
        val againstTapOnly = build(defaults = gated, bindings = tapOnlyMap.bindings)
        assertTrue(againstAll.touch.modifiedCount > 0)
        assertEquals(0, againstTapOnly.touch.modifiedCount)
    }

    @Test
    fun sectionOf_is_the_partition_the_model_builds() {
        val model = build()
        for (row in model.touch.rows) {
            assertEquals(
                InputBindingsSection.TOUCH,
                InputBindingsFilter.sectionOf(row.binding.pattern),
                row.binding.id,
            )
        }
        for (row in model.mouse.rows) {
            assertEquals(InputBindingsSection.MOUSE, InputBindingsFilter.sectionOf(row.binding.pattern))
        }
        for (row in model.keyboard.rows) {
            assertEquals(InputBindingsSection.KEYBOARD, InputBindingsFilter.sectionOf(row.binding.pattern))
        }
        for (row in model.tv.rows) {
            assertEquals(InputBindingsSection.TV, InputBindingsFilter.sectionOf(row.binding.pattern))
        }
    }

    @Test
    fun query_filters_rows_but_totals_stay_unfiltered() {
        val model = build(query = "key.m")
        assertEquals(0, model.touch.rows.size)
        assertTrue(model.keyboard.rows.any { it.binding.id == PlayerInputDefaults.ID_KEY_M })
        assertTrue(model.keyboard.rows.any { it.binding.id == PlayerInputDefaults.ID_KEY_MEDIA_PLAY_PAUSE })
        assertEquals(
            defaultMap.bindings.count { PlayerInputDefaults.isTouchPattern(it.pattern) },
            model.touch.totalCount,
            "collapsed-header totals must not follow the query",
        )
    }

    @Test
    fun matching_is_case_insensitive_and_blank_queries_match_everything() {
        assertTrue(InputBindingsFilter.matches("CTRL", "ctrl + m"))
        assertTrue(InputBindingsFilter.matches("  ctrl  ", "ctrl + m"))
        assertFalse(InputBindingsFilter.matches("zoom", "ctrl + m"))
        assertTrue(InputBindingsFilter.matches("", "anything"))
        assertTrue(InputBindingsFilter.matches("   ", "anything"))
    }
}
