package com.raulshma.jellyplay.feature.requests.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.requests.ProvidePlatformLocalsFallback
import com.raulshma.jellyplay.feature.requests.RequestsScreen

fun EntryProviderScope<NavKey>.requestsSection(
    navigator: Navigator,
) {
    entry<Route.Requests> {
        // The ViewModelStoreOwner/LifecycleOwner provisioning
        // fallback MUST sit outside RequestsScreen — koinViewModel() evaluates
        // as a default parameter before an in-screen provider would run (see
        // ProvidePlatformLocalsFallback). Pass-through on
        // ALL shipped surfaces — android/desktop have their own owners;
        // the wrapper remains the fallback for hosts without owners
        // (tests, embedded previews) only.
        ProvidePlatformLocalsFallback {
            RequestsScreen(
                onBack = { navigator.goBack() },
                onNavigateToDetail = { tmdbId, mediaType ->
                    navigator.navigate(Route.SeerrDetail(tmdbId, mediaType))
                },
            )
        }
    }
}
