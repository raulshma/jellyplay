package com.raulshma.jellyplay.core.model.seerr

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins the Seerr status decision table ([effectiveMediaStatus], the
 * availability predicates, and the [seerrRequestButtonState] action-button
 * precedence) — the rules the requests list/sheet and the details action
 * buttons previously re-derived inline (verbatim, twice).
 */
class SeerrStatusDecisionsTest {

    // ── effectiveMediaStatus (the is4k pick) ───────────────────────────

    @Test
    fun `effectiveMediaStatus reads regular status for non-4k request`() {
        val request = SeerrRequestItem(
            is4k = false,
            media = SeerrRequestMedia(status = SeerrMediaStatus.AVAILABLE, status4k = SeerrMediaStatus.PENDING),
        )

        assertEquals(SeerrMediaStatus.AVAILABLE, request.effectiveMediaStatus())
    }

    @Test
    fun `effectiveMediaStatus reads status4k for 4k request`() {
        val request = SeerrRequestItem(
            is4k = true,
            media = SeerrRequestMedia(status = SeerrMediaStatus.AVAILABLE, status4k = SeerrMediaStatus.PROCESSING),
        )

        assertEquals(SeerrMediaStatus.PROCESSING, request.effectiveMediaStatus())
    }

    @Test
    fun `effectiveMediaStatus maps unmapped values to UNKNOWN`() {
        // fromValue folds unknown ints to UNKNOWN — the 4k column is often 0
        // on non-4k servers, so this is a live edge, not a theoretical one.
        val request = SeerrRequestItem(
            is4k = true,
            // The wire's 0 (never-requested 4K column) folds to UNKNOWN at the seam.
            media = SeerrRequestMedia(status = SeerrMediaStatus.AVAILABLE, status4k = SeerrMediaStatus.UNKNOWN),
        )

        assertEquals(SeerrMediaStatus.UNKNOWN, request.effectiveMediaStatus())
    }

    // ── availability predicates ────────────────────────────────────────

    @Test
    fun `isAvailable is true only for AVAILABLE and PARTIALLY_AVAILABLE`() {
        // Partial counts as present: the details "open in library" button
        // keys off this for partially-grabbed series too.
        assertTrue(SeerrMediaStatus.AVAILABLE.isAvailable)
        assertTrue(SeerrMediaStatus.PARTIALLY_AVAILABLE.isAvailable)
        assertFalse(SeerrMediaStatus.UNKNOWN.isAvailable)
        assertFalse(SeerrMediaStatus.PENDING.isAvailable)
        assertFalse(SeerrMediaStatus.PROCESSING.isAvailable)
        assertFalse(SeerrMediaStatus.DELETED.isAvailable)
    }

    @Test
    fun `isPending is true only for PENDING`() {
        SeerrMediaStatus.entries.forEach { status ->
            assertEquals(status == SeerrMediaStatus.PENDING, status.isPending)
        }
    }

    @Test
    fun `isProcessing is true only for PROCESSING`() {
        SeerrMediaStatus.entries.forEach { status ->
            assertEquals(status == SeerrMediaStatus.PROCESSING, status.isProcessing)
        }
    }

    @Test
    fun `predicates partition the enum disjointly for the action button`() {
        // The details screen renders exactly one of available/requested/
        // requestable — pin that the three predicates the button folds on
        // stay pairwise disjoint (an UNKNOWN status is none of them, hence
        // the "requestable" else-branch).
        SeerrMediaStatus.entries.forEach { status ->
            val flagged =
                (if (status.isAvailable) 1 else 0) + (if (status.isPending) 1 else 0) + (if (status.isProcessing) 1 else 0)
            assertTrue(flagged <= 1, "$status matched more than one action-button predicate")
        }
    }

    // ── seerrRequestButtonState (the action-button precedence) ─────────

    /** A [SeerrMediaInfo] at [status] with [requestCount] request entries. */
    private fun buttonMedia(status: SeerrMediaStatus, requestCount: Int = 0) =
        SeerrMediaInfo(
            status = status,
            requests = List(requestCount) { SeerrMediaRequest() },
        )

    @Test
    fun `button state pins the full status-by-request decision table`() {
        // Both axes of the fold: the gating fold (when a request entry alone
        // claims the button) and the precedence (available > processing >
        // pending > existing request > requestable).
        val table = listOf(
            // (status, request entries) -> expected button
            Triple(SeerrMediaStatus.UNKNOWN, 0, SeerrRequestButtonState.NotRequested),
            Triple(SeerrMediaStatus.UNKNOWN, 1, SeerrRequestButtonState.Requested.ExistingRequest),
            Triple(SeerrMediaStatus.DELETED, 0, SeerrRequestButtonState.NotRequested),
            Triple(SeerrMediaStatus.DELETED, 1, SeerrRequestButtonState.Requested.ExistingRequest),
            Triple(SeerrMediaStatus.PENDING, 0, SeerrRequestButtonState.Requested.Pending),
            Triple(SeerrMediaStatus.PENDING, 1, SeerrRequestButtonState.Requested.Pending),
            Triple(SeerrMediaStatus.PROCESSING, 0, SeerrRequestButtonState.Requested.Processing),
            Triple(SeerrMediaStatus.PROCESSING, 1, SeerrRequestButtonState.Requested.Processing),
            Triple(SeerrMediaStatus.PARTIALLY_AVAILABLE, 0, SeerrRequestButtonState.Available),
            Triple(SeerrMediaStatus.PARTIALLY_AVAILABLE, 1, SeerrRequestButtonState.Available),
            Triple(SeerrMediaStatus.AVAILABLE, 0, SeerrRequestButtonState.Available),
            Triple(SeerrMediaStatus.AVAILABLE, 1, SeerrRequestButtonState.Available),
        )
        table.forEach { (status, requests, expected) ->
            assertEquals(expected, seerrRequestButtonState(buttonMedia(status, requests)), "at $status with $requests request(s)")
        }
    }

    @Test
    fun `absent mediaInfo folds to not requested`() {
        // Overseerr omits mediaInfo entirely for never-requested media; the
        // status then reads as UNKNOWN and no request entry exists.
        assertEquals(SeerrRequestButtonState.NotRequested, seerrRequestButtonState(null))
    }

    @Test
    fun `availability outranks an in-flight request`() {
        // A partially-grabbed series with an open request still shows the
        // "open in library" affordance — the availability arm wins.
        assertEquals(
            SeerrRequestButtonState.Available,
            seerrRequestButtonState(buttonMedia(SeerrMediaStatus.PARTIALLY_AVAILABLE, requestCount = 2)),
        )
    }

    @Test
    fun `processing outranks a pending-shaped request entry`() {
        // The inner sub-shape order: a PROCESSING status renders processing
        // even when request entries also exist (the outer gating OR and the
        // inner when disagreed in shape but never in outcome — pinned here).
        assertEquals(
            SeerrRequestButtonState.Requested.Processing,
            seerrRequestButtonState(buttonMedia(SeerrMediaStatus.PROCESSING, requestCount = 1)),
        )
    }
}
