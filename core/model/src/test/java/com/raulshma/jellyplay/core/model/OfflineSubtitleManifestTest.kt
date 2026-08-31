package com.raulshma.jellyplay.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the [OfflineSubtitleEntry] wire format. The download pipeline persists
 * `manifest.json` next to the video and re-reads it offline, so adding fields
 * must never break manifests written by older builds (and vice versa).
 */
class OfflineSubtitleManifestTest {

    /** Same configuration as the Hilt-provided Json instance (ignoreUnknownKeys). */
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `manifest without isImage field decodes as text`() {
        // Verbatim shape written by builds predating isImage.
        val legacy = """{"subtitles":[{"index":3,"fileName":"3.srt","language":"eng"}]}"""
        val manifest = json.decodeFromString<OfflineSubtitleManifest>(legacy)
        assertEquals(1, manifest.subtitles.size)
        val entry = manifest.subtitles.single()
        assertFalse(entry.isImage)
        assertEquals("3.srt", entry.fileName)
    }

    @Test
    fun `isImage survives a write-read round trip`() {
        val manifest = OfflineSubtitleManifest(
            subtitles = listOf(
                OfflineSubtitleEntry(index = 1, fileName = "1.srt", codec = "srt"),
                OfflineSubtitleEntry(index = 2, fileName = "2.sup", codec = "pgssub", isImage = true),
            ),
        )
        val decoded = json.decodeFromString<OfflineSubtitleManifest>(json.encodeToString(manifest))
        assertFalse(decoded.subtitles[0].isImage)
        assertTrue(decoded.subtitles[1].isImage)
    }

    @Test
    fun `newer manifest with extra keys still decodes`() {
        val newer =
            """{"subtitles":[{"index":2,"fileName":"2.sup","codec":"pgssub","isImage":true,"futureField":9}]}"""
        val decoded = json.decodeFromString<OfflineSubtitleManifest>(newer)
        assertTrue(decoded.subtitles.single().isImage)
    }

    @Test
    fun `isBitmapSidecar gates on the stored flag or a legacy codec sniff`() {
        // Fresh bundle: flag set at write time.
        assertTrue(OfflineSubtitleEntry(index = 1, fileName = "1.sup", isImage = true).isBitmapSidecar)
        assertFalse(OfflineSubtitleEntry(index = 1, fileName = "1.srt", codec = "srt").isBitmapSidecar)
        // Legacy manifests decode isImage=false but still carry the codec —
        // the sniff must catch them so old image sidecars gate identically.
        assertTrue(
            OfflineSubtitleEntry(index = 2, fileName = "2.srt", codec = "pgssub").isBitmapSidecar,
        )
        assertTrue(
            OfflineSubtitleEntry(index = 3, fileName = "3.srt", codec = "HDMV_PGS_SUBTITLE").isBitmapSidecar,
        )
        // Null/unknown codecs stay text (the dominant legacy case).
        assertFalse(OfflineSubtitleEntry(index = 4, fileName = "4.srt").isBitmapSidecar)
        assertFalse(
            OfflineSubtitleEntry(index = 5, fileName = "5.srt", codec = "future_codec").isBitmapSidecar,
        )
    }
}
