package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDownloadSelectionTest {

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

    @Test
    fun initialSelection_allSelectedIsFalse() {
        val s1 = createSeason("s1", "Season 1")
        val episodes = mapOf("s1" to listOf(createEpisode("ep1", "Ep 1", "s1")))
        val selection = SeriesDownloadSelection(listOf(s1), episodes = episodes)

        assertFalse(selection.allSelected)
        assertEquals(0, selection.totalSelectedCount)
    }

    @Test
    fun selectAll_selectsAllSelectableEpisodes() {
        val s1 = createSeason("s1", "Season 1")
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 2", "s1")
        val episodes = mapOf("s1" to listOf(ep1, ep2))
        val selection = SeriesDownloadSelection(listOf(s1), episodes = episodes)

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
        val selection = SeriesDownloadSelection(listOf(s1), episodes = episodes)

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
        val selection = SeriesDownloadSelection(listOf(s1, s2), episodes = episodes)

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
        val selection = SeriesDownloadSelection(
            seasons = listOf(s1),
            episodes = episodes,
            downloadedEpisodeIds = setOf("ep1")
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
        // Mirror rememberSeriesDownloadSelection: constructed with no episodes
        val selection = SeriesDownloadSelection(listOf(s1, s2))

        // Episodes arrive later (as in the real app)
        val ep1 = createEpisode("ep1", "Ep 1", "s1")
        val ep2 = createEpisode("ep2", "Ep 2", "s1")
        val ep3 = createEpisode("ep3", "Ep 1", "s2")
        selection.episodes = mapOf(
            "s1" to listOf(ep1, ep2),
            "s2" to listOf(ep3),
        )

        selection.selectAll()

        assertTrue("allSelected should be true after selectAll()", selection.allSelected)
        assertEquals(3, selection.totalSelectedCount)
        assertEquals(setOf("ep1", "ep2"), selection.selectedForSeason("s1"))
        assertEquals(setOf("ep3"), selection.selectedForSeason("s2"))
    }
}
