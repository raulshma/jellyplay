package com.raulshma.jellyplay.feature.shortcuts.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.shortcuts.ShortcutsScreen

fun EntryProviderScope<NavKey>.shortcutsSection(
    navigator: Navigator,
) {
    entry<Route.Shortcuts> {
        ShortcutsScreen(
            onBack = { navigator.goBack() },
            onNavigate = { route ->
                navigator.navigate(route)
            },
        )
    }
}
