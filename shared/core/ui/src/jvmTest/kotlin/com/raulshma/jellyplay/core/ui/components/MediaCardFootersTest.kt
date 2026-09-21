package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Pins the card-footer folds for every card shape at once (poster/wide/
 * offline all consume them):
 *
 *  - [bookFooterPercent] — the admission rules (book gate, played
 *    suppression, no-position suppression) and the percent pipeline
 *    (rounding, clamp, zero suppression, override-wins).
 *  - [mediaCardFooterMeta] — the whole trailing meta-line decision, pinned
 *    branch for branch against the ladder the poster and wide card footers
 *    used to hand-derive inline (so the fold is byte-identical to what the
 *    cards rendered): book-in-progress wins, then remaining time for
 *    unfinished items with a meaningful runtime, then total runtime for
 *    unstarted ones, then nothing.
 */
class MediaCardFootersTest {

    private val minuteTicks = 600_000_000L

    private fun book(positionTicks: Long?, played: Boolean = false) = MediaItem(
        id = "b1",
        name = "Dune",
        mediaType = MediaType.BOOK,
        playbackPositionTicks = positionTicks,
        isPlayed = played,
    )

    private fun video(
        mediaType: MediaType = MediaType.MOVIE,
        runTimeTicks: Long? = 90 * minuteTicks,
        positionTicks: Long? = null,
        played: Boolean = false,
    ) = MediaItem(
        id = "v1",
        name = "Arrival",
        mediaType = mediaType,
        runTimeTicks = runTimeTicks,
        playbackPositionTicks = positionTicks,
        isPlayed = played,
    )

    // ── bookFooterPercent: the book footer fold ─────────────────────────────

    @Test
    fun `percent ticks decode to whole percents`() {
        val item = book(BookProgressPolicy.percentToTicks(0.34))
        assertEquals(34, bookFooterPercent(item))
    }

    @Test
    fun `non-book items never show a book footer`() {
        val movie = MediaItem(
            id = "m1",
            name = "Arrival",
            mediaType = MediaType.MOVIE,
            playbackPositionTicks = 1_000_000L,
        )
        assertNull(bookFooterPercent(movie))
        assertNull(bookFooterPercent(movie, fractionOverride = 0.5f), "even a non-null override cannot admit a non-book")
    }

    @Test
    fun `played books suppress the footer`() {
        assertNull(bookFooterPercent(book(BookProgressPolicy.percentToTicks(0.5), played = true)))
    }

    @Test
    fun `unstarted books show nothing`() {
        assertNull(bookFooterPercent(book(null)))
        assertNull(
            bookFooterPercent(book(BookProgressPolicy.percentToTicks(0.0))),
            "a zero-percent decode is suppressed, not rendered as 0% complete",
        )
    }

    @Test
    fun `a sub-one-percent position rounds to nothing`() {
        val item = book(BookProgressPolicy.percentToTicks(0.001))
        assertNull(bookFooterPercent(item))
    }

    @Test
    fun `the override wins over the item decode`() {
        val item = book(BookProgressPolicy.percentToTicks(0.10))
        assertEquals(75, bookFooterPercent(item, fractionOverride = 0.75f))
    }

    @Test
    fun `percent is clamped into 0-100`() {
        val item = book(1L)
        assertEquals(
            100,
            bookFooterPercent(item, fractionOverride = 1.5f),
            "an out-of-range override clamps instead of rendering >100%",
        )
    }

    // ── mediaCardFooterMeta: the whole meta-line decision ───────────────────

    @Test
    fun `movie with progress shows remaining time`() {
        val meta = mediaCardFooterMeta(video(positionTicks = 30 * minuteTicks))
        val timeLeft = assertIs<MediaCardFooterMeta.TimeLeft>(meta)
        assertEquals("1h 0m", timeLeft.label)
    }

    @Test
    fun `unstarted movie shows total runtime`() {
        val runtime = assertIs<MediaCardFooterMeta.Runtime>(mediaCardFooterMeta(video()))
        assertEquals("1h 30m", runtime.label)
    }

    @Test
    fun `episode follows the same time ladder`() {
        val timeLeft = assertIs<MediaCardFooterMeta.TimeLeft>(
            mediaCardFooterMeta(video(mediaType = MediaType.EPISODE, positionTicks = 80 * minuteTicks)),
        )
        assertEquals("10m", timeLeft.label)
        val runtime = assertIs<MediaCardFooterMeta.Runtime>(
            mediaCardFooterMeta(video(mediaType = MediaType.EPISODE)),
        )
        assertEquals("1h 30m", runtime.label)
    }

    @Test
    fun `book in progress wins over any time label`() {
        val bookWithRuntime = MediaItem(
            id = "b2",
            name = "Dune",
            mediaType = MediaType.BOOK,
            runTimeTicks = 90 * minuteTicks,
            playbackPositionTicks = BookProgressPolicy.percentToTicks(0.34),
        )
        val progress = assertIs<MediaCardFooterMeta.BookProgress>(mediaCardFooterMeta(bookWithRuntime))
        assertEquals(34, progress.percent)
    }

    @Test
    fun `book with stray runtime ticks never renders a duration`() {
        val item = book(null).copy(runTimeTicks = 90 * minuteTicks)
        assertNull(
            mediaCardFooterMeta(item),
            "a book's runtime is meaningless — the video ladder must not fire",
        )
    }

    @Test
    fun `played movie keeps its runtime label but drops the time-left`() {
        // The old ladder rendered the total for played items (total arm gated
        // on NOT having watch progress) — the fold preserves that.
        val runtime = assertIs<MediaCardFooterMeta.Runtime>(
            mediaCardFooterMeta(video(positionTicks = 30 * minuteTicks, played = true)),
        )
        assertEquals("1h 30m", runtime.label)
    }

    @Test
    fun `live-ish item without runtime shows no meta`() {
        assertNull(mediaCardFooterMeta(video(runTimeTicks = null)))
        assertNull(mediaCardFooterMeta(video(runTimeTicks = 0L)))
        assertNull(
            mediaCardFooterMeta(
                video(mediaType = MediaType.LIVE_TV, runTimeTicks = null, positionTicks = 30 * minuteTicks),
            ),
        )
    }

    @Test
    fun `series containers never render runtime meta`() {
        assertNull(mediaCardFooterMeta(video(mediaType = MediaType.SERIES)))
        assertNull(
            mediaCardFooterMeta(video(mediaType = MediaType.SERIES, positionTicks = 30 * minuteTicks)),
        )
    }

    @Test
    fun `position past the runtime renders nothing`() {
        // The old ladder: remaining math returned null and the total arm was
        // gated off by watch progress — no meta, no fallback.
        assertNull(mediaCardFooterMeta(video(positionTicks = 120 * minuteTicks)))
    }

    @Test
    fun `sub-minute runtime renders no total`() {
        // formatRuntimeLabelFromTicks floors below one minute; the fold keeps
        // that floor instead of rendering a bogus "0m".
        assertNull(mediaCardFooterMeta(video(runTimeTicks = 30_000_000L)))
    }
}
