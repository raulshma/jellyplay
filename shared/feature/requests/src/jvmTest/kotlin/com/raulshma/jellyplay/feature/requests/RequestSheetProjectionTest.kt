package com.raulshma.jellyplay.feature.requests

import com.raulshma.jellyplay.core.model.seerr.SeerrRequestItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestMedia
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the detail-sheet slot's projection ([projectSheetRequest]) and the
 * orphaned-slot rule ([isSheetSlotOrphaned]) — the pure halves of the sheet
 * machine (RequestsScreen parks the tap-time ID and re-derives the item per
 * recomposition): the LIST's current object wins while the request is still
 * listed, an id whose request has left the list closes the sheet, null is
 * closed. The former parked OBJECT snapshot rendered the tap-time copy
 * forever, offering actions on a stale request; the parked-id shape's own
 * residue was the mirror bug — an id that left the list stayed parked, and a
 * later re-insert of the same id re-opened the sheet uninvited.
 */
class RequestSheetProjectionTest {

    private fun item(id: Int, status: SeerrRequestStatus = SeerrRequestStatus.PENDING) = SeerrRequestItem(
        id = id,
        type = "movie",
        status = status,
        media = SeerrRequestMedia(id = id, tmdbId = id),
    )

    @Test
    fun projects_the_lists_current_object_while_the_request_is_still_listed() {
        // The refresh-updated copy (status flipped since the tap) — the
        // sheet must render THIS object, not a tap-time snapshot.
        val live = item(5, status = SeerrRequestStatus.APPROVED)
        val requests = listOf(item(4), live, item(6))

        // Found anywhere in the page, not just the head, and the same
        // instance the list carries (no snapshot copy is minted).
        assertSame(live, projectSheetRequest(5, requests))
    }

    @Test
    fun a_parked_id_whose_request_left_the_list_closes_the_sheet() {
        val requests = listOf(item(4), item(6))

        // Documented choice: close (not a last-known snapshot) — the slot
        // keeps ONE meaning (id = "the user opened this request", null =
        // closed) and a refresh that drops the open request stops offering
        // actions on it.
        assertNull(projectSheetRequest(5, requests))
    }

    @Test
    fun null_id_is_closed_even_with_rows_listed() {
        assertNull(projectSheetRequest(null, listOf(item(4))))
    }

    @Test
    fun an_empty_list_closes_any_open_sheet() {
        assertNull(projectSheetRequest(5, emptyList()))
    }

    @Test
    fun a_parked_id_whose_request_left_the_list_is_orphaned() {
        // The R1 pin: an id that left the list must be CLEARED, not just
        // rendered closed — the dismiss/approve callbacks can't fire while
        // unrendered, so the parked id would survive until a later re-insert
        // of the same id (pagination, filter restore, poll re-add) re-opened
        // the sheet with no user action.
        assertTrue(isSheetSlotOrphaned(5, listOf(item(4), item(6))))
        assertTrue(isSheetSlotOrphaned(5, emptyList()))
    }

    @Test
    fun a_parked_id_still_listed_and_a_closed_slot_are_not_orphaned() {
        assertFalse(isSheetSlotOrphaned(4, listOf(item(4), item(6))))
        assertFalse(isSheetSlotOrphaned(null, listOf(item(4))))
        assertFalse(isSheetSlotOrphaned(null, emptyList()))
    }
}
