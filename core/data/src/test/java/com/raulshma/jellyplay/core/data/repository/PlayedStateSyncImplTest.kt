package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests [PlayedStateSyncImpl] — the deep module that now owns both the
 * played/resume-state fan-out (previously in MediaRepositoryImpl) and the
 * latest-wins reconciliation (previously in PlaybackSyncWorker).
 *
 * The flip protocol and the reconcile merge used to be tested through their
 * two separate hosts; they are now tested through one interface here.
 */
class PlayedStateSyncImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val offlineRepository: OfflineRepository = mockk(relaxed = true)
    private val playbackOutboxRepository: PlaybackOutboxRepository = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk()
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val downloadsStore: DownloadsStore = mockk()
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)

    private lateinit var sync: PlayedStateSyncImpl

    @Before
    fun setup() {
        // dagger.Lazy is trivially faked — PlayedStateSyncImpl only calls get().
        val lazyMedia: Lazy<MediaRepository> = mockk()
        every { lazyMedia.get() } returns mediaRepository
        val lazyStore: Lazy<DownloadsStore> = mockk()
        every { lazyStore.get() } returns downloadsStore
        val lazyDownloads: Lazy<DownloadRepository> = mockk()
        every { lazyDownloads.get() } returns downloadRepository
        // Auto-delete-after-watch defaults OFF, so existing flip tests stay
        // unaffected: getDownloadByMediaItemId is never reached.
        every { downloadsStore.downloads } returns kotlinx.coroutines.flow.MutableStateFlow(DownloadsSlice())
        sync = PlayedStateSyncImpl(
            apiClient,
            offlineRepository,
            playbackOutboxRepository,
            offlineModeManager,
            lazyMedia,
            lazyStore,
            lazyDownloads,
        )
    }

    // ── flip: played-state fan-out ─────────────────────────────────────

    @Test
    fun `flip offline applies locally and enqueues, returns success`() = runTest {
        every { offlineModeManager.isOffline } returns true
        coEvery { apiClient.markPlayed("item-1") } returns Result.failure(RuntimeException())

        val result = sync.flip("item-1", played = true)

        assertEquals(Result.success(Unit), result)
        coVerify(exactly = 1) { offlineRepository.applyPlayedState("item-1", isPlayed = true) }
        coVerify(exactly = 1) { playbackOutboxRepository.enqueuePlayedState("item-1", isPlayed = true) }
        // Offline path must NOT hit the API.
        coVerify(exactly = 0) { apiClient.markPlayed(any()) }
    }

    @Test
    fun `flip online success mirrors offline store, does not enqueue`() = runTest {
        every { offlineModeManager.isOffline } returns false
        coEvery { apiClient.markUnplayed("item-1") } returns Result.success(Unit)

        val result = sync.flip("item-1", played = false)

        assertEquals(Result.success(Unit), result)
        coVerify(exactly = 1) { offlineRepository.applyPlayedState("item-1", isPlayed = false) }
        coVerify(exactly = 0) { playbackOutboxRepository.enqueuePlayedState(any(), any()) }
    }

    @Test
    fun `flip online failure applies locally and enqueues, swallows error`() = runTest {
        every { offlineModeManager.isOffline } returns false
        coEvery { apiClient.markPlayed("item-1") } returns Result.failure(RuntimeException("5xx"))

        val result = sync.flip("item-1", played = true)

        // User intent preserved — reports success so optimistic UI keeps the flip.
        assertEquals(Result.success(Unit), result)
        coVerify(exactly = 1) { offlineRepository.applyPlayedState("item-1", isPlayed = true) }
        coVerify(exactly = 1) { playbackOutboxRepository.enqueuePlayedState("item-1", isPlayed = true) }
    }

    // ── reconcile: latest-wins merge ───────────────────────────────────

    @Test
    fun `reconcile returns null when no offline row exists`() = runTest {
        coEvery { offlineRepository.getOfflineItem("item-1") } returns null

        val result = sync.reconcileOfflineRow("item-1")

        assertEquals(null, result)
        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any()) }
    }

    @Test
    fun `reconcile resets local to played when server reports played`() = runTest {
        coEvery { offlineRepository.getOfflineItem("item-1") } returns offlineItem(isPlayed = false)
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            mediaDetail(mediaItem(isPlayed = true))
        )

        val result = sync.reconcileOfflineRow("item-1")

        assertEquals(PlayedStateSync.ComputeResult.PLAYED, result)
        coVerify(exactly = 1) {
            offlineRepository.updatePlaybackProgress(
                itemId = "item-1",
                positionTicks = 0L,
                percentage = 100.0,
                isPlayed = true,
            )
        }
    }

    @Test
    fun `reconcile mirrors unplayed when local played but server unplayed`() = runTest {
        coEvery { offlineRepository.getOfflineItem("item-1") } returns offlineItem(isPlayed = true)
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            mediaDetail(mediaItem(isPlayed = false))
        )

        val result = sync.reconcileOfflineRow("item-1")

        assertEquals(PlayedStateSync.ComputeResult.UNPLAYED, result)
        coVerify(exactly = 1) { offlineRepository.applyPlayedState("item-1", isPlayed = false) }
    }

    @Test
    fun `reconcile updates position when server is newer`() = runTest {
        // Server lastPlayedDate is recent past (newer than the 2020 offline row,
        // but not future-dated — the impl guards against future server clocks).
        val recentIso = java.time.OffsetDateTime.now().minusMinutes(5).toString()
        coEvery { offlineRepository.getOfflineItem("item-1") } returns offlineItem(
            isPlayed = false,
            lastPlayedDate = "2020-01-01T00:00:00Z",
            runTimeTicks = 600_000_000L,
        )
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            mediaDetail(
                mediaItem(
                    isPlayed = false,
                    lastPlayedDate = recentIso,
                    positionTicks = 300_000_000L,
                    runTimeTicks = 600_000_000L,
                )
            )
        )

        val result = sync.reconcileOfflineRow("item-1")

        assertEquals(PlayedStateSync.ComputeResult.POSITION_UPDATED, result)
        coVerify(exactly = 1) {
            offlineRepository.updatePlaybackProgress(
                itemId = "item-1",
                positionTicks = 300_000_000L,
                percentage = 50.0,
                isPlayed = false,
            )
        }
    }

    @Test
    fun `reconcile noop when server has no lastPlayedDate`() = runTest {
        coEvery { offlineRepository.getOfflineItem("item-1") } returns offlineItem(isPlayed = false)
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            mediaDetail(mediaItem(isPlayed = false, lastPlayedDate = null))
        )

        val result = sync.reconcileOfflineRow("item-1")

        assertEquals(PlayedStateSync.ComputeResult.NOOP, result)
    }

    @Test
    fun `reconcile noop guards against future-dated server clock`() = runTest {
        val futureIso = java.time.OffsetDateTime.now().plusMinutes(5).toString()
        coEvery { offlineRepository.getOfflineItem("item-1") } returns offlineItem(isPlayed = false)
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            mediaDetail(mediaItem(isPlayed = false, lastPlayedDate = futureIso))
        )

        val result = sync.reconcileOfflineRow("item-1")

        assertEquals(PlayedStateSync.ComputeResult.NOOP, result)
        coVerify(exactly = 0) { offlineRepository.updatePlaybackProgress(any(), any(), any(), any()) }
    }

    // ── computePlayedPercentage: consolidated helper ───────────────────

    @Test
    fun `percentage is 100 when played`() {
        assertEquals(
            100.0,
            PlayedStateSync.computePlayedPercentage(positionTicks = 10L, runTimeTicks = 100L, isPlayed = true),
            0.0,
        )
    }

    @Test
    fun `percentage guards divide by zero`() {
        assertEquals(0.0, PlayedStateSync.computePlayedPercentage(1L, 0L, isPlayed = false), 0.0)
        assertEquals(0.0, PlayedStateSync.computePlayedPercentage(0L, 100L, isPlayed = false), 0.0)
        assertEquals(0.0, PlayedStateSync.computePlayedPercentage(null, 100L, isPlayed = false), 0.0)
        assertEquals(0.0, PlayedStateSync.computePlayedPercentage(50L, null, isPlayed = false), 0.0)
    }

    @Test
    fun `percentage computes ratio and clamps to 100`() {
        assertEquals(
            50.0,
            PlayedStateSync.computePlayedPercentage(300_000_000L, 600_000_000L, isPlayed = false),
            0.001,
        )
        assertEquals(
            100.0,
            PlayedStateSync.computePlayedPercentage(1_000_000_000L, 600_000_000L, isPlayed = false),
            0.001,
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun offlineItem(
        isPlayed: Boolean,
        lastPlayedDate: String? = null,
        runTimeTicks: Long? = null,
    ) = OfflineMediaItem(
        id = "item-1",
        name = "Test",
        mediaType = MediaType.MOVIE,
        isPlayed = isPlayed,
        lastPlayedDate = lastPlayedDate,
        runTimeTicks = runTimeTicks,
    )

    private fun mediaItem(
        isPlayed: Boolean,
        lastPlayedDate: String? = null,
        positionTicks: Long? = null,
        runTimeTicks: Long? = null,
    ) = MediaItem(
        id = "item-1",
        name = "Test",
        mediaType = MediaType.MOVIE,
        isPlayed = isPlayed,
        lastPlayedDate = lastPlayedDate,
        playbackPositionTicks = positionTicks,
        runTimeTicks = runTimeTicks,
    )

    private fun mediaDetail(item: MediaItem) = MediaDetail(item = item)
}
