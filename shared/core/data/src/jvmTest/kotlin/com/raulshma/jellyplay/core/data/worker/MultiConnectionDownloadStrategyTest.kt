package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * First direct coverage of [MultiConnectionDownloadStrategy] — the N-range
 * concurrent transfer path, which spent its whole life welded to a concrete
 * `OkHttpClient` and therefore untestable. Since the strategy's migration onto
 * the [DownloadTransferClient] seam its chunk requests are scripted against
 * [FakeDownloadTransferClient] (request-keyed mode — the N chunks arrive in
 * nondeterministic order, unlike the runner's sequential requests), and the
 * split arithmetic, per-chunk status policy, cancel semantics, and progress
 * aggregation become fast pure-JVM tests.
 *
 * Parity notes pinned here:
 *  - a chunk accepts **206 or 200 only** — any other code (including 416, which
 *    the single-connection runner recovers from) fails that chunk;
 *  - every captured chunk request carries the `bytes=start-end` split, with
 *    the last chunk absorbing the division remainder;
 *  - cancel/pause mid-chunk deletes the partial and resets bytes to 0 (the
 *    scattered `RandomAccessFile` offsets are never a resumable prefix);
 *  - a short transfer (bytes < totalSize) deletes the partial and retries.
 *
 * The cancel/pause and ticker tests rely on the strategy's real 2 s
 * `PROGRESS_UPDATE_INTERVAL_MS` cadence over real dispatchers
 * (`Dispatchers.IO`/`Default` are hardcoded in the strategy), so they wait
 * ~2 s of wall time; `runTest`'s timeout is raised accordingly.
 */
class MultiConnectionDownloadStrategyTest {

    private val dao: DownloadDao = mockk(relaxed = true)
    private val client = FakeDownloadTransferClient()

    // A real temp file the chunks scatter-write into (RandomAccessFile at
    // entity.downloadPath). Fresh instance per test under the JVM tmp dir.
    private val tempFile = Files.createTempFile("multi-conn-strategy-test", ".bin").toFile()

    private fun entity(
        status: String = DownloadStatus.DOWNLOADING.name,
        downloadedBytes: Long = 0L,
        totalSizeBytes: Long = 0L,
    ) = DownloadEntity(
        id = "dl-1",
        mediaItemId = "media-1",
        name = "Test Movie",
        mediaType = "Movie",
        downloadPath = tempFile.absolutePath,
        downloadUrl = "https://example/item/stream",
        totalSizeBytes = totalSizeBytes,
        downloadedBytes = downloadedBytes,
        status = status,
    )

    private suspend fun execute(
        totalSize: Long,
        numConnections: Int,
        accessToken: String? = null,
        notifications: DownloadTransferNotifications = RecordingNotifications(),
    ): TransferOutcome = MultiConnectionDownloadStrategy.execute(
        downloadClient = client,
        dao = dao,
        downloadId = "dl-1",
        entity = entity(),
        totalSize = totalSize,
        numConnections = numConnections,
        notificationId = 1,
        accessToken = accessToken,
        notifications = notifications,
    )

    @BeforeTest
    fun setUp() {
        // The 2 s ticker and the failure routers read the row status; default
        // to DOWNLOADING so nothing thinks a pause/cancel happened unless a
        // test overrides it.
        coEvery { dao.getStatus(any()) } returns DownloadStatus.DOWNLOADING.name
        coEvery { dao.getDownloadById(any()) } returns entity()
    }

    // ---- chunk split arithmetic + Range-header capture ----------------------

    @Test
    fun `splits content into N ranges with the last chunk absorbing the remainder`() = runTest {
        val total = 1003L // not divisible by 4 → chunk 3 absorbs the 3 extra bytes
        client.replyByRequest { request ->
            val (start, end) = rangeBounds(request.range!!)
            FakeDownloadTransferClient.Reply.Status(
                code = 206,
                body = ByteArray((end - start + 1).toInt()),
                totalSize = total,
            )
        }

        val result = execute(totalSize = total, numConnections = 4)

        assertEquals(TransferOutcome.Success, result)
        // The captured Range headers ARE the split — chunkSize = 250, last
        // chunk's end is totalSize-1 (1002), not start+249 (999).
        assertEquals(
            listOf("bytes=0-249", "bytes=250-499", "bytes=500-749", "bytes=750-1002"),
            client.requests.map { requireNotNull(it.range) }.sorted(),
        )
        assertEquals(4, client.requests.size)
        // Scatter-written offsets cover the whole file.
        assertEquals(total, tempFile.length())
        coVerify { dao.updateProgressWithSpeed("dl-1", total, DownloadStatus.COMPLETED.name, 0L) }
    }

    @Test
    fun `all-206 chunks complete the download and reset the retry budget`() = runTest {
        client.replyByRequest { request ->
            val (start, end) = rangeBounds(request.range!!)
            FakeDownloadTransferClient.Reply.Status(
                code = 206,
                body = ByteArray((end - start + 1).toInt()),
                totalSize = 1000L,
            )
        }

        val result = execute(totalSize = 1000, numConnections = 4, accessToken = "tok-123")

        assertEquals(TransferOutcome.Success, result)
        // Every chunk request rode the same URL and carried the token (the
        // header-building the strategy used to hand-roll, now the adapter's).
        assertTrue(client.requests.all { it.url == "https://example/item/stream" })
        assertTrue(client.requests.all { it.accessToken == "tok-123" })
        assertFalse(client.requests.any { it.head })
        coVerify { dao.updateErrorMessage("dl-1", null) }
        coVerify { dao.updateProgressWithSpeed("dl-1", 1000L, DownloadStatus.COMPLETED.name, 0L) }
        coVerify { dao.resetRetryCount("dl-1") }
    }

    // ---- per-chunk status policy --------------------------------------------

    @Test
    fun `a non-2xx chunk fails the download and deletes the partial`() = runTest {
        // One chunk gets a 500. The sibling delivers an EMPTY 206 body so
        // totalDownloaded stays 0 — pinning the deterministic madeProgress=false
        // branch of the failure policy (FAILED + wipe + retry budget), not the
        // network-pause branch a concurrent byte-write would take.
        client.replyByRequest { request ->
            if (request.range == "bytes=0-499") {
                FakeDownloadTransferClient.Reply.Status(code = 500)
            } else {
                FakeDownloadTransferClient.Reply.Status(code = 206, body = ByteArray(0), totalSize = 1000L)
            }
        }

        val result = execute(totalSize = 1000, numConnections = 2)

        assertEquals(TransferOutcome.Retry, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 0L, DownloadStatus.FAILED.name, 0L) }
        coVerify { dao.updateErrorMessage("dl-1", null) }
        coVerify { dao.incrementRetryCount("dl-1") }
        // Multi-connection partials are never resumable — always wiped.
        assertFalse(tempFile.exists())
    }

    @Test
    fun `a non-IO chunk failure maps through the strategy's failure message`() = runTest {
        client.replyByRequest {
            FakeDownloadTransferClient.Reply.ThrowAny(IllegalStateException("boom"))
        }

        val result = execute(totalSize = 1000, numConnections = 2)

        // Generic Throwable → MarkFailed without retry; the strategy overrides
        // the policy's raw "boom" with its own per-exception mapping.
        assertEquals(TransferOutcome.Fail, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 0L, DownloadStatus.FAILED.name, 0L) }
        coVerify { dao.updateErrorMessage("dl-1", "Download failed") }
        coVerify(exactly = 0) { dao.incrementRetryCount("dl-1") }
        assertFalse(tempFile.exists())
    }

    // ---- incomplete transfer → delete + retry -------------------------------

    @Test
    fun `an incomplete transfer deletes the partial and requests a retry`() = runTest {
        // Every chunk delivers only half its range: bytes land, the total
        // doesn't. The integrity check must not ship a short file as COMPLETED.
        client.replyByRequest { request ->
            val (start, end) = rangeBounds(request.range!!)
            val delivered = ((end - start + 1) / 2).toInt()
            FakeDownloadTransferClient.Reply.Status(code = 206, body = ByteArray(delivered), totalSize = 1000L)
        }

        val result = execute(totalSize = 1000, numConnections = 4)

        assertEquals(TransferOutcome.Retry, result)
        coVerify { dao.updateErrorMessage("dl-1", "Download incomplete") }
        coVerify { dao.updateProgressWithSpeed("dl-1", 0L, DownloadStatus.FAILED.name, 0L) }
        assertFalse(tempFile.exists())
    }

    // ---- cancel/pause mid-chunk ----------------------------------------------

    @Test
    fun `mid-chunk cancel marks the row CANCELLED and wipes the partial`() = runTest(timeout = 60.seconds) {
        // Row cancelled while chunks are in flight: the 2 s ticker observes the
        // inactive status, flips the shared stop flag, and the endless chunks
        // exit at their next per-buffer check.
        coEvery { dao.getStatus(any()) } returns DownloadStatus.CANCELLED.name
        coEvery { dao.getDownloadById(any()) } returns entity(status = DownloadStatus.CANCELLED.name)
        client.replyByRequest {
            FakeDownloadTransferClient.Reply.Stream(code = 206, stream = EndlessBody(), totalSize = 8L * 1024 * 1024)
        }

        val result = execute(totalSize = 8L * 1024 * 1024, numConnections = 2)

        assertEquals(TransferOutcome.Success, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 0L, DownloadStatus.CANCELLED.name, 0L) }
        assertFalse(tempFile.exists())
    }

    @Test
    fun `mid-chunk cancel resolves to PAUSED when the row was paused, not cancelled`() = runTest(timeout = 60.seconds) {
        coEvery { dao.getStatus(any()) } returns DownloadStatus.PAUSED.name
        coEvery { dao.getDownloadById(any()) } returns entity(status = DownloadStatus.PAUSED.name)
        client.replyByRequest {
            FakeDownloadTransferClient.Reply.Stream(code = 206, stream = EndlessBody(), totalSize = 8L * 1024 * 1024)
        }

        val result = execute(totalSize = 8L * 1024 * 1024, numConnections = 2)

        assertEquals(TransferOutcome.Success, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 0L, DownloadStatus.PAUSED.name, 0L) }
        assertFalse(tempFile.exists())
    }

    // ---- progress aggregation -------------------------------------------------

    @Test
    fun `progress ticker aggregates chunk bytes into updateProgressWithSpeed`() = runTest(timeout = 60.seconds) {
        // Chunk 0 completes; chunk 1 delivers its bytes then parks on the gate,
        // so a chunk is guaranteed in flight when the ticker's first 2 s tick
        // fires. The gate opens only after the tick's DOWNLOADING write lands.
        val gate = AtomicBoolean(false)
        val downloadWriteObserved = CountDownLatch(1)
        coEvery { dao.updateProgressWithSpeed(any(), any(), DownloadStatus.DOWNLOADING.name, any()) } answers {
            downloadWriteObserved.countDown()
        }
        client.replyByRequest { request ->
            if (request.range == "bytes=0-1023") {
                FakeDownloadTransferClient.Reply.Status(code = 206, body = ByteArray(1024), totalSize = 2048L)
            } else {
                FakeDownloadTransferClient.Reply.Stream(code = 206, stream = GatedBody(1024, gate), totalSize = 2048L)
            }
        }
        val notifications = RecordingNotifications()

        // execute() can't be awaited directly — it only returns once the gated
        // chunk is released, which must happen after the tick. Run it on a real
        // dispatcher so the test thread can block on the latch.
        val result = async(Dispatchers.IO) {
            execute(totalSize = 2048, numConnections = 2, notifications = notifications)
        }

        assertTrue(
            downloadWriteObserved.await(30, TimeUnit.SECONDS),
            "the 2 s ticker never aggregated chunk bytes into updateProgressWithSpeed",
        )
        gate.set(true)

        assertEquals(TransferOutcome.Success, result.await())
        coVerify(atLeast = 1) {
            dao.updateProgressWithSpeed("dl-1", any(), DownloadStatus.DOWNLOADING.name, any())
        }
        // The same aggregate feeds the notification surface.
        assertTrue(notifications.updateNotificationCalls.isNotEmpty())
        // And once the parked chunk is released, the transfer completes whole.
        coVerify { dao.updateProgressWithSpeed("dl-1", 2048L, DownloadStatus.COMPLETED.name, 0L) }
    }

    // ---- helpers ---------------------------------------------------------------

    /** Parses `"bytes=start-end"` (the only Range form the strategy emits). */
    private fun rangeBounds(range: String): Pair<Long, Long> {
        val spec = range.removePrefix("bytes=")
        val (start, end) = spec.split('-')
        return start.toLong() to end.toLong()
    }

    /** No-op notification surface that records the ticker's progress updates. */
    private class RecordingNotifications : DownloadTransferNotifications {
        val updateNotificationCalls = mutableListOf<Int>()

        override suspend fun showForeground(
            downloadId: String,
            notificationId: Int,
            name: String,
            progress: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
        ) = Unit

        override fun updateNotification(
            downloadId: String,
            notificationId: Int,
            name: String,
            progress: Int,
            downloadedBytes: Long,
            totalBytes: Long,
            speedBytesPerSec: Long,
        ) {
            updateNotificationCalls += progress
        }

        override fun dismissNotification(notificationId: Int) = Unit

        override fun refreshSummary(inFlightCount: Int) = Unit
    }

    /**
     * Body that keeps the chunk in flight: serves 1 KiB every 2 ms forever, so
     * the chunk only ever exits through the strategy's per-buffer-read
     * cancelled check — which is exactly what the cancel tests exercise.
     * ~0.5 MB/s keeps the temp-file churn bounded while the 2 s ticker runs.
     */
    private class EndlessBody : InputStream() {
        override fun read(): Int = 0x2A

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            Thread.sleep(2)
            return minOf(len, 1024) // b stays zero-filled — bytes don't matter
        }
    }

    /**
     * Serves [byteCount] zero bytes, then parks until [gate] flips before
     * reporting EOF — holds one chunk open across the ticker's first tick so
     * the aggregation test is deterministic (no racing a fast completion).
     * A 30 s failsafe deadline turns a broken ticker into a graceful (failing)
     * assertion instead of a hung test.
     */
    private class GatedBody(
        private val byteCount: Int,
        private val gate: AtomicBoolean,
    ) : InputStream() {
        private var served = false

        override fun read(): Int {
            if (served) {
                blockUntilGate()
                return -1
            }
            served = true
            return 0x2A
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served) {
                blockUntilGate()
                return -1
            }
            served = true
            return minOf(len, byteCount) // b stays zero-filled
        }

        private fun blockUntilGate() {
            val deadline = System.currentTimeMillis() + 30_000
            while (!gate.get() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
        }
    }
}
