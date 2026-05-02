package com.raulshma.jellyplay.feature.music.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.music.artists.ArtistsScreen
import com.raulshma.jellyplay.feature.music.albums.AlbumsScreen
import com.raulshma.jellyplay.feature.music.tracks.TracksScreen
import com.raulshma.jellyplay.feature.music.genres.GenresScreen

fun EntryProviderScope<NavKey>.musicSection() {
    entry<Route.Artists> {
        ArtistsScreen(
            onItemClick = { itemId ->
                // Navigate to artist detail or play artist
            },
        )
    }
    entry<Route.Albums> {
        AlbumsScreen(
            onItemClick = { itemId ->
                // Navigate to album detail
            },
        )
    }
    entry<Route.Tracks> {
        TracksScreen(
            onItemClick = { itemId ->
                // Play track
            },
        )
    }
    entry<Route.Genres> {
        GenresScreen(
            onItemClick = { genreId ->
                // Navigate to genre items
            },
        )
    }
}
