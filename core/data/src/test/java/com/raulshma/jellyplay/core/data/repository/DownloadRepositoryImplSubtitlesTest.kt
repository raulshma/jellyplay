package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.OfflineSubtitleEntry
import com.raulshma.jellyplay.core.model.OfflineSubtitleManifest
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Pins the non-destructive contract of
 * [DownloadRepositoryImpl.downloadExternalSubtitles]:
 *
 * - a genuine server-side removal (no deliverable subtitle streams) mirrors to
 *   disk by clearing the sidecar dir and reports success;
 * - a transient fetch failure (deliverable streams exist but none downloaded)
 *   leaves existing sidecars untouched and reports failure, so the resync
 *   baseline rolls its subtitle axis back instead of wiping working subs.
 *
 * Constructed like [DownloadRepositoryImplResumeTest]; the subtitle path touches
 * only `playbackRepository` (URL resolution) and on-disk files, so the rest are
 * relaxed mocks. V3 downloads conveyor: the impl moved to :shared:core:data
 * jvmShared — this suite constructs it through the seam ctor (same fakes as the
 * resume suite).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadRepositoryImplSubtitlesTest {

    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val offlineMediaDao: OfflineMediaDao = mockk(relaxed = true)
    private val playbackStateDao: PlaybackStateDao = mockk(relaxed = true)
    private val syncBaselineDao: SyncBaselineDao = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val httpClient: OkHttpClient = mockk()
    private val preferencesStore: DownloadsStore = mockk(relaxed = true)
    private val json: Json = Json
    private val downloadDelegate: kotlin.Lazy<DownloadDelegate> = lazy { mockk<DownloadDelegate>(relaxed = true) }
    private val storagePolicy: StoragePolicy = mockk(relaxed = true)
    private val downloadEnqueuer: DownloadEnqueueCoordinator = mockk(relaxed = true)
    private val storageLayout: DownloadStorageLayoutContract = mockk(relaxed = true)
    private val syncComparator: OfflineSyncComparator = mockk(relaxed = true)
    private val episodeCatalogue: EpisodeCatalogue = mockk(relaxed = true)
    private val progressNotifier: DownloadProgressNotifier = mockk(relaxed = true)
    private val imagePreloader: OfflineImagePreloader = mockk(relaxed = true)

    private lateinit var tempDir: File
    private lateinit var downloadPath: File
    private val itemId = "item-1"

    private fun repository() = DownloadRepositoryImpl(
        downloadDao = downloadDao,
        offlineMediaDao = offlineMediaDao,
        playbackStateDao = playbackStateDao,
        syncBaselineDao = syncBaselineDao,
        database = database,
        mediaRepository = MediaRepositoryAccess { mediaRepository },
        episodeCatalogue = episodeCatalogue,
        playbackRepository = playbackRepository,
        httpClient = httpClient,
        downloadsStore = preferencesStore,
        json = json,
        downloadDelegate = downloadDelegate,
        storagePolicy = storagePolicy,
        downloadEnqueuer = downloadEnqueuer,
        storageLayout = storageLayout,
        syncComparator = syncComparator,
        progressNotifier = progressNotifier,
        imagePreloader = imagePreloader,
    )

    private fun subtitlesDir(): File =
        File(tempDir, DownloadArtifacts.subtitlesDir(itemId))

    /** Seeds a sidecar dir that looks like a previously-successful bundle. */
    private fun seedWorkingSubtitles() {
        val dir = subtitlesDir().apply { mkdirs() }
        File(dir, DownloadArtifacts.SUBTITLE_MANIFEST_FILE).writeText(
            json.encodeToString(
                OfflineSubtitleManifest(
                    listOf(OfflineSubtitleEntry(index = 0, fileName = "0.srt", language = "eng", codec = "srt")),
                ),
            ),
        )
        File(dir, "0.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nhi\n")
    }

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "jellyplay-sub-test-${System.nanoTime()}").apply { mkdirs() }
        downloadPath = File(tempDir, "video.mkv").apply { createNewFile() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `fetch failure leaves existing sidecars untouched and reports failure`() = runTest {
        seedWorkingSubtitles()
        // Deliverable stream exists, but URL resolution yields a blank URL, so
        // every iteration `continue`s and nothing lands on disk.
        val streams = listOf(MediaStream(index = 0, type = StreamType.SUBTITLE, codec = "srt", language = "eng", isExternal = true, displayTitle = "English"))
        coEvery { playbackRepository.buildSubtitleDeliveryUrl(itemId, "src-1", 0, "srt") } returns ""

        val ok = repository().downloadExternalSubtitles(itemId, "src-1", streams, downloadPath.absolutePath)

        assertFalse("fetch failure must report failure so the baseline rolls back", ok)
        val dir = subtitlesDir()
        assertTrue("sidecar dir must survive a transient fetch failure", dir.exists())
        assertTrue("existing manifest must survive", File(dir, DownloadArtifacts.SUBTITLE_MANIFEST_FILE).exists())
        assertTrue("existing sub file must survive", File(dir, "0.srt").exists())
    }

    @Test
    fun `image-only inventory is treated as removal and reports success`() = runTest {
        // External PGS has no fetchable delivery endpoint; an inventory of only
        // such streams must terminate (success, dir cleared) rather than report
        // failure on every sync — otherwise the resync retries it forever.
        seedWorkingSubtitles()
        val streams = listOf(
            MediaStream(index = 1, type = StreamType.SUBTITLE, codec = "pgs", language = "eng", isExternal = true, displayTitle = "English"),
        )

        val ok = repository().downloadExternalSubtitles(itemId, "src-1", streams, downloadPath.absolutePath)

        assertTrue("never-fetchable inventory must terminate as success", ok)
        assertFalse("sidecar dir must be cleared", subtitlesDir().exists())
    }

    @Test
    fun `genuine server-side removal clears the sidecar dir and reports success`() = runTest {
        seedWorkingSubtitles()

        val ok = repository().downloadExternalSubtitles(itemId, "src-1", emptyList(), downloadPath.absolutePath)

        assertTrue("server removal should seed the baseline as empty (success)", ok)
        assertFalse("sidecar dir must be cleared to mirror the removal", subtitlesDir().exists())
    }

    @Test
    fun `genuine removal with no prior dir reports success without creating one`() = runTest {
        // No sidecar dir existed in the first place — removal is a no-op that
        // still reports success (baseline seeds as empty).
        val ok = repository().downloadExternalSubtitles(itemId, "src-1", emptyList(), downloadPath.absolutePath)

        assertTrue(ok)
        assertFalse(subtitlesDir().exists())
    }
}
