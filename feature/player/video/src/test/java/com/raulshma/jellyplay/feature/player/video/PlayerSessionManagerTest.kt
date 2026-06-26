package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.net.Uri
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.UserPreferences
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
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var playerLifecycleManager: PlayerLifecycleManager
    private lateinit var adaptiveBitrateManager: AdaptiveBitrateManager
    private lateinit var sessionManager: PlayerSessionManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        preferencesStore = mockk(relaxed = true)
        playerLifecycleManager = mockk(relaxed = true)
        adaptiveBitrateManager = mockk(relaxed = true)

        // Default: EXTERNAL player to avoid real engine instantiation in unit tests.
        every { preferencesStore.preferences } returns
            MutableStateFlow(UserPreferences(preferredPlayer = PlayerType.EXTERNAL))

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
            preferencesStore = preferencesStore,
            playerLifecycleManager = playerLifecycleManager,
            adaptiveBitrateManager = adaptiveBitrateManager,
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
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns download
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
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns null
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
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns download
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
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns null
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
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns null

        sessionManager.loadMedia(
            PlaybackSource.Offline(itemId, "/nonexistent/path/file.mp4"),
            startPositionTicks = 0L,
        )

        val state = sessionManager.sessionState.value
        assertEquals("Error: offline file missing", state.title)
        assertFalse(state.isReady)
    }

    // ── Forced Online ─────────────────────────────────────────────────

    @Test
    fun loadMedia_forcedOnline_ignoresCompletedDownload() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        // A completed download exists in the DB…
        val download = downloadItem(itemId, tempFile.absolutePath)
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns download
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
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns null
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
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns download
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
        coEvery { downloadRepository.getDownloadByMediaItemId(itemId) } returns null
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

    private fun mediaDetail(itemId: String) = MediaDetail(
        item = MediaItem(
            id = itemId,
            name = "Test Movie",
            mediaType = MediaType.MOVIE,
        ),
    )
}
