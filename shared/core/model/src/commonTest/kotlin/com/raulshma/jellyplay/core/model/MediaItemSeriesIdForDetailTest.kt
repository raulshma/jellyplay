package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Tests for [MediaItem.seriesIdForDetail] — the single source of truth for the
 * series id a detail entry resolves to for series-scoped operations
 * (seasons/episodes load, smart-play, playlist expansion).
 *
 * Added with the unified detail screen: this lifts the repeated
 * `when (mediaType)` fork out of the provider and ViewModel, so its precedence
 * (series → itself, episode/season → parent [seriesId], anything else → null)
 * is now first-class and worth pinning independently of its call sites.
 */
class MediaItemSeriesIdForDetailTest {

    private fun item(
        id: String = "x",
        mediaType: MediaType,
        seriesId: String? = null,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = mediaType,
        seriesId = seriesId,
    )

    // ── SERIES resolves to itself ────────────────────────────────────────────

    @Test
    fun `series resolves to its own id`() {
        assertEquals(
item(id = "series-1", mediaType = MediaType.SERIES).seriesIdForDetail,
"series-1",
)
    }

    // ── EPISODE / SEASON resolve to their parent seriesId ────────────────────

    @Test
    fun `episode resolves to its parent seriesId`() {
        assertEquals(
item(id = "ep-1", mediaType = MediaType.EPISODE, seriesId = "series-1").seriesIdForDetail,
"series-1",
)
    }

    @Test
    fun `season resolves to its parent seriesId`() {
        assertEquals(
item(id = "season-1", mediaType = MediaType.SEASON, seriesId = "series-1").seriesIdForDetail,
"series-1",
)
    }

    @Test
    fun `episode with null seriesId resolves to null`() {
        // Orphan episode (no parent known) — nothing to scope to.
        assertNull(item(mediaType = MediaType.EPISODE, seriesId = null).seriesIdForDetail)
    }

    @Test
    fun `season with null seriesId resolves to null`() {
        assertNull(item(mediaType = MediaType.SEASON, seriesId = null).seriesIdForDetail)
    }

    // ── Everything else resolves to null ─────────────────────────────────────

    @Test
    fun `movie resolves to null`() {
        assertNull(item(id = "movie-1", mediaType = MediaType.MOVIE).seriesIdForDetail)
    }

    @Test
    fun `album resolves to null`() {
        assertNull(item(mediaType = MediaType.ALBUM).seriesIdForDetail)
    }

    @Test
    fun `collection resolves to null`() {
        assertNull(item(mediaType = MediaType.COLLECTION).seriesIdForDetail)
    }

    @Test
    fun `every non-series media type resolves to null`() {
        // Guard against a future MediaType being silently miscategorized: only
        // SERIES / EPISODE / SEASON may resolve non-null.
        val nonSeriesTypes = MediaType.values().toList() - setOf(
            MediaType.SERIES,
            MediaType.EPISODE,
            MediaType.SEASON,
        )
        nonSeriesTypes.forEach { type ->
            assertNull(
item(mediaType = type, seriesId = "ignored").seriesIdForDetail,
"expected null seriesIdForDetail for $type",
)
        }
    }
}
