package com.raulshma.jellyplay.feature.player.video.trickplay

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.TrickplayInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the [TrickplayPreparation] precedence ladder
 * (download present/absent × local bundle present/absent × fetch ok/fail)
 * and the directory derivations this module now owns.
 */
class TrickplayPreparationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val itemId = "item-1"

    private lateinit var controller: RecordingTrickplayController
    private lateinit var offlinePlaybackFacade: OfflinePlaybackFacade
    private lateinit var mediaRepository: MediaRepository
    private lateinit var preparation: TrickplayPreparation

    /** Downloads root — the parent dir every derivation starts from. */
    private lateinit var downloadsDir: File

    private var downloadPath: String? = null

    @BeforeTest
    fun setUp() {
        controller = RecordingTrickplayController()
        offlinePlaybackFacade = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        preparation = TrickplayPreparation(
            controller = controller,
            offlinePlaybackFacade = offlinePlaybackFacade,
            mediaRepository = mediaRepository,
        )
        downloadsDir = File(tempFolder.root, "downloads").apply { mkdirs() }
        coEvery { offlinePlaybackFacade.getDownloadPath(itemId) } answers { downloadPath }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun mediaFile(): File {
        val file = File(downloadsDir, "video.mp4")
        downloadPath = file.absolutePath
        return file
    }

    private fun sourceInfo() = TrickplayInfo(
        width = 320, height = 180, tileWidth = 5, tileHeight = 5,
        thumbnailCount = 50, interval = 2000, bandwidth = 150_000,
    )

    private fun sourceWithManifest(): MediaSource =
        MediaSource(id = "ms-1", name = "src", trickplayInfo = sourceInfo())

    private fun serverReturns(info: TrickplayInfo?) {
        val source = MediaSource(id = "ms-2", name = "srv", trickplayInfo = info)
        val detail = mockk<MediaDetail>()
        every { detail.mediaSources } returns listOf(source)
        coEvery { mediaRepository.getMediaDetail(itemId) } returns Result.success(detail)
    }

    private fun serverFails() {
        coEvery { mediaRepository.getMediaDetail(itemId) } returns Result.failure(IllegalStateException("offline"))
    }

    /** Writes a parseable meta.json into [dir] (the download bundle layout). */
    private fun writeMeta(dir: File) {
        dir.mkdirs()
        File(dir, "meta.json").writeText(
            """
            {"width":320,
            "height":180,
            "tileWidth":5,
            "tileHeight":5,
            "thumbnailCount":50,
            "interval":2000,
            "bandwidth":150000}
            """.trimIndent(),
        )
    }

    // ─── arm 1: source carries the server manifest ────────────────────────────

    @Test
    fun sourceManifest_withoutDownload_initializesStreaming() = runBlocking {
        downloadPath = null

        val result = preparation.prepare(itemId, sourceWithManifest())

        assertEquals(sourceInfo(), result)
        assertEquals(listOf("initialize"), controller.ops)
        // The streaming arm never derives or touches a filesystem dir.
        assertTrue(controller.initializedDirs.isEmpty())
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun sourceManifest_withDownload_cachesIntoUnScopedTrickplayDir() = runBlocking {
        mediaFile()
        // A scoped dir existing must not sway the cache arms — they always
        // derived the un-scoped dir inline in the former VM code.
        File(downloadsDir, "trickplay_$itemId").apply { mkdirs() }

        val result = preparation.prepare(itemId, sourceWithManifest())

        assertEquals(sourceInfo(), result)
        assertEquals(listOf("initializeWithCache"), controller.ops)
        assertEquals(File(downloadsDir, "trickplay"), controller.initializedDirs.single())
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    // ─── arm 2: local bundle shipped with the download ────────────────────────

    @Test
    fun noSourceManifest_withoutDownload_isNullWithoutSideEffects() = runBlocking {
        downloadPath = null

        val result = preparation.prepare(itemId, null)

        assertNull(result)
        assertTrue(controller.ops.isEmpty())
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun noSourceManifest_scopedBundle_initializesLocalFromScopedDir() = runBlocking {
        mediaFile()
        writeMeta(File(downloadsDir, "trickplay_$itemId"))

        val result = preparation.prepare(itemId, null)

        assertEquals(320, result?.width)
        assertEquals(listOf("initializeLocal"), controller.ops)
        assertEquals(File(downloadsDir, "trickplay_$itemId"), controller.initializedDirs.single())
        // A local bundle means no server round-trip.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun noSourceManifest_legacyUnScopedBundle_initializesLocalFromUnScopedDir() = runBlocking {
        mediaFile()
        writeMeta(File(downloadsDir, "trickplay"))

        val result = preparation.prepare(itemId, null)

        assertEquals(320, result?.width)
        assertEquals(listOf("initializeLocal"), controller.ops)
        assertEquals(File(downloadsDir, "trickplay"), controller.initializedDirs.single())
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    // ─── arm 3: live server fetch cached for the next offline session ─────────

    @Test
    fun noSourceManifest_noBundle_fetchOk_cachesServerManifestIntoUnScopedDir() = runBlocking {
        mediaFile()
        serverReturns(sourceInfo())

        val result = preparation.prepare(itemId, null)

        assertEquals(sourceInfo(), result)
        assertEquals(listOf("initializeWithCache"), controller.ops)
        val cacheDir = controller.initializedDirs.single()
        assertEquals(File(downloadsDir, "trickplay"), cacheDir)
        // mkdirs ran before the controller took the dir (the former inline
        // order), so the next offline session can read it back.
        assertTrue(cacheDir.exists())
    }

    @Test
    fun noSourceManifest_noBundle_fetchFails_isNullWithoutControllerCalls() = runBlocking {
        mediaFile()
        serverFails()

        val result = preparation.prepare(itemId, null)

        assertNull(result)
        assertTrue(controller.ops.isEmpty())
    }

    @Test
    fun noSourceManifest_noBundle_fetchOkButServerHasNoTrickplay_isNull() = runBlocking {
        mediaFile()
        serverReturns(null)

        val result = preparation.prepare(itemId, null)

        assertNull(result)
        assertTrue(controller.ops.isEmpty())
    }
}

/** Recording fake over [TrickplayController] — the dispatch assertions read this. */
private class RecordingTrickplayController : TrickplayController {

    val ops = mutableListOf<String>()
    val initializedDirs = mutableListOf<File>()

    override fun initialize(itemId: String, trickplayInfo: TrickplayInfo) {
        ops += "initialize"
    }

    override fun initializeWithCache(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File) {
        ops += "initializeWithCache"
        initializedDirs += cacheDir
    }

    override fun initializeLocal(itemId: String, trickplayInfo: TrickplayInfo, cacheDir: File) {
        ops += "initializeLocal"
        initializedDirs += cacheDir
    }

    override fun clear() { ops += "clear" }

    override suspend fun getThumbnail(positionMs: Long): Any? = null
}
