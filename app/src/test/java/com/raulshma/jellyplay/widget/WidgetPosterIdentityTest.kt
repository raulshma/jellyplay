package com.raulshma.jellyplay.widget

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the widget poster-identity policy: both Jellyfin-server flavours
 * resolve the series id first (an episode row shows its series poster, a
 * movie row its own id), and each flavour pins its own cell width — the CW
 * single column decodes narrower than the library-recommendation grid cells.
 * The Seerr widget is out of scope by design: its posters are TMDB CDN urls
 * built from the search item's poster path, not server image ids.
 */
class WidgetPosterIdentityTest {

    private fun item(id: String, seriesId: String? = null) = MediaItem(
        id = id,
        name = "Item $id",
        mediaType = if (seriesId != null) MediaType.EPISODE else MediaType.MOVIE,
        seriesId = seriesId,
    )

    @Test
    fun `continue-watching rows key by the series id when present`() {
        assertEquals("series-9", WidgetPosterIdentity.continueWatchingPosterImageId(item("ep-1", seriesId = "series-9")))
    }

    @Test
    fun `continue-watching rows fall back to the item id for non-episodes`() {
        assertEquals("movie-1", WidgetPosterIdentity.continueWatchingPosterImageId(item("movie-1")))
    }

    @Test
    fun `library-recommendation rows prefer the series id too`() {
        assertEquals("series-4", WidgetPosterIdentity.libraryRecommendationsPosterImageId(item("ser-1", seriesId = "series-4")))
        assertEquals("movie-2", WidgetPosterIdentity.libraryRecommendationsPosterImageId(item("movie-2")))
    }

    @Test
    fun `each flavour pins its own poster width`() {
        assertEquals(300, WidgetPosterIdentity.CONTINUE_WATCHING_POSTER_MAX_WIDTH)
        assertEquals(400, WidgetPosterIdentity.LIBRARY_RECOMMENDATIONS_POSTER_MAX_WIDTH)
    }
}
