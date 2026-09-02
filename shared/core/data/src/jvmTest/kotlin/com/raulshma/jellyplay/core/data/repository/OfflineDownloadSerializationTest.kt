package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Guards the on-disk serialization format used by [DownloadRepositoryImpl] when
 * bundling external subtitles ([OfflineSubtitleManifest]) and media segments
 * ([MediaSegment]) for offline playback. The [Json] config must mirror the one
 * used in production (tolerant of unknown keys for forward compatibility).
 */
class OfflineDownloadSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `subtitle manifest round-trips preserving metadata`() {
        val manifest = OfflineSubtitleManifest(
            subtitles = listOf(
                OfflineSubtitleEntry(
                    index = 3,
                    fileName = "3.srt",
                    language = "eng",
                    codec = "subrip",
                    displayTitle = "English",
                    isDefault = true,
                    isForced = false,
                ),
                OfflineSubtitleEntry(index = 5, fileName = "5.ass", codec = "ass", isForced = true),
            ),
        )

        val encoded = json.encodeToString(manifest)
        val decoded = json.decodeFromString<OfflineSubtitleManifest>(encoded)

        assertEquals(manifest, decoded)
    }

    @Test
    fun `media segments round-trip preserving SerialName fields`() {
        val segments = listOf(
            MediaSegment(id = "seg-1", itemId = "item-1", type = MediaSegmentType.INTRO, startTicks = 1_000L, endTicks = 2_000L),
            MediaSegment(id = "seg-2", itemId = "item-1", type = MediaSegmentType.OUTRO, startTicks = 9_000L, endTicks = 10_000L),
        )

        val encoded = json.encodeToString(segments)
        val decoded = json.decodeFromString<List<MediaSegment>>(encoded)

        assertEquals(segments, decoded)
    }

    @Test
    fun `decoding tolerates unknown keys written by a future app version`() {
        val onDisk = """{"subtitles":[{"index":7,"fileName":"7.vtt","language":"fra","futureField":42}]}"""

        val decoded = json.decodeFromString<OfflineSubtitleManifest>(onDisk)

        assertEquals(1, decoded.subtitles.size)
        assertEquals("7.vtt", decoded.subtitles[0].fileName)
    }

    @Test
    fun `empty segments list is distinguishable from missing file`() {
        val encoded = json.encodeToString(emptyList<MediaSegment>())
        val decoded = json.decodeFromString<List<MediaSegment>>(encoded)
        assertTrue(decoded.isEmpty())
    }
}
