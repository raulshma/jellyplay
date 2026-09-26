package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.playback.PlaybackIdentity
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
import com.raulshma.jellyplay.core.model.DownloadFileInventory
import com.raulshma.jellyplay.core.model.DownloadedFileCategory
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import com.raulshma.jellyplay.core.model.TrickplayInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Direct pins for the sidecar/artifact cluster extracted from
 * [DownloadRepositoryImpl] into [DownloadSidecarCore]: the trickplay bundle
 * (sheet naming + meta.json), the local manifest/segments reads (item-scoped
 * first, legacy fallback), the file-inventory enumeration, and the
 * offline-image write. The subtitle-fetch half of the core stays pinned at
 * repository level by DownloadRepositoryImplSubtitlesTest (androidHostTest —
 * MockWebServer + Robolectric), which constructs the impl through the seam
 * ctor and therefore exercises this core through the delegates.
 *
 * DAO/repository deps are mockk fakes; everything on disk is real (the
 * [SubtitleBundleWriterTest] placement precedent).
 */
class DownloadSidecarCoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val playbackIdentity: PlaybackIdentity = mockk(relaxed = true)
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)

    private val json = Json { ignoreUnknownKeys = true }

    private val itemId = "item-1"

    private fun core() = DownloadSidecarCore(
        playbackRepository = playbackRepository,
        playbackIdentity = playbackIdentity,
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        syncBaselineDao = syncBaselineDao,
        httpClient = OkHttpClient(),
        json = json,
    )

    private suspend fun seededDownloadRow(seriesId: String? = null): File {
        val media = tmp.newFile("video.mkv").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        coEvery { downloadDao.getDownloadByMediaItemId(itemId) } returns downloadEntity(
            downloadPath = media.absolutePath,
            seriesId = seriesId,
        )
        return media
    }

    private fun downloadEntity(downloadPath: String, seriesId: String? = null) = DownloadEntity(
        id = "dl-1",
        mediaItemId = itemId,
        name = "Video",
        mediaType = "MOVIE",
        downloadPath = downloadPath,
        downloadUrl = "https://server/Videos/$itemId",
        totalSizeBytes = 4L,
        downloadedBytes = 4L,
        status = "COMPLETED",
        seriesId = seriesId,
    )

    // ── downloadTrickplayData ─────────────────────────────────────────────

    @Test
    fun `trickplay bundle writes one sheet per tile grid and a meta json`() = runTest {
        // 150 thumbnails / 100 per sheet → exactly 2 sheets.
        val info = TrickplayInfo(
            width = 320, height = 180, tileWidth = 10, tileHeight = 10,
            thumbnailCount = 150, interval = 10_000, bandwidth = 1,
        )
        coEvery { playbackRepository.getTrickplayTileImage(itemId, 320, any()) } returns byteArrayOf(0xFF.toByte())

        val ok = core().downloadTrickplayData(itemId, info, "${seededDownloadRow()}")

        assertTrue(ok)
        val dir = File(tmp.root, DownloadArtifacts.trickplayDir(itemId))
        assertTrue(File(dir, "trickplay_0.jpg").isFile)
        assertTrue(File(dir, "trickplay_1.jpg").isFile)
        // No third sheet: 150 thumbnails fit two 100-tile grids.
        assertFalse(File(dir, "trickplay_2.jpg").exists())
        val meta = File(dir, "meta.json").readText()
        assertTrue("meta.json must carry the sprite geometry", meta.contains("\"width\":320"))
        assertTrue(meta.contains("\"tileWidth\":10"))
        assertTrue(meta.contains("\"thumbnailCount\":150"))
    }

    @Test
    fun `missing tile sheet is skipped without failing the bundle`() = runTest {
        val info = TrickplayInfo(
            width = 320, height = 180, tileWidth = 10, tileHeight = 10,
            thumbnailCount = 150, interval = 10_000, bandwidth = 1,
        )
        coEvery { playbackRepository.getTrickplayTileImage(itemId, 320, 0) } returns null
        coEvery { playbackRepository.getTrickplayTileImage(itemId, 320, 1) } returns byteArrayOf(1)

        val ok = core().downloadTrickplayData(itemId, info, "${seededDownloadRow()}")

        assertTrue("a skipped sheet is best-effort, not a failure", ok)
        val dir = File(tmp.root, DownloadArtifacts.trickplayDir(itemId))
        assertFalse(File(dir, "trickplay_0.jpg").exists())
        assertTrue(File(dir, "trickplay_1.jpg").isFile)
    }

    // ── loadLocalSubtitleManifest ─────────────────────────────────────────

    @Test
    fun `subtitle manifest read prefers the item-scoped dir then falls back to legacy`() = runTest {
        val media = tmp.newFile("video.mkv")
        val manifest = json.encodeToString(
            OfflineSubtitleManifest(listOf(OfflineSubtitleEntry(index = 0, fileName = "0.srt"))),
        )
        val scoped = File(tmp.root, "${DownloadArtifacts.subtitlesDir(itemId)}/${DownloadArtifacts.SUBTITLE_MANIFEST_FILE}")
        scoped.parentFile.mkdirs()
        scoped.writeText(manifest)
        val legacy = File(tmp.root, "${DownloadArtifacts.LEGACY_SUBTITLES_DIR}/${DownloadArtifacts.SUBTITLE_MANIFEST_FILE}")
        legacy.parentFile.mkdirs()
        legacy.writeText(manifest)

        val readScoped = core().loadLocalSubtitleManifest(media.absolutePath, itemId)
        assertNotNull(readScoped)
        assertEquals("0.srt", readScoped!!.subtitles.single().fileName)

        // Scoped dir gone → the legacy pre-fix download still serves.
        scoped.parentFile.deleteRecursively()
        val readLegacy = core().loadLocalSubtitleManifest(media.absolutePath, itemId)
        assertNotNull(readLegacy)

        // Neither → null (no manifest for this download).
        legacy.parentFile.deleteRecursively()
        assertNull(core().loadLocalSubtitleManifest(media.absolutePath, itemId))
    }

    // ── loadLocalSegments ─────────────────────────────────────────────────

    @Test
    fun `segments read prefers the item-scoped file then falls back to legacy`() = runTest {
        val media = seededDownloadRow()
        val segments = listOf(
            MediaSegment(id = "seg-1", itemId = itemId, type = MediaSegmentType.INTRO, startTicks = 0, endTicks = 100),
        )
        val scoped = File(tmp.root, DownloadArtifacts.segmentsFile(itemId)).apply {
            writeText(json.encodeToString(segments))
        }

        val readScoped = core().loadLocalSegments(itemId)
        assertEquals(listOf("seg-1"), readScoped!!.map { it.id })

        // Scoped file gone → legacy segments.json still serves.
        scoped.delete()
        File(tmp.root, DownloadArtifacts.LEGACY_SEGMENTS_FILE).writeText(json.encodeToString(segments))
        assertEquals(listOf("seg-1"), core().loadLocalSegments(itemId)!!.map { it.id })
    }

    @Test
    fun `segments read is null without a download row or any file`() = runTest {
        coEvery { downloadDao.getDownloadByMediaItemId(itemId) } returns null
        assertNull(core().loadLocalSegments(itemId))

        seededDownloadRow()
        assertNull(core().loadLocalSegments(itemId))
    }

    // ── getDownloadFileInventory ──────────────────────────────────────────

    @Test
    fun `inventory enumerates every artifact category beside the media file`() = runTest {
        val media = seededDownloadRow(seriesId = "series-1")
        File(tmp.root, DownloadArtifacts.trickplayDir(itemId)).apply { mkdirs() }
            .resolve("trickplay_0.jpg").writeBytes(byteArrayOf(1))
        File(tmp.root, DownloadArtifacts.subtitlesDir(itemId)).apply { mkdirs() }
            .resolve("0.srt").writeText("sub")
        File(tmp.root, DownloadArtifacts.segmentsFile(itemId)).writeText("[]")
        File(tmp.root, DownloadArtifacts.posterFile(itemId)).writeBytes(byteArrayOf(2))
        // series-keyed artwork + a cast portrait from the offline row's peopleJson
        File(tmp.root, DownloadArtifacts.posterFile("series-1")).writeBytes(byteArrayOf(3))
        File(tmp.root, DownloadArtifacts.personImageFile("person-1")).writeBytes(byteArrayOf(4))
        coEvery { offlineMediaDao.getById(itemId) } returns OfflineMediaEntity(
            id = itemId,
            name = "Video",
            mediaType = "MOVIE",
            seriesId = "series-1",
            peopleJson = encodeCast(listOf(com.raulshma.jellyplay.core.model.OfflinePersonInfo(id = "person-1", name = "Actor"))),
        )

        val inventory = core().getDownloadFileInventory(itemId)

        val byCategory = inventory.entries.groupBy { it.category }.mapValues { it.value.map { e -> e.displayName } }
        assertEquals(listOf(media.name), byCategory[DownloadedFileCategory.MEDIA])
        assertEquals(listOf("trickplay_0.jpg"), byCategory[DownloadedFileCategory.TRICKPLAY])
        assertEquals(listOf("0.srt"), byCategory[DownloadedFileCategory.SUBTITLE])
        assertTrue(byCategory[DownloadedFileCategory.SEGMENT]!!.isNotEmpty())
        // Per-item poster + series-keyed poster + cast portrait.
        assertEquals(
            setOf(DownloadArtifacts.posterFile(itemId), DownloadArtifacts.posterFile("series-1"), DownloadArtifacts.personImageFile("person-1")),
            byCategory[DownloadedFileCategory.IMAGE]!!.toSet(),
        )
        assertEquals(inventory.entries.sumOf { it.sizeBytes }, inventory.totalSizeBytes)
    }

    @Test
    fun `inventory is empty when the media file is gone`() = runTest {
        coEvery { downloadDao.getDownloadByMediaItemId(itemId) } returns downloadEntity(
            downloadPath = File(tmp.root, "missing.mkv").absolutePath,
        )

        assertEquals(DownloadFileInventory.EMPTY, core().getDownloadFileInventory(itemId))
    }

    // ── downloadMediaSegments / downloadImageToDisk / markSubtitlesPending ─

    @Test
    fun `segments download writes decodable json and skips the write when empty`() = runTest {
        val media = seededDownloadRow()
        val segments = listOf(
            MediaSegment(id = "seg-2", itemId = itemId, type = MediaSegmentType.OUTRO, startTicks = 5, endTicks = 50),
        )
        coEvery { playbackRepository.getMediaSegments(itemId) } returns Result.success(segments)

        assertTrue(core().downloadMediaSegments(itemId, media.absolutePath))
        val file = File(tmp.root, DownloadArtifacts.segmentsFile(itemId))
        assertEquals(listOf("seg-2"), json.decodeFromString<List<MediaSegment>>(file.readText()).map { it.id })

        // Empty server answer → success (nothing to persist), no file created
        // for a second item sharing the dir.
        coEvery { playbackRepository.getMediaSegments("item-2") } returns Result.success(emptyList())
        assertTrue(core().downloadMediaSegments("item-2", media.absolutePath))
        assertFalse(File(tmp.root, DownloadArtifacts.segmentsFile("item-2")).exists())
    }

    @Test
    fun `image write returns the absolute path and null when the server has no image`() = runTest {
        val media = seededDownloadRow()
        coEvery { playbackRepository.getItemImageBytes(itemId, "Primary", 300) } returns byteArrayOf(9)

        val path = core().downloadImageToDisk(
            itemId, "Primary", 300, tmp.root, DownloadArtifacts.posterFile(itemId),
        )

        assertEquals(File(tmp.root, DownloadArtifacts.posterFile(itemId)).absolutePath, path)
        coEvery { playbackRepository.getItemImageBytes(itemId, "Backdrop", 1280) } returns null
        assertNull(
            core().downloadImageToDisk(itemId, "Backdrop", 1280, tmp.root, DownloadArtifacts.backdropFile(itemId)),
        )
        assertFalse(File(tmp.root, DownloadArtifacts.backdropFile(itemId)).exists())
    }

    @Test
    fun `markSubtitlesPending delegates to the dao's atomic mark`() = runTest {
        core().markSubtitlesPending(itemId)

        coVerify(exactly = 1) { syncBaselineDao.markSubtitlesPending(itemId) }
    }
}
