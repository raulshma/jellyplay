package com.raulshma.jellyplay.feature.settings

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Wave 20C: the desktop backup seam's pure/IO half — the picker→IO uri
 * contract and the JDK stream mapping. The AWT FileDialog half (showing the
 * native SAVE/LOAD dialog, pre-filling the suggested export name, swallowing
 * cancels) is a native modal dialog and stays manually-verified; these tests
 * pin everything the streams do once a `file:` uri arrives.
 */
class DesktopSettingsBackupIoTest {

    @Test
    fun `picker file uri round-trips through backupFileFor`() {
        val picked = File(createTempDirectory("jp-settings-uk").toFile(), "jellyplay-settings.json")
        val uri = picked.toURI().toString()

        assertTrue(uri.startsWith("file:"))
        assertEquals(picked.absolutePath, backupFileFor(uri).absolutePath)
    }

    @Test
    fun `picker file uri with spaces and unicode round-trips through backupFileFor`() {
        // Realistic Windows pick: toURI percent-encodes ('John Smith' →
        // 'John%20Smith'); File(URI) must decode back to the exact path.
        // Reviewer nit (wave 20 fix round): the space-free test alone
        // pinned only the trivial case.
        val picked = File(createTempDirectory("jp-settings-sp").toFile(), "John Smith — backup (1).json")
        val uri = picked.toURI().toString()

        assertTrue(uri.startsWith("file:"))
        assertTrue("%20" in uri, "sanity: the path really is percent-encoded")
        assertEquals(picked.absolutePath, backupFileFor(uri).absolutePath)
    }

    @Test
    fun `export sink writes and import source reads back the same bytes`() = runTest {
        val io = DesktopSettingsBackupIo(httpCacheRoot = unusedRoot())
        val dir = createTempDirectory("jp-settings-io").toFile()
        val target = File(dir, "jellyplay-settings.json")
        val payload = """{"schemaVersion":2,"slices":{},"extras":{}}"""

        io.openExportSink(target.toURI().toString())!!.use { sink ->
            sink.write(payload.toByteArray())
        }
        val readBack = io.openImportSource(target.toURI().toString())!!.use { it.readBytes() }

        assertEquals(payload, readBack.decodeToString())
    }

    @Test
    fun `non-file uri fails the stream opener like a dead SAF stream`() = runTest {
        val io = DesktopSettingsBackupIo(httpCacheRoot = unusedRoot())

        // File(URI) rejects content-style uris; the ViewModel's runCatching
        // turns this into "Import failed: …" — same shape as Android's
        // unopenable contentResolver stream.
        assertFailsWith<IllegalArgumentException> {
            io.openImportSource("content://com.android.providers.downloads.documents/42")
        }
    }

    @Test
    fun `cache estimate walks the http-cache root`() = runTest {
        // Wave 21B: the desktop's cache estimate is real now — it sums file
        // lengths under the injected http-cache root (a missing root reads 0,
        // matching a fresh install whose OkHttp cache was never created).
        val root = createTempDirectory("jp-settings-hc").toFile()
        val io = DesktopSettingsBackupIo(httpCacheRoot = root)

        assertEquals(0L, io.estimateCacheSizeBytes(), "empty root estimates zero bytes")

        File(root, "journal").writeBytes(ByteArray(100))
        val responses = File(root, "responses").apply { mkdirs() }
        File(responses, "body-1").writeBytes(ByteArray(1_500))

        assertEquals(1_600L, io.estimateCacheSizeBytes(), "estimate sums the journal + nested response file")
    }

    private fun unusedRoot(): File = createTempDirectory("jp-settings-none").toFile()
}
