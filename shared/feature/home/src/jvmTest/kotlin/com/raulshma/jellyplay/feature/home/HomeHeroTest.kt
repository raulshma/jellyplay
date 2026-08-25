package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class HomeHeroTest {

    @Test
    fun heroItem_taglineAndOverview_handling() {
        val item = MediaItem(
            id = "hero1",
            name = "Interstellar",
            overview = "A team of explorers travel through a wormhole in space.",
            mediaType = MediaType.MOVIE,
            officialRating = "PG-13",
            communityRating = 8.6f,
        )

        assertEquals("Interstellar", item.name)
        assertEquals("PG-13", item.officialRating)
        assertEquals(8.6f, item.communityRating ?: 0f, 0.01f)
        assertTrue(item.overview!!.contains("wormhole"))
    }

    @Test
    fun heroItem_fallbackWhenNoOverview() {
        val item = MediaItem(
            id = "hero2",
            name = "Untitled Show",
            mediaType = MediaType.SERIES,
        )

        val overviewText = item.overview ?: "No description available."
        assertEquals("No description available.", overviewText)
    }
}
