package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pins the manifest/orphan semantics of the offline subtitle bundle writer:
 * the manifest is authoritative for offline playback, so a pruned-by-mistake
 * sidecar (or a stale orphan that survives forever) is directly user-visible.
 */
class SubtitleBundleWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    private fun subtitlesDir(): File = tmp.newFolder("subtitles_item1").also {
        File(it, "0.srt").writeText("zero")
        File(it, "1.ass").writeText("[Script Info]")
        File(it, "3.sup").writeBytes(byteArrayOf(0x50, 0x47)) // "PG"
    }

    private fun entry(index: Int, fileName: String) =
        OfflineSubtitleEntry(index = index, fileName = fileName)

    @Test
    fun `manifest write persists decodable entries`() {
        val dir = subtitlesDir()
        val entries = listOf(entry(0, "0.srt"), entry(3, "3.sup"))

        writeSubtitleManifest(dir, entries, json)

        val decoded = json.decodeFromString<OfflineSubtitleManifest>(
            File(dir, DownloadArtifacts.SUBTITLE_MANIFEST_FILE).readText(),
        )
        assertEquals(entries.map { it.index }, decoded.subtitles.map { it.index })
    }

    @Test
    fun `prune removes files no longer referenced and keeps live ones`() {
        val dir = subtitlesDir()
        // Bundle shrank to one text sub; 1.ass belongs to the new bundle,
        // 0.srt was removed server-side, the stray 9.vtt simulates an old
        // legacy-naming leftover.
        File(dir, "9.vtt").writeText("stale")

        pruneOrphanSidecarFiles(dir, liveFileNames = setOf("1.ass", "3.sup"))

        assertFalse(File(dir, "0.srt").exists())
        assertFalse(File(dir, "9.vtt").exists())
        assertTrue(File(dir, "1.ass").exists())
        assertTrue(File(dir, "3.sup").exists())
    }

    @Test
    fun `prune keeps vobsub pair halves when manifest names the idx`() {
        // Manifest entries name the .idx; the .sub sibling must count as live
        // too or the prune breaks a renderable pair into two dead halves.
        val dir = subtitlesDir()
        File(dir, "3.idx").writeText("# VobSub index")
        File(dir, "3.sub").writeBytes(byteArrayOf(0x00))

        pruneOrphanSidecarFiles(dir, liveFileNames = setOf("3.idx", "3.sub"))

        assertTrue(File(dir, "3.idx").exists())
        assertTrue(File(dir, "3.sub").exists())
        assertFalse(File(dir, "0.srt").exists())
        assertFalse(File(dir, "1.ass").exists())
        assertFalse(File(dir, "3.sup").exists())
    }

    @Test
    fun `prune sweeps interrupted transfer staging files`() {
        // .part siblings are written by the download path and must never
        // survive a complete pass — they are not live names.
        val dir = subtitlesDir()
        File(dir, "3.sup.part").writeBytes(byteArrayOf(0x01))

        pruneOrphanSidecarFiles(dir, liveFileNames = setOf("3.sup"))

        assertFalse(File(dir, "3.sup.part").exists())
        assertTrue(File(dir, "3.sup").exists())
    }

    @Test
    fun `pair urls swap the advertised half`() {
        val sub = "https://srv/Videos/i/m/Subtitles/3/file.sub?api_key=k"
        assertEquals(
            "https://srv/Videos/i/m/Subtitles/3/file.idx?api_key=k" to sub,
            vobsubPairUrls(sub),
        )

        val idx = "https://srv/Videos/i/m/Subtitles/3/Stream.idx?api_key=k"
        assertEquals(
            idx to "https://srv/Videos/i/m/Subtitles/3/Stream.sub?api_key=k",
            vobsubPairUrls(idx),
        )

        // No recognized extension: bitmap assumed on the URL, palette probed
        // beside it.
        val bare = "https://srv/Videos/i/m/Subtitles/3/Stream?api_key=k"
        assertEquals(
            "https://srv/Videos/i/m/Subtitles/3/Stream.idx?api_key=k" to bare,
            vobsubPairUrls(bare),
        )

        // Case-insensitive swap, other query params preserved.
        val upper = "https://srv/Subtitles/3/f.SUB?api_key=k&foo=1"
        assertEquals(
            "https://srv/Subtitles/3/f.idx?api_key=k&foo=1" to upper,
            vobsubPairUrls(upper),
        )
    }

    @Test
    fun `prune keeps legacy-named image sidecar when manifest references it`() {
        // Pre-isImage builds wrote PGS bytes under whatever name the manifest
        // recorded — e.g. 2.srt from the old extension bug. As long as the
        // current bundle still references the stored file name it must survive.
        val dir = subtitlesDir()
        File(dir, "2.srt").writeBytes(byteArrayOf(0x50, 0x47))

        pruneOrphanSidecarFiles(dir, liveFileNames = setOf("0.srt", "2.srt"))

        assertTrue(File(dir, "2.srt").exists())
        // Unreferenced pgs sidecar with the same legacy extension still goes.
        File(dir, "7.srt").writeText("orphan")
        pruneOrphanSidecarFiles(dir, liveFileNames = setOf("2.srt"))
        assertTrue(File(dir, "2.srt").exists())
        assertFalse(File(dir, "7.srt").exists())
    }
}
