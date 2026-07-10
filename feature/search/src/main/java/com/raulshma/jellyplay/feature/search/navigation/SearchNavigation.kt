package com.raulshma.jellyplay.feature.search.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.navigatePhotoAware
import com.raulshma.jellyplay.feature.search.SearchScreen

fun EntryProviderScope<NavKey>.searchSection(
    navigator: Navigator,
) {
    entry<Route.Search> {
        SearchScreen(
            onItemClick = { itemId, mediaType, parentId, itemName ->
                navigator.navigatePhotoAware(itemId, mediaType, parentId, itemName)
            },
            onNavigate = { route -> navigator.navigate(route) },
        )
    }
}
