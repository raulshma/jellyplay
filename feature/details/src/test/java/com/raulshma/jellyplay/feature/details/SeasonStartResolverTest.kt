package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure precedence tests for [SeasonStartResolver]. No Android / Compose — the
 * resolver depends only on `core.model`, so this is a plain JUnit suite.
 *
 * Hybrid rule under test: an active resume beats the persisted season; a
 * non-resume smart-play target does NOT (the user's pinned season wins).
 */
class SeasonStartResolverTest {

    private val seasons = listOf(
        season("s1"),
        season("s2"),
        season("s3"),
    )

    @Test
    fun `active resume wins over persisted season`() {
        // Smart target is mid-playback on s1; user had pinned s2. Resume wins.
        val resume = episode(seasonId = "s1", positionTicks = 5_000_000L, played = false)
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = resume,
            currentSeasonId = null,
            persistedSeasonId = "s2",
        )
        assertEquals(0, idx) // s1
    }

    @Test
    fun `no resume and persisted present returns persisted index`() {
        // Smart target is a non-resume (next-up) on s1; persisted is s2 → s2 wins.
        val nextUp = episode(seasonId = "s1", positionTicks = 0L, played = false)
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = nextUp,
            currentSeasonId = null,
            persistedSeasonId = "s2",
        )
        assertEquals(1, idx) // s2
    }

    @Test
    fun `no resume and persisted present wins even with no smart target`() {
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = null,
            currentSeasonId = null,
            persistedSeasonId = "s3",
        )
        assertEquals(2, idx) // s3
    }

    @Test
    fun `no resume, no persisted, smart target present returns smart index`() {
        val nextUp = episode(seasonId = "s3", positionTicks = 0L, played = false)
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = nextUp,
            currentSeasonId = null,
            persistedSeasonId = null,
        )
        assertEquals(2, idx) // s3
    }

    @Test
    fun `played smart target is not a resume so persisted wins`() {
        // Position > 0 but episode is fully played → not a resume (matches
        // SmartPlayResolver.hasResumeProgress: `> 0 && !isPlayed`).
        val replay = episode(seasonId = "s1", positionTicks = 9_000_000L, played = true)
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = replay,
            currentSeasonId = null,
            persistedSeasonId = "s2",
        )
        assertEquals(1, idx) // persisted s2 wins
    }

    @Test
    fun `currentSeasonId wins when no resume and no persisted`() {
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = null,
            currentSeasonId = "s2",
            persistedSeasonId = null,
        )
        assertEquals(1, idx) // s2
    }

    @Test
    fun `non-resume smart target beats currentSeasonId`() {
        // No resume, no pinned season, but smart-play resolved a next-up season
        // (s3) AND the screen was opened from an episode on another season (s1).
        // The smart-play target wins over currentSeasonId so the user lands on
        // the next-up season rather than the incidental episode season.
        val nextUp = episode(seasonId = "s3", positionTicks = 0L, played = false)
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = nextUp,
            currentSeasonId = "s1",
            persistedSeasonId = null,
        )
        assertEquals(2, idx) // s3
    }

    @Test
    fun `returns zero when nothing resolves`() {
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = null,
            currentSeasonId = null,
            persistedSeasonId = null,
        )
        assertEquals(0, idx)
    }

    @Test
    fun `persisted id not in seasons coerces to zero`() {
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = null,
            currentSeasonId = null,
            persistedSeasonId = "ghost-season",
        )
        assertEquals(0, idx) // coerceAtLeast(0)
    }

    @Test
    fun `smart resume season not in seasons coerces to zero`() {
        val resume = episode(seasonId = "ghost-season", positionTicks = 5_000_000L, played = false)
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = seasons,
            smartTargetEpisode = resume,
            currentSeasonId = null,
            persistedSeasonId = null,
        )
        assertEquals(0, idx) // coerceAtLeast(0)
    }

    @Test
    fun `empty seasons returns zero without throwing`() {
        val idx = SeasonStartResolver.resolveInitialSeasonIndex(
            seasons = emptyList(),
            smartTargetEpisode = null,
            currentSeasonId = "s1",
            persistedSeasonId = "s2",
        )
        assertEquals(0, idx)
    }

    private fun season(id: String) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.SEASON,
    )

    private fun episode(
        seasonId: String,
        positionTicks: Long,
        played: Boolean,
    ) = MediaItem(
        id = "ep_$seasonId",
        name = "ep_$seasonId",
        mediaType = MediaType.EPISODE,
        seasonId = seasonId,
        playbackPositionTicks = positionTicks,
        isPlayed = played,
    )
}
