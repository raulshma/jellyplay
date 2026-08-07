package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Tests [DownloadFailurePolicy.decide] — the pure failure-classification rule
 * extracted from four divergent catch sites in the download workers.
 *
 * Each branch of the rule (IOException × progress, generic Throwable, the
 * concurrent-pause Suppress guard, the resumable-partial fork) has a direct
 * test instead of being asserted only transitively through a CoroutineWorker
 * that needs Robolectric.
 *
 * The DAO applicator and `toWorkResult` mapping are not exercised here — they
 * are thin side-effect translations covered separately. The rule is the
 * load-bearing logic; this test is its safety net.
 */
class DownloadFailurePolicyTest {

    private val ioException: IOException = SocketTimeoutException("timed out")
    private val genericException: Throwable = IllegalStateException("boom")

    // ---- Suppress: concurrent user pause wins over everything ----------------

    @Test
    fun `concurrent user pause suppresses outcome regardless of exception`() {
        val outcome = DownloadFailurePolicy.decide(
            error = ioException,
            madeProgress = true,
            currentStatus = DownloadStatus.PAUSED.name,
            isResumablePartial = true,
        )
        assertEquals(Outcome.Suppress, outcome)
    }

    @Test
    fun `Suppress does not retry — clean exit not wasteful retry`() {
        // Pre-extraction the outer catches returned Result.retry() even when
        // the user had paused. Suppress → Result.success() is the fix.
        val outcome = DownloadFailurePolicy.decide(
            error = genericException,
            madeProgress = false,
            currentStatus = DownloadStatus.PAUSED.name,
            isResumablePartial = false,
        )
        assertFalse((outcome as Outcome.Suppress).shouldRetry)
    }

    // ---- IOException with progress → RecordPause + NETWORK ------------------

    @Test
    fun `IOException with progress records NETWORK pause`() {
        val outcome = DownloadFailurePolicy.decide(
            error = ioException,
            madeProgress = true,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        )
        assertTrue(outcome is Outcome.RecordPause)
        outcome as Outcome.RecordPause
        assertEquals(DownloadPauseReason.NETWORK, outcome.reason)
        assertTrue(outcome.shouldRetry)
    }

    @Test
    fun `RecordPause preserves resumable partial flag for applicator`() {
        // Single-connection: isResumablePartial flows through so applyTo keeps
        // the partial. Multi-connection: false so applyTo deletes it.
        val keep = DownloadFailurePolicy.decide(
            error = ioException, madeProgress = true,
            currentStatus = DownloadStatus.DOWNLOADING.name, isResumablePartial = true,
        ) as Outcome.RecordPause
        assertTrue(keep.isResumablePartial)

        val delete = DownloadFailurePolicy.decide(
            error = ioException, madeProgress = true,
            currentStatus = DownloadStatus.DOWNLOADING.name, isResumablePartial = false,
        ) as Outcome.RecordPause
        assertFalse(delete.isResumablePartial)
    }

    @Test
    fun `SocketTimeoutException is treated as IOException network class`() {
        // The outer-catch bug pre-extraction: SocketTimeout fell into a catch
        // that set no pausedReason, so reconnect auto-resume never picked it
        // up. Now it records NETWORK like any other IOException.
        val outcome = DownloadFailurePolicy.decide(
            error = SocketTimeoutException("read timeout"),
            madeProgress = true,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        ) as Outcome.RecordPause
        assertEquals(DownloadPauseReason.NETWORK, outcome.reason)
    }

    // ---- IOException without progress → MarkFailed (stuck-row fix) ----------

    @Test
    fun `IOException without progress marks failed with retry`() {
        // Pre-extraction: inner/multi catches guarded on `madeProgress` and did
        // nothing when false, leaving the row DOWNLOADING with no mutation —
        // an orphaned row. Now: FAILED + retry.
        val outcome = DownloadFailurePolicy.decide(
            error = ioException,
            madeProgress = false,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        )
        assertTrue(outcome is Outcome.MarkFailed)
        outcome as Outcome.MarkFailed
        assertNull(outcome.errorMessage) // no user-facing message for bare network IO
        assertTrue(outcome.shouldRetry)
    }

    @Test
    fun `IOException without progress on multi-connection deletes partial`() {
        val outcome = DownloadFailurePolicy.decide(
            error = ioException,
            madeProgress = false,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = false,
        ) as Outcome.MarkFailed
        assertTrue(outcome.deletePartial)
    }

    @Test
    fun `IOException without progress on single-connection keeps partial`() {
        val outcome = DownloadFailurePolicy.decide(
            error = ioException,
            madeProgress = false,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        ) as Outcome.MarkFailed
        assertFalse(outcome.deletePartial)
    }

    // ---- Generic Throwable → MarkFailed, no retry ---------------------------

    @Test
    fun `generic throwable marks failed without retry`() {
        val outcome = DownloadFailurePolicy.decide(
            error = genericException,
            madeProgress = true,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        )
        assertTrue(outcome is Outcome.MarkFailed)
        outcome as Outcome.MarkFailed
        assertFalse(outcome.shouldRetry)
    }

    @Test
    fun `generic throwable carries error message`() {
        val outcome = DownloadFailurePolicy.decide(
            error = IllegalStateException("disk full"),
            madeProgress = false,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        ) as Outcome.MarkFailed
        assertEquals("disk full", outcome.errorMessage)
    }

    @Test
    fun `generic throwable with null message falls back to class name`() {
        val error = IllegalStateException() // message = null
        val outcome = DownloadFailurePolicy.decide(
            error = error,
            madeProgress = false,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        ) as Outcome.MarkFailed
        assertEquals("IllegalStateException", outcome.errorMessage)
    }

    @Test
    fun `generic throwable on multi-connection deletes partial`() {
        val outcome = DownloadFailurePolicy.decide(
            error = genericException,
            madeProgress = true,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = false,
        ) as Outcome.MarkFailed
        assertTrue(outcome.deletePartial)
    }

    @Test
    fun `generic throwable on single-connection keeps partial`() {
        val outcome = DownloadFailurePolicy.decide(
            error = genericException,
            madeProgress = true,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        ) as Outcome.MarkFailed
        assertFalse(outcome.deletePartial)
    }

    // ---- decideForStatus: HTTP response-code classification -----------------

    @Test
    fun `401 marks FAILED with session-expired message and no retry`() {
        val outcome = DownloadFailurePolicy.decideForStatus(
            responseCode = 401,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        ) as Outcome.MarkFailed
        assertEquals(SESSION_EXPIRED_ERROR, outcome.errorMessage)
        assertFalse(outcome.shouldRetry)
    }

    @Test
    fun `403 is classified identically to 401`() {
        val outcome = DownloadFailurePolicy.decideForStatus(
            responseCode = 403,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        ) as Outcome.MarkFailed
        assertEquals(SESSION_EXPIRED_ERROR, outcome.errorMessage)
        assertFalse(outcome.shouldRetry)
    }

    @Test
    fun `transient 503 deletes partial even on single-connection and retries`() {
        // The transient branch forces deletePartial=true regardless of strategy,
        // mirroring the pre-extraction wipe-and-retry (avoids the stale-Range loop).
        val outcome = DownloadFailurePolicy.decideForStatus(
            responseCode = 503,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = true,
        ) as Outcome.MarkFailed
        assertTrue(outcome.deletePartial)
        assertTrue(outcome.shouldRetry)
        assertNull(outcome.errorMessage)
    }

    @Test
    fun `transient 429 retries like 503`() {
        val outcome = DownloadFailurePolicy.decideForStatus(
            responseCode = 429,
            currentStatus = DownloadStatus.DOWNLOADING.name,
            isResumablePartial = false,
        ) as Outcome.MarkFailed
        assertTrue(outcome.shouldRetry)
        assertTrue(outcome.deletePartial)
    }

    @Test
    fun `status classification suppresses when user paused concurrently`() {
        // Same guard as decide(Throwable): PAUSED wins so the failure handler
        // doesn't clobber the user's pause.
        val outcome = DownloadFailurePolicy.decideForStatus(
            responseCode = 401,
            currentStatus = DownloadStatus.PAUSED.name,
            isResumablePartial = true,
        )
        assertEquals(Outcome.Suppress, outcome)
    }
}
