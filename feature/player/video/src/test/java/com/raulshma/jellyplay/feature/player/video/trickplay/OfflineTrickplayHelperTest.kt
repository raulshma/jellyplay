package com.raulshma.jellyplay.feature.player.video.trickplay

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.TrickplayInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests the offline trickplay meta-file round trip and directory resolution.
 * The download path is mocked at the repository boundary so we never touch
 * the network; the local cache I/O is exercised against a temp folder.
 */
class OfflineTrickplayHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val info = TrickplayInfo(
        width = 320,
        height = 180,
        tileWidth = 10,
        tileHeight = 20,
        thumbnailCount = 45,
        interval = 1_000,
        bandwidth = 200_000,
    )

    // ── getLocalTrickplayDir ────────────────────────────────────────────────────

    @Test
    fun getLocalTrickplayDir_resolvesSiblingTrickplayFolder() {
        val media = File(tempFolder.root, "downloads/movie.mkv")
        val dir = OfflineTrickplayHelper.getLocalTrickplayDir(media.absolutePath)

        assertEquals(File(tempFolder.root, "downloads/trickplay"), dir)
    }

    @Test
    fun getLocalTrickplayDir_returnsNullWhenPathHasNoParent() {
        // A bare filename resolves against the JVM's CWD, which always has a
        // parent on POSIX; instead feed a root path so parentFile is null.
        val rootPath = if (File.separator == "\\") "C:\\" else "/"
        assertNull(OfflineTrickplayHelper.getLocalTrickplayDir(rootPath))
    }

    // ── loadLocalTrickplayInfo ──────────────────────────────────────────────────

    @Test
    fun loadLocalTrickplayInfo_returnsNullWhenMetaMissing() = runBlocking {
        val media = writeMediaFile()
        assertNull(OfflineTrickplayHelper.loadLocalTrickplayInfo(media.absolutePath))
    }

    @Test
    fun loadLocalTrickplayInfo_returnsNullWhenDirMissing() = runBlocking {
        // Path whose parent has no trickplay/ sibling.
        val media = File(tempFolder.root, "isolated/nope.mkv")
        assertNull(OfflineTrickplayHelper.loadLocalTrickplayInfo(media.absolutePath))
    }

    @Test
    fun loadLocalTrickplayInfo_parsesValidMeta() = runBlocking {
        val trickplayDir = File(tempFolder.root, "trickplay").apply { mkdirs() }
        File(trickplayDir, "meta.json").writeText(
            """{"width":320,"height":180,"tileWidth":10,"tileHeight":20,"thumbnailCount":45,"interval":1000,"bandwidth":200000}"""
        )
        val media = File(tempFolder.root, "movie.mkv")

        val parsed = OfflineTrickplayHelper.loadLocalTrickplayInfo(media.absolutePath)

        assertEquals(info, parsed)
    }

    @Test
    fun loadLocalTrickplayInfo_parsesLenientJsonWithWhitespace() = runBlocking {
        val trickplayDir = File(tempFolder.root, "trickplay").apply { mkdirs() }
        // The regex extractor tolerates arbitrary surrounding JSON structure and spacing.
        File(trickplayDir, "meta.json").writeText(
            """
            {
              "width" : 320,
              "height" : 180,
              "tileWidth" : 10,
              "tileHeight" : 20,
              "thumbnailCount" : 45,
              "interval" : 1000,
              "bandwidth" : 200000
            }
            """.trimIndent()
        )
        val media = File(tempFolder.root, "movie.mkv")

        val parsed = OfflineTrickplayHelper.loadLocalTrickplayInfo(media.absolutePath)

        assertEquals(info, parsed)
    }

    @Test
    fun loadLocalTrickplayInfo_missingBandwidth_defaultsToZero() = runBlocking {
        val trickplayDir = File(tempFolder.root, "trickplay").apply { mkdirs() }
        File(trickplayDir, "meta.json").writeText(
            """{"width":320,"height":180,"tileWidth":10,"tileHeight":20,"thumbnailCount":45,"interval":1000}"""
        )
        val media = File(tempFolder.root, "movie.mkv")

        val parsed = OfflineTrickplayHelper.loadLocalTrickplayInfo(media.absolutePath)

        assertEquals(info.copy(bandwidth = 0), parsed)
    }

    @Test
    fun loadLocalTrickplayInfo_returnsNullWhenRequiredFieldMissing() = runBlocking {
        val trickplayDir = File(tempFolder.root, "trickplay").apply { mkdirs() }
        // width is a required field — its absence must null out the parse.
        File(trickplayDir, "meta.json").writeText(
            """{"height":180,"tileWidth":10,"tileHeight":20,"thumbnailCount":45,"interval":1000}"""
        )
        val media = File(tempFolder.root, "movie.mkv")

        assertNull(OfflineTrickplayHelper.loadLocalTrickplayInfo(media.absolutePath))
    }

    @Test
    fun loadLocalTrickplayInfo_returnsNullWhenFieldNonNumeric() = runBlocking {
        val trickplayDir = File(tempFolder.root, "trickplay").apply { mkdirs() }
        File(trickplayDir, "meta.json").writeText(
            """{"width":"wide","height":180,"tileWidth":10,"tileHeight":20,"thumbnailCount":45,"interval":1000}"""
        )
        val media = File(tempFolder.root, "movie.mkv")

        assertNull(OfflineTrickplayHelper.loadLocalTrickplayInfo(media.absolutePath))
    }

    // ── downloadTrickplayData (mocked repository) ───────────────────────────────

    @Test
    fun downloadTrickplayData_writesAllSheetsAndMeta() = runBlocking {
        val repo = mockk<PlaybackRepository>()
        val thumbnailsPerSheet = info.tileWidth * info.tileHeight // 200
        val totalSheets = (info.thumbnailCount + thumbnailsPerSheet - 1) / thumbnailsPerSheet // 1
        val bytes = ByteArray(8) { it.toByte() }
        // Every requested sheet returns bytes.
        coEvery { repo.getTrickplayTileImage(any(), any(), any()) } returns bytes

        OfflineTrickplayHelper.downloadTrickplayData("item-1", info, repo, tempFolder.root)

        val trickplayDir = File(tempFolder.root, "trickplay")
        for (i in 0 until totalSheets) {
            val sheet = File(trickplayDir, "trickplay_${i}.jpg")
            assertTrue("sheet $i should be written", sheet.exists())
            assertEquals(bytes.size, sheet.length().toInt())
        }
        val meta = File(trickplayDir, "meta.json")
        assertTrue("meta.json should be written", meta.exists())
        val text = meta.readText()
        assertTrue("meta must carry width", text.contains("\"width\":320"))
        assertTrue("meta must carry thumbnailCount", text.contains("\"thumbnailCount\":45"))
    }

    @Test
    fun downloadTrickplayData_writesMultipleSheetsWhenCountExceedsTilesPerSheet() = runBlocking {
        val repo = mockk<PlaybackRepository>()
        val bigInfo = info.copy(thumbnailCount = 450) // 450 > 200 tiles/sheet → 3 sheets
        coEvery { repo.getTrickplayTileImage(any(), any(), any()) } returns ByteArray(4)

        OfflineTrickplayHelper.downloadTrickplayData("item-1", bigInfo, repo, tempFolder.root)

        val trickplayDir = File(tempFolder.root, "trickplay")
        assertTrue(File(trickplayDir, "trickplay_0.jpg").exists())
        assertTrue(File(trickplayDir, "trickplay_1.jpg").exists())
        assertTrue(File(trickplayDir, "trickplay_2.jpg").exists())
        coVerify(exactly = 3) { repo.getTrickplayTileImage("item-1", 320, any()) }
    }

    @Test
    fun downloadTrickplayData_skipsSheetsWhenRepositoryReturnsNull() = runBlocking {
        val repo = mockk<PlaybackRepository>()
        coEvery { repo.getTrickplayTileImage(any(), any(), any()) } returns null

        OfflineTrickplayHelper.downloadTrickplayData("item-1", info, repo, tempFolder.root)

        // No sheet files written, but the meta file is still persisted.
        val trickplayDir = File(tempFolder.root, "trickplay")
        assertEquals(0, trickplayDir.listFiles { _, n -> n.endsWith(".jpg") }?.size ?: 0)
        assertTrue(File(trickplayDir, "meta.json").exists())
    }

    private fun writeMediaFile(): File {
        val media = File(tempFolder.root, "movie.mkv")
        media.writeBytes(ByteArray(16))
        return media
    }
}
