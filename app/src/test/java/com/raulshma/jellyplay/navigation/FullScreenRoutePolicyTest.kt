package com.raulshma.jellyplay.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [isFullScreenRouteActive] — the full-screen back-stack scan that keeps
 * the shell's full-screen layout branch (and its NavDisplay subtree)
 * registered for the whole round-trip under a pushed-on-top route (e.g.
 * SubtitleTester over the video player). See the KDoc for the crash this
 * fold prevents.
 */
class FullScreenRoutePolicyTest {

    private object NotARoute : NavKey

    @Test
    fun `null and empty stacks read false`() {
        assertFalse(isFullScreenRouteActive(null))
        assertFalse(isFullScreenRouteActive(emptyList()))
    }

    @Test
    fun `a full-screen route anywhere on the stack reads true`() {
        assertTrue(isFullScreenRouteActive(listOf<NavKey>(Route.VideoPlayer(itemId = "1"))))
        // Below the top — the case the fold exists for: SubtitleTester pushed
        // on top of the player must not flip the layout branch mid-round-trip.
        assertTrue(
            isFullScreenRouteActive(
                listOf(Route.Home, Route.VideoPlayer(itemId = "1"), Route.SubtitleTester),
            ),
        )
    }

    @Test
    fun `browse-only stacks read false`() {
        assertFalse(
            isFullScreenRouteActive(listOf<NavKey>(Route.Home, Route.Search, Route.Settings)),
        )
    }

    @Test
    fun `mixed stacks read true only on the full-screen member`() {
        // Non-Route NavKeys and browse routes never count; the full-screen
        // member does regardless of position.
        assertFalse(isFullScreenRouteActive(listOf<NavKey>(NotARoute, Route.Home)))
        assertTrue(isFullScreenRouteActive(listOf<NavKey>(NotARoute, Route.Ambient())))
    }
}
