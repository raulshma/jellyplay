package com.raulshma.jellyplay.feature.music.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.model.isVideoType
import com.raulshma.jellyplay.feature.music.albumdetail.AlbumDetailScreen
import com.raulshma.jellyplay.feature.music.albums.AlbumsScreen
import com.raulshma.jellyplay.feature.music.artistdetail.ArtistDetailScreen
import com.raulshma.jellyplay.feature.music.artists.ArtistsScreen
import com.raulshma.jellyplay.feature.music.genres.GenresScreen
import com.raulshma.jellyplay.feature.music.genres.GenreDetailScreen
import com.raulshma.jellyplay.feature.music.moodplaylist.MoodPlaylistDetailScreen
import com.raulshma.jellyplay.feature.music.moodplaylist.MoodPlaylistsScreen
import com.raulshma.jellyplay.feature.music.smartplaylist.SmartPlaylistDetailScreen
import com.raulshma.jellyplay.feature.music.smartplaylist.SmartPlaylistsScreen
import com.raulshma.jellyplay.feature.music.browse.MusicBrowseScreen
import com.raulshma.jellyplay.feature.music.playlists.PlaylistDetailScreen
import com.raulshma.jellyplay.feature.music.playlists.PlaylistsScreen
import com.raulshma.jellyplay.feature.music.tracks.TracksScreen

fun EntryProviderScope<NavKey>.musicSection(navigator: Navigator) {
    entry<Route.MusicBrowse> {
        MusicBrowseScreen(
            onArtistClick = { artistId -> navigator.navigate(Route.ArtistDetail(artistId)) },
            onAlbumClick = { albumId -> navigator.navigate(Route.AlbumDetail(albumId)) },
            onTrackClick = { trackId -> navigator.navigate(Route.AudioPlayer(trackId)) },
            onGenreClick = { genreId, genreName -> navigator.navigate(Route.GenreDetail(genreId, genreName)) },
            onPlaylistClick = { playlistId -> navigator.navigate(Route.PlaylistDetail(playlistId, "")) },
        )
    }
    entry<Route.Artists> {
        ArtistsScreen(
            onItemClick = { artistId ->
                navigator.navigate(Route.ArtistDetail(artistId))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.Albums> {
        AlbumsScreen(
            onItemClick = { albumId ->
                navigator.navigate(Route.AlbumDetail(albumId))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.Tracks> {
        TracksScreen(
            onItemClick = { trackId ->
                navigator.navigate(Route.AudioPlayer(trackId))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.Genres> {
        GenresScreen(
            onItemClick = { genreId, genreName ->
                navigator.navigate(Route.GenreDetail(genreId, genreName))
            },
            onBack = { navigator.goBack() },
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
    entry<Route.GenreDetail> { key ->
        GenreDetailScreen(
            genreName = key.genreName,
            onItemClick = { trackId ->
                navigator.navigate(Route.AudioPlayer(trackId))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.SmartPlaylists> {
        SmartPlaylistsScreen(
            onPlaylistClick = { playlist ->
                navigator.navigate(Route.SmartPlaylistDetail(playlist.id))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.SmartPlaylistDetail> { key ->
        SmartPlaylistDetailScreen(
            playlistId = key.playlistId,
            onTrackClick = { trackId ->
                navigator.navigate(Route.AudioPlayer(trackId))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.MoodPlaylists> {
        MoodPlaylistsScreen(
            onPlaylistClick = { playlist ->
                navigator.navigate(Route.MoodPlaylistDetail(playlist.id))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.MoodPlaylistDetail> { key ->
        MoodPlaylistDetailScreen(
            playlistId = key.playlistId,
            onTrackClick = { trackId ->
                navigator.navigate(Route.AudioPlayer(trackId))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.Playlists> {
        PlaylistsScreen(
            onPlaylistClick = { playlistId, playlistName ->
                navigator.navigate(Route.PlaylistDetail(playlistId, playlistName))
            },
            onBack = { navigator.goBack() },
        )
    }
    entry<Route.PlaylistDetail> { key ->
        PlaylistDetailScreen(
            playlistId = key.playlistId,
            playlistName = key.playlistName,
            onPlayItem = { item ->
                // Video items (movie/episode/music-video) open the video player;
                // audio items fall back to the audio player. The detail screen
                // only invokes this for video items (audio goes through playAll).
                if (item.mediaType.isVideoType) {
                    navigator.navigate(Route.VideoPlayer(itemId = item.id))
                } else {
                    navigator.navigate(Route.AudioPlayer(item.id))
                }
            },
            onBack = { navigator.goBack() },
        )
    }
}
