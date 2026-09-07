package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [downloadedSeasonSlices] — the one derivation behind the delete
 * sheet's (seasons, episodes) parameter pair, previously hand-copied by the
 * media-detail screen and the home delete holder. The pinned rules:
 *  - a season with an EMPTY episode list disappears from BOTH slices (the
 *    sheet renders no empty rows, and select-all must not see the season);
 *  - a season missing from the episode map entirely is dropped from the
 *    season list the same way;
 *  - the only-downloaded rule is the caller's half: the fold does NOT
 *    intersect the map with the season list (an episodes entry whose season
 *    id is not in [seasons] survives — the detail host's select-all reads
 *    the map's keys) and does not filter WHICH episodes survive a non-empty
 *    list;
 *  - order is preserved from both inputs.
 */
class DownloadedSeasonSlicesTest {

    private fun season(id: String) =
        MediaItem(id = id, name = "Season $id", mediaType = MediaType.SEASON)

    private fun episode(id: String, seasonId: String) =
        MediaItem(id = id, name = "Episode $id", mediaType = MediaType.EPISODE, seasonId = seasonId)

    @Test
    fun seasonsWithNoEpisodesAreDroppedFromBothSlices() {
        val slices = downloadedSeasonSlices(
            seasons = listOf(season("s1"), season("s2"), season("s3")),
            episodesBySeason = mapOf(
                "s1" to listOf(episode("e1", "s1")),
                "s2" to emptyList(),
                // s3 has no map entry at all — dropped like an empty one.
            ),
        )

        assertEquals(listOf("s1"), slices.seasons.map { it.id })
        assertEquals(setOf("s1"), slices.episodesBySeason.keys)
    }

    @Test
    fun everyEpisodePassesThroughUntouched_theOnlyDownloadedRuleIsTheCallers() {
        val e1 = episode("e1", "s1")
        val e2 = episode("e2", "s1")

        val slices = downloadedSeasonSlices(
            seasons = listOf(season("s1")),
            episodesBySeason = mapOf("s1" to listOf(e1, e2)),
        )

        // No filtering of individual episodes: the sheet treats every passed
        // episode as deletable, so callers pre-restrict the map themselves.
        assertEquals(listOf(e1, e2), slices.episodesBySeason["s1"])
    }

    @Test
    fun mapEntriesForSeasonsMissingFromTheListSurvive() {
        // The fold must not intersect the two inputs: select-all's selectable
        // set is derived from the map's keys
        // (`rememberMultiEpisodeSelectionForDelete`), so silently dropping
        // "ghost" entries here would change which ids a whole-series delete
        // removes for the detail host.
        val ghostEpisodes = listOf(episode("e9", "ghost"))

        val slices = downloadedSeasonSlices(
            seasons = listOf(season("s1")),
            episodesBySeason = mapOf(
                "s1" to listOf(episode("e1", "s1")),
                "ghost" to ghostEpisodes,
            ),
        )

        assertEquals(listOf("s1"), slices.seasons.map { it.id })
        assertEquals(ghostEpisodes, slices.episodesBySeason["ghost"])
    }

    @Test
    fun orderIsPreservedFromBothInputs() {
        val slices = downloadedSeasonSlices(
            seasons = listOf(season("s3"), season("s1"), season("s2")),
            episodesBySeason = mapOf(
                "s1" to listOf(episode("e1b", "s1"), episode("e1a", "s1")),
                "s2" to listOf(episode("e2", "s2")),
                "s3" to listOf(episode("e3", "s3")),
            ),
        )

        assertEquals(listOf("s3", "s1", "s2"), slices.seasons.map { it.id })
        assertEquals(
            listOf("e1b", "e1a"),
            slices.episodesBySeason["s1"]!!.map { it.id },
            "episode order within a season is caller-owned and must not be re-sorted",
        )
    }

    @Test
    fun emptyInputsYieldEmptySlices() {
        val slices = downloadedSeasonSlices(seasons = emptyList(), episodesBySeason = emptyMap())

        assertTrue(slices.seasons.isEmpty())
        assertTrue(slices.episodesBySeason.isEmpty())
    }
}
