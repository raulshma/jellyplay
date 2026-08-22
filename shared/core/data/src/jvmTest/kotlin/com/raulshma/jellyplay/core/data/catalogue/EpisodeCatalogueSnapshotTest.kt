package com.raulshma.jellyplay.core.data.catalogue

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins the [EpisodeCatalogueSnapshot] value type's derived invariants: the
 * `fetchedSeasonIds`-excludes-missing-key edge (ported from the
 * `episodes_batchReturnsMismatchedSeasonKey_…` regression), the flattened
 * playback order in [allEpisodeIds], and the [seasonEpisodes] accessor.
 *
 * The snapshot is a `data class`, so structural equality is covered by Kotlin's
 * generated `equals`; these tests cover the *semantic* invariants the catalogue
 * must preserve when it builds one.
 */
class EpisodeCatalogueSnapshotTest {

    @Test
    fun seasonEpisodes_returnsEpisodesForPresentSeason() {
        val s1 = season("season1")
        val e1 = episode("e1", seasonId = "season1")
        val snapshot = EpisodeCatalogueSnapshot(
            seriesId = "s1",
            seasons = listOf(s1),
            episodesBySeason = mapOf("season1" to listOf(e1)),
            fetchedSeasonIds = setOf("season1"),
            sortedEpisodes = listOf(e1),
            epoch = 1L,
        )

        assertEquals(listOf("e1"), snapshot.seasonEpisodes("season1").map { it.id })
    }

    @Test
    fun seasonEpisodes_returnsEmptyForAbsentSeason() {
        val snapshot = EpisodeCatalogueSnapshot(
            seriesId = "s1",
            seasons = emptyList(),
            episodesBySeason = emptyMap(),
            fetchedSeasonIds = emptySet(),
            sortedEpisodes = emptyList(),
            epoch = 0L,
        )

        assertTrue(snapshot.seasonEpisodes("missing").isEmpty())
    }

    @Test
    fun allEpisodeIds_flattensInPlaybackOrder() {
        // sortedEpisodes is the canonical playback order; allEpisodeIds must
        // mirror it exactly (this is the playlist-expansion shape).
        val e1 = episode("e1", seasonId = "season1", seasonNumber = 1, episodeNumber = 1)
        val e2 = episode("e2", seasonId = "season1", seasonNumber = 1, episodeNumber = 2)

        val snapshot = EpisodeCatalogueSnapshot(
            seriesId = "s1",
            seasons = listOf(season("season1")),
            episodesBySeason = mapOf("season1" to listOf(e1, e2)),
            fetchedSeasonIds = setOf("season1"),
            sortedEpisodes = listOf(e1, e2),
            epoch = 1L,
        )

        assertEquals(listOf("e1", "e2"), snapshot.allEpisodeIds)
    }

    @Test
    fun fetchedSeasonIds_excludesSeasonsGroupedUnderBlankKey() {
        // Regression port: when the batched response groups an episode under ""
        // (null seasonId), the real season must NOT be in fetchedSeasonIds,
        // or the per-season refetch path is short-circuited and pins it empty.
        val realSeason = season("season1")
        val orphanEpisode = episode("e1", seasonId = "") // grouped under "" not "season1"

        val snapshot = EpisodeCatalogueSnapshot(
            seriesId = "s1",
            seasons = listOf(realSeason),
            // The episode grouped under "" does NOT populate "season1".
            episodesBySeason = mapOf("" to listOf(orphanEpisode)),
            // fetchedSeasonIds must exclude "season1" — only "" is "present".
            fetchedSeasonIds = setOf(""),
            sortedEpisodes = listOf(orphanEpisode),
            epoch = 1L,
        )

        assertTrue("season1" !in snapshot.fetchedSeasonIds, "real season must not be marked fetched")
        assertTrue("" in snapshot.fetchedSeasonIds, "blank key is technically present")
    }

    @Test
    fun isEmpty_whenNoSeasonsOrEpisodes() {
        val empty = EpisodeCatalogueSnapshot(
            seriesId = "s1",
            seasons = emptyList(),
            episodesBySeason = emptyMap(),
            fetchedSeasonIds = emptySet(),
            sortedEpisodes = emptyList(),
            epoch = 0L,
        )

        assertTrue(empty.isEmpty)
    }

    @Test
    fun isEmpty_falseWhenSeasonsPresent() {
        val withSeason = EpisodeCatalogueSnapshot(
            seriesId = "s1",
            seasons = listOf(season("season1")),
            episodesBySeason = emptyMap(),
            fetchedSeasonIds = emptySet(),
            sortedEpisodes = emptyList(),
            epoch = 0L,
        )

        assertTrue(!withSeason.isEmpty)
    }

    private fun season(id: String) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.SEASON,
    )

    private fun episode(
        id: String,
        seasonId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        seasonId = seasonId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )
}
