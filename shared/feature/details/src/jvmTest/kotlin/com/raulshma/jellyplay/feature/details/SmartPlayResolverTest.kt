package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Tests [SmartPlayResolver] — the smart-play decision rules previously trapped
 * as async private methods on DetailViewModel (untestable without a polling
 * helper against `Dispatchers.Default`). Now synchronous and direct.
 */
class SmartPlayResolverTest {

    private fun episode(
        id: String,
        season: Int = 1,
        episode: Int = 1,
        isPlayed: Boolean = false,
        positionTicks: Long? = null,
    ) = MediaItem(
        id = id,
        name = "Ep $id",
        mediaType = MediaType.EPISODE,
        seasonNumber = season,
        episodeNumber = episode,
        isPlayed = isPlayed,
        playbackPositionTicks = positionTicks,
    )

    // ── resolveSeries ──────────────────────────────────────────────────

    @Test
    fun `series empty episodes returns null`() {
        assertNull(SmartPlayResolver.resolveSeries(emptyList()))
    }

    @Test
    fun `series picks first episode with resume progress`() {
        val eps = listOf(
            episode("e1", isPlayed = true),
            episode("e2", positionTicks = 1_000_000L),
            episode("e3"),
        )
        val result = SmartPlayResolver.resolveSeries(eps)!!

        assertEquals("e2", result.episode.id)
        assertEquals(LabelKind.RESUME_EPISODE, result.label)
        assertEquals(1_000_000L, result.startPositionTicks)
    }

    @Test
    fun `series resume ignored when episode is finished`() {
        // Finished episode has position but isPlayed — must not count as resume.
        val eps = listOf(episode("e1", isPlayed = true, positionTicks = 9_000_000L))
        val result = SmartPlayResolver.resolveSeries(eps)!!

        // No unplayed, no resume → replay first.
        assertEquals("e1", result.episode.id)
        assertEquals(LabelKind.REPLAY_EPISODE, result.label)
    }

    @Test
    fun `series next-up when a prior episode was watched`() {
        val eps = listOf(
            episode("e1", isPlayed = true),
            episode("e2"),
        )
        val result = SmartPlayResolver.resolveSeries(eps)!!

        assertEquals("e2", result.episode.id)
        assertEquals(LabelKind.NEXT_UP_EPISODE, result.label)
        assertEquals(0L, result.startPositionTicks)
    }

    @Test
    fun `series plain play when nothing watched before next unplayed`() {
        val eps = listOf(episode("e1"))
        val result = SmartPlayResolver.resolveSeries(eps)!!

        assertEquals("e1", result.episode.id)
        assertEquals(LabelKind.PLAY_EPISODE, result.label)
    }

    @Test
    fun `series counts started-but-unwatched as prior-watched for next-up`() {
        // e1 has progress but is FINISHED (isPlayed) so it's not itself a resume
        // candidate — it only counts as "watched before" the next-unplayed e2.
        val eps = listOf(
            episode("e1", isPlayed = true, positionTicks = 1_000L),
            episode("e2"),
        )
        val result = SmartPlayResolver.resolveSeries(eps)!!

        assertEquals("e2", result.episode.id)
        assertEquals(LabelKind.NEXT_UP_EPISODE, result.label)
    }

    @Test
    fun `series all played replays first from zero`() {
        val eps = listOf(
            episode("e1", isPlayed = true, positionTicks = 9_000_000L),
            episode("e2", isPlayed = true),
        )
        val result = SmartPlayResolver.resolveSeries(eps)!!

        assertEquals("e1", result.episode.id)
        assertEquals(LabelKind.REPLAY_EPISODE, result.label)
        assertEquals(0L, result.startPositionTicks)
    }

    @Test
    fun `series resume beats next-up`() {
        val eps = listOf(
            episode("e1", isPlayed = true),
            episode("e2", positionTicks = 5_000L), // resume candidate
            episode("e3"),                          // first-unplayed
        )
        val result = SmartPlayResolver.resolveSeries(eps)!!

        assertEquals("e2", result.episode.id)
        assertEquals(LabelKind.RESUME_EPISODE, result.label)
    }

    // ── resolveEpisode ─────────────────────────────────────────────────

    @Test
    fun `episode with progress is resume`() {
        val ep = episode("e1", positionTicks = 3_000_000L)
        val result = SmartPlayResolver.resolveEpisode(ep)

        assertEquals("e1", result.episode.id)
        assertEquals(LabelKind.RESUME_EPISODE, result.label)
        assertEquals(3_000_000L, result.startPositionTicks)
    }

    @Test
    fun `episode without progress is play from zero`() {
        val ep = episode("e1")
        val result = SmartPlayResolver.resolveEpisode(ep)

        assertEquals(LabelKind.PLAY_EPISODE, result.label)
        assertEquals(0L, result.startPositionTicks)
    }

    @Test
    fun `finished episode is play not resume`() {
        val ep = episode("e1", isPlayed = true, positionTicks = 9_000_000L)
        val result = SmartPlayResolver.resolveEpisode(ep)

        assertEquals(LabelKind.PLAY_EPISODE, result.label)
    }
}
