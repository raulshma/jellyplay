package com.raulshma.jellyplay.feature.details

import androidx.compose.ui.state.ToggleableState
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.MultiEpisodeSelection
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Tests the rule-driven [MultiEpisodeSelection] that backs both the download
 * sheet (selectability = not-yet-downloaded) and the delete sheet
 * (selectability = every episode). These cases mirror the previous
 * `SeriesDownloadSelection` tests; the selectability lambda stands in for the
 * wrapper's `downloadedEpisodeIds` exclusion.
 */
class MultiEpisodeSelectionTest {

    private fun createSeason(id: String, name: String) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.SEASON,
    )

    private fun createEpisode(id: String, name: String, seasonId: String) = MediaItem(
        id = id,
        name = name,
        mediaType = MediaType.EPISODE,
        seasonId = seasonId,
    )

    /** Download-sheet selectability rule: every not-yet-downloaded episode. */
    private fun downloadRule(
        seasons: List<MediaItem>,
        episodes: Map<String, List<MediaItem>>,
        downloaded: Set<String> = emptySet(),
    ): () -> Map<String, Set<String>> = {
        seasons.associate { season ->
            season.id to (episodes[season.id].orEmpty()
                .map { it.id }
                .filter { it !in downloaded }
                .toSet())
        }
    }

    @Test
    fun initialSelection_allSelectedIsFalse() {
        val s1 = createSeason("s1", "Season 1")
        val episodes = mapOf("s1" to listOf(createEpisode("ep1", "Ep 1", "s1")))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes),
        )

        assertFalse(selection.allSelected)
        assertEquals(0, selection.totalSelectedCount)
    }

    @Test
    fun selectAll_selectsAllSelectableEpisodes() {
        val s1 = createSeason("s1", "Season 1")
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 2", "s1")
        val episodes = mapOf("s1" to listOf(ep1, ep2))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes),
        )

        selection.selectAll()

        assertTrue(selection.allSelected)
        assertEquals(2, selection.totalSelectedCount)
        assertEquals(setOf("ep1", "ep2"), selection.selectedForSeason("s1"))
    }

    @Test
    fun deselectAll_whenEverythingSelected_deselectsAllEpisodes() {
        val s1 = createSeason("s1", "Season 1")
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 2", "s1")
        val episodes = mapOf("s1" to listOf(ep1, ep2))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes),
        )

        selection.selectAll()
        assertTrue(selection.allSelected)

        selection.deselectAll()

        assertFalse(selection.allSelected)
        assertEquals(0, selection.totalSelectedCount)
        assertTrue(selection.selectedForSeason("s1").isEmpty())
    }

    @Test
    fun toggleSelectAll_whenAllSelected_deselectsAll() {
        val s1 = createSeason("s1", "Season 1")
        val s2 = createSeason("s2", "Season 2")
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 1", "s2")
        val episodes = mapOf("s1" to listOf(ep1), "s2" to listOf(ep2))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id, s2.id),
            selectableProvider = downloadRule(listOf(s1, s2), episodes),
        )

        selection.selectAll()
        assertTrue(selection.allSelected)

        selection.toggleSelectAll()

        assertFalse(selection.allSelected)
        assertEquals(0, selection.totalSelectedCount)
    }

    @Test
    fun downloadedEpisodes_areExcludedFromSelectAllAndDeselectAll() {
        val s1 = createSeason("s1", "Season 1")
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 2", "s1")
        val episodes = mapOf("s1" to listOf(ep1, ep2))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes, downloaded = setOf("ep1")),
        )

        selection.selectAll()
        assertTrue(selection.allSelected)
        assertEquals(1, selection.totalSelectedCount)
        assertEquals(setOf("ep2"), selection.selectedForSeason("s1"))

        selection.deselectAll()
        assertFalse(selection.allSelected)
        assertEquals(0, selection.totalSelectedCount)
    }

    @Test
    fun selectAll_afterEpisodesSetLater_allSelectedIsTrue() {
        val s1 = createSeason("s1", "Season 1")
        val s2 = createSeason("s2", "Season 2")
        // Episodes arrive later (as in the real app): the rule lambda reads a
        // captured var so a later assignment reflects through derived state,
        // mirroring how the download sheet pushes live state into the provider.
        var episodes: Map<String, List<MediaItem>> = emptyMap()
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id, s2.id),
            selectableProvider = downloadRule(listOf(s1, s2), episodes),
        )

        episodes = mapOf(
            "s1" to listOf(createEpisode("ep1", "Ep 1", "s1"), createEpisode("ep2", "Ep 2", "s1")),
            "s2" to listOf(createEpisode("ep3", "Ep 1", "s2")),
        )
        // Refresh the closure so the later `episodes` value is visible. In the
        // sheet, `rememberMultiEpisodeSelectionForDownload` does this each
        // recomposition via `updateSelectableProvider`.
        selection.updateSelectableProvider(downloadRule(listOf(s1, s2), episodes))

        selection.selectAll()

        assertTrue(selection.allSelected, "allSelected should be true after selectAll()")
        assertEquals(3, selection.totalSelectedCount)
        assertEquals(setOf("ep1", "ep2"), selection.selectedForSeason("s1"))
        assertEquals(setOf("ep3"), selection.selectedForSeason("s2"))
    }

    @Test
    fun deleteSheetRule_makesEveryEpisodeSelectable() {
        // Mirrors the delete sheet: no exclusion, every episode is selectable.
        val s1 = createSeason("s1", "Season 1")
        val episodes = mapOf("s1" to listOf(createEpisode("ep1", "Ep 1", "s1")))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = episodes.keys,
            selectableProvider = { episodes.mapValues { (_, list) -> list.map { it.id }.toSet() } },
        )

        selection.selectAll()

        assertTrue(selection.allSelected)
        assertEquals(setOf("ep1"), selection.selectedForSeason("s1"))
        assertEquals(setOf("ep1"), selection.toSelectedIds())
        assertEquals(mapOf("s1" to listOf("ep1")), selection.toSelectedMap())
    }

    // ── toggleSelectAll (select branch) ────────────────────────────────

    @Test
    fun toggleSelectAll_whenNoneSelected_selectsAll() {
        val s1 = createSeason("s1", "Season 1")
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 2", "s1")
        val episodes = mapOf("s1" to listOf(ep1, ep2))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes),
        )

        assertFalse(selection.allSelected)

        selection.toggleSelectAll()

        assertTrue(selection.allSelected)
        assertEquals(2, selection.totalSelectedCount)
    }

    // ── toggleSeason ───────────────────────────────────────────────────

    @Test
    fun toggleSeason_selectsThenDeselectsAllSelectableInThatSeason() {
        val s1 = createSeason("s1", "Season 1")
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 2", "s1")
        val episodes = mapOf("s1" to listOf(ep1, ep2))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes),
        )

        selection.toggleSeason("s1")
        assertEquals(setOf("ep1", "ep2"), selection.selectedForSeason("s1"))

        selection.toggleSeason("s1")
        assertTrue(selection.selectedForSeason("s1").isEmpty())
    }

    @Test
    fun toggleSeason_isPerSeasonAndDoesNotTouchOtherSeasons() {
        val s1 = createSeason("s1", "Season 1")
        val s2 = createSeason("s2", "Season 2")
        val episodes = mapOf(
            "s1" to listOf(createEpisode("ep1", "Ep 1", "s1")),
            "s2" to listOf(createEpisode("ep2", "Ep 2", "s2")),
        )
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id, s2.id),
            selectableProvider = downloadRule(listOf(s1, s2), episodes),
        )

        selection.toggleSeason("s1")
        assertEquals(setOf("ep1"), selection.selectedForSeason("s1"))
        // Season 2 untouched.
        assertTrue(selection.selectedForSeason("s2").isEmpty())
    }

    // ── toggleEpisode ──────────────────────────────────────────────────

    @Test
    fun toggleEpisode_addsThenRemovesASingleEpisode() {
        val s1 = createSeason("s1", "Season 1")
        val episodes = mapOf("s1" to listOf(createEpisode("ep1", "Ep 1", "s1")))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes),
        )

        selection.toggleEpisode("s1", "ep1")
        assertEquals(setOf("ep1"), selection.selectedForSeason("s1"))
        assertEquals(1, selection.totalSelectedCount)

        selection.toggleEpisode("s1", "ep1")
        assertTrue(selection.selectedForSeason("s1").isEmpty())
    }

    // ── triStateForSeason ──────────────────────────────────────────────

    @Test
    fun triStateForSeason_isOffWhenNoSelectableEpisodes() {
        val s1 = createSeason("s1", "Season 1")
        // Empty episodes → nothing selectable.
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), emptyMap()),
        )
        assertEquals(ToggleableState.Off, selection.triStateForSeason("s1"))
    }

    @Test
    fun triStateForSeason_isOnWhenAllSelectableSelected() {
        val s1 = createSeason("s1", "Season 1")
        val episodes = mapOf("s1" to listOf(createEpisode("ep1", "Ep 1", "s1")))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes),
        )
        selection.selectAll()
        assertEquals(ToggleableState.On, selection.triStateForSeason("s1"))
    }

    @Test
    fun triStateForSeason_isIndeterminateWhenPartiallySelected() {
        val s1 = createSeason("s1", "Season 1")
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 2", "s1")
        val episodes = mapOf("s1" to listOf(ep1, ep2))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes),
        )
        selection.toggleEpisode("s1", "ep1") // one of two
        assertEquals(ToggleableState.Indeterminate, selection.triStateForSeason("s1"))
    }

    @Test
    fun triStateForSeason_isOffWhenNothingSelectedButSelectablePresent() {
        val s1 = createSeason("s1", "Season 1")
        val episodes = mapOf("s1" to listOf(createEpisode("ep1", "Ep 1", "s1")))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes),
        )
        assertEquals(ToggleableState.Off, selection.triStateForSeason("s1"))
    }

    // ── Derived views ──────────────────────────────────────────────────

    @Test
    fun allSelectableIdsAndPerSeasonFlattenTheProviderAcrossSeasons() {
        val s1 = createSeason("s1", "Season 1")
        val s2 = createSeason("s2", "Season 2")
        val episodes = mapOf(
            "s1" to listOf(createEpisode("ep1", "Ep 1", "s1")),
            "s2" to listOf(createEpisode("ep2", "Ep 2", "s2")),
        )
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id, s2.id),
            selectableProvider = downloadRule(listOf(s1, s2), episodes),
        )

        assertEquals(setOf("ep1", "ep2"), selection.allSelectableIds)
        assertEquals(setOf("ep1"), selection.selectableEpisodeIdsForSeason("s1"))
        assertEquals(setOf("ep2"), selection.selectableEpisodeIdsForSeason("s2"))
        // Unknown season → empty, never null.
        assertTrue(selection.selectableEpisodeIdsForSeason("nope").isEmpty())
    }

    @Test
    fun allSelected_isFalseWhenSelectableIsEmptyEvenIfNothingSelected() {
        // Guards the `selectable.isNotEmpty()` clause: with zero selectable ids,
        // allSelected must be false (not vacuously true).
        val s1 = createSeason("s1", "Season 1")
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), emptyMap()),
        )
        assertFalse(selection.allSelected)
    }

    @Test
    fun selectAll_onlySelectsSelectableEpisodesAndPreservesSeasonKeys() {
        val s1 = createSeason("s1", "Season 1")
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 2", "s1")
        val episodes = mapOf("s1" to listOf(ep1, ep2))
        val selection = MultiEpisodeSelection(
            initialSeasonIds = listOf(s1.id),
            selectableProvider = downloadRule(listOf(s1), episodes, downloaded = setOf("ep1")),
        )

        selection.selectAll()

        // ep1 is downloaded (excluded) → only ep2 selectable & selected.
        assertEquals(setOf("ep2"), selection.selectedForSeason("s1"))
        // The season key is preserved even though only one episode was selected.
        assertEquals(setOf("s1"), selection.toSelectedMap().keys)
    }
}
