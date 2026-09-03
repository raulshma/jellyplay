package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [Navigator] state machine over plain in-memory [NavigationState]s
 * (the `rememberNavigationState` factory is composition-bound and untested):
 *
 *  - navigating a NON-tab route pushes onto the CURRENT tab's stack;
 *  - navigating a registered top-level route switches tabs; navigating the
 *    already-active tab pops it back to its root (re-tap semantics);
 *  - an installable [Navigator] navigate-filter can veto any navigation;
 *  - `goBack` pops a pushed entry, and at a tab root rewires to the START tab
 *    (returning true) — including when the current tab is not the start tab;
 *  - `isAtTabRoot` is true iff the current tab shows only its root, and true
 *    when the current tab has no stack at all;
 *  - `currentRoute` falls back to the start route when the current tab has no
 *    back stack.
 */
class NavigatorTest {

    private fun navigationState(
        vararg tabs: NavKey,
        start: NavKey = tabs.first(),
    ): NavigationState {
        val backStacks: Map<NavKey, NavBackStack<NavKey>> =
            tabs.associateWith { NavBackStack(it) }
        return NavigationState(
            startRoute = start,
            topLevelRoute = mutableStateOf(start),
            backStacks = backStacks,
        )
    }

    @Test
    fun navigate_nonTabRoute_pushesOntoCurrentTabStack() {
        val state = navigationState(Route.Home, Route.Library)
        val navigator = Navigator(state)

        navigator.navigate(Route.MediaDetail("m1"))

        assertEquals(Route.MediaDetail("m1"), navigator.currentRoute())
        assertEquals(2, state.backStacks.getValue(Route.Home).size)
    }

    @Test
    fun navigate_registeredTopLevelRoute_switchesTabWithoutPushing() {
        val state = navigationState(Route.Home, Route.Library)
        val navigator = Navigator(state)

        navigator.navigate(Route.Library)

        assertEquals(Route.Library, state.topLevelRoute.value)
        // The target tab's own stack is untouched — no entry was pushed onto it.
        assertEquals(1, state.backStacks.getValue(Route.Library).size)
        assertEquals(Route.Library, navigator.currentRoute())
    }

    @Test
    fun navigate_activeTabAgain_popsBackToTabRoot() {
        val state = navigationState(Route.Home, Route.Library)
        val navigator = Navigator(state)
        navigator.navigate(Route.MediaDetail("m1"))

        navigator.navigate(Route.Home) // re-tap the already-active tab

        assertEquals(1, state.backStacks.getValue(Route.Home).size)
        assertEquals(Route.Home, navigator.currentRoute())
        assertEquals(Route.Home, state.topLevelRoute.value)
    }

    @Test
    fun navigate_unregisteredTabSwitchThenDetail_pushesOntoNewTab() {
        val state = navigationState(Route.Home, Route.Library)
        val navigator = Navigator(state)
        navigator.navigate(Route.Library)

        navigator.navigate(Route.PersonDetail("p1"))

        assertEquals(Route.PersonDetail("p1"), navigator.currentRoute())
        assertEquals(2, state.backStacks.getValue(Route.Library).size)
        assertEquals(1, state.backStacks.getValue(Route.Home).size)
    }

    @Test
    fun navigateFilter_returningFalse_vetoesNavigation() {
        val state = navigationState(Route.Home, Route.Library)
        val blocked = Navigator(state) { it !is Route.PersonDetail }

        blocked.navigate(Route.PersonDetail("p1"))

        assertEquals(Route.Home, blocked.currentRoute())
        assertEquals(1, state.backStacks.getValue(Route.Home).size)
    }

    @Test
    fun navigateFilter_returningTrue_allowsNavigation() {
        val state = navigationState(Route.Home)
        val navigator = Navigator(state) { true }

        navigator.navigate(Route.MediaDetail("m1"))

        assertEquals(Route.MediaDetail("m1"), navigator.currentRoute())
    }

    @Test
    fun goBack_popsPushedEntryAndReturnsTrue() {
        val state = navigationState(Route.Home)
        val navigator = Navigator(state)
        navigator.navigate(Route.MediaDetail("m1"))

        val handled = navigator.goBack()

        assertTrue(handled)
        assertEquals(Route.Home, navigator.currentRoute())
    }

    @Test
    fun goBack_atRootOfNonStartTab_switchesBackToStartTab() {
        val state = navigationState(Route.Home, Route.Library)
        val navigator = Navigator(state)
        navigator.navigate(Route.Library)

        val handled = navigator.goBack()

        assertTrue(handled, "system back on a non-start tab root must be consumed")
        assertEquals(Route.Home, state.topLevelRoute.value)
    }

    @Test
    fun goBack_atAbsoluteRoot_stillReturnsTrue() {
        // Contract quirk: at the start tab's root goBack rewires to the start
        // route (a no-op) and returns true — callers consult isAtTabRoot to
        // decide whether back should exit the app instead.
        val state = navigationState(Route.Home)
        val navigator = Navigator(state)

        val handled = navigator.goBack()

        assertTrue(handled)
        assertEquals(Route.Home, state.topLevelRoute.value)
    }

    @Test
    fun goBack_withNoStackForCurrentTab_returnsFalse() {
        val state = navigationState(Route.Home)
        // Simulate a tab whose stack was never registered.
        state.topLevelRoute.value = Route.Search

        assertFalse(Navigator(state).goBack())
    }

    @Test
    fun isAtTabRoot_trueAtRoot_falseWithPushes_trueForMissingStack() {
        val state = navigationState(Route.Home)
        val navigator = Navigator(state)

        assertTrue(navigator.isAtTabRoot())

        navigator.navigate(Route.MediaDetail("m1"))
        assertFalse(navigator.isAtTabRoot())

        // A current tab with no registered stack counts as "at root".
        state.topLevelRoute.value = Route.Search
        assertTrue(navigator.isAtTabRoot())
    }

    @Test
    fun currentRoute_fallsBackToStartRoute_whenCurrentTabHasNoStack() {
        val state = navigationState(Route.Home)
        state.topLevelRoute.value = Route.Search

        assertEquals(Route.Home, Navigator(state).currentRoute())
    }

    @Test
    fun navigationState_exposesConstructorArgumentsVerbatim() {
        val start = Route.Home
        val tab = mutableStateOf<NavKey>(start)
        val stacks = mapOf<NavKey, NavBackStack<NavKey>>(start to NavBackStack(start))

        val state = NavigationState(start, tab, stacks)

        assertEquals(start, state.startRoute)
        assertEquals(tab, state.topLevelRoute)
        assertEquals(stacks, state.backStacks)
        // A key with no registered stack reads back null — the fallback path
        // consumed by currentRoute/isAtTabRoot/goBack.
        assertNull(state.backStacks[Route.Search])
    }
}
