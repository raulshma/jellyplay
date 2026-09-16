package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.util.DownloadResult
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [TrackDownloadActions] — the hoisted track-download flip choreography
 * (formerly hand-copied in AudioPlayerViewModel.downloadCurrentTrack and
 * AlbumDetailViewModel.downloadTrack/downloadAlbum):
 *
 *  - the START half: detail fetch → intake start, in that order, with the
 *    failure envelope the hosts rely on (a failed fetch or failed start is
 *    swallowed — a track row has no error surface) EXCEPT cancellation,
 *    which rethrows ([runCatchingRethrowingCancellation] — the copied
 *    `catch (_: Exception)` bodies masked scope teardown; this is the
 *    deliberate improvement);
 *  - the BULK admission table (null / FAILED / CANCELLED start; every live
 *    or completed status skips) under the concurrency bound;
 *  - REMOVE never happens inside the module: the confirm-before-remove
 *    policy lives at the hosts' call sites.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackDownloadActionsTest {

    private val mediaRepository: MediaRepository = mockk()

    /** Recording window — a StateFlow stands in for the repository's Room flow. */
    private class RecordingStatusWindow : TrackDownloadStatusWindow {
        val rows = MutableStateFlow<List<DownloadItem>>(emptyList())
        val removedIds = mutableListOf<String>()
        var windowReads = 0
        override val isSupported: Boolean = true
        override fun downloadsFor(ids: List<String>): Flow<List<DownloadItem>> {
            windowReads += 1
            return rows
        }
        override suspend fun remove(downloadId: String) {
            removedIds += downloadId
        }
    }

    /** Recording intake with an optional per-start interference hook. */
    private class RecordingIntake : DownloadIntake {
        val startedIds = mutableListOf<String>()
        var onStart: (suspend (MediaDetail) -> Unit)? = null
        override suspend fun start(
            detail: MediaDetail,
            maxBitrate: Int?,
            selectedSubtitleIndices: Set<Int>?,
        ): DownloadResult {
            startedIds += detail.item.id
            onStart?.invoke(detail)
            return DownloadResult(downloadItem = null, error = null)
        }
        override suspend fun startSeries(
            seriesId: String,
            episodeIds: Map<String, List<String>>?,
        ): Result<List<String>> = Result.success(emptyList())
        override suspend fun startFromItem(item: MediaItem): DownloadRequestResult =
            DownloadRequestResult.Started
    }

    private val window = RecordingStatusWindow()
    private val intake = RecordingIntake()

    private fun CoroutineScope.actions() = TrackDownloadActions(
        scope = this,
        intake = intake,
        mediaRepository = mediaRepository,
        statusWindow = window,
    )

    private fun item(id: String) = MediaItem(id = id, name = "Track $id", mediaType = MediaType.AUDIO)

    private fun detail(id: String) = MediaDetail(item = item(id))

    private fun row(downloadId: String, mediaItemId: String, status: DownloadStatus) = DownloadItem(
        id = downloadId,
        mediaItemId = mediaItemId,
        name = "Track $mediaItemId",
        mediaType = MediaType.AUDIO,
        downloadPath = "/tmp/$downloadId",
        downloadUrl = "https://srv/$downloadId",
        totalSizeBytes = 10L,
        downloadedBytes = 10L,
        status = status,
    )

    // ── flip: the START half ─────────────────────────────────────────────────

    @Test
    fun `flip fetches the detail then starts the intake with it`() = runTest {
        coEvery { mediaRepository.getMediaDetail("track-1") } returns Result.success(detail("track-1"))

        actions().flip("track-1")
        advanceUntilIdle()

        assertEquals(listOf("track-1"), intake.startedIds)
        assertTrue(window.removedIds.isEmpty())
    }

    @Test
    fun `flip with an unresolvable detail starts nothing`() = runTest {
        coEvery { mediaRepository.getMediaDetail("track-1") } returns Result.failure(RuntimeException("offline"))

        actions().flip("track-1")
        advanceUntilIdle()

        assertTrue(intake.startedIds.isEmpty())
        assertTrue(window.removedIds.isEmpty())
    }

    @Test
    fun `flip swallows an intake failure instead of crashing the scope`() = runTest {
        coEvery { mediaRepository.getMediaDetail("track-1") } returns Result.success(detail("track-1"))
        intake.onStart = { throw RuntimeException("disk full") }

        // If the failure escaped, the TestScope child fails and runTest
        // reports it — the hosts' silent-flip contract.
        actions().flip("track-1")
        advanceUntilIdle()

        assertEquals(listOf("track-1"), intake.startedIds, "the start was attempted, its failure swallowed")
    }

    @Test
    fun `flip cancellation during the intake start propagates instead of being masked`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getMediaDetail("track-1") } returns Result.success(detail("track-1"))
        intake.onStart = { gate.await() }

        val job: Job = actions().flip("track-1")
        advanceUntilIdle()
        job.cancel()
        // join() returns on cancellation too — the pin is the FINAL state: a
        // swallowed CancellationException (the pre-extraction `catch (_:
        // Exception)`) would complete the job NORMALLY instead.
        job.join()

        assertTrue(job.isCancelled, "cancellation must rethrow, not report as a failed flip")
    }

    // ── bulk: the admission table + concurrency bound ────────────────────────

    @Test
    fun `bulk admits only null, failed or cancelled rows and skips the live ones`() = runTest {
        window.rows.value = listOf(
            row("d-1", "t-pending", DownloadStatus.PENDING),
            row("d-2", "t-queued", DownloadStatus.QUEUED),
            row("d-3", "t-downloading", DownloadStatus.DOWNLOADING),
            row("d-4", "t-paused", DownloadStatus.PAUSED),
            row("d-5", "t-completed", DownloadStatus.COMPLETED),
            row("d-6", "t-failed", DownloadStatus.FAILED),
            row("d-7", "t-cancelled", DownloadStatus.CANCELLED),
        )
        coEvery { mediaRepository.getMediaDetail(any()) } answers { Result.success(detail(firstArg())) }

        val items = listOf("t-pending", "t-queued", "t-downloading", "t-paused", "t-completed", "t-failed", "t-cancelled", "t-missing")
            .map(::item)
        actions().bulk(items)
        advanceUntilIdle()

        assertEquals(
            listOf("t-failed", "t-cancelled", "t-missing").sorted(),
            intake.startedIds.sorted(),
            "only no-row, FAILED and CANCELLED items start",
        )
    }

    @Test
    fun `bulk respects the concurrency bound`() = runTest {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        coEvery { mediaRepository.getMediaDetail(any()) } answers { Result.success(detail(firstArg())) }
        intake.onStart = {
            val now = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { current -> maxOf(current, now) }
            delay(1_000)
            inFlight.decrementAndGet()
        }

        actions().bulk((1..9).map { item("t$it") })
        advanceUntilIdle()

        assertEquals(9, intake.startedIds.size, "every admitted item started")
        assertEquals(3, maxInFlight.get(), "at most 3 transfers in flight (the default admission concurrency)")
    }

    @Test
    fun `bulk never removes — the remove decision stays at the call sites`() = runTest {
        window.rows.value = listOf(
            row("d-5", "t-completed", DownloadStatus.COMPLETED),
            row("d-6", "t-failed", DownloadStatus.FAILED),
        )
        coEvery { mediaRepository.getMediaDetail(any()) } answers { Result.success(detail(firstArg())) }

        actions().bulk(listOf(item("t-completed"), item("t-failed"), item("t-missing")))
        advanceUntilIdle()

        assertTrue(window.removedIds.isEmpty(), "bulk skips completed rows, it does not delete them")
    }

    @Test
    fun `bulk without items never touches the window`() = runTest {
        actions().bulk(emptyList())
        advanceUntilIdle()

        assertEquals(0, window.windowReads)
        assertTrue(intake.startedIds.isEmpty())
    }
}
