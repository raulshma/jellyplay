package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [tvPrimaryRoutes]: the TV drawer has no overflow menu, so Shortcuts
 * rides as an explicit primary item — but its position must still come from
 * the stored nav customization like every other item (#152). Shortcuts
 * silently pinned last (or dropped) is exactly the "reorder does nothing"
 * shape of report this guards against.
 */
class TvDrawerNavOrderTest {

    private val base = listOf(Route.Home, Route.Library, Route.Search, Route.LiveTv)

    @Test
    fun `shortcuts honors stored rank`() {
        val ordered = tvPrimaryRoutes(
            baseRoutes = base,
            includeShortcuts = true,
            navItemOrder = listOf("Shortcuts", "Home", "Library", "Search", "LiveTv"),
        )
        assertEquals(
            listOf(Route.Shortcuts, Route.Home, Route.Library, Route.Search, Route.LiveTv),
            ordered,
        )
    }

    @Test
    fun `shortcuts mid-list honors stored rank`() {
        val ordered = tvPrimaryRoutes(
            baseRoutes = base,
            includeShortcuts = true,
            navItemOrder = listOf("Home", "Shortcuts", "Library", "Search", "LiveTv"),
        )
        assertEquals(
            listOf(Route.Home, Route.Shortcuts, Route.Library, Route.Search, Route.LiveTv),
            ordered,
        )
    }

    @Test
    fun `empty order keeps base order with shortcuts last`() {
        val ordered = tvPrimaryRoutes(
            baseRoutes = base,
            includeShortcuts = true,
            navItemOrder = emptyList(),
        )
        assertEquals(base + Route.Shortcuts, ordered)
    }

    @Test
    fun `hidden shortcuts is excluded`() {
        val ordered = tvPrimaryRoutes(
            baseRoutes = base,
            includeShortcuts = false,
            navItemOrder = listOf("Shortcuts", "Home", "Library", "Search", "LiveTv"),
        )
        assertEquals(base, ordered)
    }

    @Test
    fun `unknown order entries are ignored`() {
        val ordered = tvPrimaryRoutes(
            baseRoutes = base,
            includeShortcuts = true,
            navItemOrder = listOf("Bogus", "Home"),
        )
        assertEquals(
            listOf(Route.Home, Route.Library, Route.Search, Route.LiveTv, Route.Shortcuts),
            ordered,
        )
    }

    @Test
    fun `partial order appends unordered survivors in input order`() {
        val ordered = tvPrimaryRoutes(
            baseRoutes = base,
            includeShortcuts = true,
            navItemOrder = listOf("LiveTv"),
        )
        assertEquals(
            listOf(Route.LiveTv, Route.Home, Route.Library, Route.Search, Route.Shortcuts),
            ordered,
        )
    }
}
