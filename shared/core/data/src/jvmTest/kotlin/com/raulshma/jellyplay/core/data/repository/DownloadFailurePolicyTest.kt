package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.repository.DownloadFailurePolicy.decide
import com.raulshma.jellyplay.core.data.repository.DownloadFailurePolicy.decideForStatus
import com.raulshma.jellyplay.core.model.DownloadStatus
import java.net.SocketTimeoutException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the download failure-classification rule in [DownloadFailurePolicy] —
 * the pure decision core every catch site converges on (no DAO, no WorkManager):
 *
 *  - any [Throwable] while the row is concurrently PAUSED → [Outcome.Suppress]
 *    (a failure never clobbers the user's pause);
 *  - [IOException] (incl. timeouts) with progress → PAUSED + NETWORK + retry;
 *    without progress → FAILED (retryable, no user-facing message);
 *  - generic [Throwable] → FAILED with the exception message, no retry;
 *  - 401/403 → FAILED + [SESSION_EXPIRED_ERROR], no retry;
 *  - every other non-2xx (transient 5xx/429, and 416 if a stale-range recovery
 *    ever leaks into the policy) → FAILED, partial wiped, retry — the
 *    wipe-and-retry that breaks the stale-`Range` retry loop. 416 itself is
 *    normally intercepted by the transfer runner as "re-issue without Range:"
 *    and never reaches the policy; this pins the degrade if it ever does.
 */
class DownloadFailurePolicyTest {

    private val downloading = DownloadStatus.DOWNLOADING.name
    private val paused = DownloadStatus.PAUSED.name

    // ── decide(Throwable) ───────────────────────────────────────────────

    @Test
    fun `IOException with progress pauses with NETWORK reason and keeps resumable partial`() {
        val outcome = decide(
            error = SocketTimeoutException("read timed out"),
            madeProgress = true,
            currentStatus = downloading,
            isResumablePartial = true,
        )

        val pause = assertIs<Outcome.RecordPause>(outcome)
        assertEquals(DownloadPauseReason.NETWORK, pause.reason)
        assertTrue(pause.isResumablePartial)
        assertTrue(pause.shouldRetry)
    }

    @Test
    fun `IOException with progress on a non-resumable strategy still pauses but drops the partial`() {
        val outcome = decide(
            error = IOException("connection reset"),
            madeProgress = true,
            currentStatus = downloading,
            isResumablePartial = false,
        )

        val pause = assertIs<Outcome.RecordPause>(outcome)
        assertEquals(DownloadPauseReason.NETWORK, pause.reason)
        assertFalse(pause.isResumablePartial)
        assertTrue(pause.shouldRetry)
    }

    @Test
    fun `IOException without progress fails the row instead of leaving it stuck`() {
        // The historical bug: a pre-body IO failure marked nothing and the row
        // sat DOWNLOADING forever. It must land FAILED (retryable, no message).
        val outcome = decide(
            error = IOException("connect refused"),
            madeProgress = false,
            currentStatus = downloading,
            isResumablePartial = true,
        )

        val failed = assertIs<Outcome.MarkFailed>(outcome)
        assertNull(failed.errorMessage)
        assertFalse(failed.deletePartial) // resumable strategy keeps the (empty) partial
        assertTrue(failed.shouldRetry)
    }

    @Test
    fun `IOException without progress on multi-connection deletes the partial`() {
        val outcome = decide(
            error = IOException("connect refused"),
            madeProgress = false,
            currentStatus = downloading,
            isResumablePartial = false,
        )

        val failed = assertIs<Outcome.MarkFailed>(outcome)
        assertTrue(failed.deletePartial)
        assertTrue(failed.shouldRetry)
    }

    @Test
    fun `generic Throwable fails the row with the exception message and no retry`() {
        val outcome = decide(
            error = IllegalStateException("decoder not initialised"),
            madeProgress = true,
            currentStatus = downloading,
            isResumablePartial = true,
        )

        val failed = assertIs<Outcome.MarkFailed>(outcome)
        assertEquals("decoder not initialised", failed.errorMessage)
        assertFalse(failed.deletePartial)
        assertFalse(failed.shouldRetry)
    }

    @Test
    fun `generic Throwable with null message falls back to the class simple name`() {
        val outcome = decide(
            error = IllegalStateException(),
            madeProgress = false,
            currentStatus = downloading,
            isResumablePartial = false,
        )

        val failed = assertIs<Outcome.MarkFailed>(outcome)
        assertEquals("IllegalStateException", failed.errorMessage)
        assertTrue(failed.deletePartial)
        assertFalse(failed.shouldRetry)
    }

    @Test
    fun `failure on a concurrently PAUSED row is suppressed - user pause wins`() {
        assertEquals(
            Outcome.Suppress,
            decide(
                error = IOException("mid-pause failure"),
                madeProgress = true,
                currentStatus = paused,
                isResumablePartial = true,
            ),
        )
        assertEquals(
            Outcome.Suppress,
            decide(
                error = IllegalStateException("mid-pause crash"),
                madeProgress = false,
                currentStatus = paused,
                isResumablePartial = false,
            ),
        )
    }

    // ── decideForStatus(Int) ────────────────────────────────────────────

    @Test
    fun `401 fails the row with the session-expired message and no retry`() {
        val outcome = decideForStatus(
            responseCode = 401,
            currentStatus = downloading,
            isResumablePartial = true,
        )

        val failed = assertIs<Outcome.MarkFailed>(outcome)
        assertEquals(SESSION_EXPIRED_ERROR, failed.errorMessage)
        assertFalse(failed.deletePartial) // single-connection partial is kept on FAILED
        assertFalse(failed.shouldRetry) // retrying burns the budget on the same 401
    }

    @Test
    fun `403 behaves like 401`() {
        val outcome = decideForStatus(403, downloading, isResumablePartial = false)

        val failed = assertIs<Outcome.MarkFailed>(outcome)
        assertEquals(SESSION_EXPIRED_ERROR, failed.errorMessage)
        assertTrue(failed.deletePartial)
        assertFalse(failed.shouldRetry)
    }

    @Test
    fun `transient 5xx wipes the partial and retries from byte zero`() {
        // deletePartial is forced true even for the resumable single-connection
        // strategy — the wipe is the fix for the stale-Range retry loop.
        val outcome = decideForStatus(503, downloading, isResumablePartial = true)

        val failed = assertIs<Outcome.MarkFailed>(outcome)
        assertNull(failed.errorMessage)
        assertTrue(failed.deletePartial)
        assertTrue(failed.shouldRetry)
    }

    @Test
    fun `transient 429 wipes the partial and retries`() {
        val outcome = decideForStatus(429, downloading, isResumablePartial = true)

        val failed = assertIs<Outcome.MarkFailed>(outcome)
        assertNull(failed.errorMessage)
        assertTrue(failed.deletePartial)
        assertTrue(failed.shouldRetry)
    }

    @Test
    fun `416 degrades to the transient wipe-and-retry if it ever reaches the policy`() {
        // 416 is a recovery action ("re-issue without Range:") owned by the
        // transfer runner; the policy has no dedicated branch for it. Pin the
        // degrade: it is treated as transient, not auth-failed.
        val outcome = decideForStatus(416, downloading, isResumablePartial = true)

        val failed = assertIs<Outcome.MarkFailed>(outcome)
        assertNull(failed.errorMessage)
        assertTrue(failed.deletePartial)
        assertTrue(failed.shouldRetry)
    }

    @Test
    fun `status failure on a concurrently PAUSED row is suppressed`() {
        assertEquals(
            Outcome.Suppress,
            decideForStatus(401, currentStatus = paused, isResumablePartial = true),
        )
        assertEquals(
            Outcome.Suppress,
            decideForStatus(503, currentStatus = paused, isResumablePartial = false),
        )
    }

    // ── Outcome invariants ──────────────────────────────────────────────

    @Test
    fun `Suppress never reports retryable`() {
        assertFalse(Outcome.Suppress.shouldRetry)
    }
}
