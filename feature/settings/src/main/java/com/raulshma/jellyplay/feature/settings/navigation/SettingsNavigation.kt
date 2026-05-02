package com.raulshma.jellyplay.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.SettingsScreen

fun EntryProviderScope<NavKey>.settingsSection(
    navigator: Navigator,
    onLogout: () -> Unit,
) {
    entry<Route.Settings> {
        SettingsScreen(
            onBack = { navigator.goBack() },
            onLogout = onLogout,
        )
    }
}
