package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DownloadStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Tests [DownloadStates] — the download status predicates previously scattered
 * as repeated `status == X.name || status == Y.name` chains across the repo and
 * both workers. Each predicate now has a direct test instead of being asserted
 * only through worker/repo integration.
 */
class DownloadStatesTest {

    @Test
    fun `isActive true for in-flight states`() {
        listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PENDING).forEach {
            assertTrue(DownloadStates.isActive(it.name), it.name)
        }
    }

    @Test
    fun `isActive false for terminal or paused states`() {
        listOf(DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.COMPLETED, DownloadStatus.CANCELLED).forEach {
            assertFalse(DownloadStates.isActive(it.name), it.name)
        }
    }

    @Test
    fun `isPausedOrFailed true only for paused or failed`() {
        assertTrue(DownloadStates.isPausedOrFailed(DownloadStatus.PAUSED.name))
        assertTrue(DownloadStates.isPausedOrFailed(DownloadStatus.FAILED.name))
        assertFalse(DownloadStates.isPausedOrFailed(DownloadStatus.COMPLETED.name))
        assertFalse(DownloadStates.isPausedOrFailed(DownloadStatus.DOWNLOADING.name))
    }

    @Test
    fun `isInactive true for paused or cancelled`() {
        assertTrue(DownloadStates.isInactive(DownloadStatus.PAUSED.name))
        assertTrue(DownloadStates.isInactive(DownloadStatus.CANCELLED.name))
        assertFalse(DownloadStates.isInactive(DownloadStatus.DOWNLOADING.name))
        assertFalse(DownloadStates.isInactive(DownloadStatus.FAILED.name))
        assertFalse(DownloadStates.isInactive(null))
    }

    @Test
    fun `isUserPaused requires paused status and USER reason`() {
        assertTrue(DownloadStates.isUserPaused(DownloadStatus.PAUSED.name, DownloadPauseReason.USER.persistedValue))
        // NETWORK pause is not a user pause — auto-resume may resume it.
        assertFalse(DownloadStates.isUserPaused(DownloadStatus.PAUSED.name, DownloadPauseReason.NETWORK.persistedValue))
        // Non-paused status, even with a stale USER reason, is not user-paused.
        assertFalse(DownloadStates.isUserPaused(DownloadStatus.DOWNLOADING.name, DownloadPauseReason.USER.persistedValue))
        assertFalse(DownloadStates.isUserPaused(DownloadStatus.PAUSED.name, null))
    }

    @Test
    fun `isExhausted matches the documented retry budget`() {
        assertFalse(DownloadStates.isExhausted(0))
        assertFalse(DownloadStates.isExhausted(DOWNLOAD_MAX_AUTO_RETRY - 1))
        assertTrue(DownloadStates.isExhausted(DOWNLOAD_MAX_AUTO_RETRY))
        assertTrue(DownloadStates.isExhausted(DOWNLOAD_MAX_AUTO_RETRY + 5))
    }

    @Test
    fun `parse round-trips known statuses and rejects unknown`() {
        assertEquals(DownloadStatus.COMPLETED, DownloadStates.parse(DownloadStatus.COMPLETED.name))
        assertEquals(DownloadStatus.PAUSED, DownloadStates.parse(DownloadStatus.PAUSED.name))
        assertNull(DownloadStates.parse("BOGUS"))
    }

    @Test
    fun `keepsResumeBytes true only for PAUSED, matching the resumeByteOffset rule`() {
        assertTrue(DownloadStates.keepsResumeBytes(DownloadStatus.PAUSED.name))
        listOf(DownloadStatus.FAILED, DownloadStatus.PENDING, DownloadStatus.COMPLETED, DownloadStatus.CANCELLED)
            .forEach { assertFalse(DownloadStates.keepsResumeBytes(it.name), it.name) }
    }

    @Test
    fun `resumeByteOffset preserves bytes for PAUSED and resets everything else`() {
        // PAUSED keeps the contiguous prefix (single-connection Range resume).
        assertEquals(1_048_576L, DownloadStates.resumeByteOffset(DownloadStatus.PAUSED.name, 1_048_576L))
        // FAILED, PENDING, COMPLETED, CANCELLED all restart from 0 — the
        // partial body may be gapped (multi-connection) or deleted, so a
        // mid-file Range: would corrupt the output.
        assertEquals(0L, DownloadStates.resumeByteOffset(DownloadStatus.FAILED.name, 1_048_576L))
        assertEquals(0L, DownloadStates.resumeByteOffset(DownloadStatus.PENDING.name, 999L))
        assertEquals(0L, DownloadStates.resumeByteOffset(DownloadStatus.COMPLETED.name, 999L))
        assertEquals(0L, DownloadStates.resumeByteOffset(DownloadStatus.CANCELLED.name, 999L))
        assertEquals(0L, DownloadStates.resumeByteOffset(DownloadStatus.PAUSED.name, 0L))
    }
}
