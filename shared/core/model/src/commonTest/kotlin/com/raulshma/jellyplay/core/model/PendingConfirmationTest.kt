package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins [PendingConfirmation]'s write algebra — the pure transforms behind
 * every confirm-dialog flow's hold / dismiss / confirm / settle setter. The
 * gate must always consult the caller-owned in-flight fact passed per call:
 * a confirm or dismiss that ignored it would double-fire the action or close
 * the dialog out from under it. [PendingConfirmation.confirm] is also pinned
 * to never clear — settle timing is the caller's [PendingConfirmation.clear]
 * write.
 */
class PendingConfirmationTest {

    @Test
    fun empty_state_is_not_pending() {
        assertFalse(PendingConfirmation<String>().isPending)
        assertNull(PendingConfirmation<String>().item)
    }

    @Test
    fun hold_sets_the_item_and_activates() {
        val next = PendingConfirmation<String>().hold("a")

        assertEquals("a", next.item)
        assertTrue(next.isPending)
    }

    @Test
    fun hold_over_hold_replaces_the_pending_item() {
        val next = PendingConfirmation("old").hold("new")

        assertEquals("new", next.item)
        assertTrue(next.isPending)
    }

    @Test
    fun dismiss_leaves_the_dialog_open_exactly_while_in_flight() {
        for (inFlight in listOf(false, true)) {
            val next = PendingConfirmation("a").dismiss(inFlight)

            if (inFlight) {
                // The refusal is a silent no-op: the item is retained.
                assertEquals("a", next.item)
                assertTrue(next.isPending)
            } else {
                assertNull(next.item)
                assertFalse(next.isPending)
            }
        }
    }

    @Test
    fun confirm_returns_the_item_exactly_when_idle_and_never_clears() {
        for (inFlight in listOf(false, true)) {
            val state = PendingConfirmation("a")

            val confirmed = state.confirm(inFlight)

            if (inFlight) {
                assertNull(confirmed)
            } else {
                assertEquals("a", confirmed)
            }
            // The gate only gates — settle timing is the caller's clear write.
            assertTrue(state.isPending)
            assertEquals(PendingConfirmation("a"), state)
        }
    }

    @Test
    fun in_flight_confirm_leaves_state_untouched() {
        val state = PendingConfirmation("a")

        state.confirm(inFlight = true)

        assertEquals(PendingConfirmation("a"), state)
    }

    @Test
    fun clear_empties_from_any_state() {
        val next = PendingConfirmation("a").clear()

        assertNull(next.item)
        assertFalse(next.isPending)
    }

    @Test
    fun clear_on_already_empty_is_identity() {
        assertEquals(PendingConfirmation<String>(), PendingConfirmation<String>().clear())
    }

    @Test
    fun writes_compose_into_the_documented_dialog_flow() {
        val held = PendingConfirmation<String>().hold("a")

        // The confirm fires the action; while it is in flight the gate is
        // shut and the dialog cannot be dismissed out from under it.
        assertEquals("a", held.confirm(inFlight = false))
        assertNull(held.confirm(inFlight = true))
        assertTrue(held.dismiss(inFlight = true).isPending)

        // Settle: the caller's clear write closes the dialog.
        assertEquals(PendingConfirmation<String>(), held.clear())
    }

    @Test
    fun generic_over_a_data_class_payload() {
        val next = PendingConfirmation<Payload>().hold(Payload(1))

        assertEquals(Payload(1), next.item)
        assertTrue(next.isPending)
        assertEquals(Payload(1), next.confirm(inFlight = false))
        assertEquals(PendingConfirmation<Payload>(), next.dismiss(inFlight = false))
    }

    @Test
    fun generic_over_a_sealed_payload() {
        val next = PendingConfirmation<Request>().hold(Request.Delete("a"))

        assertEquals(Request.Delete("a"), next.confirm(inFlight = false))
        assertNull(next.confirm(inFlight = true))
        assertEquals(PendingConfirmation<Request>(), next.clear())
    }

    @Test
    fun generic_over_unit() {
        val next = PendingConfirmation<Unit>().hold(Unit)

        assertEquals(Unit, next.item)
        assertTrue(next.isPending)
    }
}

private data class Payload(val id: Int)

private sealed interface Request {
    data class Delete(val id: String) : Request
}
