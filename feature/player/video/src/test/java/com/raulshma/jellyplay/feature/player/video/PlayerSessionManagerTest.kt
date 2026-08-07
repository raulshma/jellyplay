package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.PlayerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Unit tests for [PlayerSessionManager]'s [PlaybackSource]-based dispatch.
 *
 * Uses [PlayerType.EXTERNAL] to short-circuit engine creation — both
 * `loadOffline` and `loadOnline` return immediately after the EXTERNAL check,
 * letting us verify dispatch decisions without instantiating a real ExoPlayer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSessionManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var mediaRepository: MediaRepository
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
    private lateinit var aggregateStore: VideoPlayerAggregateStore
    private lateinit var playerLifecycleManager: PlayerLifecycleManager
    private lateinit var adaptiveBitrateManager: AdaptiveBitrateManager
    private lateinit var sessionManager: PlayerSessionManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.getString(com.raulshma.jellyplay.feature.player.video.R.string.player_video_error_offline_file_missing) } returns "Error: offline file missing"
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        playbackSourceResolver = mockk(relaxed = true)
        aggregateStore = mockk(relaxed = true)
        playerLifecycleManager = mockk(relaxed = true)
        val pipController = mockk<com.raulshma.jellyplay.core.data.playback.PipController>(relaxed = true)
        adaptiveBitrateManager = mockk(relaxed = true)

        // Default: EXTERNAL player to avoid real engine instantiation in unit tests.
        every { aggregateStore.aggregate } returns
            MutableStateFlow(VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL)))

        // Default: PlaybackInfo resolution yields nothing, so loadOnline
        // falls back to the static direct-stream URL (PlayMethod.DIRECT_PLAY).
        coEvery {
            playbackRepository.resolvePlayback(
                any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns null

        // android.net.Uri.fromFile returns null in JVM unit tests; mock it so
        // loadOffline's URL construction doesn't NPE. A relaxed mock returns
        // "" for toString(), which is sufficient — we assert on dispatch
        // decisions, not the URL value.
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns mockk(relaxed = true)

        sessionManager = PlayerSessionManager(
            context = context,
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher + SupervisorJob()),
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            aggregateStore = aggregateStore,
            playerLifecycleManager = playerLifecycleManager,
            pipController = pipController,
            adaptiveBitrateManager = adaptiveBitrateManager,
            // Tests use EXTERNAL, which short-circuits before any real engine is
            // built, so a real factory wired to the (relaxed-mock) context is fine.
            playerEngineFactory = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory(
                context,
                okHttpClient,
                mockk<com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider>(relaxed = true),
            ),
            playbackSourceResolver = playbackSourceResolver,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    // ── Auto resolution ───────────────────────────────────────────────

    @Test
    fun loadMedia_autoWithCompletedDownload_goesOffline() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        val download = downloadItem(itemId, tempFile.absolutePath)
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns download
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineMediaItem(itemId)

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertEquals("Offline", state.playMethodString)
        assertTrue(state.isReady)
        assertEquals(itemId, state.currentItemId)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun loadMedia_autoWithoutDownload_goesOnline() = runTest(testDispatcher) {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null
        coEvery { mediaRepository.getMediaDetail(itemId) } returns Result.success(mediaDetail(itemId))

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertEquals("Direct Play", state.playMethodString)
        assertTrue(state.isReady)
        coVerify(exactly = 1) { mediaRepository.getMediaDetail(itemId) }
    }

    @Test
    fun loadMedia_autoWithNonCompletedDownload_goesOnline() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        val download = downloadItem(itemId, tempFile.absolutePath, status = DownloadStatus.DOWNLOADING)
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns download
        coEvery { mediaRepository.getMediaDetail(itemId) } returns Result.success(mediaDetail(itemId))

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertEquals("Direct Play", state.playMethodString)
        coVerify(exactly = 1) { mediaRepository.getMediaDetail(itemId) }
    }

    // ── Forced Offline ────────────────────────────────────────────────

    @Test
    fun loadMedia_forcedOffline_goesOfflineEvenWithoutDbEntry() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        // No download in the DB
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null
        coEvery { offlineRepository.getOfflineItem(itemId) } returns null

        sessionManager.loadMedia(
            PlaybackSource.Offline(itemId, tempFile.absolutePath),
            startPositionTicks = 0L,
        )

        val state = sessionManager.sessionState.value
        assertEquals("Offline", state.playMethodString)
        assertTrue(state.isReady)
        // Should NOT have called the server
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun loadMedia_forcedOfflineWithMissingFile_reportsError() = runTest(testDispatcher) {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null

        sessionManager.loadMedia(
            PlaybackSource.Offline(itemId, "/nonexistent/path/file.mp4"),
            startPositionTicks = 0L,
        )

        val state = sessionManager.sessionState.value
        assertEquals("Error: offline file missing", state.title)
        assertFalse(state.isReady)
    }

    // ── Offline metadata propagation ──────────────────────────────────

    // Regression: loadOffline used to build MediaDetail.item without copying
    // seriesId/seasonId/episodeNumber from OfflineMediaItem, which silently
    // broke next-episode discovery, the "up next" overlay, and autoplay in
    // offline mode (every downstream path bailed on the null fields).
    @Test
    fun loadOffline_propagatesSeriesMetadataAndFlagsOffline() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-ep", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "ep-1"
        val download = downloadItem(itemId, tempFile.absolutePath)
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns download
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineEpisodeItem(itemId)

        sessionManager.loadMedia(
            PlaybackSource.Offline(itemId, tempFile.absolutePath),
            startPositionTicks = 0L,
        )

        val item = sessionManager.sessionState.value.mediaDetail?.item
        assertEquals("series-1", item?.seriesId)
        assertEquals("season-1", item?.seasonId)
        assertEquals(1, item?.seasonNumber)
        assertEquals(1, item?.episodeNumber)
        assertEquals("Test Series", item?.seriesName)
        assertTrue(sessionManager.sessionState.value.isOffline)
    }

    @Test
    fun loadOffline_withoutOfflineMetadata_stillFlagsOfflineAndKeepsItemId() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-movie", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns downloadItem(itemId, tempFile.absolutePath)
        // Legacy download with no offline_media row — only the download exists.
        coEvery { offlineRepository.getOfflineItem(itemId) } returns null

        sessionManager.loadMedia(
            PlaybackSource.Offline(itemId, tempFile.absolutePath),
            startPositionTicks = 0L,
        )

        val state = sessionManager.sessionState.value
        assertEquals(itemId, state.mediaDetail?.item?.id)
        assertNull(state.mediaDetail?.item?.seriesId)
        assertTrue(state.isOffline)
    }

    // ── Forced Online ─────────────────────────────────────────────────

    @Test
    fun loadMedia_forcedOnline_ignoresCompletedDownload() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        // A completed download exists in the DB…
        val download = downloadItem(itemId, tempFile.absolutePath)
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns download
        // …but we force online, so the server should be queried.
        coEvery { mediaRepository.getMediaDetail(itemId) } returns Result.success(mediaDetail(itemId))

        sessionManager.loadMedia(
            PlaybackSource.Online(itemId, null),
            startPositionTicks = 0L,
        )

        val state = sessionManager.sessionState.value
        assertEquals("Direct Play", state.playMethodString)
        assertTrue(state.isReady)
        coVerify(exactly = 1) { mediaRepository.getMediaDetail(itemId) }
    }

    // ── Legacy overload ───────────────────────────────────────────────

    @Test
    fun loadMedia_legacyOverload_delegatesToAutoResolution() = runTest(testDispatcher) {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null
        coEvery { mediaRepository.getMediaDetail(itemId) } returns Result.success(mediaDetail(itemId))

        sessionManager.loadMedia(itemId, mediaSourceId = null, startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertEquals("Direct Play", state.playMethodString)
        assertTrue(state.isReady)
    }

    @Test
    fun loadMedia_legacyOverload_withCompletedDownload_goesOffline() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        val download = downloadItem(itemId, tempFile.absolutePath)
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns download
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineMediaItem(itemId)

        sessionManager.loadMedia(itemId, mediaSourceId = null, startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertEquals("Offline", state.playMethodString)
        assertTrue(state.isReady)
    }

    // ── Session state reset ───────────────────────────────────────────

    @Test
    fun loadMedia_resetsIsReadyAtStart() = runTest(testDispatcher) {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null
        coEvery { mediaRepository.getMediaDetail(itemId) } returns Result.success(mediaDetail(itemId))

        // We can't observe the transient false because loadMedia is synchronous
        // in runTest, but we can verify the final state is ready.
        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)
        assertTrue(sessionManager.sessionState.value.isReady)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun downloadItem(
        itemId: String,
        path: String,
        status: DownloadStatus = DownloadStatus.COMPLETED,
    ) = DownloadItem(
        id = "dl-$itemId",
        mediaItemId = itemId,
        name = "Test Movie",
        mediaType = MediaType.MOVIE,
        downloadPath = path,
        downloadUrl = "http://example.com/movie",
        totalSizeBytes = 1_000_000L,
        downloadedBytes = 1_000_000L,
        status = status,
    )

    private fun offlineMediaItem(itemId: String) = OfflineMediaItem(
        id = itemId,
        name = "Test Movie",
        mediaType = MediaType.MOVIE,
        overview = "A test movie for unit testing.",
        runTimeTicks = 3_600_000 * 10_000L,
    )

    /** An episode offline item carrying series/season/episode metadata. */
    private fun offlineEpisodeItem(itemId: String) = OfflineMediaItem(
        id = itemId,
        name = "Episode 1",
        mediaType = MediaType.EPISODE,
        overview = "First episode.",
        runTimeTicks = 2_400_000 * 10_000L,
        seriesId = "series-1",
        seasonId = "season-1",
        seriesName = "Test Series",
        seasonName = "Season 1",
        seasonNumber = 1,
        episodeNumber = 1,
    )

    private fun mediaDetail(itemId: String) = MediaDetail(
        item = MediaItem(
            id = itemId,
            name = "Test Movie",
            mediaType = MediaType.MOVIE,
        ),
    )
}
