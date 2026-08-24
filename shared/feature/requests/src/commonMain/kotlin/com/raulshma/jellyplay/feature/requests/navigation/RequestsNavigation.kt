package com.raulshma.jellyplay.feature.requests.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.requests.RequestsScreen

fun EntryProviderScope<NavKey>.requestsSection(
    navigator: Navigator,
) {
    entry<Route.Requests> {
        RequestsScreen(
            onBack = { navigator.goBack() },
            onNavigateToDetail = { tmdbId, mediaType ->
                navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
            },
        )
    }
}
