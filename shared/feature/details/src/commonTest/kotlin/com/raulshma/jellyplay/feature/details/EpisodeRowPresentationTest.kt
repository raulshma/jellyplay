package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MissingEpisodeReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure fold tests for [EpisodeRowPresentation] — the watch-state decisions
 * [EpisodeCard] and `CompactEpisodeRow` used to hand-copy in parallel. The
 * fold must keep deciding exactly what the rows rendered: dim rule (played OR
 * virtual), play affordance suppression on virtual episodes, progress overlay
 * vs played bar, preference-gated watched tag, downloaded-gated delete, and
 * the runtime/"Xm left"/last-watched metadata gates. No Android / Compose.
 */
class EpisodeRowPresentationTest {

    @Test
    fun `plain unplayed episode shows only the play affordance`() {
        val p = EpisodeRowPresentation.from(episode(), hideThumbnail = false, isDownloaded = false)

        assertFalse(p.isDimmed)
        assertFalse(p.isPlayed)
        assertFalse(p.isVirtual)
        assertFalse(p.hasWatchProgress)
        assertNull(p.progressFraction)
        assertFalse(p.showPlayedBar)
        assertTrue(p.showPlayAffordance)
        assertFalse(p.showVirtualBadge)
        assertFalse(p.showWatchedTag)
        assertFalse(p.showDeleteAffordance)
        assertFalse(p.showSpoilerPlaceholder)
        assertFalse(p.showLastWatched)
        assertNull(p.remainingTime)
        assertNull(p.totalTime)
    }

    @Test
    fun `played episode dims and trades the progress overlay for played bar + watched tag`() {
        val p = EpisodeRowPresentation.from(
            episode(isPlayed = true, runTimeTicks = TICKS),
            hideThumbnail = false,
            isDownloaded = false,
            showWatchedCheckmark = true,
        )

        assertTrue(p.isDimmed)
        assertTrue(p.isPlayed)
        assertFalse(p.hasWatchProgress)
        assertNull(p.progressFraction)
        assertTrue(p.showPlayedBar)
        assertTrue(p.showWatchedTag)
        assertTrue(p.showLastWatched)
        assertNull(p.remainingTime)
        assertNotNull(p.totalTime)
    }

    @Test
    fun `watched tag respects the card display preference`() {
        val played = episode(isPlayed = true)

        val withPref = EpisodeRowPresentation.from(played, hideThumbnail = false, isDownloaded = false, showWatchedCheckmark = true)
        val withoutPref = EpisodeRowPresentation.from(played, hideThumbnail = false, isDownloaded = false, showWatchedCheckmark = false)

        assertTrue(withPref.showWatchedTag)
        assertFalse(withoutPref.showWatchedTag)
    }

    @Test
    fun `in-progress episode keeps the progress overlay and the remaining-time line`() {
        val p = EpisodeRowPresentation.from(
            episode(runTimeTicks = TICKS, positionTicks = TICKS / 2),
            hideThumbnail = false,
            isDownloaded = false,
        )

        assertTrue(p.hasWatchProgress)
        assertEquals(0.5f, p.progressFraction)
        assertFalse(p.showPlayedBar)
        assertFalse(p.isDimmed)
        assertTrue(p.showPlayAffordance)
        assertTrue(p.showLastWatched)
        assertFalse(p.showWatchedTag)
        assertNotNull(p.remainingTime)
        assertNotNull(p.totalTime)
    }

    @Test
    fun `in-progress episode without a runtime has a null fraction but stays on the progress branch`() {
        // The rows branch on hasWatchProgress (rendering the overlay at a
        // coerced 0 fraction), not on the fraction itself — the fold must keep
        // the two signals distinguishable.
        val p = EpisodeRowPresentation.from(
            episode(runTimeTicks = null, positionTicks = 5_000_000L),
            hideThumbnail = false,
            isDownloaded = false,
        )

        assertTrue(p.hasWatchProgress)
        assertNull(p.progressFraction)
        assertFalse(p.showPlayedBar)
        assertNull(p.remainingTime)
        assertNull(p.totalTime)
    }

    @Test
    fun `virtual episode dims, suppresses play and delete, badges itself`() {
        val p = EpisodeRowPresentation.from(
            episode(isVirtual = true, reason = MissingEpisodeReason.UNAIRED),
            hideThumbnail = false,
            isDownloaded = true,
        )

        assertTrue(p.isDimmed)
        assertTrue(p.isVirtual)
        assertFalse(p.showPlayAffordance)
        assertTrue(p.showVirtualBadge)
        // Nothing on disk to delete even though the host reports downloaded.
        assertFalse(p.showDeleteAffordance)
        assertFalse(p.showPlayedBar)
        assertFalse(p.hasWatchProgress)
        assertFalse(p.showLastWatched)
    }

    @Test
    fun `delete affordance shows only for downloaded non-virtual episodes`() {
        val plain = episode()

        val downloaded = EpisodeRowPresentation.from(plain, hideThumbnail = false, isDownloaded = true)
        val remote = EpisodeRowPresentation.from(plain, hideThumbnail = false, isDownloaded = false)

        assertTrue(downloaded.showDeleteAffordance)
        assertFalse(remote.showDeleteAffordance)
    }

    @Test
    fun `hidden thumbnail swaps artwork for the spoiler placeholder`() {
        val p = EpisodeRowPresentation.from(episode(), hideThumbnail = true, isDownloaded = false)

        assertTrue(p.showSpoilerPlaceholder)
    }

    @Test
    fun `played-but-positionless episode gates the last-watched line on isPlayed`() {
        val p = EpisodeRowPresentation.from(episode(isPlayed = true), hideThumbnail = false, isDownloaded = false)

        // No saved position, but fully played → any watch activity.
        assertTrue(p.showLastWatched)
    }

    // ── Fixtures ──

    private companion object {
        const val TICKS = 10_000_000L
    }

    private fun episode(
        isPlayed: Boolean = false,
        isVirtual: Boolean = false,
        reason: MissingEpisodeReason? = null,
        runTimeTicks: Long? = null,
        positionTicks: Long? = null,
    ) = MediaItem(
        id = "e1",
        name = "Episode 1",
        mediaType = com.raulshma.jellyplay.core.model.MediaType.EPISODE,
        indexNumber = 1,
        runTimeTicks = runTimeTicks,
        playbackPositionTicks = positionTicks,
        isPlayed = isPlayed,
        isVirtual = isVirtual,
        missingReason = reason,
    )
}
