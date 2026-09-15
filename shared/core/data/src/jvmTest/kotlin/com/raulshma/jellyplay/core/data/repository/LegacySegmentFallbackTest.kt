package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegmentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [legacySegmentFallback] — the legacy intro/credit fallback synthesis
 * moved verbatim out of [PlaybackRepositoryImpl.getMediaSegments] — against
 * fixed payloads: the exact segment shapes (ids, ticks, types), the
 * hasIntro/hasCredits gating, and the intro-before-outro ordering. The facade
 * tests pin the fetch/caching choreography; what is pinned HERE is the
 * mapping itself, including edges the facade suite never exercises.
 */
class LegacySegmentFallbackTest {

    private fun intro(
        itemId: String = "item-1",
        start: Long = 100L,
        end: Long = 200L,
    ) = IntroTimestamps(itemId = itemId, introStartTicks = start, introEndTicks = end)

    private fun credits(
        itemId: String = "item-1",
        start: Long = 300L,
        end: Long = 400L,
    ) = CreditTimestamps(itemId = itemId, creditStartTicks = start, creditEndTicks = end)

    @Test
    fun `both payloads synthesize an intro then an outro segment`() {
        val segments = legacySegmentFallback(intro(), credits())

        assertEquals(2, segments.size)
        assertEquals(MediaSegmentType.INTRO, segments[0].type)
        assertEquals(MediaSegmentType.OUTRO, segments[1].type)
        assertEquals(100L, segments[0].startTicks)
        assertEquals(200L, segments[0].endTicks)
        assertEquals(300L, segments[1].startTicks)
        assertEquals(400L, segments[1].endTicks)
    }

    @Test
    fun `segment ids and itemIds derive from the timestamps' own itemId`() {
        // The server echoes the item it answered for — the synthesized id is
        // a stable identity off THAT id, not the requested one.
        val segments = legacySegmentFallback(intro(itemId = "echoed-1"), credits(itemId = "echoed-2"))

        assertEquals("legacy-intro-echoed-1", segments[0].id)
        assertEquals("echoed-1", segments[0].itemId)
        assertEquals("legacy-outro-echoed-2", segments[1].id)
        assertEquals("echoed-2", segments[1].itemId)
    }

    @Test
    fun `credits map to the OUTRO type - the segment vocabulary has no credit type`() {
        val segments = legacySegmentFallback(intro = null, credits = credits())

        assertEquals(listOf(MediaSegmentType.OUTRO), segments.map { it.type })
    }

    @Test
    fun `a null payload contributes nothing`() {
        assertEquals(emptyList(), legacySegmentFallback(intro = null, credits = null))
        assertEquals(1, legacySegmentFallback(intro(), credits = null).size)
        assertEquals(1, legacySegmentFallback(intro = null, credits()).size)
    }

    @Test
    fun `an inverted or zero-length intro run is dropped`() {
        // hasIntro is strict end > start.
        assertTrue(legacySegmentFallback(intro(start = 200L, end = 100L), credits()).none { it.type == MediaSegmentType.INTRO })
        assertTrue(legacySegmentFallback(intro(start = 150L, end = 150L), credits()).none { it.type == MediaSegmentType.INTRO })
    }

    @Test
    fun `an inverted or zero-length credit run is dropped`() {
        assertTrue(legacySegmentFallback(intro(), credits(start = 400L, end = 300L)).none { it.type == MediaSegmentType.OUTRO })
        assertTrue(legacySegmentFallback(intro(), credits(start = 350L, end = 350L)).none { it.type == MediaSegmentType.OUTRO })
    }
}
