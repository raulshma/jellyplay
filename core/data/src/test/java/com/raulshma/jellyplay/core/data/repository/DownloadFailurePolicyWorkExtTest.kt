package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.DownloadDao
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import androidx.work.ListenableWorker

/**
 * Pins the `Outcome` → WorkManager mapping and the `applyAndRoute` atomicity
 * invariant: the DAO write is applied **before** the WorkManager result is
 * produced (the two steps every failure site used to open-code and could
 * drift). Classification pinning: RecordPause (and MarkFailed with
 * `shouldRetry = true`) → `Result.retry()`, Suppress → `Result.success()`
 * (clean exit, no wasteful retry), terminal MarkFailed → `Result.failure()`.
 * Suppress touches no DAO columns; MarkFailed always writes the error message
 * (even null, to clear a stale prior message).
 */
class DownloadFailurePolicyWorkExtTest {

    private val dao: DownloadDao = mockk(relaxed = true)

    private fun tempPartialFile(): File =
        File.createTempFile("partial", ".bin").apply { writeBytes(ByteArray(16)) }

    // ---- toWorkResult ---------------------------------------------------------

    @Test
    fun `RecordPause with retry maps to WorkManager retry`() {
        val outcome = Outcome.RecordPause(
            reason = DownloadPauseReason.NETWORK,
            isResumablePartial = true,
            shouldRetry = true,
        )

        val result = outcome.toWorkResult()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `MarkFailed with retry also maps to WorkManager retry`() {
        // Non-2xx transient (503/429) classification: FAILED row but the worker
        // should be re-enqueued.
        val outcome = Outcome.MarkFailed(
            errorMessage = null,
            deletePartial = true,
            shouldRetry = true,
        )

        val result = outcome.toWorkResult()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `Suppress maps to success - clean exit not wasteful retry`() {
        val result = Outcome.Suppress.toWorkResult()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `terminal MarkFailed without retry maps to failure`() {
        val outcome = Outcome.MarkFailed(
            errorMessage = SESSION_EXPIRED_ERROR,
            deletePartial = false,
            shouldRetry = false,
        )

        val result = outcome.toWorkResult()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    // ---- applyAndRoute ----------------------------------------------------------

    @Test
    fun `applyAndRoute RecordPause writes pause columns before returning retry`() = runTest {
        val partial = tempPartialFile()

        val result = Outcome.RecordPause(
            reason = DownloadPauseReason.NETWORK,
            isResumablePartial = true,
            shouldRetry = true,
        ).applyAndRoute(
            dao = dao,
            downloadId = "dl-1",
            partialFile = partial,
            preservedBytes = 4096L,
        )

        // Single-connection applicator: the resumable prefix is kept, its byte
        // count written back as the resume point.
        coVerifySequence {
            dao.updateProgressWithSpeed("dl-1", 4096L, "PAUSED", 0L)
            dao.updatePausedReason("dl-1", "NETWORK")
            dao.incrementRetryCount("dl-1")
        }
        assertTrue(partial.exists())
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `applyAndRoute Suppress touches no row and returns success`() = runTest {
        val partial = tempPartialFile()

        val result = Outcome.Suppress.applyAndRoute(
            dao = dao,
            downloadId = "dl-1",
            partialFile = partial,
            preservedBytes = 4096L,
        )

        // The user's concurrent pause is the source of truth — no row writes.
        verify { dao wasNot Called }
        assertTrue(partial.exists())
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `applyAndRoute terminal MarkFailed deletes partial zeroes bytes and returns failure`() = runTest {
        val partial = tempPartialFile()
        assertTrue(partial.exists())

        val result = Outcome.MarkFailed(
            errorMessage = SESSION_EXPIRED_ERROR,
            deletePartial = true,
            shouldRetry = false,
        ).applyAndRoute(
            dao = dao,
            downloadId = "dl-2",
            partialFile = partial,
            preservedBytes = 8192L,
        )

        coVerifySequence {
            dao.updateProgressWithSpeed("dl-2", 0L, "FAILED", 0L)
            dao.updatePausedReason("dl-2", null)
            dao.updateErrorMessage("dl-2", SESSION_EXPIRED_ERROR)
        }
        assertFalse("partial must be deleted when deletePartial is set", partial.exists())
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `applyAndRoute MarkFailed with null message still clears the error column`() = runTest {
        val partial = tempPartialFile()

        val result = Outcome.MarkFailed(
            errorMessage = null,
            deletePartial = false,
            shouldRetry = true,
        ).applyAndRoute(
            dao = dao,
            downloadId = "dl-3",
            partialFile = partial,
            preservedBytes = 1024L,
        )

        coVerifySequence {
            dao.updateProgressWithSpeed("dl-3", 1024L, "FAILED", 0L)
            dao.updatePausedReason("dl-3", null)
            dao.updateErrorMessage("dl-3", null) // clears a stale prior message
            dao.incrementRetryCount("dl-3")
        }
        assertTrue(partial.exists())
        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `DAO write happens before the result - a failing write prevents any result`() = runTest {
        // Atomicity pin: the DAO write precedes the WorkManager mapping, so a
        // throwing write propagates instead of silently returning a result that
        // drifted from the row state.
        coEvery { dao.updateProgressWithSpeed(any(), any(), any(), any()) } throws IllegalStateException("db closed")

        val outcome = Outcome.RecordPause(
            reason = DownloadPauseReason.NETWORK,
            isResumablePartial = true,
            shouldRetry = true,
        )

        val error = runCatching {
            outcome.applyAndRoute(dao, "dl-4", tempPartialFile(), 512L)
        }.exceptionOrNull()

        assertTrue("DAO write must precede the result mapping", error is IllegalStateException)
    }

    @Test
    fun `applyTo keeps DAO writes identical to applyAndRoute`() = runTest {
        val partial = tempPartialFile()

        Outcome.RecordPause(
            reason = DownloadPauseReason.NETWORK,
            isResumablePartial = false, // multi-connection shape
            shouldRetry = true,
        ).applyTo(dao, "dl-5", partial)

        // The multi-connection applicator wipe behavior is pinned in the shared
        // module; here we only pin the extension reaches the same DAO writes.
        coVerifySequence {
            dao.updateProgressWithSpeed("dl-5", any(), "PAUSED", 0L)
            dao.updatePausedReason("dl-5", "NETWORK")
            dao.incrementRetryCount("dl-5")
        }
    }
}
