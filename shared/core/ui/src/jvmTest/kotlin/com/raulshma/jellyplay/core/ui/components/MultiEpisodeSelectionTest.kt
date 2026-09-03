package com.raulshma.jellyplay.core.ui.components

import androidx.compose.ui.state.ToggleableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Selection-machine invariants for [MultiEpisodeSelection] (the shared base of
 * the download/delete sheets): only `toggle*`/`select*`/`deselectAll` mutate
 * [MultiEpisodeSelection.selectedEpisodeIds]; every tri-state/count predicate
 * is a pure derivation of (selectedIds x caller-supplied selectability). The
 * class is a plain constructor (only `mutableStateOf` internals), so it runs
 * headlessly on the JVM — the `remember*` factories stay composition-bound and
 * untested here. The pinned invariants:
 *  - a fresh instance selects nothing;
 *  - toggling an episode twice round-trips to empty;
 *  - `selectAll` selects exactly the selectable ids and `allSelected` is true
 *    iff every selectable id is selected (and false when nothing is
 *    selectable);
 *  - `toggleSeason` flips one season's whole selectable set, leaving other
 *    seasons untouched, and is a no-op on a season with no selectables;
 *  - swapping the selectability provider never wipes existing selections.
 */
class MultiEpisodeSelectionTest {

    private val s1Episodes = setOf("e1", "e2")
    private val s2Episodes = setOf("e3")

    private fun selection(
        provider: () -> Map<String, Set<String>> = {
            mapOf("s1" to s1Episodes, "s2" to s2Episodes)
        },
        seasons: List<String> = listOf("s1", "s2"),
    ) = MultiEpisodeSelection(seasons, provider)

    @Test
    fun freshSelection_selectsNothingAndTriStatesAreOff() {
        val selection = selection()

        assertEquals(mapOf("s1" to emptySet<String>(), "s2" to emptySet()), selection.selectedEpisodeIds)
        assertEquals(0, selection.totalSelectedCount)
        assertFalse(selection.allSelected)
        assertEquals(ToggleableState.Off, selection.triStateForSeason("s1"))
        assertEquals(ToggleableState.Off, selection.triStateForSeason("s2"))
    }

    @Test
    fun toggleEpisode_roundTripsSelectionOnAndOff() {
        val selection = selection()

        selection.toggleEpisode("s1", "e1")
        assertEquals(setOf("e1"), selection.selectedForSeason("s1"))
        assertEquals(1, selection.totalSelectedCount)
        assertEquals(ToggleableState.Indeterminate, selection.triStateForSeason("s1"))

        selection.toggleEpisode("s1", "e1")
        assertEquals(emptySet(), selection.selectedForSeason("s1"))
        assertEquals(0, selection.totalSelectedCount)
        assertEquals(ToggleableState.Off, selection.triStateForSeason("s1"))
    }

    @Test
    fun selectAll_selectsExactlyEverySelectableId() {
        val selection = selection()

        selection.selectAll()

        assertEquals(setOf("e1", "e2", "e3"), selection.toSelectedIds())
        assertEquals(3, selection.totalSelectedCount)
        assertTrue(selection.allSelected)
        assertEquals(ToggleableState.On, selection.triStateForSeason("s1"))
        assertEquals(ToggleableState.On, selection.triStateForSeason("s2"))
    }

    @Test
    fun toggleSeason_flipsWholeSeasonWithoutTouchingOthers() {
        val selection = selection()

        selection.toggleSeason("s1")
        assertEquals(s1Episodes, selection.selectedForSeason("s1"))
        assertEquals(emptySet(), selection.selectedForSeason("s2"), "other seasons must stay untouched")
        assertEquals(ToggleableState.Off, selection.triStateForSeason("s2"), "untouched season with nothing selected reads Off")

        selection.toggleSeason("s1")
        assertEquals(emptySet(), selection.selectedForSeason("s1"))
        assertEquals(0, selection.totalSelectedCount)
    }

    @Test
    fun toggleSeason_onSeasonWithNoSelectables_isNoOp() {
        val selection = selection()
        selection.toggleEpisode("s1", "e1")

        selection.toggleSeason("missing") // not in the provider map: no selectables

        // all{} over an empty selectable set is vacuously true → subtracting it
        // changes nothing; the user's e1 pick must survive.
        assertEquals(setOf("e1"), selection.selectedForSeason("s1"))
        assertEquals(1, selection.totalSelectedCount)
    }

    @Test
    fun allSelected_falseWhilePartiallySelected() {
        val selection = selection()
        selection.toggleEpisode("s1", "e1")

        assertFalse(selection.allSelected, "one of three selectable ids chosen is not 'all'")
        assertEquals(ToggleableState.Indeterminate, selection.triStateForSeason("s1"))
        assertEquals(ToggleableState.Off, selection.triStateForSeason("s2"))
    }

    @Test
    fun toggleSelectAll_partialSelectionGoesToAll_thenAllClears() {
        val selection = selection()
        selection.toggleEpisode("s1", "e1")

        selection.toggleSelectAll()
        assertTrue(selection.allSelected, "toggle from a partial state selects everything")

        selection.toggleSelectAll()
        assertEquals(0, selection.totalSelectedCount, "toggle from all-selected clears everything")
        assertFalse(selection.allSelected)
    }

    @Test
    fun deselectAll_clearsEverySeason() {
        val selection = selection()
        selection.toggleEpisode("s1", "e1")
        selection.toggleEpisode("s2", "e3")

        selection.deselectAll()

        assertEquals(0, selection.totalSelectedCount)
        assertEquals(mapOf("s1" to emptySet<String>(), "s2" to emptySet()), selection.selectedEpisodeIds)
    }

    @Test
    fun providerSwap_neverWipesExistingSelections() {
        val selection = selection()
        selection.toggleEpisode("s1", "e1")

        // The download sheet refreshes the provider closure as data streams
        // in; only toggle* may mutate the selection, so e1 must survive.
        selection.updateSelectableProvider {
            mapOf("s1" to setOf("e1", "e2", "e9"), "s2" to emptySet())
        }

        assertEquals(setOf("e1"), selection.selectedForSeason("s1"))
        assertEquals(1, selection.totalSelectedCount)
        assertFalse(selection.allSelected, "newly selectable e9 is not chosen yet")
    }

    @Test
    fun noSelectableIds_allSelectedStaysFalse_evenAfterSelectAll() {
        val selection = selection(
            provider = { emptyMap() },
            seasons = listOf("s1"),
        )

        selection.selectAll()

        assertTrue(selection.toSelectedIds().isEmpty())
        assertFalse(selection.allSelected, "allSelected requires a non-empty selectable set")
        assertEquals(ToggleableState.Off, selection.triStateForSeason("s1"))
    }

    @Test
    fun seasonMissingFromProvider_selectablesAndSelectionReadEmpty() {
        val selection = selection()

        assertTrue(selection.selectableEpisodeIdsForSeason("ghost").isEmpty())
        assertTrue(selection.selectedForSeason("ghost").isEmpty())
        assertEquals(ToggleableState.Off, selection.triStateForSeason("ghost"))
    }

    @Test
    fun snapshots_toSelectedMapMirrorsTheSelection() {
        val selection = selection()
        selection.toggleEpisode("s1", "e2")
        selection.toggleEpisode("s2", "e3")

        assertEquals(mapOf("s1" to listOf("e2"), "s2" to listOf("e3")), selection.toSelectedMap())
        assertEquals(setOf("e2", "e3"), selection.toSelectedIds())
    }
}
