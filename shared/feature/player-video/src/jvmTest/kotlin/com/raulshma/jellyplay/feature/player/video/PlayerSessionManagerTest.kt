package com.raulshma.jellyplay.feature.player.video

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
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.PlayerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Pure-JVM port of the legacy Android test of the same name (the migration
 * dropped the Robolectric harness; the `Context`/`Uri.fromFile` mocks it
 * needed do not exist in common code — [fileUriString] builds real
 * `file://` URIs from real temp files, so no static mocking remains).
 *
 * This file carries the upstream v0.10.6 additions: the #146 offline-gate
 * suite. The constructor grows the two upstream seams — [OfflineModeManager]
 * (fail-fast gate) and [PlayerVideoMessageBus] (feedback) — mirrored here by
 * a recording fake bus so the failLoad contract ("the state write and the
 * feedback emission cannot drift apart") is pinned end to end.
 *
 * Uses [PlayerType.EXTERNAL] to short-circuit engine creation — both
 * `loadOffline` and `loadOnline` return immediately after the EXTERNAL check,
 * letting us verify dispatch decisions without instantiating a real engine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSessionManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    /** Records [PlayerVideoMessageBus.error] emissions — the test's feedback probe. */
    private class RecordingMessageBus : PlayerVideoMessageBus {
        val errors = mutableListOf<String>()
        val infos = mutableListOf<String>()

        override fun info(message: String) {
            infos += message
        }

        override fun error(message: String) {
            errors += message
        }

        override fun info(message: PlayerVideoMessage) {
            infos += message.toString()
        }
    }

    private lateinit var mediaRepository: MediaRepository
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var offlineRepository: OfflineRepository
    private lateinit var playbackSourceResolver: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
    private lateinit var aggregateStore: VideoPlayerAggregateStore
    private lateinit var playerLifecycleManager: PlayerLifecycleManager
    private lateinit var adaptiveBitrateManager: AdaptiveBitrateManager
    private lateinit var offlineModeManager: com.raulshma.jellyplay.core.data.offline.OfflineModeManager
    private lateinit var messageBus: RecordingMessageBus
    private lateinit var sessionManager: PlayerSessionManager

    @Before
    fun setUp() {
        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        offlineRepository = mockk(relaxed = true)
        playbackSourceResolver = mockk(relaxed = true)
        aggregateStore = mockk(relaxed = true)
        playerLifecycleManager = mockk(relaxed = true)
        val pipController = mockk<PipController>(relaxed = true)
        adaptiveBitrateManager = mockk(relaxed = true)
        // Relaxed mock: isOffline defaults to false (online), matching the
        // historical no-gate behaviour the existing tests were written against.
        offlineModeManager = mockk(relaxed = true)
        messageBus = RecordingMessageBus()

        // Default: EXTERNAL player to avoid real engine instantiation in unit tests.
        every { aggregateStore.aggregate } returns
            MutableStateFlow(VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL)))
        // The load path awaits the raw (hydrated) flow rather than the StateFlow's
        // cold-start initial value, so stub it with the same single emission.
        every { aggregateStore.aggregateRaw } returns flowOf(
            VideoPlayerAggregate(playback = PlaybackSlice(preferredPlayer = PlayerType.EXTERNAL)),
        )

        // Default: PlaybackInfo resolution yields nothing, so loadOnline
        // falls back to the static direct-stream URL (PlayMethod.DIRECT_PLAY).
        coEvery {
            playbackRepository.resolvePlayback(
                any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns null

        sessionManager = PlayerSessionManager(
            scope = CoroutineScope(testDispatcher + SupervisorJob()),
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            aggregateStore = aggregateStore,
            playerLifecycleManager = playerLifecycleManager,
            pipController = pipController,
            adaptiveBitrateManager = adaptiveBitrateManager,
            // EXTERNAL short-circuits before any engine is built, so the
            // factory is never invoked on these paths.
            playerEngineFactory = mockk(relaxed = true),
            playbackSourceResolver = playbackSourceResolver,
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            offlineMediaProbe = mockk(relaxed = true),
            offlineModeManager = offlineModeManager,
            userMessageBus = messageBus,
        )
    }

    // ── Offline gate (#146) ───────────────────────────────────────────

    @Test
    fun loadMedia_offlineMode_blocksOnlineResolutionInsteadOfDeadAir() = runTest(testDispatcher) {
        val itemId = "item-movie"
        every { offlineModeManager.isOffline } returns true
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertFalse(state.isReady)
        // The whole point: no network stage runs while offline.
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
        // failLoad contract: the state write and the feedback emission carry
        // the SAME message — comparing them needs no resource literal here.
        assertEquals(1, messageBus.errors.size)
        assertEquals(messageBus.errors.single(), state.title)
        assertTrue(state.title.isNotBlank())
    }

    @Test
    fun loadMedia_offlineMode_stillPlaysLocalDownloads() = runTest(testDispatcher) {
        val tempFile = Files.createTempFile("test-video", ".mp4").toFile()
        tempFile.deleteOnExit()
        val itemId = "item-movie"
        every { offlineModeManager.isOffline } returns true
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns
            downloadItem(itemId, tempFile.absolutePath)
        coEvery { offlineRepository.getOfflineItem(itemId) } returns offlineMediaItem(itemId)

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertEquals("Offline", state.playMethodString)
        assertTrue(state.isReady)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun loadOnline_detailFetchFailure_surfacesFeedbackAndIsNotReady() = runTest(testDispatcher) {
        val itemId = "item-movie"
        coEvery { playbackSourceResolver.resolveUsableDownload(itemId) } returns null
        coEvery { mediaRepository.getMediaDetail(itemId) } returns
            Result.failure(java.io.IOException("dead air"))

        sessionManager.loadMedia(PlaybackSource.Auto(itemId, null), startPositionTicks = 0L)

        val state = sessionManager.sessionState.value
        assertFalse(state.isReady)
        // The name's promise: feedback actually reached the bus, and the state
        // write and the emission carry the same message (failLoad contract).
        assertEquals(1, messageBus.errors.size)
        assertEquals(messageBus.errors.single(), state.title)
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
}
