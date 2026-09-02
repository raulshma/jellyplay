package com.raulshma.jellyplay.feature.syncplay.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.syncplay.SyncPlayScreen

fun EntryProviderScope<NavKey>.syncPlaySection(navigator: Navigator) {
    entry<Route.SyncPlay> {
        SyncPlayScreen(
            onBack = { navigator.goBack() },
        )
    }
}
