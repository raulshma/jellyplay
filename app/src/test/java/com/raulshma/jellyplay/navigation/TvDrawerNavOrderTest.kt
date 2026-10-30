package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS
import com.raulshma.jellyplay.core.ui.navigation.MUSIC_TOP_LEVEL_ROUTES
import com.raulshma.jellyplay.core.ui.navigation.NAV_DESTINATIONS
import com.raulshma.jellyplay.core.ui.navigation.NavPrimarySurface
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.VIDEO_TOP_LEVEL_ROUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [tvPrimaryRoutes]: the TV drawer has no overflow menu, so the registry's
 * TV-drawer placement (Shortcuts) rides as an explicit primary item — but its
 * position must still come from the stored nav customization like every other
 * item (#152). Shortcuts silently pinned last (or dropped) is exactly the
 * "reorder does nothing" shape of report this guards against.
 *
 * The placement itself is DERIVED from the registry's descriptor
 * ([NavPrimarySurface.TvDrawer] on the NAV_DESTINATIONS rows), so the guard
 * tests below tie the drawer's projection back to the phone-bar projection the
 * topLevelGroups tables pin — the two surfaces can only disagree where the
 * descriptor says so.
 */
class TvDrawerNavOrderTest {

    private val base = listOf(Route.Home, Route.Library, Route.Search, Route.LiveTv)

    @Test
    fun `shortcuts honors stored rank`() {
        val ordered = tvPrimaryRoutes(
            baseRoutes = base,
            hiddenNavItems = emptySet(),
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
            hiddenNavItems = emptySet(),
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
            hiddenNavItems = emptySet(),
            navItemOrder = emptyList(),
        )
        assertEquals(base + Route.Shortcuts, ordered)
    }

    @Test
    fun `hidden shortcuts is excluded`() {
        val ordered = tvPrimaryRoutes(
            baseRoutes = base,
            hiddenNavItems = setOf("Shortcuts"),
            navItemOrder = listOf("Shortcuts", "Home", "Library", "Search", "LiveTv"),
        )
        assertEquals(base, ordered)
    }

    @Test
    fun `unknown order entries are ignored`() {
        val ordered = tvPrimaryRoutes(
            baseRoutes = base,
            hiddenNavItems = emptySet(),
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
            hiddenNavItems = emptySet(),
            navItemOrder = listOf("LiveTv"),
        )
        assertEquals(
            listOf(Route.LiveTv, Route.Home, Route.Library, Route.Search, Route.Shortcuts),
            ordered,
        )
    }

    // ── derivation guards: registry descriptor ⇒ consumer projections agree ──

    @Test
    fun `tv drawer projection is the phone bar projection plus the registry placement`() {
        // The phone bar renders the mode's top-level table as-is (both shells
        // push the same map through visibleTopLevelRoutes); the TV drawer is
        // that same base plus the descriptor's BOLT-ON placements
        // (TvDrawer minus PhoneBar — the bar tabs themselves must stay
        // mode-selected: a video-mode drawer has no MusicBrowse). Both home
        // modes must agree.
        assertEquals(
            VIDEO_TOP_LEVEL_ROUTES.keys.toList() + Route.Shortcuts,
            tvPrimaryRoutes(
                baseRoutes = VIDEO_TOP_LEVEL_ROUTES.keys.toList(),
                hiddenNavItems = emptySet(),
                navItemOrder = emptyList(),
            ),
        )
        assertEquals(
            MUSIC_TOP_LEVEL_ROUTES.keys.toList() + Route.Shortcuts,
            tvPrimaryRoutes(
                baseRoutes = MUSIC_TOP_LEVEL_ROUTES.keys.toList(),
                hiddenNavItems = emptySet(),
                navItemOrder = emptyList(),
            ),
        )
    }

    @Test
    fun `per-surface placement matches today's pinned membership`() {
        NAV_DESTINATIONS.forEach { row ->
            val isBarTab = row.route in ALL_TOP_LEVEL_ROUTE_KEYS
            // Phone bar: bar tabs only — Shortcuts parks in the ⋮ overflow.
            assertEquals(isBarTab, NavPrimarySurface.PhoneBar in row.primarySurfaces)
            // TV drawer: the phone-bar set plus the descriptor's bolt-on
            // placements (Shortcuts — no overflow menu to park it in).
            assertEquals(
                isBarTab || row.route == Route.Shortcuts,
                NavPrimarySurface.TvDrawer in row.primarySurfaces,
            )
            // Desktop rail: the full registry renders.
            assertTrue(NavPrimarySurface.DesktopRail in row.primarySurfaces)
        }
    }

    @Test
    fun `shortcuts is the only phone-bar vs tv-drawer placement delta`() {
        // The settings customization UI's TV fork (TvDrawer rows) versus the
        // non-TV fork (PhoneBar rows) may differ only by the Shortcuts row —
        // the same single delta the drawer projection above pins. A second
        // delta here means a surface disagrees with the customization
        // vocabulary and needs its own row, not a silent filter.
        val deltas = NAV_DESTINATIONS
            .filter { row ->
                (NavPrimarySurface.PhoneBar in row.primarySurfaces) !=
                    (NavPrimarySurface.TvDrawer in row.primarySurfaces)
            }
            .map { it.route }
        assertEquals(listOf(Route.Shortcuts), deltas)
    }
}
