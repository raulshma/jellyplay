package com.raulshma.jellyplay.feature.arrqueue.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.arrqueue.ArrQueueScreen

fun EntryProviderScope<NavKey>.arrQueueSection(
    navigator: Navigator,
) {
    entry<Route.ArrQueue> {
        ArrQueueScreen(
            onBack = { navigator.goBack() },
            onOpenArrSettings = { navigator.navigate(Route.ArrSettings()) },
        )
    }
}
