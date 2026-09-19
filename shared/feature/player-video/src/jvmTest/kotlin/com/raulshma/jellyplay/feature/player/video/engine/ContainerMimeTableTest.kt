package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins for [ContainerMimeTable] — the container → Media3 MIME table the
 * androidMain `ContainerMimeMapper` adapter delegates to. The returned
 * strings ARE the media3 `MimeTypes` constants (mirrored as plain strings so
 * commonMain carries no media3 dependency); every arm is pinned verbatim so
 * the adapter and the table can never drift from the tested values.
 */
class ContainerMimeTableTest {

    @Test
    fun mp4Family_mapsToApplicationMp4() {
        listOf("mp4", "m4v", "m4a", "mov", "ismv", "isma").forEach { container ->
            assertEquals("application/mp4", ContainerMimeTable.mapToMime(container), container)
        }
    }

    @Test
    fun matroskaFamily_mapsToApplicationMatroska() {
        listOf("mkv", "webm", "mka").forEach { container ->
            assertEquals("application/x-matroska", ContainerMimeTable.mapToMime(container), container)
        }
    }

    @Test
    fun mpegTsFamily_mapsToVideoMp2t() {
        listOf("ts", "m2ts", "mts", "tsa", "tsv").forEach { container ->
            assertEquals("video/mp2t", ContainerMimeTable.mapToMime(container), container)
        }
    }

    @Test
    fun audioContainers_mapToTheirMimeConstants() {
        assertEquals("audio/flac", ContainerMimeTable.mapToMime("flac"))
        assertEquals("audio/mpeg", ContainerMimeTable.mapToMime("mp3"))
        assertEquals("audio/mp4a-latm", ContainerMimeTable.mapToMime("aac"))
        assertEquals("audio/mp4a-latm", ContainerMimeTable.mapToMime("adts"))
        assertEquals("audio/ogg", ContainerMimeTable.mapToMime("ogg"))
        assertEquals("audio/ogg", ContainerMimeTable.mapToMime("oga"))
        assertEquals("audio/ogg", ContainerMimeTable.mapToMime("opus"))
        assertEquals("audio/wav", ContainerMimeTable.mapToMime("wav"))
        assertEquals("video/x-flv", ContainerMimeTable.mapToMime("flv"))
    }

    @Test
    fun input_isLowercasedAndTrimmed() {
        assertEquals("application/x-matroska", ContainerMimeTable.mapToMime(" MKV "))
        assertEquals("video/mp2t", ContainerMimeTable.mapToMime("M2TS"))
        assertEquals("audio/mpeg", ContainerMimeTable.mapToMime(" Mp3\t"))
    }

    @Test
    fun unknownOrNullInput_mapsToNullForExtensionFallback() {
        // AVI deliberately null (no dedicated Media3 MIME/extractor) so
        // ExoPlayer falls back to content sniffing.
        assertNull(ContainerMimeTable.mapToMime("avi"))
        assertNull(ContainerMimeTable.mapToMime("wmv"))
        assertNull(ContainerMimeTable.mapToMime(null))
        assertNull(ContainerMimeTable.mapToMime(""))
        assertNull(ContainerMimeTable.mapToMime("   "))
    }
}
