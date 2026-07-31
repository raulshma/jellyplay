package com.raulshma.jellyplay.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRecommendationsWidgetWorkerTest {

    @Test
    fun buildMediaDeepLink_createsCorrectCustomSchemeUri() {
        val deepLink = WidgetDeepLinks.buildMediaDeepLink("item-abc-123")
        assertTrue(deepLink.contains("://media/item-abc-123"))
    }

    @Test
    fun buildSeerrDeepLink_createsCorrectSeerrUri() {
        val seerrLink = WidgetDeepLinks.buildSeerrDeepLink(105, "movie")
        assertTrue(seerrLink.contains("://seerr/105/movie"))
    }

    @Test
    fun buildSeerrDeepLink_tvShow_formatsType() {
        val seerrLink = WidgetDeepLinks.buildSeerrDeepLink(200, "tv")
        assertEquals("jellyplay://seerr/200/tv", seerrLink)
    }
}
