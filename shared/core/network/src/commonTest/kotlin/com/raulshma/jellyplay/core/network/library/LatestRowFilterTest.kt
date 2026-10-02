package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the #168 classic-rows latest row: the client-side kind fold (the
 * belt-and-braces behind the classic pool's Episode pin), the per-folder
 * pool policy ([classicLatestEpisodePool]), the pre-12 grouping twin
 * ([toClassicLatestCards]), and the degraded Series synthesis.
 */
class LatestRowFilterTest {

    // ── The kind fold ([toFilteredLatestRows]) ────────────────────────────

    @Test
    fun `the fold drops series and season rows when narrowed to episodes`() {
        val rows = listOf(
            row("series", MediaType.SERIES),
            row("season", MediaType.SEASON),
            row("episode", MediaType.EPISODE),
        )

        assertEquals(
            listOf("episode"),
            rows.toFilteredLatestRows(maxParentalRating = null, allowedKinds = setOf(MediaType.EPISODE)).map { it.id },
        )
    }

    @Test
    fun `a null allowed-kinds set leaves the fold unconstrained`() {
        val rows = listOf(row("series", MediaType.SERIES), row("season", MediaType.SEASON))

        assertEquals(
            listOf("series", "season"),
            rows.toFilteredLatestRows(maxParentalRating = null, allowedKinds = null).map { it.id },
        )
    }

    @Test
    fun `the fold chains the parental filter`() {
        val rows = listOf(
            row("pg-episode", MediaType.EPISODE, officialRating = "PG"),
            row("r-episode", MediaType.EPISODE, officialRating = "R"),
        )

        assertEquals(
            listOf("pg-episode"),
            rows.toFilteredLatestRows(maxParentalRating = 13, allowedKinds = setOf(MediaType.EPISODE)).map { it.id },
        )
    }

    // ── The per-folder pool policy ([classicLatestEpisodePool]) ───────────
    // TV folders fetch the 10.x grouping pool (limit × the 10.x server's own
    // 5× overfetch); movies/mixed stay unconstrained — both generations
    // resolve a movies folder to plain Movie rows, and mixed routing is
    // server-side.

    @Test
    fun `tv folders get the pool, movie mixed and unknown folders stay unconstrained`() {
        assertEquals(16 * CLASSIC_LATEST_POOL_MULTIPLIER, classicLatestEpisodePool("tvshows", limit = 16))
        assertEquals(null, classicLatestEpisodePool("movies", limit = 16))
        assertEquals(null, classicLatestEpisodePool("mixed", limit = 16))
        assertEquals(null, classicLatestEpisodePool("books", limit = 16))
        assertEquals(null, classicLatestEpisodePool(null, limit = 16))
    }

    @Test
    fun `the pool multiplier is the pre-12 server overfetch`() {
        assertEquals(5, CLASSIC_LATEST_POOL_MULTIPLIER)
    }

    // ── The pre-12 grouping twin ([toClassicLatestCards]) ─────────────────
    // Mirrors UserViewManager.GetLatestItems + the GetLatestMedia controller
    // pick: first-encounter card order, appends never open new cards, stop
    // at `limit` cards, >1 episode per series → Series card, exactly 1 → the
    // episode itself.

    @Test
    fun `a series with multiple pool episodes becomes one series card at its first encounter`() {
        val pool = listOf(
            episode("e1", series = "sA"),
            episode("e2", series = "sB"),
            episode("e3", series = "sA"),
        )

        val cards = pool.toClassicLatestCards(limit = 16)

        assertEquals(
            listOf(
                ClassicLatestCard.Grouped("sA", listOf(pool[0], pool[2])),
                ClassicLatestCard.Single(pool[1]),
            ),
            cards,
        )
    }

    @Test
    fun `a series with exactly one pool episode renders the episode itself`() {
        val pool = listOf(episode("e1", series = "sA"))

        assertEquals(listOf(ClassicLatestCard.Single(pool[0])), pool.toClassicLatestCards(limit = 16))
    }

    @Test
    fun `grouping stops at the limit cards - later episodes of listed series never arrive`() {
        val pool = listOf(
            episode("e1", series = "sA"),
            episode("e2", series = "sB"),
            episode("e3", series = "sA"), // appends to sA, card count stays 2
            episode("e4", series = "sC"), // third card → break; nothing after runs
            episode("e5", series = "sB"), // past the break: unreachable
        )

        val cards = pool.toClassicLatestCards(limit = 3)

        assertEquals(3, cards.size)
        assertEquals(ClassicLatestCard.Grouped("sA", listOf(pool[0], pool[2])), cards[0])
        // sB stays a single-episode card: e5 never appended past the break.
        assertEquals(ClassicLatestCard.Single(pool[1]), cards[1])
        assertEquals(ClassicLatestCard.Single(pool[3]), cards[2])
    }

    @Test
    fun `the break fires on the item that fills the row - no append lands after it`() {
        // limit 2: e2 fills the row and breaks the walk immediately, so e3's
        // append to sA never happens — 10.x checks the limit after EVERY
        // item. (Pins the break placement: letting an at-limit append skip
        // the check would re-open the walk and overflow the row with e4.)
        val pool = listOf(
            episode("e1", series = "sA"),
            episode("e2", series = "sB"),
            episode("e3", series = "sA"),
            episode("e4", series = "sC"),
        )

        val cards = pool.toClassicLatestCards(limit = 2)

        assertEquals(2, cards.size)
        assertEquals(ClassicLatestCard.Single(pool[0]), cards[0])
        assertEquals(ClassicLatestCard.Single(pool[1]), cards[1])
    }

    @Test
    fun `an episode without a series id stands alone like a null index container`() {
        val pool = listOf(
            episode("loose", series = null),
            episode("e2", series = "sA"),
        )

        assertEquals(
            listOf(
                ClassicLatestCard.Single(pool[0]),
                ClassicLatestCard.Single(pool[1]),
            ),
            pool.toClassicLatestCards(limit = 16),
        )
    }

    @Test
    fun `a zero or negative limit yields no cards and an empty pool stays empty`() {
        assertEquals(emptyList<ClassicLatestCard>(), listOf(episode("e1", "sA")).toClassicLatestCards(limit = 0))
        assertEquals(emptyList<ClassicLatestCard>(), emptyList<MediaItem>().toClassicLatestCards(limit = 16))
    }

    // ── The degraded Series synthesis ([synthesizedSeries]) ───────────────

    @Test
    fun `the synthesized series card carries the series id, name and the group size as child count`() {
        val first = episode("e1", series = "sA", seriesName = "Show A")
        val card = ClassicLatestCard.Grouped("sA", listOf(first, episode("e2", series = "sA", seriesName = "Show A")))

        val synthesized = card.synthesizedSeries()

        assertEquals("sA", synthesized.id)
        assertEquals("Show A", synthesized.name)
        assertEquals(MediaType.SERIES, synthesized.mediaType)
        assertEquals(2, synthesized.childCount)
    }

    private fun row(
        id: String,
        mediaType: MediaType,
        officialRating: String? = null,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = mediaType,
        officialRating = officialRating,
    )

    private fun episode(id: String, series: String?, seriesName: String? = null) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.EPISODE,
        seriesId = series,
        seriesName = seriesName,
    )
}
