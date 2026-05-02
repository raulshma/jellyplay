package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

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
    val topLevelRoute = rememberSaveable(saver = androidx.compose.runtime.saveable.Saver(
        save = { it.value.toString() },
        restore = { mutableStateOf(startRoute) },
    )) { mutableStateOf(startRoute) }

    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(startRoute, topLevelRoute, backStacks)
    }
}

class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute.value = route
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
        state.backStacks[state.topLevelRoute.value]?.last() ?: state.startRoute
}
