package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.data.repository.SESSION_EXPIRED_ERROR
import com.raulshma.jellyplay.core.data.repository.SIZE_MISMATCH_ERROR
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [DownloadTransferRunner] — the extracted single-connection transfer loop
 * — against a [FakeDownloadTransferClient]. This is the first direct coverage
 * of the download hot path (HTTP-status branches, 416 recovery, integrity
 * checks, mid-transfer pause), which was previously welded to a concrete
 * `OkHttpClient` and therefore untested.
 *
 * Each test scripts the HTTP responses the loop will see and asserts the DAO
 * writes (status, errorMessage, bytes) + [TransferOutcome] that result,
 * pinning the parity with the pre-extraction inline branches.
 *
 * Moved verbatim from the legacy :core:data shim's Robolectric-free suite
 * (same assertions); the ListenableWorker Result assertions became
 * TransferOutcome checks and JUnit4 asserts became kotlin.test at the move.
 *
 * The runner's foreground/notification calls route through a lambda that is a
 * no-op here (the loop wraps them in `runCatching`), so no Robolectric is
 * needed — this is a pure-JVM test like the DownloadFailurePolicy suite.
 */
class DownloadTransferRunnerTest {

    private val dao: DownloadDao = mockk(relaxed = true)
    private val client = FakeDownloadTransferClient()

    // A real temp file the loop can write to (the runner opens a FileOutputStream
    // at entity.downloadPath). Created per-test under the JVM tmp dir.
    private val tempFile = Files.createTempFile("download-runner-test", ".bin").toFile()

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

    private fun runner(stopped: () -> Boolean = { false }) = DownloadTransferRunner(
        dao = dao,
        client = client,
        isStopped = stopped,
        updateForeground = { _, _, _, _, _, _ -> /* no-op in tests */ },
        dismissForeground = { /* no-op in tests */ },
    )

    @BeforeTest
    fun setUp() {
        // getStatus is read by the throttle-poll and the failure routers. Default
        // to DOWNLOADING so the loop doesn't think a pause/cancel happened unless
        // a test overrides it.
        coEvery { dao.getStatus(any()) } returns DownloadStatus.DOWNLOADING.name
    }

    // ---- 200 happy path → COMPLETED ----------------------------------------

    @Test
    fun `200 with matching size completes and resets retry budget`() = runTest {
        val body = ByteArray(1024) { it.toByte() }
        client.enqueueOk(body, totalSize = 1024L, code = 200)
        // The throttle poll only fires every 2s; with a small body the loop
        // finishes in one read, so no progress poll occurs.

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 1024L)

        assertEquals(TransferOutcome.Success, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 1024L, DownloadStatus.COMPLETED.name, 0L) }
        coVerify { dao.resetRetryCount("dl-1") }
        assertTrue(tempFile.exists() && tempFile.length() == 1024L)
    }

    // ---- 401/403 → FAILED + session-expired, NO retry -----------------------

    @Test
    fun `401 marks FAILED with session-expired message and does not retry`() = runTest {
        client.enqueue(FakeDownloadTransferClient.Reply.Status(code = 401))

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 0L)

        assertEquals(TransferOutcome.Fail, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 0L, DownloadStatus.FAILED.name, 0L) }
        coVerify { dao.updateErrorMessage("dl-1", SESSION_EXPIRED_ERROR) }
        // shouldRetry = false → no retry-count increment.
        coVerify(exactly = 0) { dao.incrementRetryCount("dl-1") }
    }

    @Test
    fun `403 is treated the same as 401`() = runTest {
        client.enqueue(FakeDownloadTransferClient.Reply.Status(code = 403))

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 0L)

        assertEquals(TransferOutcome.Fail, result)
        coVerify { dao.updateErrorMessage("dl-1", SESSION_EXPIRED_ERROR) }
    }

    // ---- transient non-2xx (503) → wipe + retry -----------------------------

    @Test
    fun `503 marks FAILED without message, deletes partial, and retries`() = runTest {
        tempFile.writeBytes(ByteArray(64)) // a pre-existing partial to be wiped
        client.enqueue(FakeDownloadTransferClient.Reply.Status(code = 503))

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 0L)

        assertEquals(TransferOutcome.Retry, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 0L, DownloadStatus.FAILED.name, 0L) }
        coVerify { dao.updateErrorMessage("dl-1", null) }
        coVerify { dao.incrementRetryCount("dl-1") } // shouldRetry = true
        // The transient branch forces a partial delete even on single-connection.
        assertEquals(0L, tempFile.length())
    }

    @Test
    fun `503 on resume zeroes the row bytes and deletes the partial`() = runTest {
        // Regression: a transient 503 mid-resume must reset downloadedBytes to 0
        // AND delete the partial. If the row kept existingBytes while the file
        // was gone, the retry would send `Range: bytes=N-`, append a tail to a
        // fresh file, hit the total, and ship a truncated download as COMPLETED.
        // The fix lives in the single-connection applicator, which honours
        // MarkFailed.deletePartial (the transient branch forces it true).
        tempFile.writeBytes(ByteArray(4096)) // a 4 KiB partial already on disk
        client.enqueue(FakeDownloadTransferClient.Reply.Status(code = 503))

        val result = runner().transfer(
            entity(downloadedBytes = 4096L), existingBytes = 4096L,
            notificationId = 1, accessToken = null, probedTotalSize = 0L,
        )

        assertEquals(TransferOutcome.Retry, result)
        // The request carried a Range header (resume path).
        assertEquals("bytes=4096-", client.requests[0].range)
        // Row zeroed — NOT 4096. This is the bug: the applicator previously
        // preserved existingBytes while the runner deleted the file.
        coVerify { dao.updateProgressWithSpeed("dl-1", 0L, DownloadStatus.FAILED.name, 0L) }
        coVerify { dao.updateErrorMessage("dl-1", null) }
        coVerify { dao.incrementRetryCount("dl-1") }
        // Partial file deleted.
        assertEquals(0L, tempFile.length())
    }

    // ---- 416 stale-range recovery → 200 retry succeeds ---------------------

    @Test
    fun `416 recovers by re-issuing without Range and transfers from zero`() = runTest {
        val body = ByteArray(512) { it.toByte() }
        // First call (GET with Range) → 416; recovery re-issues without Range → 200.
        client.enqueue(
            FakeDownloadTransferClient.Reply.Status(code = 416),
            FakeDownloadTransferClient.Reply.Status(code = 200, body = body, totalSize = 512L),
        )

        val result = runner().transfer(
            entity(downloadedBytes = 100L), existingBytes = 100L,
            notificationId = 1, accessToken = null, probedTotalSize = 512L,
        )

        assertEquals(TransferOutcome.Success, result)
        // The 416 path resets the row to PENDING/0 before retrying.
        coVerify { dao.updateProgress("dl-1", 0L, DownloadStatus.PENDING.name) }
        coVerify { dao.updateProgressWithSpeed("dl-1", 512L, DownloadStatus.COMPLETED.name, 0L) }
        // Two requests issued: the first with Range, the recovery without.
        assertEquals(2, client.requests.size)
        assertEquals("bytes=100-", client.requests[0].range)
        assertEquals(null, client.requests[1].range)
    }

    @Test
    fun `416 recovery whose retry is non-2xx fails`() = runTest {
        client.enqueue(
            FakeDownloadTransferClient.Reply.Status(code = 416),
            FakeDownloadTransferClient.Reply.Status(code = 404),
        )

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 0L)

        assertEquals(TransferOutcome.Fail, result)
        coVerify { dao.updateProgress("dl-1", 0L, DownloadStatus.FAILED.name) }
    }

    // ---- integrity checks ---------------------------------------------------

    @Test
    fun `size mismatch marks FAILED with size-mismatch message and retries`() = runTest {
        // Server claims 1024 total but ships only 256 bytes.
        client.enqueueOk(ByteArray(256), totalSize = 1024L, code = 200)

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 1024L)

        assertEquals(TransferOutcome.Retry, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 256L, DownloadStatus.FAILED.name, 0L) }
        coVerify { dao.updateErrorMessage("dl-1", SIZE_MISMATCH_ERROR) }
        coVerify { dao.incrementRetryCount("dl-1") }
    }

    @Test
    fun `zero-byte body with unknown size is rejected as size mismatch`() = runTest {
        // No Content-Length (totalSize null) + empty body. Without this guard a
        // clean stream end would mark an empty 200 as COMPLETED.
        client.enqueue(FakeDownloadTransferClient.Reply.Status(code = 200, body = ByteArray(0), totalSize = null))

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 0L)

        assertEquals(TransferOutcome.Retry, result)
        coVerify { dao.updateErrorMessage("dl-1", SIZE_MISMATCH_ERROR) }
    }

    @Test
    fun `chunked stream with no content-length falls back to HEAD probe for integrity`() = runTest {
        // Body carries no Content-Length (totalSize null); the HEAD probe knew 128.
        val body = ByteArray(128) { it.toByte() }
        client.enqueue(FakeDownloadTransferClient.Reply.Status(code = 200, body = body, totalSize = null))

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 128L)

        assertEquals(TransferOutcome.Success, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 128L, DownloadStatus.COMPLETED.name, 0L) }
    }

    // ---- mid-transfer IOException → DownloadFailurePolicy -------------------

    @Test
    fun `pre-body IOException marks FAILED with retry`() = runTest {
        client.enqueue(FakeDownloadTransferClient.Reply.Throw(SocketTimeoutException("connect timed out")))

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 0L)

        // Pre-body: madeProgress = false → MarkFailed + retry (stuck-row fix).
        assertEquals(TransferOutcome.Retry, result)
        coVerify { dao.updateProgressWithSpeed("dl-1", 0L, DownloadStatus.FAILED.name, 0L) }
        coVerify { dao.incrementRetryCount("dl-1") }
    }

    // ---- mid-transfer pause via DB-status flip ------------------------------

    @Test
    fun `isStopped pauses the transfer cleanly and returns success`() = runTest {
        client.enqueueOk(ByteArray(1024), totalSize = 1024L, code = 200)

        val result = runner(stopped = { true }).transfer(
            entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 1024L,
        )

        // Cooperative cancel mid-loop → PAUSED + clean success exit.
        assertEquals(TransferOutcome.Success, result)
        coVerify { dao.updateProgress("dl-1", 0L, DownloadStatus.PAUSED.name) }
    }

    @Test
    fun `status flipped to CANCELLED by another process stops the transfer`() = runTest {
        client.enqueueOk(ByteArray(1024), totalSize = 1024L, code = 200)
        // The 2s throttle poll sees an inactive status and bails cleanly.
        coEvery { dao.getStatus("dl-1") } returns DownloadStatus.CANCELLED.name

        val result = runner().transfer(entity(), existingBytes = 0L, notificationId = 1, accessToken = null, probedTotalSize = 1024L)

        assertEquals(TransferOutcome.Success, result)
    }

    // ---- resume happy path --------------------------------------------------

    @Test
    fun `206 resume appends to existing partial and completes`() = runTest {
        // Simulate a 4 KiB file already 1 KiB on disk; server honours the Range.
        tempFile.writeBytes(ByteArray(1024)) // existing partial
        val remaining = ByteArray(3072) { it.toByte() }
        client.enqueue(
            FakeDownloadTransferClient.Reply.Status(code = 206, body = remaining, totalSize = 4096L),
        )

        val result = runner().transfer(
            entity(downloadedBytes = 1024L), existingBytes = 1024L,
            notificationId = 1, accessToken = null, probedTotalSize = 4096L,
        )

        assertEquals(TransferOutcome.Success, result)
        // 206 resume → request carried a Range header.
        assertEquals("bytes=1024-", client.requests[0].range)
        // Appended: 1024 existing + 3072 streamed = 4096.
        coVerify { dao.updateProgressWithSpeed("dl-1", 4096L, DownloadStatus.COMPLETED.name, 0L) }
    }
}
