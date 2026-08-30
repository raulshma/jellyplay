package com.raulshma.jellyplay.feature.home

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins [HomeSearchSession]'s teardown ORDERING — the choreography that used
 * to live as seven hand-copied triples across HomeScreen and HomeAppBar.
 */
class HomeSearchSessionTest {

    @Test
    fun open_raisesTheExpandedFlag() {
        val session = HomeSearchSession { }

        assertFalse(session.isExpanded)
        session.open()
        assertTrue(session.isExpanded)
    }

    @Test
    fun close_collapsesThenClearsThenDefocuses() {
        val order = mutableListOf<String>()
        val session = HomeSearchSession { event ->
            if (event == HomeUiEvent.ClearSearch) order += "clear"
        }

        session.open()
        session.close {
            // At the moment focus clears, the surface must ALREADY be down
            // and the query ALREADY cleared — the invariant the seven old
            // call-site copies had to maintain by hand.
            order += "defocus(expanded=${session.isExpanded})"
        }

        assertEquals(listOf("clear", "defocus(expanded=false)"), order)
        assertFalse(session.isExpanded)
    }

    @Test
    fun closeThen_runsActionAfterTheFullTeardown() {
        val order = mutableListOf<String>()
        val session = HomeSearchSession { event ->
            if (event == HomeUiEvent.ClearSearch) order += "clear"
        }

        session.open()
        session.closeThen(clearFocus = { order += "defocus" }) {
            order += "action"
        }

        assertEquals(listOf("clear", "defocus", "action"), order)
        assertFalse(session.isExpanded)
    }

    @Test
    fun close_whenNeverOpened_stillClearsAndDefocuses() {
        // Back pressed as the IME dismisses the field: no expand ever
        // observed, but the query and keyboard must not be stranded.
        val order = mutableListOf<String>()
        val session = HomeSearchSession { event ->
            if (event == HomeUiEvent.ClearSearch) order += "clear"
        }

        session.close { order += "defocus" }

        assertEquals(listOf("clear", "defocus"), order)
        assertFalse(session.isExpanded)
    }

    @Test
    fun open_afterClose_reopens() {
        val session = HomeSearchSession { }

        session.open()
        session.close { }
        session.open()

        assertTrue(session.isExpanded)
    }
}
