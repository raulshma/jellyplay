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
        // Wave 15B: the ViewModelStoreOwner/LifecycleOwner provisioning
        // fallback MUST sit outside RequestsScreen — koinViewModel() evaluates
        // as a default parameter before an in-screen provider would run (see
        // ProvidePlatformLocalsFallback). Wave 15C update: pass-through on
        // ALL shipped surfaces now — android/desktop have their own owners
        // and the web shell provisions at its root
        // (apps/web ProvideWebShellViewModelOwners); the wrapper remains the
        // fallback for non-shell hosts only.
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
