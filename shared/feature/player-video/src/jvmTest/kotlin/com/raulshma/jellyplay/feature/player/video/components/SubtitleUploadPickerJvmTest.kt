package com.raulshma.jellyplay.feature.player.video.components

import com.raulshma.jellyplay.feature.player.video.DesktopVideoPlayerPlatform
import com.raulshma.jellyplay.feature.player.video.pickedLocalFile
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wave 20C: the desktop subtitle-upload seam's pure/IO half — the advisory
 * extension list and the `file:` uri → length/bytes gateway the upload
 * button's existing IO path consumes. The AWT FileDialog half (native LOAD
 * dialog, cancel retaining the prior pick) is manually-verified.
 */
class SubtitleUploadPickerJvmTest {

    @Test
    fun `advisory filter accepts every subtitle extension case-insensitively`() {
        listOf("movie.srt", "movie.ass", "movie.ssa", "movie.vtt", "movie.sub", "movie.idx", "Movie.SRT")
            .forEach { assertTrue(isAdvisorySubtitleFileName(it), "expected accept: $it") }
    }

    @Test
    fun `advisory filter rejects non-subtitle and extensionless names`() {
        listOf("movie.mkv", "movie.mp4", "archive.tar.gz", "namesrt", "subtitle.")
            .forEach { assertFalse(isAdvisorySubtitleFileName(it), "expected reject: $it") }
    }

    @Test
    fun `pickedLocalFile resolves only existing local files`() {
        val dir = createTempDirectory("jp-sub-uk").toFile()
        val picked = File(dir, "movie.srt").apply { writeText("1\n00:00:01,000 --> 00:00:02,000\nhi\n") }

        assertEquals(picked.absolutePath, pickedLocalFile(picked.toURI().toString())?.absolutePath)
        // Missing file, a bare directory, and non-file uris stay null (the
        // pre-20C stub sizes them 0/empty instead of throwing).
        assertNull(pickedLocalFile(File(dir, "missing.srt").toURI().toString()))
        assertNull(pickedLocalFile(dir.toURI().toString()))
        assertNull(pickedLocalFile("content://media/external/file/42"))
    }

    @Test
    fun `desktop gateway reports picked file length and bytes`() {
        val dir = createTempDirectory("jp-sub-gw").toFile()
        val bytes = "1\n00:00:01,000 --> 00:00:02,000\nhi\n".toByteArray()
        val picked = File(dir, "movie.srt").apply { writeBytes(bytes) }
        val platform = DesktopVideoPlayerPlatform()

        assertEquals(bytes.size.toLong(), platform.queryFileSizeBytes(picked.toURI().toString()))
        assertContentEquals(bytes, platform.readBytes(picked.toURI().toString()))
    }

    @Test
    fun `desktop gateway keeps stub behaviour for non-file uris`() {
        val platform = DesktopVideoPlayerPlatform()

        assertEquals(0L, platform.queryFileSizeBytes("content://media/external/file/42"))
        assertContentEquals(ByteArray(0), platform.readBytes("content://media/external/file/42"))
    }
}
