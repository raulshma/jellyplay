package com.raulshma.jellyplay.feature.music.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.music.albumdetail.AlbumDetailScreen
import com.raulshma.jellyplay.feature.music.albums.AlbumsScreen
import com.raulshma.jellyplay.feature.music.artistdetail.ArtistDetailScreen
import com.raulshma.jellyplay.feature.music.artists.ArtistsScreen
import com.raulshma.jellyplay.feature.music.genres.GenresScreen
import com.raulshma.jellyplay.feature.music.tracks.TracksScreen

fun EntryProviderScope<NavKey>.musicSection(navigator: Navigator) {
    entry<Route.Artists> {
        ArtistsScreen(
            onItemClick = { artistId ->
                navigator.navigate(Route.ArtistDetail(artistId))
            },
        )
    }
    entry<Route.Albums> {
        AlbumsScreen(
            onItemClick = { albumId ->
                navigator.navigate(Route.AlbumDetail(albumId))
            },
        )
    }
    entry<Route.Tracks> {
        TracksScreen(
            onItemClick = { trackId ->
                navigator.navigate(Route.AudioPlayer(trackId))
            },
        )
    }
    entry<Route.Genres> {
        GenresScreen(
            onItemClick = { genreId ->
                // Navigate to genre items - could use Library with genre filter
            },
        )
    }
    entry<Route.AlbumDetail> { key ->
        AlbumDetailScreen(
            albumId = key.albumId,
            onTrackClick = { trackId ->
                navigator.navigate(Route.AudioPlayer(trackId))
            },
            onArtistClick = { artistId ->
                navigator.navigate(Route.ArtistDetail(artistId))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.ArtistDetail> { key ->
        ArtistDetailScreen(
            artistId = key.artistId,
            onAlbumClick = { albumId ->
                navigator.navigate(Route.AlbumDetail(albumId))
            },
            onTrackClick = { trackId ->
                navigator.navigate(Route.AudioPlayer(trackId))
            },
            onBack = { navigator.goBack() },
        )
    }
}
