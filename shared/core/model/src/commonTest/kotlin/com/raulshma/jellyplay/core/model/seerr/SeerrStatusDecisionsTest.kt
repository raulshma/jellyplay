package com.raulshma.jellyplay.core.model.seerr

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins the Seerr status decision table ([effectiveMediaStatus] and the
 * availability predicates) — the rules the requests list/sheet and the
 * details action buttons previously re-derived inline (verbatim, twice).
 */
class SeerrStatusDecisionsTest {

    // ── effectiveMediaStatus (the is4k pick) ───────────────────────────

    @Test
    fun `effectiveMediaStatus reads regular status for non-4k request`() {
        val request = SeerrRequestItem(
            is4k = false,
            media = SeerrRequestMedia(status = SeerrMediaStatus.AVAILABLE.value, status4k = SeerrMediaStatus.PENDING.value),
        )

        assertEquals(SeerrMediaStatus.AVAILABLE, request.effectiveMediaStatus())
    }

    @Test
    fun `effectiveMediaStatus reads status4k for 4k request`() {
        val request = SeerrRequestItem(
            is4k = true,
            media = SeerrRequestMedia(status = SeerrMediaStatus.AVAILABLE.value, status4k = SeerrMediaStatus.PROCESSING.value),
        )

        assertEquals(SeerrMediaStatus.PROCESSING, request.effectiveMediaStatus())
    }

    @Test
    fun `effectiveMediaStatus maps unmapped values to UNKNOWN`() {
        // fromValue folds unknown ints to UNKNOWN — the 4k column is often 0
        // on non-4k servers, so this is a live edge, not a theoretical one.
        val request = SeerrRequestItem(
            is4k = true,
            media = SeerrRequestMedia(status = SeerrMediaStatus.AVAILABLE.value, status4k = 0),
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
}
