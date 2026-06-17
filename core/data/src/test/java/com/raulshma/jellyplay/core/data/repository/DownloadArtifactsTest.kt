package com.raulshma.jellyplay.core.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DownloadArtifactsTest {

    private fun newTempDir(): File = Files.createTempDirectory("da_test").toFile()

    @Test
    fun `cleanup removes trickplay subtitles and segments but keeps the video`() {
        val dir = newTempDir()
        try {
            val trickplayDir = File(dir, "trickplay").apply { mkdirs() }
            File(trickplayDir, "trickplay_0.jpg").writeBytes(byteArrayOf(1))
            File(trickplayDir, "meta.json").writeText("{}")

            val subDir = File(dir, "subtitles").apply { mkdirs() }
            File(subDir, "3.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nHi\n")
            File(subDir, "manifest.json").writeText("{}")

            File(dir, "segments.json").writeText("[]")
            val video = File(dir, "movie.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }

            DownloadArtifacts.cleanup(dir)

            assertFalse(File(dir, "trickplay").exists())
            assertFalse(File(dir, "subtitles").exists())
            assertFalse(File(dir, "segments.json").exists())
            assertTrue(video.exists())
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
    fun `cleanup only removes segments json file not a directory with that name`() {
        val dir = newTempDir()
        try {
            File(dir, "segments.json").writeText("[]")
            DownloadArtifacts.cleanup(dir)
            assertFalse(File(dir, "segments.json").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
