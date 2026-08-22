package com.raulshma.jellyplay.core.data.repository

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.File
import java.nio.file.Files

class DownloadArtifactsTest {

    private fun newTempDir(): File = Files.createTempDirectory("da_test").toFile()

    @Test
    fun `cleanup removes item-scoped trickplay subtitles and segments but keeps the video`() {
        val dir = newTempDir()
        val itemId = "item-abc"
        try {
            val trickplayDir = File(dir, DownloadArtifacts.trickplayDir(itemId)).apply { mkdirs() }
            File(trickplayDir, "trickplay_0.jpg").writeBytes(byteArrayOf(1))
            File(trickplayDir, "meta.json").writeText("{}")

            val subDir = File(dir, DownloadArtifacts.subtitlesDir(itemId)).apply { mkdirs() }
            File(subDir, "3.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nHi\n")
            File(subDir, "manifest.json").writeText("{}")

            File(dir, DownloadArtifacts.segmentsFile(itemId)).writeText("[]")
            val video = File(dir, "movie.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }

            DownloadArtifacts.cleanup(dir, itemId)

            assertFalse(File(dir, DownloadArtifacts.trickplayDir(itemId)).exists())
            assertFalse(File(dir, DownloadArtifacts.subtitlesDir(itemId)).exists())
            assertFalse(File(dir, DownloadArtifacts.segmentsFile(itemId)).exists())
            assertTrue(video.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `cleanup also removes legacy un-scoped artifacts`() {
        val dir = newTempDir()
        try {
            val trickplayDir = File(dir, DownloadArtifacts.LEGACY_TRICKPLAY_DIR).apply { mkdirs() }
            File(trickplayDir, "trickplay_0.jpg").writeBytes(byteArrayOf(1))

            val subDir = File(dir, DownloadArtifacts.LEGACY_SUBTITLES_DIR).apply { mkdirs() }
            File(subDir, "0.srt").writeText("sub")

            File(dir, DownloadArtifacts.LEGACY_SEGMENTS_FILE).writeText("[]")

            DownloadArtifacts.cleanup(dir, "any-item")

            assertFalse(File(dir, DownloadArtifacts.LEGACY_TRICKPLAY_DIR).exists())
            assertFalse(File(dir, DownloadArtifacts.LEGACY_SUBTITLES_DIR).exists())
            assertFalse(File(dir, DownloadArtifacts.LEGACY_SEGMENTS_FILE).exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `cleanup is safe on null parent and on a dir with no artifacts`() {
        DownloadArtifacts.cleanup(null)
        val dir = newTempDir()
        try {
            DownloadArtifacts.cleanup(dir)
            assertTrue(dir.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `cleanup of one item does not remove another item's artifacts`() {
        val dir = newTempDir()
        val itemA = "item-aaa"
        val itemB = "item-bbb"
        try {
            // Create scoped artifacts for both items.
            File(dir, DownloadArtifacts.subtitlesDir(itemA)).apply { mkdirs() }
                .also { File(it, "0.srt").writeText("sub A") }
            File(dir, DownloadArtifacts.subtitlesDir(itemB)).apply { mkdirs() }
                .also { File(it, "0.srt").writeText("sub B") }
            File(dir, DownloadArtifacts.trickplayDir(itemA)).apply { mkdirs() }
                .also { File(it, "meta.json").writeText("{}") }
            File(dir, DownloadArtifacts.trickplayDir(itemB)).apply { mkdirs() }
                .also { File(it, "meta.json").writeText("{}") }
            File(dir, DownloadArtifacts.segmentsFile(itemA)).writeText("[]")
            File(dir, DownloadArtifacts.segmentsFile(itemB)).writeText("[]")

            // Delete item A's artifacts only.
            DownloadArtifacts.cleanup(dir, itemA)

            // Item A gone.
            assertFalse(File(dir, DownloadArtifacts.subtitlesDir(itemA)).exists())
            assertFalse(File(dir, DownloadArtifacts.trickplayDir(itemA)).exists())
            assertFalse(File(dir, DownloadArtifacts.segmentsFile(itemA)).exists())

            // Item B untouched.
            assertTrue(File(dir, DownloadArtifacts.subtitlesDir(itemB)).exists())
            assertTrue(File(dir, DownloadArtifacts.trickplayDir(itemB)).exists())
            assertTrue(File(dir, DownloadArtifacts.segmentsFile(itemB)).exists())
            // Verify content is intact.
            assertTrue(File(File(dir, DownloadArtifacts.subtitlesDir(itemB)), "0.srt").readText() == "sub B")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `two items produce distinct artifact paths`() {
        val itemA = "item-aaa"
        val itemB = "item-bbb"

        assertTrue(DownloadArtifacts.subtitlesDir(itemA) != DownloadArtifacts.subtitlesDir(itemB))
        assertTrue(DownloadArtifacts.trickplayDir(itemA) != DownloadArtifacts.trickplayDir(itemB))
        assertTrue(DownloadArtifacts.segmentsFile(itemA) != DownloadArtifacts.segmentsFile(itemB))
    }
}
