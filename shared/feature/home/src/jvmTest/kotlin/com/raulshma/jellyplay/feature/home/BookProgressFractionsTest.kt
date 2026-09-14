package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.BookTocCache
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Pins the shared Continue-Reading fraction decode
 * ([decodeBookProgressFractions]) the online refresher and the offline gate
 * both run: exact page fractions where the TOC cache knows the page count,
 * percent fallback otherwise, and per-item best-effort — a cache miss, a
 * zero pageCount (reflowable/unknown), or a throwing read degrades that one
 * item, never the whole row. Also pins the card-side lookup fold
 * ([bookProgressFractionFor]) both poster rows render through: decoded
 * fraction where the map knows the item, per-item fallback otherwise.
 */
class BookProgressFractionsTest {

    private class FakeTocCache(
        private val caches: Map<String, BookTocCache> = emptyMap(),
        private val throwFor: Set<String> = emptySet(),
    ) : BookTocCacheRepository {
        override fun observeToc(itemId: String) = flowOf(caches[itemId])
        override suspend fun getToc(itemId: String): BookTocCache? {
            if (itemId in throwFor) error("cache read failed")
            return caches[itemId]
        }
        override suspend fun putToc(itemId: String, format: BookFormat, pageCount: Int, entries: List<BookTocEntry>) = Unit
        override suspend fun deleteToc(itemId: String) = Unit
    }

    private fun toc(pageCount: Int) = BookTocCache(
        itemId = "b1",
        format = BookFormat.PDF,
        pageCount = pageCount,
        entries = emptyList(),
        updatedAt = 0L,
    )

    private fun book(id: String, positionTicks: Long?) = MediaItem(
        id = id,
        name = "Book $id",
        mediaType = MediaType.BOOK,
        playbackPositionTicks = positionTicks,
    )

    @Test
    fun `a paged book decodes its exact page fraction from the cached page count`() = runTest {
        // Page index 4 (0-based) of a 200-page book → (4+1)/200.
        val item = book("b1", BookProgressPolicy.pageToTicks(4))
        val fractions = FakeTocCache(mapOf("b1" to toc(200))).decodeBookProgressFractions(listOf(item))
        assertEquals(mapOf("b1" to 5f / 200f), fractions)
    }

    @Test
    fun `a cache miss or unknown page count falls back to the percent reading`() = runTest {
        val pagedWithoutCount = book("b1", BookProgressPolicy.pageToTicks(50))
        val reflowable = book("b2", BookProgressPolicy.percentToTicks(0.35))
        val fractions = FakeTocCache(mapOf("b1" to toc(0)))
            .decodeBookProgressFractions(listOf(pagedWithoutCount, reflowable))
        assertEquals(
            mapOf(
                "b1" to BookProgressPolicy.ticksToPercent(BookProgressPolicy.pageToTicks(50)).toFloat(),
                "b2" to 0.35f,
            ),
            fractions,
        )
    }

    @Test
    fun `an item with no saved position is skipped`() = runTest {
        val fractions = FakeTocCache(mapOf("b1" to toc(200)))
            .decodeBookProgressFractions(listOf(book("b1", null), book("b2", 0L)))
        assertTrue(fractions.isEmpty())
    }

    @Test
    fun `a throwing cache read degrades that item to the percent fallback`() = runTest {
        // b2 has a cached page count (would decode 51/200 exactly) but its
        // read throws — the decode must fall back to b2's percent reading,
        // not skip it and not fail the row.
        val ok = book("b1", BookProgressPolicy.pageToTicks(4))
        val failing = book("b2", BookProgressPolicy.pageToTicks(50))
        val fractions = FakeTocCache(mapOf("b1" to toc(200), "b2" to toc(200)), throwFor = setOf("b2"))
            .decodeBookProgressFractions(listOf(ok, failing))
        assertEquals(
            mapOf(
                "b1" to 5f / 200f,
                "b2" to BookProgressPolicy.ticksToPercent(BookProgressPolicy.pageToTicks(50)).toFloat(),
            ),
            fractions,
        )
    }

    @Test
    fun `cancellation still propagates through the cache read`() = runTest {
        val cache = object : BookTocCacheRepository {
            override fun observeToc(itemId: String) = flowOf(null)
            override suspend fun getToc(itemId: String): BookTocCache = throw CancellationException("cancelled")
            override suspend fun putToc(itemId: String, format: BookFormat, pageCount: Int, entries: List<BookTocEntry>) = Unit
            override suspend fun deleteToc(itemId: String) = Unit
        }
        assertFailsWith<CancellationException> {
            cache.decodeBookProgressFractions(listOf(book("b1", BookProgressPolicy.percentToTicks(0.5))))
        }
    }

    @Test
    fun `an empty row decodes to an empty map`() = runTest {
        assertNull(FakeTocCache().getToc("missing"))
        assertEquals(emptyMap(), FakeTocCache().decodeBookProgressFractions(emptyList()))
    }

    @Test
    fun `card lookup prefers the decoded map and falls back per item`() {
        val lookup = bookProgressFractionFor<MediaItem>(
            fractions = mapOf("b1" to 0.25f),
            idOf = { it.id },
            fallback = { if (it.id == "b2") 0.5f else null },
        )

        // Decoded fraction wins over any fallback.
        assertEquals(0.25f, lookup(book("b1", null)))
        // Map miss → the item's percent fallback.
        assertEquals(0.5f, lookup(book("b2", null)))
        // Neither map nor fallback knows the item → no bar override (the
        // card renders its own default progress).
        assertNull(lookup(book("b3", null)))
    }
}
