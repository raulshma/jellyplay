package com.raulshma.jellyplay.core.data.catalogue

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Pins the catalogue's playback-order comparator, ported verbatim from
 * `DetailViewModel.sortedByPlaybackOrder`:
 *
 *   compareBy(seasonNumber ?: Int.MAX, episodeNumber ?: indexNumber ?: Int.MAX, name)
 *
 * The snapshot's [EpisodeCatalogueSnapshot.sortedEpisodes] is the single
 * canonical order feeding smart-play and adjacency, so any divergence here
 * would mis-target resume / next-up. These cases fix the null fallbacks
 * (`Int.MAX`) and the tie-break-by-name behavior.
 */
class EpisodeCatalogueOrderTest {

    @Test
    fun sortsBySeasonThenEpisodeNumber() {
        val s1e2 = episode("s1e2", season = 1, episode = 2)
        val s2e1 = episode("s2e1", season = 2, episode = 1)
        val s1e1 = episode("s1e1", season = 1, episode = 1)

        val sorted = listOf(s1e2, s2e1, s1e1).sortedByPlaybackOrder()

        assertEquals(listOf("s1e1", "s1e2", "s2e1"), sorted.map { it.id })
    }

    @Test
    fun preservesEncounterOrderForEqualKeys() {
        // Two episodes with identical season/episode/name are stable-sorted
        // (Kotlin's sortedWith is stable) — encounter order is preserved.
        val a = MediaItem(id = "a", name = "Same", mediaType = MediaType.EPISODE, seasonNumber = 1, episodeNumber = 1)
        val b = MediaItem(id = "b", name = "Same", mediaType = MediaType.EPISODE, seasonNumber = 1, episodeNumber = 1)

        val sorted = listOf(a, b).sortedByPlaybackOrder()

        assertEquals(listOf("a", "b"), sorted.map { it.id })
    }

    @Test
    fun nullSeasonNumberSortsLast() {
        val withSeason = episode("with", season = 1, episode = 1)
        val noSeason = MediaItem(
            id = "noseason",
            name = "Z",
            mediaType = MediaType.EPISODE,
            seasonNumber = null,
            episodeNumber = 1,
        )

        val sorted = listOf(noSeason, withSeason).sortedByPlaybackOrder()

        assertEquals(listOf("with", "noseason"), sorted.map { it.id })
    }

    @Test
    fun nullEpisodeNumberFallsBackToIndexNumber() {
        // episodeNumber null → indexNumber is the secondary key.
        val byIndex = MediaItem(
            id = "byIndex",
            name = "A",
            mediaType = MediaType.EPISODE,
            seasonNumber = 1,
            episodeNumber = null,
            indexNumber = 2,
        )
        val byEpisode = episode("byEpisode", season = 1, episode = 1)

        val sorted = listOf(byIndex, byEpisode).sortedByPlaybackOrder()

        assertEquals(listOf("byEpisode", "byIndex"), sorted.map { it.id })
    }

    @Test
    fun nullEpisodeAndIndexNumbersSortLastWithinSeason() {
        val numbered = episode("numbered", season = 1, episode = 1)
        val unnumbered = MediaItem(
            id = "unnumbered",
            name = "A",
            mediaType = MediaType.EPISODE,
            seasonNumber = 1,
            episodeNumber = null,
            indexNumber = null,
        )

        val sorted = listOf(unnumbered, numbered).sortedByPlaybackOrder()

        // Unnumbered (Int.MAX) sorts after the numbered episode regardless of name.
        assertEquals(listOf("numbered", "unnumbered"), sorted.map { it.id })
    }

    @Test
    fun nameBreaksTiesWhenSeasonAndEpisodeEqual() {
        val b = MediaItem(id = "b", name = "Bravo", mediaType = MediaType.EPISODE, seasonNumber = 1, episodeNumber = 1)
        val a = MediaItem(id = "a", name = "Alpha", mediaType = MediaType.EPISODE, seasonNumber = 1, episodeNumber = 1)

        val sorted = listOf(b, a).sortedByPlaybackOrder()

        assertEquals(listOf("a", "b"), sorted.map { it.id })
    }

    @Test
    fun crossSeasonOrderingFollowsSeasonNumber() {
        val s3e1 = episode("s3e1", season = 3, episode = 1)
        val s1e5 = episode("s1e5", season = 1, episode = 5)
        val s2e1 = episode("s2e1", season = 2, episode = 1)

        val sorted = listOf(s3e1, s1e5, s2e1).sortedByPlaybackOrder()

        assertEquals(listOf("s1e5", "s2e1", "s3e1"), sorted.map { it.id })
    }

    private fun episode(
        id: String,
        season: Int,
        episode: Int,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        seasonNumber = season,
        episodeNumber = episode,
    )
}
