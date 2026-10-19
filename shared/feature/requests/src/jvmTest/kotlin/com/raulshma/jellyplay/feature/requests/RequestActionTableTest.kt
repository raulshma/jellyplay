package com.raulshma.jellyplay.feature.requests

import com.raulshma.jellyplay.core.model.seerr.SeerrRequestStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the admin action ladder ([requestActionsFor]) exhaustively — the
 * FAILED→retry+delete / PENDING→approve+decline / otherwise→delete table
 * [RequestListItem] and [RequestDetailBottomSheet] both render. A table edit
 * must update this file in lockstep (the `RequestStatusPresentationTest`
 * rule).
 */
class RequestActionTableTest {

    @Test
    fun `non-admins get no actions for any status`() {
        SeerrRequestStatus.entries.forEach { status ->
            assertTrue(requestActionsFor(status, isAdmin = false).isEmpty(), "at $status")
        }
    }

    @Test
    fun `failed offers retry and delete`() {
        assertEquals(
            listOf(RequestAction.RETRY, RequestAction.DELETE),
            requestActionsFor(SeerrRequestStatus.FAILED, isAdmin = true),
        )
    }

    @Test
    fun `pending offers approve and decline and nothing else`() {
        assertEquals(
            listOf(RequestAction.APPROVE, RequestAction.DECLINE),
            requestActionsFor(SeerrRequestStatus.PENDING, isAdmin = true),
        )
    }

    @Test
    fun `every non-pending non-failed status offers delete only`() {
        listOf(
            SeerrRequestStatus.APPROVED,
            SeerrRequestStatus.DECLINED,
            SeerrRequestStatus.COMPLETED,
        ).forEach { status ->
            assertEquals(
                listOf(RequestAction.DELETE),
                requestActionsFor(status, isAdmin = true),
                "at $status",
            )
        }
    }

    @Test
    fun `the table is exhaustive over the status enum`() {
        // Every status folds to a defined ladder (never throws / never null):
        // PENDING → moderation pair, FAILED → retry+delete, rest → delete.
        SeerrRequestStatus.entries.forEach { status ->
            val actions = requestActionsFor(status, isAdmin = true)
            assertTrue(actions.isNotEmpty(), "at $status")
            assertEquals(
                actions.distinct(),
                actions,
                "at $status — no duplicate verbs",
            )
        }
    }
}
