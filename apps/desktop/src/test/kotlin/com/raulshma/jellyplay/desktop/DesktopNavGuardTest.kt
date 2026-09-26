package com.raulshma.jellyplay.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.NavigationState
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * Pins the dead-end guard adapter extracted from DesktopNavScaffold
 * ([desktopGuardedNavigator] + [desktopDeadEndMessage] — the desktop twin of
 * the Android shell's PlaybackHostRouter navigateFilter, in the
 * DesktopUpdateCheckController idiom):
 *
 *  - a route the section-registry ledger HAS registered navigates through
 *    the underlying [com.raulshma.jellyplay.core.ui.navigation.Navigator]
 *    untouched (pushed onto the current tab's stack);
 *  - an unregistered route is swallowed (never reaches the stack — the
 *    crash hazard NavDisplay's throwing default would turn into) and
 *    surfaces ONE snackbar naming the route with the fixed wording;
 *  - the wording is the route's simple class name + the user-facing suffix,
 *    falling back to toString() when the class has no simple name.
 *
 * Over plain in-memory [NavigationState]s (the NavigatorTest idiom — the
 * `rememberNavigationState` factory is composition-bound and untested).
 */
class DesktopNavGuardTest {

    private fun navigationState(vararg tabs: NavKey): NavigationState {
        val backStacks: Map<NavKey, NavBackStack<NavKey>> =
            tabs.associateWith { NavBackStack(it) }
        return NavigationState(
            startRoute = tabs.first(),
            topLevelRoute = mutableStateOf(tabs.first()),
            backStacks = backStacks,
        )
    }

    @Test
    fun `registered routes pass through to the underlying navigator`() = runTest {
        val state = navigationState(Route.Home)
        val navigator = desktopGuardedNavigator(
            navigation = state,
            isRegistered = { true },
            scope = this,
            showMessage = { error("a registered route must never surface the guard snackbar") },
        )

        navigator.navigate(Route.MediaDetail("m1"))
        advanceUntilIdle()

        assertEquals(Route.MediaDetail("m1"), navigator.currentRoute())
        assertEquals(2, state.backStacks.getValue(Route.Home).size)
    }

    @Test
    fun `unregistered routes are swallowed and surface one snackbar`() = runTest {
        val state = navigationState(Route.Home)
        val messages = mutableListOf<String>()
        val navigator = desktopGuardedNavigator(
            navigation = state,
            isRegistered = { route -> route !is Route.MediaDetail },
            scope = this,
            showMessage = { messages.add(it) },
        )

        navigator.navigate(Route.MediaDetail("m1"))
        advanceUntilIdle()

        assertEquals(Route.Home, navigator.currentRoute(), "the dead end never reached the stack")
        assertEquals(1, state.backStacks.getValue(Route.Home).size)
        assertEquals(listOf("MediaDetail is not available on desktop yet."), messages)
    }

    @Test
    fun `the message names the route's simple class name with the fixed suffix`() {
        assertEquals(
            "SubtitleTester is not available on desktop yet.",
            desktopDeadEndMessage(Route.SubtitleTester),
        )
        assertEquals(
            "LiveTvChannelPlayer is not available on desktop yet.",
            desktopDeadEndMessage(Route.LiveTvChannelPlayer(channelId = "c", channelName = "n")),
        )
    }

    @Test
    fun `a class with no simple name still carries the fixed suffix`() {
        // The ?: toString() fallback branch — an anonymous NavKey never occurs
        // in production (every Route leaf is a named class), so the pinned
        // contract is just that the wording degrades, never breaks.
        val anonymous = object : NavKey {}
        assertTrue(
            desktopDeadEndMessage(anonymous).endsWith(" is not available on desktop yet."),
            "the fallback must still carry the user-facing suffix: ${desktopDeadEndMessage(anonymous)}",
        )
    }
}
