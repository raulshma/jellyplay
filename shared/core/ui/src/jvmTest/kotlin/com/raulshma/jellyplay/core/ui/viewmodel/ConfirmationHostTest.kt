package com.raulshma.jellyplay.core.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [ConfirmationHost]'s two settle arms — the contract that makes the
 * documented "confirm write never cleared" bug class structurally
 * impossible: the synchronous arm clears inside the same write that returns
 * the target (so a second confirm after settle cannot re-fire), while the
 * deferred arm never clears (settle timing stays the caller's
 * [ConfirmationHost.clear] write) and the caller-owned in-flight fact shuts
 * both the confirm gate and the dismiss.
 */
class ConfirmationHostTest {

    @Test
    fun empty_host_is_not_pending() {
        val host = ConfirmationHost<String>()

        assertFalse(host.isPending)
        assertNull(host.item)
        assertNull(host.current.item)
    }

    @Test
    fun show_holds_the_item_and_activates() {
        val host = ConfirmationHost<String>()

        host.show("a")

        assertEquals("a", host.item)
        assertTrue(host.isPending)
    }

    @Test
    fun show_over_show_replaces_the_pending_item() {
        val host = ConfirmationHost<String>()
        host.show("old")

        host.show("new")

        assertEquals("new", host.item)
    }

    @Test
    fun synchronous_confirm_returns_the_item_and_clears_in_the_same_write() {
        val host = ConfirmationHost<String>()
        host.show("a")

        val confirmed = host.confirm()

        assertEquals("a", confirmed)
        // The settle arm is INSIDE: nothing left armed for the next open.
        assertFalse(host.isPending)
        assertNull(host.item)
    }

    @Test
    fun confirm_after_settle_cannot_re_fire() {
        val host = ConfirmationHost<String>()
        host.show("a")
        host.confirm()

        assertNull(host.confirm())
    }

    @Test
    fun synchronous_confirm_on_an_empty_host_is_a_no_op() {
        val host = ConfirmationHost<String>()

        assertNull(host.confirm())
        assertFalse(host.isPending)
    }

    @Test
    fun in_flight_confirm_gates_and_in_flight_dismiss_is_refused() {
        val host = ConfirmationHost<String>()
        host.show("a")

        assertNull(host.confirm(inFlight = true))
        assertTrue(host.isPending)
        assertEquals("a", host.item)
        host.dismiss(inFlight = true)
        assertTrue(host.isPending)

        // The guard is per-call: once the caller's fact clears, the
        // synchronous arm settles — returns the item AND clears.
        assertEquals("a", host.confirm())
        assertFalse(host.isPending)
    }

    @Test
    fun deferred_confirm_never_clears_settle_is_the_callers_clear_write() {
        val host = ConfirmationHost<String>()
        host.show("a")

        assertEquals("a", host.confirm(inFlight = false))

        // The deferred arm only gates — the dialog stays up over the request.
        assertTrue(host.isPending)
        assertEquals("a", host.item)

        host.clear()
        assertFalse(host.isPending)
    }

    @Test
    fun dismiss_when_idle_clears_the_host() {
        val host = ConfirmationHost<String>()
        host.show("a")

        host.dismiss(inFlight = false)

        assertFalse(host.isPending)
        assertNull(host.item)
    }

    @Test
    fun generic_over_a_data_class_payload() {
        val host = ConfirmationHost<Payload>()
        host.show(Payload(1))

        assertEquals(Payload(1), host.confirm())
        assertFalse(host.isPending)
    }

    @Test
    fun generic_over_unit() {
        val host = ConfirmationHost<Unit>()
        host.show(Unit)

        assertTrue(host.isPending)
        assertEquals(Unit, host.confirm())
        assertFalse(host.isPending)
    }
}

private data class Payload(val id: Int)
