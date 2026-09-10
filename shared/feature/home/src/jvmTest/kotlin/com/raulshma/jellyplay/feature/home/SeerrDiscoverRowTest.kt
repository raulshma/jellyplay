package com.raulshma.jellyplay.feature.home

import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Asserts the PRODUCTION partition/normalization functions — previously the
 * tests reimplemented the pattern constants, the chunking loop and the media
 * type rule locally and asserted against those local copies. Also pins the
 * discover pattern selection ([discoverPatternFor]) and card-width arithmetic
 * ([discoverItemWidth]) shared by the discover rows and the *arr row.
 */
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
    fun discoverPatternFor_compactYieldsTheCompactPattern() {
        assertEquals(COMPACT_DISCOVER_PATTERN, discoverPatternFor(WindowSizeClass.Compact))
    }

    @Test
    fun discoverPatternFor_expandedYieldsTheExpandedPattern() {
        assertEquals(EXPANDED_DISCOVER_PATTERN, discoverPatternFor(WindowSizeClass.Medium))
        assertEquals(EXPANDED_DISCOVER_PATTERN, discoverPatternFor(WindowSizeClass.Expanded))
    }

    @Test
    fun discoverItemWidth_subtractsTheGapsAndDividesByTargetSize() {
        // 360 dp row, 8 dp spacing, 3 cards: (360 - 8*2) / 3 = 114.67 dp.
        assertEquals((344f / 3f).dp, discoverItemWidth(360.dp, 8.dp, 3))
        // 800 dp row, 8 dp spacing, 5 cards: (800 - 8*4) / 5 = 153.6 dp.
        assertEquals((768f / 5f).dp, discoverItemWidth(800.dp, 8.dp, 5))
    }

    @Test
    fun partitionDiscoverRows_chunksListAccordingToPattern() {
        val items = (1..10).map { id ->
            SeerrSearchItem(
                id = id,
                mediaType = if (id % 2 == 0) "movie" else "tv",
                title = "Item $id",
            )
        }

        val rows = partitionDiscoverRows(items, COMPACT_DISCOVER_PATTERN)

        assertEquals(4, rows.size)
        assertEquals(3, rows[0].size)
        assertEquals(2, rows[1].size)
        assertEquals(3, rows[2].size)
        assertEquals(2, rows[3].size)
        // Rows cover the items in order — no drops, no duplicates.
        assertEquals(items.map { it.id }, rows.flatten().map { it.id })
    }

    @Test
    fun partitionDiscoverRows_patternCycles_forLongLists() {
        val items = (1..20).map { id ->
            SeerrSearchItem(id = id, mediaType = "movie", title = "Item $id")
        }

        val rows = partitionDiscoverRows(items, EXPANDED_DISCOVER_PATTERN)

        assertEquals(listOf(5, 4, 6, 5), rows.map { it.size })
    }

    @Test
    fun partitionDiscoverRows_emptyInput_yieldsNoRows() {
        assertTrue(partitionDiscoverRows(emptyList(), COMPACT_DISCOVER_PATTERN).isEmpty())
    }

    @Test
    fun normalizeSeerrMediaType_foldsMovieAndTv_passesOthersThrough() {
        assertEquals("movie", normalizeSeerrMediaType("MOVIE"))
        assertEquals("movie", normalizeSeerrMediaType("movie"))
        assertEquals("tv", normalizeSeerrMediaType("TV"))
        assertEquals("tv", normalizeSeerrMediaType("tv"))
        assertEquals("anime", normalizeSeerrMediaType("anime"))
    }
}
