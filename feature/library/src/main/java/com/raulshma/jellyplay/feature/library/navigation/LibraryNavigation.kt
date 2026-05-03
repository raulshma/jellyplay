package com.raulshma.jellyplay.feature.library.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.library.LibraryScreen

fun EntryProviderScope<NavKey>.librarySection(
    navigator: Navigator,
) {
    entry<Route.Library> {
        LibraryScreen(
            onItemClick = { itemId -> navigator.navigate(Route.MediaDetail(itemId)) },
            onSmartPlaylistsClick = { navigator.navigate(Route.SmartPlaylists) },
            onMoodPlaylistsClick = { navigator.navigate(Route.MoodPlaylists) },
            onPlaylistsClick = { navigator.navigate(Route.Playlists) },
        )
    }
}
