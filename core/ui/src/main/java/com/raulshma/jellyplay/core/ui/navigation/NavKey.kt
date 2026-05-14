package com.raulshma.jellyplay.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class Route : NavKey {

    @Serializable data object ServerList : Route()
    @Serializable data object AddServer : Route()
    @Serializable data class Login(val serverAddress: String) : Route()
    @Serializable data class QuickConnect(val serverAddress: String) : Route()
    @Serializable data class UserSelection(
        val serverId: String,
        val serverAddress: String,
        val serverName: String,
    ) : Route()

    @Serializable data object Home : Route()
    @Serializable data object Library : Route()
    @Serializable data object Search : Route()
    @Serializable data object LiveTv : Route()

    @Serializable data class MediaDetail(val itemId: String) : Route()
    @Serializable data class SeerrDetail(val tmdbId: Int, val mediaType: String) : Route()
    @Serializable data class PersonDetail(val personId: String) : Route()

    @Serializable data class VideoPlayer(
        val itemId: String,
        val mediaSourceId: String? = null,
        val startPositionTicks: Long = 0,
        val subtitleStreamIndex: Int? = null,
        val audioStreamIndex: Int? = null,
    ) : Route()

    @Serializable data class AudioPlayer(val itemId: String) : Route()

    @Serializable data object Downloads : Route()

    @Serializable data object Settings : Route()

    @Serializable data object ServerManagement : Route()
    @Serializable data object UserManagement : Route()
    @Serializable data object SeerrSettings : Route()

    @Serializable data object Artists : Route()
    @Serializable data object Albums : Route()
    @Serializable data object Tracks : Route()
    @Serializable data object Genres : Route()
    @Serializable data class ArtistDetail(val artistId: String) : Route()
    @Serializable data class AlbumDetail(val albumId: String) : Route()
    @Serializable data object SmartPlaylists : Route()
    @Serializable data class SmartPlaylistDetail(val playlistId: String) : Route()
    @Serializable data object MoodPlaylists : Route()
    @Serializable data class MoodPlaylistDetail(val playlistId: String) : Route()
    @Serializable data class CollectionDetail(val collectionId: String) : Route()
    @Serializable data object Playlists : Route()
    @Serializable data class PlaylistDetail(val playlistId: String) : Route()

    @Serializable data class OfflinePlayer(
        val filePath: String,
        val title: String,
    ) : Route()

    @Serializable data class LiveTvChannelPlayer(
        val channelId: String,
        val channelName: String,
    ) : Route()

    @Serializable data object LiveTvGuide : Route()

    @Serializable data object Dvr : Route()

    @Serializable data object SyncPlay : Route()

    @Serializable data class Ambient(
        val imageUrl: String? = null,
        val title: String = "",
        val artist: String = "",
    ) : Route()

    @Serializable data object MusicBrowse : Route()
}

val VIDEO_TOP_LEVEL_ROUTES = linkedMapOf(
    Route.Home to "Home",
    Route.Library to "Library",
    Route.Search to "Search",
    Route.LiveTv to "Live TV",
)

val MUSIC_TOP_LEVEL_ROUTES = linkedMapOf(
    Route.Home to "Home",
    Route.MusicBrowse to "Browse",
    Route.Search to "Search",
)

val TOP_LEVEL_ROUTES = VIDEO_TOP_LEVEL_ROUTES

val ALL_TOP_LEVEL_ROUTE_KEYS: Set<Route> =
    VIDEO_TOP_LEVEL_ROUTES.keys.union(MUSIC_TOP_LEVEL_ROUTES.keys)
