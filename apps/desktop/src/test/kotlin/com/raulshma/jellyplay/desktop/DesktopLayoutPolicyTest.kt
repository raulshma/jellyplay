package com.raulshma.jellyplay.desktop

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [desktopTopRouteIsFullscreen] — the desktop rail-visibility read's
 * truth table. The table is TOP-ONLY by design (the KDoc records the
 * deliberate delta vs the Android shell's whole-stack
 * isFullScreenRouteActive scan): the top entry decides, the stack below it
 * is not an input.
 */
class DesktopLayoutPolicyTest {

    private object NotARoute : NavKey

    @Test
    fun `a full-screen route on top reads true`() {
        // The video player — the only full-screen route real desktop
        // navigation can push (the player registration in DesktopNavScaffold).
        assertTrue(desktopTopRouteIsFullscreen(Route.VideoPlayer(itemId = "1")))
        // Other full-screen members count equally when on top.
        assertTrue(desktopTopRouteIsFullscreen(Route.Ambient()))
        assertTrue(desktopTopRouteIsFullscreen(Route.Onboarding))
    }

    @Test
    fun `browse routes on top read false — rail shows`() {
        assertFalse(desktopTopRouteIsFullscreen(Route.Home))
        assertFalse(desktopTopRouteIsFullscreen(Route.Settings))
        assertFalse(desktopTopRouteIsFullscreen(Route.Search))
    }

    @Test
    fun `null and non-route keys read false`() {
        // null = no stack for the current tab yet; non-Route NavKeys are
        // nav3's base type and carry no isFullScreen.
        assertFalse(desktopTopRouteIsFullscreen(null))
        assertFalse(desktopTopRouteIsFullscreen(NotARoute))
    }

    @Test
    fun `a full-screen route below the top is invisible to this read`() {
        // The shape the ANDROID scan exists for (player with the subtitle
        // tester pushed on top) is unreachable here — the subtitle tester is
        // not registered on desktop (its push dead-ends in the guard) — and
        // this read deliberately looks at the top entry alone: were that
        // shape ever reached, the tester as the top would decide (not
        // full-screen → rail shows) and the player underneath would never
        // flip it.
        assertFalse(desktopTopRouteIsFullscreen(Route.SubtitleTester))
    }
}
