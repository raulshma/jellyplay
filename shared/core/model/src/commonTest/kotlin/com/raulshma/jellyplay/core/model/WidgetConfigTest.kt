package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the invariants of the widget source taxonomies — the user-facing labels
 * and descriptions the widget-config screens render, and the slim widget item
 * shapes' count bounds:
 *
 *  - Every [LibraryRecommendationsSource] and [SeerrWidgetSource] maps to its
 *    documented display name + description (these strings are rendered as-is).
 *  - [WidgetConfig]'s continue-watching count bounds are the constants the
 *    config UI clamps against (3..20, default 10).
 */
class WidgetConfigTest {

    @Test
    fun `library source labels match the documented copy`() {
        assertEquals("Because you watched", LibraryRecommendationsSource.SIMILAR_TO_RECENT.displayName)
        assertEquals("Latest in your library", LibraryRecommendationsSource.LATEST.displayName)
        assertEquals("Your favorites", LibraryRecommendationsSource.FAVORITES.displayName)
        assertEquals("Surprise me", LibraryRecommendationsSource.SURPRISE_ME.displayName)
    }

    @Test
    fun `seerr source labels match the documented copy`() {
        assertEquals("Trending this week", SeerrWidgetSource.TRENDING.displayName)
        assertEquals("Popular movies", SeerrWidgetSource.POPULAR_MOVIES.displayName)
        assertEquals("Popular TV", SeerrWidgetSource.POPULAR_TV.displayName)
        assertEquals("Upcoming movies", SeerrWidgetSource.UPCOMING_MOVIES.displayName)
        assertEquals("Upcoming TV", SeerrWidgetSource.UPCOMING_TV.displayName)
    }

    @Test
    fun `every source carries a non-blank label and description`() {
        for (source in LibraryRecommendationsSource.entries) {
            assertTrue(source.displayName.isNotBlank(), source.name)
            assertTrue(source.description.isNotBlank(), source.name)
        }
        for (source in SeerrWidgetSource.entries) {
            assertTrue(source.displayName.isNotBlank(), source.name)
            assertTrue(source.description.isNotBlank(), source.name)
        }
    }

    @Test
    fun `widget count bounds are stable constants`() {
        assertEquals(10, WidgetConfig.DEFAULT_CONTINUE_WATCHING_ITEM_COUNT)
        assertEquals(3, WidgetConfig.MIN_CONTINUE_WATCHING_ITEM_COUNT)
        assertEquals(20, WidgetConfig.MAX_CONTINUE_WATCHING_ITEM_COUNT)
        assertEquals(10, WidgetConfig().continueWatchingItemCount)
    }

    @Test
    fun `widget config defaults enable artwork and progress`() {
        val config = WidgetConfig()
        assertEquals(LibraryRecommendationsSource.LATEST, config.librarySource)
        assertEquals(SeerrWidgetSource.TRENDING, config.seerrSource)
        assertEquals(true, config.nowPlayingShowArtwork)
        assertEquals(true, config.nowPlayingShowProgress)
    }
}
