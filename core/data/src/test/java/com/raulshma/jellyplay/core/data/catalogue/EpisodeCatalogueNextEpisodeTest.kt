package com.raulshma.jellyplay.core.data.catalogue

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure next-up / adjacency logic for [NextEpisode] — the catalogue-side twin of
 * `feature.details.SmartPlayResolverTest`. No MockK, no runTest, no dispatcher
 * rule: the decision is a pure function of the sorted list.
 *
 * These cases pin the resolution order (resume > next-unplayed > replay-first)
 * and the NEXT_UP-vs-PLAY distinction ported verbatim from
 * `SmartPlayResolver.resolveSeries`, plus the prev/next neighbor helpers.
 */
class EpisodeCatalogueNextEpisodeTest {

    @Test
    fun forSorted_empty_returnsNoneWithNullEpisode() {
        val result = NextEpisode.forSorted(emptyList())
        assertEquals(NextUpKind.NONE, result.kind)
        assertNull(result.episode)
        assertEquals(0L, result.startPositionTicks)
    }

    @Test
    fun forSorted_unplayedFirstEpisode_returnsPlay() {
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        val ep2 = episode("e2", 1, 2, isPlayed = false)

        val result = NextEpisode.forSorted(listOf(ep1, ep2))

        assertEquals(NextUpKind.PLAY, result.kind)
        assertEquals("e1", result.episode?.id)
        assertEquals(0L, result.startPositionTicks)
    }

    @Test
    fun forSorted_resumeProgress_returnsResume() {
        val ep1 = episode("e1", 1, 1, isPlayed = false)
        val ep2 = episode("e2", 1, 2, isPlayed = false, positionTicks = 50_000_000L)

        val result = NextEpisode.forSorted(listOf(ep1, ep2))

        assertEquals(NextUpKind.RESUME, result.kind)
        assertEquals("e2", result.episode?.id)
        assertEquals(50_000_000L, result.startPositionTicks)
    }

    @Test
    fun forSorted_earlierEpisodePlayed_returnsNextUp() {
        val ep1 = episode("e1", 1, 1, isPlayed = true)
        val ep2 = episode("e2", 1, 2, isPlayed = false)

        val result = NextEpisode.forSorted(listOf(ep1, ep2))

        assertEquals(NextUpKind.NEXT_UP, result.kind)
        assertEquals("e2", result.episode?.id)
    }

    @Test
    fun forSorted_allPlayed_returnsReplayOnFirst() {
        val ep1 = episode("e1", 1, 1, isPlayed = true)
        val ep2 = episode("e2", 1, 2, isPlayed = true)

        val result = NextEpisode.forSorted(listOf(ep1, ep2))

        assertEquals(NextUpKind.REPLAY, result.kind)
        assertEquals("e1", result.episode?.id)
        assertEquals(0L, result.startPositionTicks)
    }

    @Test
    fun forSorted_resumeTakesPrecedenceOverNextUp() {
        // ep1 has resume progress AND ep2 is unplayed: resume wins.
        val ep1 = episode("e1", 1, 1, isPlayed = false, positionTicks = 10_000_000L)
        val ep2 = episode("e2", 1, 2, isPlayed = false)

        val result = NextEpisode.forSorted(listOf(ep1, ep2))

        assertEquals(NextUpKind.RESUME, result.kind)
        assertEquals("e1", result.episode?.id)
    }

    @Test
    fun forSorted_playedEpisodeWithPositionIsNotAResumeTarget() {
        // A played episode (>95% earlier) still reports its position, but
        // hasResumeProgress() requires !isPlayed — so it must NOT be chosen as
        // RESUME. With everything played, the result is REPLAY.
        val ep1 = episode("e1", 1, 1, isPlayed = true, positionTicks = 99_000_000L)

        val result = NextEpisode.forSorted(listOf(ep1))

        assertEquals(NextUpKind.REPLAY, result.kind)
        assertEquals("e1", result.episode?.id)
    }

    // ── neighbors ───────────────────────────────────────────────────────

    @Test
    fun neighbors_returnsPreviousAndNextAroundCurrent() {
        val eps = listOf(
            episode("e1", 1, 1),
            episode("e2", 1, 2),
            episode("e3", 1, 3),
        )

        val (previous, next) = NextEpisode.neighbors(eps, "e2")

        assertEquals("e1", previous?.id)
        assertEquals("e3", next?.id)
    }

    @Test
    fun neighbors_atStart_returnsNullPrevious() {
        val eps = listOf(episode("e1", 1, 1), episode("e2", 1, 2))

        val (previous, next) = NextEpisode.neighbors(eps, "e1")

        assertNull(previous)
        assertEquals("e2", next?.id)
    }

    @Test
    fun neighbors_atEnd_returnsNullNext() {
        val eps = listOf(episode("e1", 1, 1), episode("e2", 1, 2))

        val (previous, next) = NextEpisode.neighbors(eps, "e2")

        assertEquals("e1", previous?.id)
        assertNull(next)
    }

    @Test
    fun neighbors_currentAbsent_returnsBothNull() {
        val eps = listOf(episode("e1", 1, 1))

        val (previous, next) = NextEpisode.neighbors(eps, "missing")

        assertNull(previous)
        assertNull(next)
    }

    private fun episode(
        id: String,
        season: Int,
        episode: Int,
        isPlayed: Boolean = false,
        positionTicks: Long? = null,
    ) = MediaItem(
        id = id,
        name = "Episode $episode",
        mediaType = MediaType.EPISODE,
        seasonNumber = season,
        episodeNumber = episode,
        isPlayed = isPlayed,
        playbackPositionTicks = positionTicks,
    )
}
