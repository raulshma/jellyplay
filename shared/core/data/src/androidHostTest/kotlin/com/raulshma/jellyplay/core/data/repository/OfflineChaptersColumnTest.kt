package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ChapterInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the [chaptersJson] column codec on `offline_media`
 * ([encodeChapters] / [decodeChapters]) for the download-time chapter
 * snapshot (feature contract: [com.raulshma.jellyplay.core.model.OfflineMediaItem.chapters]).
 */
class OfflineChaptersColumnTest {

    @Test
    fun `chapters round-trip through the column codec`() {
        val chapters = listOf(
            ChapterInfo(name = "Opening", startPositionTicks = 0L),
            ChapterInfo(
                name = "Credits",
                startPositionTicks = 100_000_000L,
                imageTag = "tag-1",
                imageDateModified = "2024-01-01T00:00:00Z",
            ),
        )

        val decoded = decodeChapters(encodeChapters(chapters))

        assertEquals(chapters, decoded)
    }

    @Test
    fun `null and blank blobs decode to empty`() {
        assertTrue(decodeChapters(null).isEmpty())
        assertTrue(decodeChapters("").isEmpty())
        assertTrue(decodeChapters("   ").isEmpty())
    }

    @Test
    fun `garbage blob decodes to empty instead of throwing`() {
        assertTrue(decodeChapters("not json").isEmpty())
    }

    @Test
    fun `decoding tolerates unknown keys written by a future app version`() {
        val onDisk =
            """[{"name":"Intro","startPositionTicks":5,"aFutureField":1}]"""

        val decoded = decodeChapters(onDisk)

        assertEquals(1, decoded.size)
        assertEquals("Intro", decoded[0].name)
        assertEquals(5L, decoded[0].startPositionTicks)
    }
}
