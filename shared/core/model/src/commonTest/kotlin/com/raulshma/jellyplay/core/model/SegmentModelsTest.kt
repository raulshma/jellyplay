package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of the segment models:
 *
 *  - [MediaSegmentType.fromApiName] is case-insensitive on the Jellyfin API
 *    name and falls back to [MediaSegmentType.UNKNOWN] for anything else
 *    (unknown server-side segment kinds must never crash the parser).
 *  - Jellyfin ticks are 10 per microsecond, i.e. 10_000 ticks per ms: the
 *    [MediaSegment] projections ([MediaSegment.startMs], [MediaSegment.endMs],
 *    [MediaSegment.durationMs]) each divide by exactly 10_000, truncating
 *    sub-millisecond remainder.
 *  - [MediaSegment.hasSegment] is false for zero-length / inverted ranges and
 *    true only for endTicks strictly greater than startTicks.
 *  - [SegmentBehavior.DEFAULT_BEHAVIORS] covers every [MediaSegmentType] — a
 *    new segment type added without a default behavior would silently fall out
 *    of the skip-UI's lookup.
 *  - [MediaSegmentType.SEGMENT_PRIORITY] is a permutation of all entries (the
 *    overlapping-segment picker indexes into it).
 */
class SegmentModelsTest {

    // ── MediaSegmentType.fromApiName ─────────────────────────────────────────

    @Test
    fun `fromApiName maps every known name case-insensitively`() {
        for (type in MediaSegmentType.entries) {
            assertEquals(type, MediaSegmentType.fromApiName(type.name), type.name)
            assertEquals(type, MediaSegmentType.fromApiName(type.name.lowercase()), type.name.lowercase())
            assertEquals(type, MediaSegmentType.fromApiName(type.name.uppercase()), type.name.uppercase())
        }
    }

    @Test
    fun `fromApiName falls back to UNKNOWN for unknown names`() {
        assertEquals(MediaSegmentType.UNKNOWN, MediaSegmentType.fromApiName("NotASegment"))
        assertEquals(MediaSegmentType.UNKNOWN, MediaSegmentType.fromApiName(""))
    }

    @Test
    fun `UNKNOWN segment is a real entry that also round-trips`() {
        // "UNKNOWN" itself is a legitimate API name mapping to itself, not the
        // fallback path — pinning that the map is built from entries (including
        // UNKNOWN) and not from the non-UNKNOWN subset.
        assertEquals(MediaSegmentType.UNKNOWN, MediaSegmentType.fromApiName("UNKNOWN"))
    }

    // ── MediaSegment tick projections ────────────────────────────────────────

    @Test
    fun `tick projections convert at 10_000 ticks per ms`() {
        val segment = MediaSegment(
            id = "s1",
            itemId = "i1",
            type = MediaSegmentType.INTRO,
            startTicks = 120_000_000L,   // 12_000 ms
            endTicks = 240_500_000L,     // 24_050 ms
        )
        assertEquals(12_000L, segment.startMs)
        assertEquals(24_050L, segment.endMs)
        assertEquals(12_050L, segment.durationMs)
    }

    @Test
    fun `tick projections truncate sub-millisecond remainders`() {
        val segment = MediaSegment(
            id = "s1",
            itemId = "i1",
            type = MediaSegmentType.OUTRO,
            startTicks = 9_999L,      // 0.9999 ms -> 0 ms
            endTicks = 19_999L,       // 1.9999 ms -> 1 ms
        )
        assertEquals(0L, segment.startMs)
        assertEquals(1L, segment.endMs)
        assertEquals(1L, segment.durationMs)
    }

    @Test
    fun `hasSegment is true only for strictly positive-length ranges`() {
        assertTrue(
            MediaSegment("s", "i", MediaSegmentType.INTRO, startTicks = 10, endTicks = 20).hasSegment,
        )
        assertFalse(
            MediaSegment("s", "i", MediaSegmentType.INTRO, startTicks = 10, endTicks = 10).hasSegment,
        )
        assertFalse(
            MediaSegment("s", "i", MediaSegmentType.INTRO, startTicks = 20, endTicks = 10).hasSegment,
        )
        assertFalse(
            MediaSegment("s", "i", MediaSegmentType.INTRO, startTicks = 0, endTicks = 0).hasSegment,
        )
    }

    @Test
    fun `durationMs of an inverted segment is negative`() {
        // The model deliberately does not coerce: an inverted range reports a
        // negative duration so upstream sanity checks can detect it.
        val inverted = MediaSegment("s", "i", MediaSegmentType.RECAP, startTicks = 20_000, endTicks = 10_000)
        assertEquals(-1L, inverted.durationMs)
    }

    // ── SegmentBehavior defaults + SEGMENT_PRIORITY ──────────────────────────

    @Test
    fun `DEFAULT_BEHAVIORS covers every segment type`() {
        assertEquals(
            MediaSegmentType.entries.toSet(),
            SegmentBehavior.DEFAULT_BEHAVIORS.keys.toSet(),
        )
    }

    @Test
    fun `DEFAULT_BEHAVIORS pin the curated defaults`() {
        assertEquals(SegmentBehavior.SHOW_BUTTON, SegmentBehavior.DEFAULT_BEHAVIORS[MediaSegmentType.INTRO])
        assertEquals(SegmentBehavior.SHOW_BUTTON, SegmentBehavior.DEFAULT_BEHAVIORS[MediaSegmentType.OUTRO])
        assertEquals(SegmentBehavior.IGNORE, SegmentBehavior.DEFAULT_BEHAVIORS[MediaSegmentType.PREVIEW])
        assertEquals(SegmentBehavior.IGNORE, SegmentBehavior.DEFAULT_BEHAVIORS[MediaSegmentType.RECAP])
        assertEquals(SegmentBehavior.AUTO_SKIP, SegmentBehavior.DEFAULT_BEHAVIORS[MediaSegmentType.COMMERCIAL])
        assertEquals(SegmentBehavior.IGNORE, SegmentBehavior.DEFAULT_BEHAVIORS[MediaSegmentType.UNKNOWN])
    }

    @Test
    fun `SEGMENT_PRIORITY is a permutation of all segment types with COMMERCIAL first`() {
        assertEquals(MediaSegmentType.entries.toSet(), MediaSegmentType.SEGMENT_PRIORITY.toSet())
        assertEquals(MediaSegmentType.COMMERCIAL, MediaSegmentType.SEGMENT_PRIORITY.first())
    }

    @Test
    fun `media segment decodes the PascalCase Jellyfin wire shape`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(
            MediaSegment.serializer(),
            """{"Id":"seg-1","ItemId":"item-1","Type":"INTRO","StartTicks":1000,"EndTicks":2000}""",
        )
        assertEquals("seg-1", decoded.id)
        assertEquals("item-1", decoded.itemId)
        assertEquals(MediaSegmentType.INTRO, decoded.type)
        assertEquals(1000L, decoded.startTicks)
        assertEquals(2000L, decoded.endTicks)
        assertTrue(decoded.hasSegment)
    }

    @Test
    fun `segment behavior taxonomy is complete`() {
        assertEquals(
            setOf("SHOW_BUTTON", "AUTO_SKIP", "IGNORE"),
            SegmentBehavior.entries.map { it.name }.toSet(),
        )
    }
}
