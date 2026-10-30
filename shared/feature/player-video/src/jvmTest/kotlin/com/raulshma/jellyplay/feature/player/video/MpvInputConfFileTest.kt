package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.MpvInputConfFile
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shared input.conf write site (issue #26): non-blank text lands
 * atomically in the config dir, blank/null clears a stale file, failures
 * degrade to [MpvInputConfFile.SyncResult.Failed] without throwing. Runs over
 * a temp dir — the helper is plain `java.io.File` code with no Android
 * surface.
 */
class MpvInputConfFileTest {

    private fun newDir(): File = createTempDirectory("mpv-input-conf").toFile().also { it.deleteOnExit() }

    @Test
    fun `non-blank text writes the file and reports its path`() {
        val dir = newDir()
        val result = MpvInputConfFile.sync(dir, "MBTN_RIGHT cycle pause")
        assertTrue(result is MpvInputConfFile.SyncResult.Synced)
        val file = File(dir, MpvInputConfFile.FILE_NAME)
        assertTrue(file.isFile)
        assertEquals("MBTN_RIGHT cycle pause", file.readText())
        assertEquals(file.absolutePath, (result as MpvInputConfFile.SyncResult.Synced).path)
    }

    @Test
    fun `sync creates a missing config dir`() {
        val dir = File(newDir(), "nested/mpv")
        val result = MpvInputConfFile.sync(dir, "a=b")
        assertTrue(result is MpvInputConfFile.SyncResult.Synced)
        assertTrue(File(dir, MpvInputConfFile.FILE_NAME).isFile)
    }

    @Test
    fun `blank text clears a stale file and reports cleared`() {
        val dir = newDir()
        assertTrue(MpvInputConfFile.sync(dir, "old bindings") is MpvInputConfFile.SyncResult.Synced)
        val result = MpvInputConfFile.sync(dir, "   ")
        assertEquals(MpvInputConfFile.SyncResult.Cleared, result)
        assertFalse(File(dir, MpvInputConfFile.FILE_NAME).exists())
    }

    @Test
    fun `null text on a fresh dir is cleared`() {
        assertEquals(
            MpvInputConfFile.SyncResult.Cleared,
            MpvInputConfFile.sync(newDir(), null),
        )
    }

    @Test
    fun `re-sync replaces the previous content`() {
        val dir = newDir()
        MpvInputConfFile.sync(dir, "first")
        MpvInputConfFile.sync(dir, "second")
        assertEquals("second", File(dir, MpvInputConfFile.FILE_NAME).readText())
        // No temp-file residue.
        assertFalse(File(dir, "${MpvInputConfFile.FILE_NAME}.tmp").exists())
    }

    @Test
    fun `a failed write degrades to Failed without throwing`() {
        // A config dir that is actually a regular FILE makes both the mkdirs
        // and the temp write fail inside the containment.
        val notADir = File(newDir(), "blocked").apply { writeText("not a directory") }
        val result = MpvInputConfFile.sync(notADir, "text")
        assertEquals(MpvInputConfFile.SyncResult.Failed, result)
    }
}
