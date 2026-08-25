package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class SeerrDiscoverRowTest {

    @Test
    fun compactDiscoverPattern_sumsCorrectly() {
        assertEquals(listOf(3, 2, 3), COMPACT_DISCOVER_PATTERN)
        assertEquals(8, COMPACT_DISCOVER_PATTERN.sum())
    }

    @Test
    fun expandedDiscoverPattern_sumsCorrectly() {
        assertEquals(listOf(5, 4, 6, 5), EXPANDED_DISCOVER_PATTERN)
        assertEquals(20, EXPANDED_DISCOVER_PATTERN.sum())
    }

    @Test
    fun partitionDiscoverItems_chunksListAccordingToPattern() {
        val items = (1..10).map { id ->
            SeerrSearchItem(
                id = id,
                mediaType = if (id % 2 == 0) "movie" else "tv",
                title = "Item $id",
            )
        }

        var index = 0
        val rows = mutableListOf<List<SeerrSearchItem>>()
        var patternIndex = 0
        while (index < items.size) {
            val targetSize = COMPACT_DISCOVER_PATTERN[patternIndex % COMPACT_DISCOVER_PATTERN.size]
            val chunkSize = targetSize.coerceAtMost(items.size - index)
            rows.add(items.subList(index, index + chunkSize))
            index += chunkSize
            patternIndex++
        }

        assertEquals(4, rows.size)
        assertEquals(3, rows[0].size)
        assertEquals(2, rows[1].size)
        assertEquals(3, rows[2].size)
        assertEquals(2, rows[3].size)
    }

    @Test
    fun mediaTypeNormalization_mapsMovieAndTvCorrectly() {
        fun normalizeType(raw: String): String = when {
            raw.equals("movie", ignoreCase = true) -> "movie"
            raw.equals("tv", ignoreCase = true) -> "tv"
            else -> raw
        }

        assertEquals("movie", normalizeType("MOVIE"))
        assertEquals("movie", normalizeType("movie"))
        assertEquals("tv", normalizeType("TV"))
        assertEquals("tv", normalizeType("tv"))
        assertEquals("anime", normalizeType("anime"))
    }
}
