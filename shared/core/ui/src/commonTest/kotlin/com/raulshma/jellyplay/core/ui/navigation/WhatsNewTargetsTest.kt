package com.raulshma.jellyplay.core.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The What's-New deep-link vocabulary: target ids resolve ONLY against the
 * compiled-in map (a remote feed cannot invent a destination), highlight ids
 * ride the standard [withHighlightSettingId] dispatch, and unknown ids
 * degrade to no action.
 */
class WhatsNewTargetsTest {

    @Test
    fun `known targets resolve to their routes`() {
        assertEquals(Route.Home, WhatsNewTargets.resolve("home"))
        assertEquals(Route.Downloads, WhatsNewTargets.resolve("downloads"))
        assertEquals(Route.HomeSettings(), WhatsNewTargets.resolve("settings.home"))
        assertEquals(Route.WhatsNew, WhatsNewTargets.resolve("settings.whatsNew"))
    }

    @Test
    fun `highlight ids apply to highlight-capable routes and vanish on the rest`() {
        val highlighted = WhatsNewTargets.resolve("settings.home", highlightSettingId = "discover_rows")
        assertIs<Route.HomeSettings>(highlighted)
        assertEquals("discover_rows", highlighted.highlightSettingId)

        // A top-level destination carries no highlight — the id is ignored,
        // not an error.
        assertEquals(Route.Home, WhatsNewTargets.resolve("home", highlightSettingId = "x"))
    }

    @Test
    fun `unknown and blank targets resolve to null`() {
        assertNull(WhatsNewTargets.resolve("settings.evil"))
        assertNull(WhatsNewTargets.resolve("android.intent.VIEW"))
        assertNull(WhatsNewTargets.resolve(null))
        assertNull(WhatsNewTargets.resolve(""))
        assertNull(WhatsNewTargets.resolve("   "))
    }

    @Test
    fun `every published id resolves`() {
        WhatsNewTargets.ids.forEach { id ->
            assertTrue(
                WhatsNewTargets.resolve(id) != null,
                "published id $id must resolve (a dead entry in the map is a dead deep link)",
            )
        }
    }
}
