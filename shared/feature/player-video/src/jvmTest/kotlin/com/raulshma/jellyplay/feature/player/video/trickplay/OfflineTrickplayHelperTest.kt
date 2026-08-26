package com.raulshma.jellyplay.feature.player.video.trickplay

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.TrickplayInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import kotlin.test.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineTrickplayHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun getLocalTrickplayDir_returnsParentTrickplayFolder() {
        val mediaFile = File(tempFolder.root, "downloads/video.mp4")
        val dir = OfflineTrickplayHelper.getLocalTrickplayDir(mediaFile.absolutePath)
        assertNotNull(dir)
        assertEquals(dir?.name, "trickplay")
    }

    @Test
    fun loadLocalTrickplayInfo_parsesJsonMetadataSuccessfully() = runBlocking {
        val downloadsDir = File(tempFolder.root, "downloads").apply { mkdirs() }
        val trickplayDir = File(downloadsDir, "trickplay").apply { mkdirs() }
        val metaFile = File(trickplayDir, "meta.json")

        metaFile.writeText(
            """
            {
                "width": 320,
                "height": 180,
                "tileWidth": 5,
                "tileHeight": 5,
                "thumbnailCount": 50,
                "interval": 2000,
                "bandwidth": 150000
            }
            """.trimIndent()
        )

        val videoPath = File(downloadsDir, "video.mp4").absolutePath
        val info = OfflineTrickplayHelper.loadLocalTrickplayInfo(videoPath)

        assertNotNull(info)
        assertEquals(320, info?.width)
        assertEquals(180, info?.height)
        assertEquals(5, info?.tileWidth)
        assertEquals(5, info?.tileHeight)
        assertEquals(50, info?.thumbnailCount)
        assertEquals(2000, info?.interval)
        assertEquals(150000, info?.bandwidth)
    }

    @Test
    fun loadLocalTrickplayInfo_returnsNullWhenMetaJsonMissingOrInvalid() = runBlocking {
        val downloadsDir = File(tempFolder.root, "downloads").apply { mkdirs() }
        val videoPath = File(downloadsDir, "video.mp4").absolutePath

        assertNull(OfflineTrickplayHelper.loadLocalTrickplayInfo(videoPath))
    }

    @Test
    fun downloadTrickplayData_writesSheetsAndMetaJson() = runBlocking {
        val repo: PlaybackRepository = mockk()
        coEvery { repo.getTrickplayTileImage("item-1", 160, 0) } returns ByteArray(10) { 1 }

        val targetDir = tempFolder.newFolder("target")
        val trickplayInfo = TrickplayInfo(
            width = 160,
            height = 90,
            tileWidth = 2,
            tileHeight = 2,
            thumbnailCount = 4,
            interval = 1000,
            bandwidth = 50000,
        )

        OfflineTrickplayHelper.downloadTrickplayData("item-1", trickplayInfo, repo, targetDir)

        val trickplayDir = File(targetDir, "trickplay")
        val sheetFile = File(trickplayDir, "trickplay_0.jpg")
        val metaFile = File(trickplayDir, "meta.json")

        assertEquals(true, sheetFile.exists())
        assertEquals(true, metaFile.exists())
    }
}
