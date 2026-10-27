package com.raulshma.jellyplay.feature.shortcuts.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutAction
import com.raulshma.jellyplay.feature.shortcuts.ShortcutsScreen
import com.raulshma.jellyplay.feature.shortcuts.customize.ShortcutsCustomizeScreen

/**
 * @param supportedActions the shell's executable shortcut actions (the shell
 *   feature's ShortcutActionSurface.supportedActions vocabulary, passed as
 *   plain data — no feature→shell edge). The customize editor never offers an
 *   action outside the set; defaults to every action (the "this shell can run
 *   them all" case).
 */
fun EntryProviderScope<NavKey>.shortcutsSection(
    navigator: Navigator,
    supportedActions: Set<ShortcutAction> = ShortcutAction.entries.toSet(),
) {
    entry<Route.Shortcuts> {
        ShortcutsScreen(
            onBack = { navigator.goBack() },
            onNavigate = { route ->
                navigator.navigate(route)
            },
        )
    }

    entry<Route.ShortcutsCustomize> { entry ->
        ShortcutsCustomizeScreen(
            onBack = { navigator.goBack() },
            initialSurface = entry.initialSurface,
            supportedActions = supportedActions,
        )
    }
}
