package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

@Stable
class NavigationState(
    val startRoute: NavKey,
    val topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
)

@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>,
): NavigationState {
    // Restore the selected top-level tab across process death. The saver
    // serializes the current tab's NavKey.toString() (data class/object
    // toString() is stable) and looks it back up among the known top-level
    // routes, falling back to the start route if the saved tab is no longer
    // registered (e.g. it was removed in an app update). Previously the
    // restore ignored its input and always returned startRoute, so the user's
    // last-selected tab was silently lost on recreation.
    val topLevelRoute = rememberSaveable(saver = androidx.compose.runtime.saveable.Saver(
        save = { it.value.toString() },
        restore = { saved ->
            val byString = topLevelRoutes.associateBy { key -> key.toString() }
            mutableStateOf(byString[saved] ?: startRoute)
        },
    )) { mutableStateOf(startRoute) }

    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(startRoute, topLevelRoute, backStacks)
    }
}

class Navigator(val state: NavigationState, private val navigateFilter: ((NavKey) -> Boolean)? = null) {
    fun navigate(route: NavKey) {
        if (navigateFilter != null && !navigateFilter(route)) return
        if (route in state.backStacks.keys) {
            if (state.topLevelRoute.value == route) {
                // Already on this tab – pop back to the root screen
                val stack = state.backStacks[route]
                if (stack != null) {
                    while (stack.size > 1) {
                        stack.removeLastOrNull()
                    }
                }
            } else {
                state.topLevelRoute.value = route
            }
        } else {
            state.backStacks[state.topLevelRoute.value]?.add(route)
        }
    }

    fun goBack(): Boolean {
        val currentStack = state.backStacks[state.topLevelRoute.value] ?: return false
        if (currentStack.last() == state.topLevelRoute.value) {
            state.topLevelRoute.value = state.startRoute
            return true
        }
        currentStack.removeLastOrNull()
        return currentStack.isNotEmpty()
    }

    fun currentRoute(): NavKey =
        state.backStacks[state.topLevelRoute.value]?.lastOrNull() ?: state.startRoute

    /**
     * `true` when the current tab is showing only its top-level/root route (no
     * pushed screens to pop). Used to decide whether the system back button
     * should navigate within the app or fall through to the OS (exit). See #62-I.
     */
    fun isAtTabRoot(): Boolean {
        val currentStack = state.backStacks[state.topLevelRoute.value] ?: return true
        return currentStack.size <= 1
    }
}
