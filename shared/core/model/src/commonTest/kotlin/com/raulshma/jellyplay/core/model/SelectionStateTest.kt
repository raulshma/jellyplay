package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins [SelectionState]'s write algebra — the pure transforms behind every
 * bulk-selection screen's toggle / select all / clear setter (same pattern as
 * the [LibraryFilters] algebra). Selection mode must always be DERIVED from
 * the id set: a transform that returned a stale or hand-set mode flag would
 * strand the screen's action bar in (or out of) selection mode.
 */
class SelectionStateTest {

    @Test
    fun empty_state_is_inactive() {
        assertFalse(SelectionState<String>().active)
    }

    @Test
    fun toggled_on_a_missing_id_adds_it_and_activates() {
        val next = SelectionState<String>().toggled("a")

        assertEquals(setOf("a"), next.ids)
        assertTrue(next.active)
    }

    @Test
    fun toggled_on_a_picked_id_removes_it_and_deactivates_when_empty() {
        val next = SelectionState(setOf("a", "b")).toggled("a")

        assertEquals(setOf("b"), next.ids)
        assertTrue(next.active)

        val last = next.toggled("b")
        assertEquals(emptySet(), last.ids)
        assertFalse(last.active)
    }

    @Test
    fun toggled_never_stores_a_mode_flag_that_can_drift_from_the_ids() {
        // Removing one of several ids keeps the rest — and keeps mode on.
        val state = SelectionState(setOf(1, 2, 3)).toggled(2)
        assertEquals(setOf(1, 3), state.ids)
        assertTrue(state.active)
    }

    @Test
    fun cleared_returns_the_default_state() {
        assertEquals(SelectionState<String>(), SelectionState(setOf("a", "b")).cleared())
        assertFalse(SelectionState(setOf("a", "b")).cleared().active)
    }

    @Test
    fun selectAll_picks_every_id_in_the_collection_and_activates() {
        val next = SelectionState<String>().selectAll(listOf("a", "b", "c"))

        assertEquals(setOf("a", "b", "c"), next.ids)
        assertTrue(next.active)
    }

    @Test
    fun selectAll_over_an_empty_list_stays_inactive() {
        val next = SelectionState(setOf("a")).selectAll(emptyList())

        assertEquals(emptySet(), next.ids)
        assertFalse(next.active)
    }

    @Test
    fun selectAll_replaces_the_previous_selection() {
        val next = SelectionState(setOf("old")).selectAll(listOf("a", "b"))

        assertEquals(setOf("a", "b"), next.ids)
    }

    @Test
    fun transforms_compose() {
        val next = SelectionState<String>()
            .toggled("a")
            .toggled("b")
            .toggled("a")
            .selectAll(listOf("x", "y"))
            .cleared()

        assertEquals(SelectionState<String>(), next)
        assertFalse(next.active)
    }
}
