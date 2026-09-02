package com.raulshma.jellyplay.feature.editor

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Wave 20A pure-helper coverage for the desktop file-picker actual. The AWT
 * FileDialog is a native modal window and throws HeadlessException in a test
 * JVM, so the dialog interaction itself is manually-verified-only; what is
 * pinned here is everything around it: the advisory image-extension filter
 * (including the directory pass-through that keeps the dialog navigable),
 * the cancel path (null directory/file → null → onPicked never fires), and
 * the picked-file contract (file:/ preview URI, lazy Dispatchers.IO read,
 * and the JDK exception a vanished file throws for the ViewModel's
 * runCatching to route into uiState.error).
 */
class DesktopEditorFilePickerTest {

    @Test
    fun `isEditorImageFileName accepts common image extensions case-insensitively`() {
        listOf(
            "poster.png", "POSTER.PNG", "poster.Jpg", "photo.jpeg",
            "art.webp", "anim.gif", "old.bmp",
        ).forEach { name ->
            assertTrue(isEditorImageFileName(name), "expected image name accepted: $name")
        }
    }

    @Test
    fun `isEditorImageFileName rejects non-image and extensionless names`() {
        listOf(
            "movie.srt", "notes.txt", "data.bin", "archive.tar.gz",
            "noextension", "", ".hidden",
        ).forEach { name ->
            assertFalse(isEditorImageFileName(name), "expected non-image name rejected: $name")
        }
    }

    @Test
    fun `image filename filter keeps directories visible while hiding non-images`() {
        // AWT consults the filter for every list entry — directories included.
        // Rejecting them would hide the folders themselves and make the
        // dialog unnavigable, so the pass-through is load-bearing.
        val dir = Files.createTempDirectory("editor-picker-filter").toFile()
        try {
            File(dir, "subfolder").mkdirs()
            File(dir, "poster.png").writeText("")
            File(dir, "movie.srt").writeText("")

            val filter = editorImageFilenameFilter()
            assertTrue(filter.accept(dir, "subfolder"), "directories must stay visible")
            assertTrue(filter.accept(dir, "poster.png"))
            assertFalse(filter.accept(dir, "movie.srt"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `editorPickedFile returns null on dialog cancel`() {
        // Cancel reads as a null pick from pickAwtFile (its pure half maps
        // the dialog's null directory/file answers to null) — the sheet's
        // prior pick survives, matching SAF cancel semantics.
        assertNull(editorPickedFile(picked = null))
    }

    @Test
    fun `editorPickedFile builds the seam contract from a real file`() {
        val bytes = byteArrayOf(1, 2, 3)
        val temp = File.createTempFile("poster", ".png")
        try {
            temp.writeBytes(bytes)

            val picked = assertNotNull(editorPickedFile(temp))
            assertEquals(temp.name, picked.fileName)
            // The same URI form the player's wave 9 document picker emits;
            // coil3's common FileUriFetcher decodes it for the preview.
            assertEquals(temp.toURI().toString(), picked.previewUrl)
            assertTrue(picked.previewUrl?.startsWith("file:/") == true, picked.previewUrl)
            assertContentEquals(bytes, runBlocking { picked.readBytes() })
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `editorPickedFile readBytes throws when the file vanishes before upload`() {
        // The deferred read means the failure surfaces at upload time, not at
        // pick time: the JDK FileNotFoundException propagates out of
        // readBytes and the ViewModel's platform-neutral runCatching routes
        // its message into uiState.error (EditorViewModelUploadTest pins the
        // routing; the "Cannot open input stream…" text is Android-only).
        val temp = File.createTempFile("gone", ".srt")
        val picked = assertNotNull(editorPickedFile(temp))
        assertTrue(temp.delete())

        assertFailsWith<FileNotFoundException> {
            runBlocking { picked.readBytes() }
        }
    }
}
